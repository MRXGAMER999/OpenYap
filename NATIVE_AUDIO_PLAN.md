# Native C++ Audio Pipeline - Implementation Plan

## Problem

Audio is currently captured via Java Sound API as uncompressed WAV (32 KB/sec at 16kHz), base64-encoded (+33%), and sent to Gemini. For a 30-second recording, that's ~1.28 MB uploaded. This is the single biggest latency contributor after the non-streaming API.

## Solution

Replace the entire Java-based audio pipeline with a native C++ DLL (`openyap_native.dll`) that handles:

1. **Audio Capture** via WASAPI (replaces `javax.sound.sampled`) at 48kHz (required by MF AAC)
2. **Voice Activity Detection (VAD)** to trim silence
3. **Noise Suppression** via Windows audio processing
4. **AAC Encoding** via Windows Media Foundation (zero dependencies, minimum 96 kbps)
5. All accessed from Kotlin via the existing JNA setup

Expected payload reduction: **~2.7x** (from ~1.28 MB to ~480 KB for a 30-sec recording).

### Target Model

**`gemini-3.1-flash-lite-preview`** — our most cost-efficient option. Confirmed audio input support
including `audio/aac`. Does NOT support Live API or audio generation, so this plan uses the
batch `generateContent` endpoint with inline base64 audio data.

### Key Constraints (verified against official docs)

| Constraint | Detail |
|------------|--------|
| **MF AAC sample rate** | Only 44100 Hz and 48000 Hz input supported. 16kHz is NOT valid. |
| **MF AAC min bitrate** | 96 kbps (12,000 bytes/sec). Cannot go lower. |
| **MF AAC payload type** | Must set `MF_MT_AAC_PAYLOAD_TYPE = 1` (ADTS). Default is 0 (raw `raw_data_block`), which Gemini cannot parse. ADTS wraps each frame with sync headers so the stream is self-describing. Available since Windows 8. |
| **JNA Callback GC** | Must store strong reference to any `Callback` passed to native. JNA uses `WeakReference` internally — GC'd callbacks crash the JVM. |
| **JNA version** | Project currently uses 5.16.0. Update to **5.17.0** (latest) — includes callback stability improvements relevant to this plan. |
| **Gemini inline limit** | 20 MB max for inline audio data. ~20 min of AAC at 96 kbps. |
| **Gemini audio formats** | Supported: WAV, MP3, AIFF, **AAC**, OGG Vorbis, FLAC. Opus is NOT listed. |
| **CMake best practice** | Use `target_compile_features(... PRIVATE cxx_std_17)` not `set(CMAKE_CXX_STANDARD)`. |

## Architecture

```
                         Kotlin (JVM)
                              |
                           JNA calls
                              |
                    openyap_native.dll (C++)
                     /        |        \
                WASAPI    Media Found.   WebRTC VAD
               (capture)  (AAC encode)   (optional)
```

### Current vs Proposed Flow

```
CURRENT:
  TargetDataLine -> ByteArrayOutputStream -> WAV file -> readBytes() -> Base64 -> Gemini
  (Java Sound)     (in-memory PCM, 16kHz)   (disk)      (JVM)         (JVM)
  Format: 16kHz, 16-bit, mono PCM -> WAV -> ~32 KB/sec + 33% Base64

PROPOSED:
  WASAPI -> Ring Buffer -> VAD trim -> MF AAC Encode -> byte[] returned to JVM -> Base64 -> Gemini
  (native)  (native)      (native)    (native, 48kHz)   (JNA callback)           (JVM)
  Format: 48kHz, 16-bit, mono PCM -> AAC (96 kbps min) -> ~12 KB/sec + 33% Base64
```

### Expected Payload Reduction

> **Important:** The Windows Media Foundation AAC encoder has a **minimum output bitrate of 96 kbps**
> (12,000 bytes/sec). Lower bitrates like 16 kbps are NOT supported by MF AAC. The actual reduction
> is ~2.7x compared to uncompressed WAV, not ~16x as initially estimated.

| Duration | Current (WAV + Base64) | Proposed (AAC + Base64) | Reduction |
|----------|----------------------|------------------------|-----------|
| 10 sec   | ~427 KB              | ~160 KB                | **~2.7x** |
| 30 sec   | ~1.28 MB             | ~480 KB                | **~2.7x** |
| 60 sec   | ~2.56 MB             | ~960 KB                | **~2.7x** |

AAC at 96 kbps (MF minimum) = ~12 KB/sec, plus Base64 overhead (+33%) = ~16 KB/sec.

> **Note on Gemini inline data limit:** Gemini supports up to **20 MB** for inline audio data.
> Even at 96 kbps AAC, a 60-second recording is well under this limit (~960 KB).
> The 20 MB limit allows recordings up to ~20 minutes before needing the File API.

---

## Module Structure

```
OpenYap/
  native/                          # NEW - C++ project
    CMakeLists.txt
    src/
      openyap_native.h             # Public C API (JNA-compatible)
      openyap_native.cpp           # DLL entry point + API impl
      audio_capture.h              # WASAPI capture
      audio_capture.cpp
      audio_encoder.h              # Media Foundation AAC encoder
      audio_encoder.cpp
      vad.h                        # Voice Activity Detection
      vad.cpp
      noise_suppressor.h           # Audio preprocessing
      noise_suppressor.cpp
    build/                         # CMake output
    prebuilt/
      windows-x64/
        openyap_native.dll         # Prebuilt for distribution

  shared/
    src/jvmMain/kotlin/com/openyap/platform/
      NativeAudioBridge.kt         # NEW - JNA bindings to DLL
      NativeAudioRecorder.kt       # NEW - implements AudioRecorder interface
      JvmAudioRecorder.kt          # KEPT as fallback
```

---

## Phase 1: Core DLL Skeleton + AAC Encoding

**Goal:** Build a DLL that takes raw PCM bytes and returns AAC-encoded bytes. Validate the encoding works with Gemini.

### 1.1 C++ Public API (`openyap_native.h`)

```c
// All functions use C linkage for JNA compatibility
#ifdef __cplusplus
extern "C" {
#endif

// --- Lifecycle ---
// Initialize Media Foundation. Call once at app startup.
int openyap_init(void);

// Cleanup. Call once at app shutdown.
void openyap_shutdown(void);

// --- Audio Encoding ---
// Encode raw PCM (16-bit, mono) to ADTS-wrapped AAC via Windows Media Foundation.
//
// IMPORTANT: MF AAC Encoder constraints:
//   - Supported input sample rates: 44100 Hz and 48000 Hz ONLY
//   - Minimum output bitrate: 96000 bps (96 kbps)
//   - 16kHz input is NOT supported; capture at 48kHz and encode at 48kHz
//   - Output MUST use MF_MT_AAC_PAYLOAD_TYPE = 1 (ADTS), not 0 (raw).
//     Raw AAC frames lack sync headers and Gemini cannot parse them.
//     ADTS is supported since Windows 8.
//
// Returns: number of bytes written to out_buffer, or negative error code.
int openyap_encode_aac(
    const short* pcm_data,       // Input: PCM samples (signed 16-bit)
    int pcm_sample_count,        // Input: number of samples
    int sample_rate,             // Input: 44100 or 48000 (MF AAC requirement)
    int channels,                // Input: 1 (mono) or 2 (stereo)
    int bitrate,                 // Input: minimum 96000 (96 kbps)
    unsigned char* out_buffer,   // Output: caller-allocated buffer
    int out_buffer_size          // Output: buffer capacity
);

// --- Audio Capture ---
// Start capturing audio from default input device via WASAPI.
// Callback fires with PCM chunks as they arrive.
typedef void (*audio_callback_t)(const short* pcm_data, int sample_count, void* user_data);

int openyap_capture_start(
    int sample_rate,
    int channels,
    audio_callback_t callback,
    void* user_data
);

int openyap_capture_stop(void);

// --- VAD ---
// Returns 1 if the PCM chunk contains speech, 0 if silence.
int openyap_vad_is_speech(
    const short* pcm_data,
    int sample_count,
    int sample_rate
);

// --- Amplitude ---
// Returns peak amplitude (0.0 - 1.0) of a PCM chunk.
float openyap_amplitude(
    const short* pcm_data,
    int sample_count
);

// --- Error Handling ---
const char* openyap_last_error(void);

#ifdef __cplusplus
}
#endif
```

### 1.2 JNA Bindings (`NativeAudioBridge.kt`)

> **CRITICAL: JNA Callback GC Risk**
>
> From JNA's `Callback` javadoc: *"If native code attempts to call a callback which has been GC'd,
> you will likely crash the VM."* `CallbackReference` uses a `WeakReference<Callback>` internally.
> You **must** store a strong reference to any callback passed to native code for as long as the
> native code may invoke it. Passing a lambda or anonymous object without storing it will crash.

```kotlin
// --- JNA Interface (no companion; loading is handled by NativeAudioBridge) ---

interface OpenYapNative : Library {
    fun openyap_init(): Int
    fun openyap_shutdown()

    fun openyap_encode_aac(
        pcmData: ShortArray,
        pcmSampleCount: Int,
        sampleRate: Int,        // Must be 44100 or 48000
        channels: Int,
        bitrate: Int,           // Minimum 96000
        outBuffer: ByteArray,
        outBufferSize: Int,
    ): Int

    fun openyap_capture_start(
        sampleRate: Int,
        channels: Int,
        callback: AudioCallback,
        userData: Pointer?,
    ): Int

    fun openyap_capture_stop(): Int

    fun openyap_vad_is_speech(
        pcmData: ShortArray,
        sampleCount: Int,
        sampleRate: Int,
    ): Int

    fun openyap_amplitude(
        pcmData: ShortArray,
        sampleCount: Int,
    ): Float

    fun openyap_last_error(): String?

    fun interface AudioCallback : Callback {
        fun invoke(pcmData: Pointer, sampleCount: Int, userData: Pointer?)
    }
}

// --- Single entry point for DLL loading, init, and availability ---

object NativeAudioBridge {
    val instance: OpenYapNative? by lazy {
        try {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
            val dllName = resourcesDir?.let { "$it/openyap_native" } ?: "openyap_native"
            val lib = Native.load(dllName, OpenYapNative::class.java)
            lib.openyap_init()
            lib
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }

    val isAvailable: Boolean get() = instance != null
}
```

### 1.3 CMake Build (`CMakeLists.txt`)

```cmake
cmake_minimum_required(VERSION 3.20)
project(openyap_native LANGUAGES CXX)

add_library(openyap_native SHARED
    src/openyap_native.cpp
    src/audio_encoder.cpp
    src/audio_capture.cpp
    src/vad.cpp
    src/noise_suppressor.cpp
)

# Modern CMake: use target_compile_features instead of set(CMAKE_CXX_STANDARD 17)
# PRIVATE because this is a standalone DLL consumed via JNA, not by other CMake targets
target_compile_features(openyap_native PRIVATE cxx_std_17)

# Windows Media Foundation + WASAPI
target_link_libraries(openyap_native PRIVATE
    mfplat mfreadwrite mfuuid mf
    wmcodecdspuuid
    ole32 propsys
    mmdevapi
)

# Export all public symbols
target_compile_definitions(openyap_native PRIVATE OPENYAP_EXPORTS)

# Install prebuilt DLL
install(TARGETS openyap_native
    RUNTIME DESTINATION ${CMAKE_SOURCE_DIR}/prebuilt/windows-x64
)
```

### 1.4 Tasks

| # | Task | Details |
|---|------|---------|
| 1 | Create `native/` directory and CMake project | CMakeLists.txt + src/ structure |
| 2 | Implement `openyap_init` / `openyap_shutdown` | `MFStartup()` / `MFShutdown()` |
| 3 | Implement `openyap_encode_aac` | MFTransform AAC encoder: 48kHz PCM -> ADTS AAC (96 kbps min, `MF_MT_AAC_PAYLOAD_TYPE=1`) |
| 4 | Test AAC output with Gemini | Verify Gemini accepts `audio/aac` with the encoded output |
| 5 | Create `NativeAudioBridge.kt` JNA bindings | Load DLL, expose encode function, store strong callback refs |
| 6 | Wire encoding into `GeminiClient.kt` | Replace base64 WAV with base64 AAC |

---

## Phase 2: Native Audio Capture (WASAPI)

**Goal:** Replace `javax.sound.sampled.TargetDataLine` with WASAPI loopback/capture.

### Why WASAPI over Java Sound?

| Feature | Java Sound | WASAPI |
|---------|-----------|--------|
| Latency | ~40-100ms | ~10ms |
| Exclusive mode | No | Yes |
| Per-device selection | Limited | Full |
| Audio processing pipeline | No | Yes (APO) |
| Reliable on modern Windows | Flaky | Native |

### 2.1 Implementation (`audio_capture.cpp`)

```
WASAPI Shared Mode Capture
  -> IAudioClient::Initialize(AUDCLK_SHARED, ...)
  -> IAudioCaptureClient::GetBuffer()
  -> Resample to 48kHz mono if needed (MFT resampler)
     (48kHz required by MF AAC encoder; 16kHz is NOT supported)
  -> Fire callback with PCM chunks
  -> Ring buffer for amplitude calculation
```

### 2.2 `NativeAudioRecorder.kt`

New class implementing the existing `AudioRecorder` interface:

```kotlin
class NativeAudioRecorder : AudioRecorder {
    private val native = NativeAudioBridge.instance!!  // only constructed when isAvailable == true
    private val _amplitudeFlow = MutableStateFlow(0f)
    private val pcmBuffer = ConcurrentLinkedQueue<ShortArray>()

    // CRITICAL: Store strong reference to callback to prevent GC crash.
    // JNA's CallbackReference uses WeakReference internally. If the callback
    // is GC'd while native code still holds a pointer to it, the JVM will crash.
    private val captureCallback = OpenYapNative.AudioCallback { pcmData, sampleCount, _ ->
        val samples = pcmData.getShortArray(0, sampleCount)
        pcmBuffer.add(samples)
        _amplitudeFlow.value = native.openyap_amplitude(samples, sampleCount)
    }

    override val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    override suspend fun startRecording(outputPath: String) {
        pcmBuffer.clear()
        native.openyap_capture_start(48000, 1, captureCallback, null)
    }

    override suspend fun stopRecording(): String {
        native.openyap_capture_stop()

        // Drain all PCM chunks without boxing (ConcurrentLinkedQueue has no drain())
        val chunks = buildList {
            while (true) { add(pcmBuffer.poll() ?: break) }
        }
        val totalSamples = chunks.sumOf { it.size }
        val allSamples = ShortArray(totalSamples).also { dest ->
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(dest, offset)
                offset += chunk.size
            }
        }

        // Encode to ADTS-wrapped AAC
        val outBuffer = ByteArray(totalSamples)  // always larger than compressed output
        val bytesWritten = native.openyap_encode_aac(
            allSamples, totalSamples,
            48000,   // sample rate: must be 44100 or 48000 for MF AAC
            1,       // channels: mono
            96000,   // bitrate: 96 kbps (MF AAC minimum)
            outBuffer, outBuffer.size
        )
        val aacBytes = outBuffer.copyOf(bytesWritten)
        // Write to temp file or return bytes directly
        ...
    }
}
```

### 2.3 Tasks

| # | Task | Details |
|---|------|---------|
| 1 | Implement WASAPI capture in `audio_capture.cpp` | Shared mode, default device, 48kHz mono output |
| 2 | Add MFT resampler for format conversion | Handle devices with different native formats, output 48kHz |
| 3 | Implement callback mechanism | Fire `audio_callback_t` from capture thread |
| 4 | Create `NativeAudioRecorder.kt` | Implement `AudioRecorder` interface using JNA bridge; store strong callback ref |
| 5 | Wire into `main.kt` | Replace `JvmAudioRecorder()` with `NativeAudioRecorder()` |
| 6 | Keep `JvmAudioRecorder` as fallback | Use if DLL fails to load |

---

## Phase 3: VAD + Noise Suppression

**Goal:** Trim silence from recordings and reduce background noise before encoding.

### 3.1 Voice Activity Detection (`vad.cpp`)

Simple energy-based VAD (no external library needed):

```
For each ~20ms frame (960 samples at 48kHz):
  1. Compute RMS energy
  2. Compare against adaptive threshold
  3. Apply hangover (keep N frames after last speech)
  4. Mark frame as speech/silence
```

This trims leading/trailing silence and can reduce a 30-second recording with 10 seconds of actual speech down to ~12 seconds (speech + small margins).

### 3.2 Noise Suppression (`noise_suppressor.cpp`)

Two approaches, simplest first:

**Option A: Windows Communication-Mode Capture (APO chain)**
- Windows has built-in noise suppression + echo cancellation in the communications audio pipeline
- Enable by calling `IAudioClient2::SetClientProperties` with `AudioCategory_Communications`
  before initializing the WASAPI stream. This activates the endpoint's APO chain
  (noise suppression, echo cancellation, automatic gain control).
- Note: `AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM` only enables format conversion (resampling),
  NOT noise suppression. Don't confuse the two.
- Alternative: use the Voice Capture DSP MFT (`CLSID_CWMAudioAEC`) for explicit control
- Free, zero external dependencies, but quality varies by device/driver

**Option B: Simple spectral subtraction**
- Estimate noise profile from first 0.5s of recording
- Subtract noise spectrum from subsequent frames
- ~100 lines of C++, good results for stationary noise

### 3.3 Tasks

| # | Task | Details |
|---|------|---------|
| 1 | Implement energy-based VAD | `openyap_vad_is_speech()` per-frame |
| 2 | Add VAD trimming to capture pipeline | Strip leading/trailing silence |
| 3 | Enable Windows APO noise suppression | `IAudioClient2::SetClientProperties` with `AudioCategory_Communications` |
| 4 | (Optional) Spectral subtraction | For environments where APO isn't enough |

---

## Phase 4: Integration + Fallback

**Goal:** Clean integration with the existing codebase, graceful fallback if DLL is missing.

### 4.1 DLL Loading Strategy

> Loading, init, and availability are all handled by the unified `NativeAudioBridge` singleton
> defined in Phase 1.2. There is no separate `NativeAudioProvider` — `NativeAudioBridge.isAvailable`
> serves that role. `NativeAudioBridge.instance` returns `null` if the DLL is missing.

### 4.2 Factory Pattern in `main.kt`

```kotlin
val audioRecorder: AudioRecorder = if (NativeAudioBridge.isAvailable) {
    NativeAudioRecorder()
} else {
    JvmAudioRecorder() // fallback to Java Sound
}
```

### 4.3 GeminiClient Changes

```kotlin
// Detect format from file extension or pass mime type
suspend fun processAudio(
    audioBytes: ByteArray,
    mimeType: String,           // "audio/aac" or "audio/wav"
    systemPrompt: String,
    apiKey: String,
    model: String,
): String
```

### 4.4 Tasks

| # | Task | Details |
|---|------|---------|
| 1 | Add `mimeType` parameter to `GeminiClient.processAudio()` | Support both WAV and AAC |
| 2 | Use `NativeAudioBridge.isAvailable` for fallback logic | Single source of truth (defined in Phase 1.2) |
| 3 | Update `main.kt` to use factory pattern | Native preferred, Java Sound fallback |
| 4 | Update `RecordingViewModel` to pass mime type | Based on which recorder is active |
| 5 | Bundle prebuilt DLL in distribution | Gradle copy task to include in MSI |
| 6 | Keep `JvmAudioRecorder` working | No regressions for users without DLL |

---

## Phase 5: Distribution

### 5.1 DLL Bundling

The prebuilt DLL needs to be included in the Compose Desktop distribution. The `appResourcesRootDir`
directive tells Compose Desktop to include platform-specific resources in the packaged application.
Files under `resources/windows-x64/` are automatically included for Windows x64 builds.

```kotlin
// composeApp/build.gradle.kts
compose.desktop {
    application {
        mainClass = "com.openyap.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "OpenYap"
            packageVersion = "1.0.0"

            // Include native DLLs in the distribution
            // Place DLL at: composeApp/resources/windows-x64/openyap_native.dll
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        }
    }
}
```

**Directory structure:**
```
composeApp/
  resources/
    windows-x64/
      openyap_native.dll    # Prebuilt DLL for distribution
```

**Gradle task to copy DLL from native build output:**
```kotlin
// composeApp/build.gradle.kts (add after compose.desktop block)
tasks.register<Copy>("copyNativeDll") {
    from(rootProject.layout.projectDirectory.dir("native/build/Release"))
    include("openyap_native.dll")
    into(layout.projectDirectory.dir("resources/windows-x64"))
}
```

### 5.2 DLL Loading Path

> DLL loading is handled by `NativeAudioBridge` (defined in Phase 1.2). It resolves the path via
> `compose.application.resources.dir` for packaged apps, falling back to the system PATH for dev mode.
> No separate loading code is needed in Phase 5.

### 5.3 CI/CD

| Step | Tool | Notes |
|------|------|-------|
| Build DLL | CMake + MSVC | GitHub Actions `windows-latest` runner |
| Test DLL | CTest | Unit tests for encoder/decoder |
| Package DLL | Gradle | Copy to `resources/windows-x64/` |
| Build MSI | Compose Desktop | `packageMsi` task |

---

## Files Changed (Summary)

### New Files
| File | Purpose |
|------|---------|
| `native/CMakeLists.txt` | C++ build configuration |
| `native/src/openyap_native.h` | Public C API |
| `native/src/openyap_native.cpp` | DLL entry + API dispatch |
| `native/src/audio_capture.h/.cpp` | WASAPI capture |
| `native/src/audio_encoder.h/.cpp` | MF AAC encoder |
| `native/src/vad.h/.cpp` | Voice Activity Detection |
| `native/src/noise_suppressor.h/.cpp` | Noise reduction |
| `shared/.../platform/NativeAudioBridge.kt` | JNA interface + unified DLL loading/init singleton |
| `shared/.../platform/NativeAudioRecorder.kt` | AudioRecorder impl |

### Modified Files
| File | Change |
|------|--------|
| `shared/.../service/GeminiClient.kt` | Add `mimeType` parameter; update `DEFAULT_MODELS` to include 3.x models |
| `shared/.../model/AppSettings.kt` | Update default model to `gemini-3.1-flash-lite-preview` |
| `shared/.../viewmodel/RecordingViewModel.kt` | Pass mime type based on active recorder |
| `composeApp/.../main.kt` | Factory pattern for audio recorder |
| `composeApp/build.gradle.kts` | DLL bundling in distribution |
| `gradle/libs.versions.toml` | Update JNA from 5.16.0 to 5.17.0 |
| `shared/build.gradle.kts` | No changes (JNA already present) |

### Unchanged Files
| File | Reason |
|------|--------|
| `JvmAudioRecorder.kt` | Kept as fallback |
| `AudioRecorder.kt` | Interface unchanged |
| All UI files | No changes needed |

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| MF AAC encoder not available on some Windows editions | Low (available since Win 7) | Fallback to WAV |
| WASAPI device initialization fails | Medium | Fallback to `JvmAudioRecorder` |
| DLL loading issues (path, architecture) | Medium | Clear error message + Java Sound fallback |
| Gemini rejects AAC format | Low (documented support) | Test early in Phase 1, fallback to FLAC/WAV |
| C++ build complexity for contributors | High | Ship prebuilt DLLs, Docker build for CI |
| VAD too aggressive (clips speech) | Medium | Conservative thresholds + configurable sensitivity |

---

## Implementation Order

```
Phase 1 (Week 1-2): DLL skeleton + AAC encoding + Gemini validation
Phase 2 (Week 2-3): WASAPI capture replaces Java Sound
Phase 3 (Week 3-4): VAD + noise suppression
Phase 4 (Week 4):   Integration, fallback, testing
Phase 5 (Week 4-5): Distribution, CI/CD, prebuilt DLLs
```

**Phase 1 is the critical path** -- if AAC encoding works and Gemini accepts it, the rest is incremental improvement. If AAC doesn't work with Gemini, we pivot to **FLAC or MP3** (both confirmed supported by Gemini). Note: Opus is NOT a listed Gemini audio format and should not be used as a fallback.

> **Model note:** This plan targets `gemini-3.1-flash-lite-preview`. This model does NOT support
> the Live API or audio generation. If real-time streaming is needed in the future, a different
> model (e.g. `gemini-2.5-flash-native-audio-preview`) would be required, along with the Live API's
> `audio/pcm;rate=16000` format — a different architecture than this batch-upload plan.
