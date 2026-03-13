# Tasks: Native Audio Pipeline

**Feature:** Native Audio Pipeline
**Branch:** `001-native-audio-pipeline`
**Generated:** 2026-03-13
**Total Tasks:** 32
**Spec:** [spec.md](./spec.md) | **Plan:** [plan.md](./plan.md)

---

## User Story Mapping

| Story | Spec Story | Priority | Description |
|-------|-----------|----------|-------------|
| US1 | User Story 1 | P1 | Compressed Audio Upload (~2.7x payload reduction) |
| US2 | User Story 2 | P1 | Graceful Fallback to Existing Audio |
| US3 | User Story 3 | P2 | Native Audio Capture with Real-Time Feedback |
| US4 | User Story 4 | P3 | Silence Trimming via Voice Activity Detection |
| US5 | User Story 5 | P3 | Background Noise Reduction |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependency upgrades and project scaffolding required before any implementation.

- [X] T001 Upgrade JNA version from `5.16.0` to `5.17.0` in `gradle/libs.versions.toml` (line 11) for callback stability improvements (FR-012)
- [X] T002 [P] Create `native/` directory structure: `native/CMakeLists.txt`, `native/src/`, `native/prebuilt/windows-x64/` per plan.md project structure
- [X] T003 [P] Create `.gitignore` entry for `native/build/` directory

---

## Phase 2: Foundational — Native C++ DLL

> **Blocks:** US1, US3, US4, US5. Must complete before any user story except US2 (fallback).

**Purpose**: Build the `openyap_native.dll` implementing the C API contract defined in `contracts/native-dll-api.md`.

### CMake Build System

- [X] T004 Create `native/CMakeLists.txt` with C++17 target, MSVC v143, `_WIN32_WINNT=0x0602`, Ninja generator support, linking `mfplat.lib`, `mfreadwrite.lib`, `mf.lib`, `mfuuid.lib`, `wmcodecdspuuid.lib`, `ole32.lib`, `propsys.lib` per research.md R-004

### DLL API Entry Points

- [X] T005 Create `native/src/openyap_native.h` — public C API header with `extern "C"` declarations matching `contracts/native-dll-api.md` (all 8 functions + `audio_callback_t` typedef)
- [X] T006 Create `native/src/openyap_native.cpp` — DLL entry point implementing `openyap_init` (COM + MFStartup), `openyap_shutdown` (MFShutdown), `openyap_last_error`, and dispatch to subsystem modules

### WASAPI Audio Capture

- [X] T007 Create `native/src/audio_capture.h` and `native/src/audio_capture.cpp` — WASAPI shared-mode event-driven capture: `IAudioClient2::SetClientProperties(AudioCategory_Communications)`, `AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUTOCONVERTPCM | SRC_DEFAULT_QUALITY`, 48kHz/16-bit/mono output, callback invocation every ~10-20ms per research.md R-001, R-005
- [X] T008 Add dual-layer microphone disconnect detection in `audio_capture.cpp`: check `AUDCLNT_E_DEVICE_INVALIDATED` from `GetBuffer()`/`GetNextPacketSize()` + `IAudioSessionEvents::OnSessionDisconnected` async notification per research.md R-003

### AAC Encoding

- [X] T009 Create `native/src/audio_encoder.h` and `native/src/audio_encoder.cpp` — MF AAC encoder: `MF_MT_AAC_PAYLOAD_TYPE = 1` (ADTS), 48kHz input, 96kbps minimum bitrate, file output to caller-specified path per `contracts/native-dll-api.md` `openyap_encode_aac` contract

### Voice Activity Detection

- [X] T010 Create `native/src/vad.h` and `native/src/vad.cpp` — energy-based RMS VAD: 20ms frames (960 samples at 48kHz), adaptive threshold, 300ms hangover, 200ms leading/trailing margins per data-model.md VAD Frame Parameters

### Amplitude Calculation

- [X] T011 [P] Implement `openyap_amplitude` in `native/src/openyap_native.cpp` — peak amplitude: `max(abs(sample)) / 32768.0f`, returns 0.0-1.0 per `contracts/native-dll-api.md`

### Build & Prebuilt

- [X] T012 Build `openyap_native.dll` locally via CMake + Ninja + MSVC, verify all 8 exported functions are present (use `dumpbin /exports`), copy to `native/prebuilt/windows-x64/openyap_native.dll`

**Checkpoint**: Native DLL built and verified. JNA bridge work can begin.

---

## Phase 3: US2 — Graceful Fallback (Priority: P1)

> **Goal:** Application works identically to pre-feature state when native DLL is unavailable.
> **Independent Test:** Remove/rename `openyap_native.dll` → restart → record → verify WAV output, no errors, no crashes per quickstart.md P1 Fallback checklist.

### JNA Bridge (shared foundation for US1 + US2)

- [X] T013 Create `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt` — singleton with lazy `Native.load("openyap_native", OpenYapNative::class.java)`, `openyap_init()` call, `isAvailable: Boolean` property, JVM shutdown hook for `openyap_shutdown()` per data-model.md entity 2
- [X] T014 Create inner `OpenYapNative` JNA interface in `NativeAudioBridge.kt` — all 8 function signatures + `AudioCallback : Callback` nested interface per data-model.md entity 3 and `contracts/native-dll-api.md` JNA Mapping Summary

### Factory Pattern in main.kt

- [X] T015 [US2] Modify `composeApp/src/jvmMain/kotlin/com/openyap/main.kt` (line ~73) — replace direct `JvmAudioRecorder()` instantiation with factory: `if (NativeAudioBridge.isAvailable) NativeAudioRecorder(NativeAudioBridge.instance!!) else JvmAudioRecorder()`. Determine `audioMimeType` and `audioFileExtension` based on `NativeAudioBridge.isAvailable`.
- [X] T016 [US2] Add diagnostic logging in `main.kt` startup: log `"Native audio pipeline available"` or `"Native audio pipeline unavailable, using fallback"` per FR-015

**Checkpoint**: With DLL removed, application works exactly as before. US2 acceptance scenarios satisfied.

---

## Phase 4: US1 — Compressed Audio Upload (Priority: P1)

> **Goal:** Recorded audio is compressed to AAC (~2.7x payload reduction). Gemini correctly processes it.
> **Independent Test:** Record 30s → verify `.aac` temp file, ~480 KB payload, Gemini returns accurate response per quickstart.md P1 checklist.
> **Depends on:** Phase 2 (DLL) + Phase 3 (bridge).

### NativeAudioRecorder Implementation

- [X] T017 [US1] Create `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt` — implements `AudioRecorder` interface: `startRecording` (clears pcmBuffer, calls `openyap_capture_start(48000, 1, callback, null)`), `stopRecording` (calls `openyap_capture_stop`, drains buffer, calls `openyap_encode_aac`, returns path), `amplitudeFlow` (updated from callback), `hasPermission` (returns true) per data-model.md entity 4
- [X] T018 [US1] Implement `captureCallback` as a `private val` instance field on `NativeAudioRecorder` (strong reference for GC safety per FR-012): copy PCM via `Pointer.getShortArray()`, offer to `ConcurrentLinkedQueue<ShortArray>`, update `_amplitudeFlow` via `openyap_amplitude` per research.md R-006
- [X] T019 [US1] Register `CallbackThreadInitializer(daemon=true, detach=false, name="WASAPI-Audio-Callback")` for the callback instance in `NativeAudioRecorder` constructor per research.md R-006

### GeminiClient mimeType Parameterization

- [X] T020 [US1] Modify `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt` — add `mimeType: String` parameter to `processAudio` (after `audioBytes`), replace hardcoded `"audio/wav"` at line 78 with the parameter per `contracts/gemini-audio-contract.md`

### RecordingViewModel Changes

- [X] T021 [US1] Modify `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt` — add `audioMimeType: String` constructor parameter (injected from main.kt), change temp file extension at line 208 from `.wav` to use injected `audioFileExtension: String`, pass `mimeType` to `geminiClient.processAudio` call at line 304 per data-model.md entity 6
- [X] T022 [US1] Update `main.kt` RecordingViewModel construction (line ~90) to pass `audioMimeType` and `audioFileExtension` based on `NativeAudioBridge.isAvailable` (`"audio/aac"` / `".aac"` vs `"audio/wav"` / `".wav"`)

**Checkpoint**: Recording produces AAC, Gemini processes it correctly. US1 acceptance scenarios satisfied. Core feature MVP complete.

---

## Phase 5: US3 — Native Capture with Real-Time Feedback (Priority: P2)

> **Goal:** Real-time amplitude indicator during native capture at 20+ updates/sec, <20ms latency.
> **Independent Test:** Start recording → observe amplitude indicator → verify responsive real-time feedback per quickstart.md P2 checklist.
> **Note:** The capture and amplitude callback mechanism is already implemented in T017-T019 (NativeAudioRecorder). This phase verifies the end-to-end flow.

- [X] T023 [US3] Verify amplitude indicator in UI updates in real-time during native capture — ensure `RecordingViewModel` collects `amplitudeFlow` from `NativeAudioRecorder` and passes to `RecordingOverlay` / `RecordingIndicator` composables. If wiring is already correct (amplitude flows through unchanged `AudioRecorder.amplitudeFlow`), mark as verified.

**Checkpoint**: Amplitude indicator responds to voice in real-time during native capture. US3 acceptance scenarios satisfied.

---

## Phase 6: US4 — Silence Trimming via VAD (Priority: P3)

> **Goal:** Leading/trailing silence automatically trimmed from recordings before encoding.
> **Independent Test:** Record with 5+ seconds silence before/after speech → verify uploaded audio is noticeably shorter per quickstart.md P3 Silence Trimming checklist.

- [X] T024 [US4] Implement VAD trimming in `NativeAudioRecorder.stopRecording()` — after draining pcmBuffer, iterate 20ms frames calling `openyap_vad_is_speech` per frame, determine first/last speech frame, apply 200ms leading margin and 200ms trailing margin + 300ms hangover, encode only the trimmed region per data-model.md VAD Frame Parameters
- [X] T025 [US4] Handle edge case: if VAD detects no speech in entire recording (complete silence), submit the full recording rather than submitting nothing per spec.md Edge Cases

**Checkpoint**: Silence is trimmed from recordings. US4 acceptance scenarios satisfied.

---

## Phase 7: US5 — Background Noise Reduction (Priority: P3)

> **Goal:** Noise suppression active via Windows APO chain when platform supports it.
> **Independent Test:** Record with background noise → compare quality with fallback recorder per quickstart.md P3 Noise Suppression checklist.
> **Note:** Noise suppression is activated by `AudioCategory_Communications` set in T007 (audio_capture.cpp). No additional Kotlin-side work required.

- [X] T026 [US5] Verify `AudioCategory_Communications` is set in `audio_capture.cpp` `IAudioClient2::SetClientProperties` (done in T007) — confirm noise suppression activates on systems with APO support. Add optional logging via `IAudioEffectsManager` query (Win11+ only, `E_NOINTERFACE` on Win10 is expected) per research.md R-005

**Checkpoint**: Noise suppression active where supported. US5 acceptance scenarios satisfied.

---

## Phase 8: DLL Bundling & Distribution

> **Purpose**: Package `openyap_native.dll` with the application for distribution.

- [X] T027 Add DLL copy task or resource configuration in `composeApp/build.gradle.kts` to bundle `native/prebuilt/windows-x64/openyap_native.dll` into the packaged MSI distribution (via `nativeDistributions` or `fromFiles` configuration)
- [X] T028 [P] Update `NativeAudioBridge.kt` DLL loading to check `compose.application.resources.dir` system property first (for packaged builds), then fall back to system PATH / working directory per quickstart.md section 2

---

## Phase 9: Edge Cases & Robustness

> **Purpose**: Handle all edge cases from spec.md.

- [X] T029 Handle microphone disconnect in `NativeAudioRecorder`: detect capture stop (from native disconnect detection in T008), preserve accumulated PCM, encode partial audio, signal error state to ViewModel with non-blocking notification per FR-016
- [X] T030 Handle native encoding failure in `NativeAudioRecorder`: if `openyap_encode_aac` returns non-zero, log error via `openyap_last_error`, propagate error to ViewModel per FR-009
- [X] T031 Add resource cleanup in `NativeAudioRecorder`: ensure `openyap_capture_stop()` is called if recording is active when the recorder is destroyed (application shutdown during recording) per FR-011

---

## Phase 10: Polish & Cross-Cutting

> **Purpose**: CI, documentation, and final verification.

- [X] T032 [P] Create GitHub Actions workflow `.github/workflows/build-dll.yml` for native DLL CI: `windows-2022` runner, `ilammy/msvc-dev-cmd@v1`, CMake + Ninja build, upload artifact per research.md R-004 CI Configuration

---

## Dependency Graph

```
Phase 1 (Setup: T001-T003)
   │
   ├────────────────────────────────────────────┐
   ▼                                            ▼
Phase 2 (Native DLL: T004-T012)             Phase 3 (US2 Fallback: T013-T016)
   │                                            │
   └──────────────┬─────────────────────────────┘
                  ▼
           Phase 4 (US1 Compressed: T017-T022)     ← Core MVP
                  │
                  ├─────────────────────────┐
                  ▼                         ▼
           Phase 5 (US3: T023)       Phase 6 (US4: T024-T025)
                  │                         │
                  │    Phase 7 (US5: T026)   │
                  │         │               │
                  └────┬────┘───────────────┘
                       ▼
                Phase 8 (Bundling: T027-T028)
                       │
                       ▼
                Phase 9 (Edge Cases: T029-T031)
                       │
                       ▼
                Phase 10 (Polish: T032)
```

---

## Parallel Execution Opportunities

| Parallel Group | Tasks | Why Parallel |
|----------------|-------|--------------|
| Phase 1 setup | T002 ∥ T003 | Independent file creation |
| Phase 2 native modules | T007 ∥ T009 ∥ T010 ∥ T011 | Independent C++ source files (all link into same DLL) |
| Phase 2 + Phase 3 | T004-T012 ∥ T013-T014 | DLL build and JNA bridge are independent until integration |
| Phase 5 + Phase 6 + Phase 7 | T023 ∥ T024-T025 ∥ T026 | Independent features post-US1 |
| Phase 8 | T027 ∥ T028 | Build config and Kotlin loading logic are independent files |

---

## Implementation Strategy

### MVP First (US1 + US2 — Phases 1-4)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Native DLL (T004-T012) — in parallel with Phase 3
3. Complete Phase 3: Fallback wiring (T013-T016)
4. Complete Phase 4: NativeAudioRecorder + Gemini integration (T017-T022)
5. **STOP and VALIDATE**: Test both native path (AAC) and fallback path (WAV)
6. Delivers core value: ~2.7x payload reduction with safe fallback

### Incremental Delivery

1. MVP (above) → Test with quickstart.md P1 checklists
2. Add US3 verification (T023) → Confirm amplitude feedback works
3. Add US4 silence trimming (T024-T025) → Test with quickstart.md P3 checklist
4. Add US5 noise suppression verification (T026) → Test with quickstart.md P3 checklist
5. Add bundling + edge cases + CI (T027-T032) → Full feature complete

### Estimated Effort per Phase

| Phase | Tasks | Effort | Notes |
|-------|-------|--------|-------|
| 1. Setup | 3 | Small | Config changes only |
| 2. Native DLL | 9 | Large | Core C++ implementation |
| 3. US2 Fallback | 4 | Medium | JNA bridge + factory |
| 4. US1 Compressed | 6 | Medium | Kotlin integration |
| 5. US3 Amplitude | 1 | Small | Verification only |
| 6. US4 VAD | 2 | Small | Logic in stopRecording |
| 7. US5 Noise | 1 | Small | Verification only |
| 8. Bundling | 2 | Small | Build config |
| 9. Edge Cases | 3 | Medium | Error handling |
| 10. Polish | 1 | Small | CI workflow |

---

## Notes

- [P] tasks = different files, no dependencies — can run in parallel
- [Story] label maps task to specific user story for traceability
- Tests are NOT included per constitution IV (manual testing only) — use quickstart.md verification checklists instead
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
