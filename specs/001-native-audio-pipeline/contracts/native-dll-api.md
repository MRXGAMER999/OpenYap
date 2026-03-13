# Native DLL C API Contract

**Feature**: 001-native-audio-pipeline
**Library**: `openyap_native.dll`
**ABI**: C linkage (`extern "C"`), `__stdcall` on Windows
**Consumed by**: Kotlin/JVM via JNA (`com.sun.jna.Library`)

---

## Versioning

No explicit API versioning in v1. The DLL is tightly coupled to the application version and distributed together. Future versions may add `openyap_version()` if independent evolution is needed.

---

## Lifecycle Functions

### `openyap_init`

```c
int openyap_init(void);
```

Initialize Windows Media Foundation (`MFStartup`) and COM (`CoInitializeEx`). Must be called once before any other function.

| Return | Meaning |
|---|---|
| `0` | Success |
| `-1` | COM initialization failed |
| `-2` | Media Foundation initialization failed |

**Thread safety**: Must be called from a single thread. Not reentrant.

---

### `openyap_shutdown`

```c
void openyap_shutdown(void);
```

Cleanup: `MFShutdown()`, release COM resources. Call once at application exit. No-op if `openyap_init` was never called.

**Thread safety**: Must be called from the same thread as `openyap_init`, after all capture/encoding operations have stopped.

---

## Audio Capture Functions

### `openyap_capture_start`

```c
typedef void (*audio_callback_t)(const short* pcm_data, int sample_count, void* user_data);

int openyap_capture_start(
    int sample_rate,        // Must be 48000 (enforced internally)
    int channels,           // Must be 1 (mono)
    audio_callback_t callback,
    void* user_data
);
```

Start capturing audio from the system default input device via WASAPI shared mode.

| Parameter | Constraint | Notes |
|---|---|---|
| `sample_rate` | `48000` | Other values rejected with error `-3` |
| `channels` | `1` | Other values rejected with error `-3` |
| `callback` | Non-null | Fires every ~10-20ms with PCM chunks |
| `user_data` | Nullable | Passed through to callback unchanged |

| Return | Meaning |
|---|---|
| `0` | Success — capture thread started |
| `-1` | Already capturing (must stop first) |
| `-2` | WASAPI device initialization failed |
| `-3` | Invalid parameters |
| `-4` | No audio input device available |

**Callback contract**:
- `pcm_data` pointer is valid **only** for the duration of the callback invocation. Callers must copy data before returning.
- `sample_count` is the number of `short` samples (not bytes). Typical value: 480-960 (10-20ms at 48kHz).
- Callback fires on the native WASAPI capture thread. Must not block.
- The DLL stores a copy of the `callback` function pointer. The caller (JNA) must ensure the callback object is not garbage-collected while capture is active (see FR-012).

**WASAPI configuration**:
- Shared mode with `AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY`
- `AudioCategory_Communications` via `IAudioClient2::SetClientProperties` (activates APO noise suppression chain)
- Event-driven capture loop (not polling)

**Device disconnection**: If `IAudioCaptureClient::GetBuffer()` returns `AUDCLNT_E_DEVICE_INVALIDATED`, the capture loop stops. A final callback is **not** fired. The next call to any capture function returns an error. The caller should call `openyap_capture_stop()` to clean up.

---

### `openyap_capture_stop`

```c
int openyap_capture_stop(void);
```

Stop the active capture session. Waits for the capture thread to exit.

| Return | Meaning |
|---|---|
| `0` | Success — capture stopped |
| `-1` | No active capture session |
| `-2` | Device was disconnected during capture (capture already stopped; resources cleaned up) |

**Thread safety**: May be called from any thread. Blocks until the capture thread joins.

---

## Audio Encoding Functions

### `openyap_encode_aac`

```c
int openyap_encode_aac(
    const short* pcm_data,      // Input: PCM samples (signed 16-bit, mono)
    int pcm_sample_count,       // Input: total number of samples
    int sample_rate,            // Input: 44100 or 48000
    int channels,               // Input: 1 (mono)
    int bitrate,                // Input: minimum 96000 (96 kbps)
    const char* output_path     // Input: filesystem path for AAC output file
);
```

Encode a contiguous PCM buffer to an ADTS-wrapped AAC file via Windows Media Foundation.

| Parameter | Constraint | Notes |
|---|---|---|
| `pcm_data` | Non-null, valid for `pcm_sample_count * sizeof(short)` bytes | |
| `pcm_sample_count` | > 0 | Total samples, not frames |
| `sample_rate` | `44100` or `48000` | MF AAC encoder requirement |
| `channels` | `1` | Stereo support not needed |
| `bitrate` | >= `96000` | MF AAC minimum; values below are clamped to 96000 |
| `output_path` | Non-null, valid filesystem path | Parent directory must exist |

| Return | Meaning |
|---|---|
| `0` | Success — AAC file written to `output_path` |
| `-1` | MF encoder creation failed |
| `-2` | Invalid parameters |
| `-3` | Encoding failed (MF transform error) |
| `-4` | File write failed |

**Output format**: ADTS-wrapped AAC-LC (`MF_MT_AAC_PAYLOAD_TYPE = 1`). Self-describing stream with sync headers per frame. Compatible with Gemini `audio/aac` inline data.

**Thread safety**: May be called from any thread. Not reentrant (uses internal MF state). Do not call while another `openyap_encode_aac` is in progress.

---

## Analysis Functions

### `openyap_vad_is_speech`

```c
int openyap_vad_is_speech(
    const short* pcm_data,
    int sample_count,
    int sample_rate
);
```

Classify a single audio frame as speech or silence using energy-based VAD.

| Return | Meaning |
|---|---|
| `1` | Frame contains speech |
| `0` | Frame is silence |
| `-1` | Invalid parameters |

**Recommended usage**: Call per 20ms frame (960 samples at 48kHz) after capture completes, to determine trim boundaries.

---

### `openyap_amplitude`

```c
float openyap_amplitude(
    const short* pcm_data,
    int sample_count
);
```

Calculate peak amplitude of a PCM chunk, normalized to 0.0-1.0.

| Return | Range | Meaning |
|---|---|---|
| `0.0` | Minimum | Digital silence |
| `1.0` | Maximum | Full-scale signal |

**Implementation**: `max(abs(sample)) / 32768.0f`

---

## Error Handling

### `openyap_last_error`

```c
const char* openyap_last_error(void);
```

Returns a human-readable description of the last error. The returned string is valid until the next API call that produces an error.

| Return | Meaning |
|---|---|
| Non-null string | Error description |
| `NULL` | No error since last check |

---

## Error Code Summary

| Code | Meaning | Functions |
|---|---|---|
| `0` | Success | All |
| `-1` | Context-dependent (see each function) | All |
| `-2` | Initialization/device failure | `init`, `capture_start`, `encode_aac`, `capture_stop` |
| `-3` | Invalid parameters | `capture_start`, `encode_aac` |
| `-4` | No device / file write failure | `capture_start`, `encode_aac` |

---

## JNA Mapping Summary

| C Type | JNA/Kotlin Type | Notes |
|---|---|---|
| `int` | `Int` | |
| `float` | `Float` | |
| `const short*` (input) | `ShortArray` | JNA marshals automatically |
| `const short*` (callback) | `Pointer` | Must use `Pointer.getShortArray()` to copy |
| `const char*` | `String` | JNA handles UTF-8 conversion |
| `audio_callback_t` | `interface AudioCallback : Callback` | Must store strong reference |
| `void*` | `Pointer?` | |
