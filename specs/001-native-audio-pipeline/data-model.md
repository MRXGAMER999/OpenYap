# Data Model: Native Audio Pipeline

**Feature**: 001-native-audio-pipeline
**Date**: 2026-03-13

---

## Entities

### 1. AudioRecorder (existing interface — unchanged)

**Location**: `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt`

```kotlin
interface AudioRecorder {
    suspend fun startRecording(outputPath: String)
    suspend fun stopRecording(): String
    val amplitudeFlow: StateFlow<Float>
    suspend fun hasPermission(): Boolean
}
```

**Notes**: No changes. Both `JvmAudioRecorder` and `NativeAudioRecorder` implement this interface. The interface is defined in `commonMain` per Constitution I.

---

### 2. NativeAudioBridge (new — singleton)

**Location**: `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt`

| Field | Type | Description |
|---|---|---|
| `instance` | `OpenYapNative?` | Lazily-loaded JNA library instance. `null` if DLL unavailable. |
| `isAvailable` | `Boolean` | Derived: `instance != null` |

**Lifecycle**:
- Initialized once (lazy) on first access
- Calls `openyap_init()` during initialization (MFStartup)
- Never unloaded during application lifetime
- `openyap_shutdown()` called via JVM shutdown hook

**State transitions**: None — immutable after initialization.

---

### 3. OpenYapNative (new — JNA interface)

**Location**: `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt` (inner interface)

Defines the JNA bindings to the native DLL's C API:

| Function | Parameters | Return | Description |
|---|---|---|---|
| `openyap_init` | — | `Int` | Initialize Media Foundation. 0 = success. |
| `openyap_shutdown` | — | — | Cleanup MF resources. |
| `openyap_capture_start` | `sampleRate: Int, channels: Int, callback: AudioCallback, userData: Pointer?` | `Int` | Start WASAPI capture. 0 = success. |
| `openyap_capture_stop` | — | `Int` | Stop capture. 0 = success. |
| `openyap_encode_aac` | `pcmData: Pointer, pcmSampleCount: Int, sampleRate: Int, channels: Int, bitrate: Int, outputPath: String` | `Int` | Encode PCM to AAC file. 0 = success. |
| `openyap_vad_is_speech` | `pcmData: ShortArray, sampleCount: Int, sampleRate: Int` | `Int` | 1 = speech, 0 = silence. |
| `openyap_amplitude` | `pcmData: ShortArray, sampleCount: Int` | `Float` | Peak amplitude 0.0-1.0. |
| `openyap_last_error` | — | `String?` | Last error description. |

**Nested types**:

| Type | Description |
|---|---|
| `AudioCallback : Callback` | `fun invoke(pcmData: Pointer, sampleCount: Int, userData: Pointer?)` — fires every ~10-20ms with PCM chunks from WASAPI capture thread |

---

### 4. NativeAudioRecorder (new — AudioRecorder implementation)

**Location**: `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt`

| Field | Type | Visibility | Description |
|---|---|---|---|
| `native` | `OpenYapNative` | private | Non-null ref from `NativeAudioBridge.instance!!` |
| `_amplitudeFlow` | `MutableStateFlow<Float>` | private | Updated from capture callback |
| `amplitudeFlow` | `StateFlow<Float>` | public (override) | Read-only exposure |
| `pcmBuffer` | `ConcurrentLinkedQueue<ShortArray>` | private | Lock-free accumulator for PCM chunks |
| `captureCallback` | `OpenYapNative.AudioCallback` | private val | **Strong reference** — prevents JNA GC crash (FR-012) |
| `outputPath` | `String?` | private | Temp file path for current recording |

**State transitions**:

```
Idle ──startRecording(path)──> Capturing
  - Clears pcmBuffer
  - Stores outputPath
  - Sets CallbackThreadInitializer(detach=false)
  - Calls openyap_capture_start(48000, 1, captureCallback, null)

Capturing ──stopRecording()──> Encoding ──> Idle
  - Calls openyap_capture_stop()
  - Drains pcmBuffer into contiguous ShortArray
  - Applies VAD trimming (openyap_vad_is_speech per 20ms frame)
  - Calls openyap_encode_aac(..., outputPath)
  - Returns outputPath (AAC file)
  - Resets amplitudeFlow to 0f
```

**Validation rules**:
- `startRecording` must not be called while already capturing
- `captureCallback` must be stored as an instance field (never local/lambda)
- PCM data from `Pointer` must be copied immediately (pointer invalid after callback return)

---

### 5. GeminiClient.processAudio (modified signature)

**Location**: `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt`

**Current**:
```kotlin
suspend fun processAudio(
    audioBytes: ByteArray,
    systemPrompt: String,
    apiKey: String,
    model: String,
): String
```

**Proposed**:
```kotlin
suspend fun processAudio(
    audioBytes: ByteArray,
    mimeType: String,       // NEW — "audio/aac" or "audio/wav"
    systemPrompt: String,
    apiKey: String,
    model: String,
): String
```

**Change**: Replace hardcoded `"audio/wav"` with the `mimeType` parameter in the `InlineData` construction.

---

### 6. RecordingViewModel (modified — minimal changes)

**Location**: `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt`

**Changes required**:

| Area | Current | Proposed |
|---|---|---|
| Temp file extension | `.wav` (line 208) | Configurable: `.aac` when native, `.wav` when fallback |
| `processAudio` call | No `mimeType` param | Pass `mimeType` based on active recorder type |
| Constructor | `audioRecorder: AudioRecorder` | No change — remains interface-typed |

**New field needed**:
```kotlin
private val audioMimeType: String   // Injected: "audio/aac" or "audio/wav"
```

Or alternatively, a simple boolean/enum:
```kotlin
private val useNativeAudio: Boolean  // Determines extension + mime type
```

---

### 7. RecordingState (existing — unchanged)

```kotlin
sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val durationSeconds: Int = 0, val amplitude: Float = 0f) : RecordingState
    data object Processing : RecordingState
    data class Success(val text: String, val charCount: Int) : RecordingState
    data class Error(val message: String) : RecordingState
}
```

No changes. The recording state machine is unaffected by which recorder implementation is active.

---

## Relationships

```
                  ┌─────────────────────┐
                  │   AudioRecorder     │  (commonMain interface)
                  │   interface         │
                  └─────────┬───────────┘
                            │ implements
               ┌────────────┴────────────┐
               │                         │
    ┌──────────▼──────────┐  ┌───────────▼──────────┐
    │  JvmAudioRecorder   │  │ NativeAudioRecorder  │  (jvmMain)
    │  (fallback, WAV)    │  │ (native, AAC)        │
    └─────────────────────┘  └───────────┬──────────┘
                                         │ uses
                              ┌──────────▼──────────┐
                              │  NativeAudioBridge   │  (jvmMain singleton)
                              │  .instance           │
                              └──────────┬──────────┘
                                         │ JNA loads
                              ┌──────────▼──────────┐
                              │  openyap_native.dll  │  (C++ DLL)
                              │  OpenYapNative iface │
                              └─────────────────────┘

    ┌─────────────────────┐        ┌──────────────────┐
    │  RecordingViewModel │──uses──│  AudioRecorder    │
    │                     │──uses──│  GeminiClient     │
    │  (commonMain)       │        │  (mimeType param) │
    └─────────────────────┘        └──────────────────┘

    ┌──────────────┐
    │   main.kt    │  Factory: NativeAudioBridge.isAvailable
    │  (jvmMain)   │    ? NativeAudioRecorder()
    │              │    : JvmAudioRecorder()
    └──────────────┘
```

## Native C API Data Structures

These are not Kotlin entities but define the contract between the DLL and JNA:

### PCM Audio Format

| Property | Value | Notes |
|---|---|---|
| Sample rate | 48000 Hz | WASAPI auto-converts from device native rate |
| Bit depth | 16-bit signed | `short` / `int16_t` |
| Channels | 1 (mono) | WASAPI auto-converts from stereo |
| Byte order | Little-endian | Windows native |
| Frame size | 2 bytes | 16-bit * 1 channel |

### AAC Output Format

| Property | Value | Notes |
|---|---|---|
| Codec | AAC-LC | Via Windows Media Foundation |
| Container | ADTS | `MF_MT_AAC_PAYLOAD_TYPE = 1` (not raw) |
| Sample rate | 48000 Hz | Matches PCM input |
| Bitrate | 96000 bps (minimum) | MF AAC encoder minimum |
| Channels | 1 (mono) | Matches PCM input |
| MIME type | `audio/aac` | Sent to Gemini API |

### VAD Frame Parameters

| Property | Value | Notes |
|---|---|---|
| Frame duration | 20 ms | Standard VAD frame size |
| Samples per frame | 960 | 48000 Hz * 0.020 s |
| Algorithm | Energy-based RMS | Compare against adaptive threshold |
| Hangover | ~300 ms (15 frames) | Keep frames after last speech detection |
| Leading margin | 200 ms (10 frames) | Preserve before first speech |
| Trailing margin | 200 ms (10 frames) | Preserve after last speech |
