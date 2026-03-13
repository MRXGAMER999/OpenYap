# Research: Native Audio Pipeline

**Feature**: 001-native-audio-pipeline
**Date**: 2026-03-13
**Status**: Complete — all NEEDS CLARIFICATION items resolved

---

## R-001: WASAPI Resampling Strategy

**Context**: The MF AAC encoder only accepts 44100 Hz or 48000 Hz input. The default microphone may operate at any sample rate (16kHz, 22.05kHz, 44.1kHz, 48kHz, 96kHz). We need 48kHz PCM delivered to the encoder regardless of device native format.

### Decision: Use `AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM` + `AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY`

### Rationale

1. **Dramatically simpler**: Two flag constants OR'd into `IAudioClient::Initialize` vs 150-250 lines of COM boilerplate for the MF Resampler MFT.
2. **Integrated into pipeline**: Conversion happens at the WASAPI source. PCM data arriving via `IAudioCaptureClient::GetBuffer` is already 48kHz/16-bit/mono — ready for the AAC encoder with zero intermediate buffers.
3. **Quality sufficient for speech**: The `SRC_DEFAULT_QUALITY` flag activates a polyphase filter resampler. For voice captured from a microphone and encoded to AAC at 96kbps, this is more than adequate.
4. **Lower latency**: No separate processing stage or additional frame buffering.
5. **Reliable on target platform**: Fully reliable on Windows 10/11 shared-mode capture. Minimum Windows 8 requirement is already met.

### Alternatives Considered

| Alternative | Verdict | Reason Rejected |
|---|---|---|
| MF Audio Resampler MFT (`CLSID_CResamplerMediaObject`) | Viable but overkill | 150-250 lines of COM/MF boilerplate, `IMFSample` wrapping, `ProcessInput`/`ProcessOutput` pump loop — all for no meaningful quality gain on speech audio. Only justified if resampling audio from non-WASAPI sources. |
| Capture at device native rate + resample before encode | Anti-pattern | Adds complexity to handle variable input rates. The entire point of `AUTOCONVERTPCM` is to normalize at the source. |
| Force exclusive mode at 48kHz | Fragile | Many microphones don't support 48kHz exclusive mode (USB headsets, Bluetooth). Monopolizes the device. |
| External library (libsamplerate/libsoxr) | Unnecessary dependency | Adds a C library to build/ship. Windows already provides a good resampler via the flag. Contradicts zero-dependency design. |

### Implementation Notes

- **Always combine both flags**: `AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY`. Without `SRC_DEFAULT_QUALITY`, Windows uses linear interpolation which produces audible artifacts for large rate jumps (16kHz → 48kHz).
- **Verify format after Initialize**: Call `GetMixFormat` or inspect the actual buffer format to confirm 48kHz was applied. The flag may silently fall back on rare driver configurations.
- **This flag does NOT enable noise suppression**: Noise suppression is a separate concern handled via `AudioCategory_Communications` (see R-005).
- **Channel conversion included**: If the device is stereo and you request mono, the auto-convert handles the downmix.
- **Shared mode only**: Incompatible with exclusive mode (not a concern — shared mode is the correct choice for capture).

---

## R-002: AAC Output Path Strategy

**Context**: Current `JvmAudioRecorder.stopRecording()` writes WAV to a temp file path, then `RecordingViewModel` reads the file bytes and sends them to `GeminiClient`. The `AudioRecorder` interface returns a `String` file path from `stopRecording()`.

### Decision: Write AAC to temp file (same pattern as current WAV approach)

### Rationale

1. **Zero interface changes**: `AudioRecorder.stopRecording(): String` returns a file path. No change to this cross-platform contract in `commonMain`. No ripple effects into `RecordingViewModel`, `fileReader`/`fileDeleter` lambdas, or cleanup logic.
2. **Existing cleanup infrastructure handles Constitution III**: The ViewModel already calls `deleteFile(path)` after API response, on cancellation, on generation mismatch, and for too-short recordings. This is battle-tested for the "delete audio after use" privacy requirement.
3. **Performance is irrelevant**: A 30-second AAC recording at 96kbps is ~480KB. Writing/reading 480KB to a temp file on any modern SSD takes <1ms. Network round-trip to Gemini dwarfs this.
4. **YAGNI (Constitution V)**: Returning bytes directly is premature optimization that adds complexity for zero measurable benefit.
5. **Clean ownership boundary**: Native code owns the write, JVM owns the read. No shared memory lifetime management, no `freeBuffer()` export needed.

### Alternatives Considered

| Alternative | Verdict | Reason Rejected |
|---|---|---|
| Return bytes via JNA `Pointer` | Complex | Requires separate `freeBuffer()` native export, memory leak risk if missed, breaks `AudioRecorder` interface. |
| JNA `Memory` / direct `ByteBuffer` | Nondeterministic cleanup | JNA `Memory` auto-frees on GC which is nondeterministic. Breaks interface. |
| Callback-based streaming | Massive over-engineering | For a use case that sends one complete blob to an API. |
| Shared memory / memory-mapped file | Absurd complexity | For ~480KB payloads. |

### Implementation Notes

- `NativeAudioRecorder.stopRecording(outputPath)` calls native `openyap_capture_stop()`, then encodes accumulated PCM to AAC, writes to `outputPath` with `.aac` extension.
- The only downstream change: `GeminiClient.processAudio()` receives `mimeType = "audio/aac"` instead of `"audio/wav"`.
- File extension change from `.wav` to `.aac` in the temp file path generation (in `RecordingViewModel` or `NativeAudioRecorder`).

---

## R-003: WASAPI Microphone Disconnect Detection

**Context**: FR-016 requires graceful stop on device disconnection, preserving captured audio and showing a non-blocking notification.

### Decision: Dual-layer detection — HRESULT polling + `IAudioSessionEvents`

### Rationale

1. **HRESULT polling is mandatory**: Every `GetBuffer()`/`GetNextPacketSize()` call must handle errors anyway. `AUDCLNT_E_DEVICE_INVALIDATED` is the synchronous detection path.
2. **`IAudioSessionEvents` covers sleep gaps**: The capture loop may be waiting on an event handle between buffer fills. `OnSessionDisconnected` fires asynchronously on a system thread, can set an atomic flag to wake the capture thread immediately.
3. **`IMMNotificationClient` is redundant for mid-recording detection**: Session-level callback already covers device removal. MMDevice-level callback is useful for device-picker UI (not in scope).

### HRESULT Codes to Check

| Code | Hex | Meaning |
|---|---|---|
| `AUDCLNT_E_DEVICE_INVALIDATED` | `0x88890004` | Device unplugged, disabled, or reconfigured |
| `AUDCLNT_E_SERVICE_NOT_RUNNING` | `0x88890010` | Windows audio service crashed |

### Buffer Flags to Check (`pdwFlags` from `GetBuffer`)

| Flag | Meaning |
|---|---|
| `AUDCLNT_BUFFERFLAGS_DATA_DISCONTINUITY` | Glitch/gap in audio data |
| `AUDCLNT_BUFFERFLAGS_SILENT` | Buffer contains silence (skip writing) |

### Implementation Pattern

```
Capture loop:
  1. WaitForSingleObject(hAudioEvent, timeout)
  2. Check atomic disconnectFlag (set by IAudioSessionEvents callback)
  3. GetNextPacketSize() — check for AUDCLNT_E_DEVICE_INVALIDATED
  4. While packets available:
     a. GetBuffer() — check for AUDCLNT_E_DEVICE_INVALIDATED
     b. If !(flags & SILENT): write PCM data to accumulator
     c. ReleaseBuffer()
  5. On any invalidation: break loop, finalize output, signal Kotlin layer

IAudioSessionEvents::OnSessionDisconnected:
  - If reason == DisconnectReasonDeviceRemoval: set atomic flag, signal capture event
  - Must be non-blocking (never wait on synchronization objects)
  - Never call UnregisterAudioSessionNotification from within callback
```

### Edge Cases

| Scenario | Behavior |
|---|---|
| Partial buffer at disconnect | All frames from prior successful `GetBuffer` calls are valid. At most ~10ms (one buffer period) of audio is lost. |
| Audio service crash | `AUDCLNT_E_SERVICE_NOT_RUNNING` instead of `DEVICE_INVALIDATED`. `OnSessionDisconnected` may NOT fire. Must handle via HRESULT polling. |
| Device reconnects | Old `IAudioClient` is permanently dead. Must create new one via `IMMDevice::Activate()`. Not relevant for current recording session — finalize and let user start new recording. |

---

## R-004: CMake CI Toolchain

**Context**: Need to build `openyap_native.dll` in GitHub Actions CI for Windows 64-bit distribution.

### Decision: `windows-2022` runner + Ninja generator + MSVC v143 + `_WIN32_WINNT=0x0602`

### Rationale

1. **`windows-2022` (pinned)**: `windows-latest` currently maps to `windows-2025` and can change. Both have identical MSVC v143 toolsets. Pinning avoids surprise breakage.
2. **Ninja generator**: Preinstalled (v1.13.2), significantly faster than Visual Studio generator (no .sln/.vcxproj overhead), single-config model (`CMAKE_BUILD_TYPE=Release` at configure time).
3. **MSVC v143**: Default toolset for VS 2022. Supports Windows 8 targeting via `_WIN32_WINNT`. No older toolset needed.
4. **`_WIN32_WINNT=0x0602`**: Targets Windows 8 at the API level. The SDK version (10.0.x) doesn't determine minimum OS target.

### CI Configuration

```yaml
jobs:
  build-dll:
    runs-on: windows-2022
    steps:
      - uses: actions/checkout@v4
      - uses: ilammy/msvc-dev-cmd@v1
        with: { arch: x64 }
      - run: cmake -S native -B native/build -G Ninja -DCMAKE_BUILD_TYPE=Release
      - run: cmake --build native/build
      - uses: actions/upload-artifact@v4
        with:
          name: openyap_native-windows-x64
          path: native/build/openyap_native.dll
```

### CMakeLists.txt Requirements

```cmake
target_compile_definitions(openyap_native PRIVATE
    WINVER=0x0602
    _WIN32_WINNT=0x0602
    WIN32_LEAN_AND_MEAN
    UNICODE
    _UNICODE
)
```

### Important Notes

- **Media Foundation headers/libs**: Part of Windows SDK, preinstalled on runners. Link-time resolution works. **Runtime MF functionality is limited on Server**: no audio devices, no actual encoding. CI can only compile + link, not run media tests.
- **CRT linking**: Default `/MD` (dynamic CRT) is fine since JVM processes typically have VC++ runtime. Static CRT (`/MT`) is an option for standalone distribution but increases DLL size by ~200-400KB.
- **Ninja requires MSVC dev environment**: `ilammy/msvc-dev-cmd@v1` sets up `cl.exe` on PATH. One-line setup.

---

## R-005: Windows APO Noise Suppression Detection

**Context**: FR-008 requires noise reduction when the platform supports it. FR-009 requires graceful handling when capabilities are absent.

### Decision: Always set `AudioCategory_Communications` — do not gate on APO availability

### Rationale

1. **`SetClientProperties` never fails due to missing APOs**: It is a hint to the audio engine. If the driver doesn't have APOs for the communications mode, Windows silently falls back. Capture always proceeds.
2. **`IAudioEffectsManager` requires Windows 11 (Build 22000+)**: Making it a hard dependency breaks Windows 10 support. Use it opportunistically for logging only.
3. **Effects can change at runtime**: User toggling "Windows Studio Effects" mid-session adds complexity with no benefit for record-and-proceed.

### Implementation Pattern

```
1. Activate IAudioClient via IMMDevice::Activate
2. QI for IAudioClient2
3. SetClientProperties: eCategory = AudioCategory_Communications
   - Do NOT set AUDCLNT_STREAMOPTIONS_RAW (that disables APO processing)
4. IAudioClient::Initialize (shared mode, capture)
5. [Optional/Logging] Try GetService(IID_IAudioEffectsManager):
   - S_OK: enumerate effects, log which are present and active
   - E_NOINTERFACE: log "pre-Win11, APO status unknown — proceeding with best-effort"
6. Proceed with capture regardless
```

### Fallback Behavior

| Scenario | Behavior |
|---|---|
| APOs present and active | Noise suppression, AEC, AGC applied transparently |
| APOs present but OFF | Capture proceeds without effects. Optionally toggle ON if `canSetState == true` |
| No APOs on endpoint | Capture proceeds with raw audio. No error. |
| Windows 10 | `GetService(IID_IAudioEffectsManager)` returns `E_NOINTERFACE`. Communications category hint still activates whatever the driver supports |

### Key Constraint

**Never set `AUDCLNT_STREAMOPTIONS_RAW`** — that explicitly bypasses the APO chain, which is the opposite of what we want.

---

## R-006: JNA Native Callback Best Practices

**Context**: Native DLL fires audio callbacks every 10-20ms with ~960 PCM samples. Kotlin side must store chunks and calculate amplitude without crashing or blocking.

### Decision: Instance-field strong reference + `CallbackThreadInitializer(detach=false)` + lock-free queue

### Key Rules

**DO:**
1. Store callback as a **non-null instance field** (`private val`) on `NativeAudioRecorder` — prevents GC crash
2. Use `CallbackThreadInitializer(daemon=true, detach=false, name="WASAPI-Audio-Callback")` — avoids JVM attach/detach on every 20ms call
3. Copy PCM data inside the callback via `Pointer.read(0, buffer, 0, count)` — the `Pointer` is only valid during invocation
4. Use `ConcurrentLinkedQueue<ShortArray>` to pass data out of callback thread
5. Calculate amplitude inside the callback (just `max(abs())` over 960 shorts — ~1us)
6. Call `openyap_capture_stop()` before the owning object is destroyed

**DO NOT:**
1. Pass a lambda or anonymous function directly to native registration
2. Allocate objects inside the callback hot path (triggers GC pressure)
3. Block on locks, suspending functions, or I/O inside the callback
4. Access the `Pointer` argument after the callback returns
5. Assume the callback runs on a coroutine dispatcher — it runs on the native WASAPI thread

### Performance Assessment

| Component | Cost | Budget (20ms = 20,000us) |
|---|---|---|
| JNA trampoline entry | ~0.1-0.5 us | |
| JNI attach (first call only, detach=false) | ~5-20 us | |
| JNA interface dispatch | ~1-5 us | |
| `Pointer.read()` for 960 shorts | ~1-3 us | |
| Amplitude calculation | ~1 us | |
| `ConcurrentLinkedQueue.offer()` | ~0.1-0.5 us | |
| **Total per callback** | **~3-10 us** | **<0.05% of budget** |

Verdict: 20ms callback intervals with 960 samples are trivially handled by JNA. No performance concern.

### Thread Safety Architecture

```
Native WASAPI Thread              Lock-Free Queue           Kotlin Coroutine
  |                                    |                         |
  |-- callback fires ----------------->|                         |
  |   Pointer.read → ShortArray copy   |                         |
  |   amplitude = max(abs(samples))    |                         |
  |   queue.offer(chunk)               |                         |
  |   _amplitudeFlow.value = amplitude |                         |
  |   return to native                 |                         |
  |                                    |-- poll on stopRecording -->|
  |                                    |   drain all chunks         |
  |                                    |   concatenate → encode     |
```

### JNA 5.17.0 Callback Changes

JNA 5.17.0 does not contain callback-specific changes. The critical stability improvements were in 5.12.0 (replaced finalizers with Cleaner for `CallbackReference`) and 5.15.0 (fixed `free_callback` weak reference leak). Version 5.17.0 includes all of these. The upgrade from 5.16.0 to 5.17.0 is still justified for general stability and staying current.

---

## Technology Best Practices Summary

| Technology | Best Practice | Source |
|---|---|---|
| WASAPI Shared Mode Capture | Event-driven (`AUDCLNT_STREAMFLAGS_EVENTCALLBACK`) with `WaitForSingleObject` on buffer event | Microsoft WASAPI docs |
| MF AAC Encoder | `MF_MT_AAC_PAYLOAD_TYPE = 1` (ADTS), 48kHz input, 96kbps minimum bitrate | MF AAC Encoder docs |
| JNA Callbacks | Strong reference as instance field, `CallbackThreadInitializer(detach=false)`, lock-free queue for cross-thread data | JNA source code analysis |
| WASAPI Disconnect Handling | Check `AUDCLNT_E_DEVICE_INVALIDATED` from every API call + `IAudioSessionEvents::OnSessionDisconnected` | Microsoft WASAPI recovery docs |
| Windows Noise Suppression | `AudioCategory_Communications` via `IAudioClient2::SetClientProperties`, never `AUDCLNT_STREAMOPTIONS_RAW` | Microsoft APO docs |
| CMake CI | Ninja generator + `ilammy/msvc-dev-cmd@v1` + pinned `windows-2022` runner | GitHub Actions best practices |
