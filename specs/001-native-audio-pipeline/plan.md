# Implementation Plan: Native Audio Pipeline

**Branch**: `001-native-audio-pipeline` | **Date**: 2026-03-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-native-audio-pipeline/spec.md`

## Summary

Replace the Java Sound-based audio capture with a native C++ DLL (`openyap_native.dll`) that provides WASAPI audio capture, AAC encoding via Windows Media Foundation, energy-based voice activity detection, and Windows APO-based noise suppression — all accessed from Kotlin via JNA. This achieves ~2.7x audio payload reduction (from ~1.28 MB to ~480 KB for a 30-second recording) by encoding to AAC (96 kbps minimum via MF AAC) instead of sending raw WAV. The existing `JvmAudioRecorder` is preserved as a transparent fallback when the native library is unavailable.

## Technical Context

**Language/Version**: Kotlin 2.3.0 (KMP, JVM target) + C++17 (native DLL)
**Primary Dependencies**: Compose Multiplatform 1.10.0, Ktor 3.1.1 (CIO), kotlinx.serialization 1.10.0, kotlinx.coroutines 1.10.2, JNA 5.16.0 (upgrade to 5.17.0 required for callback stability), Windows Media Foundation (AAC encoding), WASAPI (audio capture)
**Storage**: Local filesystem (temp WAV/AAC files, deleted after Gemini API response per constitution III)
**Testing**: Manual testing only (constitution IV); CTest for native DLL unit tests (non-gating)
**Target Platform**: Windows 64-bit (minimum Windows 8 for ADTS AAC payload type support)
**Project Type**: Desktop application (Compose Desktop via Skia, packaged as MSI)
**Performance Goals**: <20ms capture latency (WASAPI), real-time amplitude feedback at 20+ updates/sec, ~2.7x payload reduction vs uncompressed WAV
**Constraints**: <500ms added startup time for native library init, <20 MB compressed output for 20-minute recordings (Gemini inline data limit), MF AAC minimum 96 kbps output bitrate, MF AAC only accepts 44100/48000 Hz input
**Scale/Scope**: Single-user desktop app, single default audio input device, recordings up to 20 minutes

### Resolved Clarifications (from research.md)

1. **WASAPI resampling strategy**: Use `AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY`. Two flag constants vs 150-250 lines of MF Resampler MFT boilerplate. Polyphase filter quality is sufficient for speech. → [R-001](research.md#r-001-wasapi-resampling-strategy)
2. **AAC file output path**: Write AAC to temp file (same pattern as current WAV). Zero interface changes, existing cleanup infrastructure handles Constitution III. → [R-002](research.md#r-002-aac-output-path-strategy)
3. **Microphone disconnect detection**: Dual-layer — check `AUDCLNT_E_DEVICE_INVALIDATED` from `GetBuffer()`/`GetNextPacketSize()` + `IAudioSessionEvents::OnSessionDisconnected` for async notification. → [R-003](research.md#r-003-wasapi-microphone-disconnect-detection)
4. **CMake toolchain**: `windows-2022` runner (pinned), Ninja generator, MSVC v143, `_WIN32_WINNT=0x0602` for Windows 8 targeting. → [R-004](research.md#r-004-cmake-ci-toolchain)
5. **Noise suppression availability detection**: Always set `AudioCategory_Communications` — `SetClientProperties` never fails. Optionally query `IAudioEffectsManager` (Win11+) for logging only. → [R-005](research.md#r-005-windows-apo-noise-suppression-detection)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Assessment |
|-----------|--------|------------|
| **I. KMP-First Architecture** | VIOLATION — justified | Native C++ DLL and JNA bindings are inherently platform-specific (Windows JVM). However: (a) the `AudioRecorder` interface remains in `commonMain`, (b) `NativeAudioBridge.kt` and `NativeAudioRecorder.kt` reside in `jvmMain` (platform source set) per constitutional rules, (c) platform source sets contain only implementations of interfaces defined in `commonMain`. The native DLL itself is outside the KMP source set structure (`native/` directory) which is acceptable — it is a build artifact, not Kotlin code. |
| **II. Clean Module Separation** | PASS | All new Kotlin code lives in `shared/src/jvmMain/.../platform/`. No ViewModel changes beyond passing mime type. No UI changes. `composeApp` dependency on `shared` is unchanged; no reverse dependency introduced. |
| **III. Privacy & Security** | PASS | Audio files (both WAV and AAC) continue to be deleted immediately after Gemini API response. No new network calls beyond the existing Gemini API. No new storage of audio data beyond the active request lifecycle. |
| **IV. Manual Testing Only** | PASS | Native DLL has optional CTest unit tests (non-gating, exploratory). Feature correctness verified by manual testing per constitution. No automated test gates added. |
| **V. Simplicity & YAGNI** | VIOLATION — justified | Adding a C++ DLL + CMake build introduces complexity. However: (a) the alternative (pure Java/Kotlin AAC encoding) is not viable — JVM has no built-in AAC encoder and third-party Java AAC libraries are unmaintained or GPL-licensed, (b) Windows Media Foundation provides production-quality AAC encoding at zero dependency cost, (c) the DLL is prebuilt and bundled — contributors do not need C++ toolchains, (d) the fallback to `JvmAudioRecorder` ensures the DLL is additive, not mandatory. New dependency (`openyap_native.dll`) is justified: it solves the 2.7x payload reduction that existing code cannot achieve. JNA version bump from 5.16.0 to 5.17.0 is justified: callback stability improvements directly relevant to native audio callback safety. |

### Post-Design Re-evaluation (Phase 1 complete)

| Principle | Status | Changes from Pre-Design |
|-----------|--------|------------------------|
| **I. KMP-First** | VIOLATION — justified (unchanged) | Design confirmed: `AudioRecorder` interface unchanged in `commonMain`. `NativeAudioRecorder`, `NativeAudioBridge` in `jvmMain`. `GeminiClient.processAudio` gains a `mimeType: String` parameter — this is a `commonMain` interface change but is format-agnostic (not platform-specific). No new violations. |
| **II. Module Separation** | PASS (unchanged) | Design confirmed: only `shared/` module gains new code. `composeApp/main.kt` factory pattern is wiring-only (acceptable per constitution). No `@Composable` code in `shared`, no business logic in `composeApp`. |
| **III. Privacy & Security** | PASS (unchanged) | Design confirmed: AAC temp files follow same lifecycle as WAV temp files — deleted after API response via existing `fileDeleter` lambda. No new data persistence. |
| **IV. Manual Testing** | PASS (unchanged) | `quickstart.md` verification checklist is manual. CTest for DLL is explicitly non-gating. |
| **V. Simplicity & YAGNI** | VIOLATION — justified (unchanged) | Design decisions from research reinforce simplicity within the native layer: WASAPI auto-convert (2 flags vs 250 LOC resampler), file-based output (0 interface changes), `AudioCategory_Communications` (no APO detection logic needed). Every research decision chose the simpler option. |

**Gate result: PASS** — All violations are justified. No new violations introduced by Phase 1 design.

## Project Structure

### Documentation (this feature)

```text
specs/001-native-audio-pipeline/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
native/                                          # NEW — C++ project
├── CMakeLists.txt                               # CMake build configuration
├── src/
│   ├── openyap_native.h                         # Public C API (JNA-compatible)
│   ├── openyap_native.cpp                       # DLL entry point + API dispatch
│   ├── audio_capture.h / audio_capture.cpp      # WASAPI capture
│   ├── audio_encoder.h / audio_encoder.cpp      # MF AAC encoder
│   ├── vad.h / vad.cpp                          # Voice Activity Detection
│   └── noise_suppressor.h / noise_suppressor.cpp # Noise reduction
├── build/                                       # CMake output (gitignored)
└── prebuilt/
    └── windows-x64/
        └── openyap_native.dll                   # Prebuilt for distribution

shared/src/jvmMain/kotlin/com/openyap/platform/
├── NativeAudioBridge.kt                         # NEW — JNA interface + DLL loading singleton
├── NativeAudioRecorder.kt                       # NEW — AudioRecorder impl using native bridge
└── JvmAudioRecorder.kt                          # UNCHANGED — kept as fallback

shared/src/commonMain/kotlin/com/openyap/
├── platform/AudioRecorder.kt                    # UNCHANGED — interface
├── service/GeminiClient.kt                      # MODIFIED — add mimeType parameter
├── viewmodel/RecordingViewModel.kt              # MODIFIED — pass mime type
└── model/AppSettings.kt                         # MODIFIED — update default model

composeApp/src/jvmMain/kotlin/com/openyap/
└── main.kt                                      # MODIFIED — factory pattern for recorder

composeApp/
├── build.gradle.kts                             # MODIFIED — DLL bundling
└── resources/windows-x64/
    └── openyap_native.dll                       # Copied from native/prebuilt for distribution

gradle/libs.versions.toml                        # MODIFIED — JNA 5.16.0 → 5.17.0
```

**Structure Decision**: Existing two-module KMP structure (`shared` + `composeApp`) is preserved. A new top-level `native/` directory is added for the C++ DLL project, which is outside the Gradle build system (built separately via CMake). All new Kotlin code follows the existing pattern: interfaces in `commonMain`, implementations in `jvmMain`.

## Complexity Tracking

> **Violations from Constitution Check that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Principle I: Native C++ DLL outside KMP source sets | Windows Media Foundation AAC encoding requires native C API access; no Kotlin/JVM equivalent exists | Pure-JVM AAC encoding is not viable: no built-in JVM AAC encoder, third-party Java AAC libraries (JAAD, FDK-AAC Java bindings) are unmaintained or GPL-licensed. JNA bridge to Windows built-in encoder is zero-dependency on the encoding side. |
| Principle V: C++ DLL + CMake build complexity | Achieves 2.7x payload reduction using OS-provided encoder at zero runtime dependency cost | (a) Sending raw WAV: wastes bandwidth, no path to improvement. (b) Server-side encoding: adds server dependency, violates privacy-first principle III. (c) FFmpeg bundling: 80+ MB binary, massive complexity increase. (d) Java Sound + FLAC: FLAC is lossless (~1.5x reduction), far less than AAC's 2.7x. |
| Principle V: JNA version bump 5.16.0 → 5.17.0 | Callback stability improvements directly mitigate JNA GC crash risk (FR-012) | Staying on 5.16.0 risks JVM crashes when native code invokes GC'd callbacks; the version bump is a single-line change in `libs.versions.toml`. |
