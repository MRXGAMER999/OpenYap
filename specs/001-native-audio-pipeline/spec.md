# Feature Specification: Native Audio Pipeline

**Feature Branch**: `001-native-audio-pipeline`  
**Created**: 2026-03-13  
**Status**: Done  
**Input**: User description: "Native C++ audio pipeline with WASAPI capture, AAC encoding, and JNA integration — replacing the Java Sound-based audio capture with a native solution for compressed audio, voice activity detection, and noise suppression"

## Clarifications

### Session 2026-03-13

- Q: Should the system produce diagnostic logging or user notification when the native pipeline falls back or encounters encoding failures? → A: Log fallback events and encoding failures to the application log (not visible to users).
- Q: Should VAD silence trimming use a fixed sensitivity threshold or be user-configurable? → A: Fixed sensitivity with conservative defaults (no user control).
- Q: When the microphone is disconnected during recording, should the user be notified and should partial audio be preserved? → A: Stop recording, preserve captured audio, show a brief non-blocking notification.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Compressed Audio Upload (Priority: P1)

A user records a voice message and sends it to the AI assistant. The recorded audio is compressed before upload, reducing the data sent over the network by approximately 2.7x compared to the current uncompressed format. The user experiences the same recording flow — press record, speak, stop — but the upload completes faster due to the smaller payload.

**Why this priority**: Audio compression is the core value proposition of this feature. It directly reduces upload latency, which is the single biggest performance bottleneck after the non-streaming API. Every user benefits immediately from smaller payloads regardless of network speed.

**Independent Test**: Can be fully tested by recording a 30-second voice message and verifying the uploaded payload is approximately 480 KB (down from ~1.28 MB), and that the AI assistant correctly transcribes/processes the audio.

**Acceptance Scenarios**:

1. **Given** the native audio pipeline is available, **When** a user records a 30-second voice message and submits it, **Then** the uploaded audio payload is approximately 2.7x smaller than the equivalent uncompressed recording.
2. **Given** the native audio pipeline is available, **When** a user records and submits audio, **Then** the AI service correctly processes the compressed audio and returns an accurate response.
3. **Given** the native audio pipeline is available, **When** a user records audio of any duration up to 20 minutes, **Then** the compressed output stays within the 20 MB inline data limit of the AI service.

---

### User Story 2 - Graceful Fallback to Existing Audio (Priority: P1)

A user whose system does not support the native audio pipeline (e.g., missing native library, unsupported Windows edition) records a voice message. The application transparently falls back to the existing Java Sound-based recording. The user experiences no errors, crashes, or degraded functionality — only the performance benefit of compression is absent.

**Why this priority**: Equal to P1 because reliability is non-negotiable. The native pipeline must never break the existing experience. Users without native support must still be able to use the application exactly as before.

**Independent Test**: Can be fully tested by removing/hiding the native library and verifying the application records, encodes, and submits audio using the existing fallback path without errors.

**Acceptance Scenarios**:

1. **Given** the native library is not available on the system, **When** the application starts, **Then** it silently falls back to the existing audio recording mechanism without error messages or user intervention.
2. **Given** the native library fails to initialize at runtime, **When** a user attempts to record, **Then** the application uses the fallback recorder and the user can successfully record and submit audio.
3. **Given** the fallback recorder is active, **When** the user records and submits audio, **Then** the AI service processes the uncompressed audio correctly (same behavior as before this feature).

---

### User Story 3 - Native Audio Capture with Real-Time Feedback (Priority: P2)

A user records a voice message using the native audio capture system. During recording, they see real-time amplitude feedback (visual indication of their voice level), confirming the microphone is working and audio is being captured. The native capture provides lower latency and more reliable device access compared to the legacy approach.

**Why this priority**: Native capture improves recording reliability and enables real-time visual feedback, but compressed encoding (P1) delivers more immediate user value. This story enhances the recording experience but is not required for the core compression benefit.

**Independent Test**: Can be fully tested by starting a recording, observing the amplitude indicator updating in real-time, and verifying the captured audio is clear and complete.

**Acceptance Scenarios**:

1. **Given** the native audio pipeline is available, **When** a user starts recording, **Then** the application captures audio from the default input device with less than 20ms latency.
2. **Given** recording is in progress, **When** the user speaks, **Then** a real-time amplitude indicator reflects their voice level with visually responsive updates.
3. **Given** recording is in progress, **When** the user stops recording, **Then** all captured audio is preserved without gaps or corruption.

---

### User Story 4 - Silence Trimming via Voice Activity Detection (Priority: P3)

A user records a voice message that includes periods of silence before, during, or after speaking. The system automatically trims leading and trailing silence from the recording, further reducing the upload payload and improving AI processing efficiency. The user does not need to take any action — trimming happens transparently.

**Why this priority**: VAD provides incremental payload reduction beyond compression. A 30-second recording with only 10 seconds of speech could be trimmed to ~12 seconds, further reducing upload size. However, the primary compression from P1 already delivers the major improvement.

**Independent Test**: Can be fully tested by recording a message with 5 seconds of silence before and after speaking, then verifying the uploaded audio contains only the speech portion plus small safety margins.

**Acceptance Scenarios**:

1. **Given** a recording contains leading silence of 3+ seconds, **When** the recording is processed, **Then** the leading silence is trimmed to a brief margin (under 0.5 seconds).
2. **Given** a recording contains trailing silence of 3+ seconds, **When** the recording is processed, **Then** the trailing silence is trimmed to a brief margin (under 0.5 seconds).
3. **Given** a recording contains speech interspersed with brief pauses (under 1 second), **When** the recording is processed, **Then** the pauses are preserved (not incorrectly clipped as silence).

---

### User Story 5 - Background Noise Reduction (Priority: P3)

A user records a voice message in a noisy environment (e.g., fan noise, air conditioning, street noise). The system applies noise suppression to the audio before encoding, resulting in clearer audio sent to the AI assistant. This improves transcription accuracy and the overall quality of AI responses.

**Why this priority**: Noise suppression improves audio quality but is less critical than compression and capture reliability. The AI service can handle moderately noisy audio, so this is an enhancement rather than a necessity.

**Independent Test**: Can be fully tested by recording in an environment with steady background noise (e.g., a running fan), then comparing the submitted audio quality with and without noise suppression enabled.

**Acceptance Scenarios**:

1. **Given** the native audio pipeline is available and the user's system supports audio processing, **When** a user records in an environment with steady background noise, **Then** the resulting audio has noticeably reduced background noise compared to the raw capture.
2. **Given** noise suppression is active, **When** the user speaks, **Then** the speech quality is preserved without noticeable distortion or artifacts.
3. **Given** the system does not support the noise suppression capability, **When** the user records audio, **Then** the recording proceeds normally without noise suppression (no errors or degradation).

---

### Edge Cases

- What happens when the user's microphone sample rate differs from the required encoding rate? The system must resample to a compatible rate transparently.
- What happens when the user unplugs their microphone during recording? The system should stop recording gracefully, preserve any audio captured so far, and show a brief non-blocking notification informing the user that recording was stopped.
- What happens when the compressed audio output exceeds the AI service's inline data limit (20 MB)? The system should warn the user or handle the overflow gracefully.
- What happens when the native library is present but a specific encoding capability is unavailable? The system should fall back to uncompressed audio rather than failing.
- What happens when the user records in complete silence (no speech detected by VAD)? The system should still submit a minimal audio clip rather than submitting nothing.
- What happens when multiple audio input devices are available? The system should use the system default device, consistent with current behavior.
- What happens when the application is shut down during recording? The system should clean up native resources without leaking memory or crashing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST compress recorded audio to achieve approximately 2.7x payload reduction compared to the current uncompressed format for recordings of any duration.
- **FR-002**: System MUST ensure compressed audio is accepted and correctly processed by the target AI service (Gemini), producing accurate transcription/responses.
- **FR-003**: System MUST automatically fall back to the existing Java Sound-based recording if the native audio pipeline is unavailable, without user intervention or visible errors.
- **FR-004**: System MUST capture audio from the system's default input device at a sample rate compatible with the encoding requirements (resampling if the device's native rate differs).
- **FR-005**: System MUST provide a real-time amplitude indicator during recording that reflects the user's current voice level.
- **FR-006**: System MUST trim leading and trailing silence from recordings using a fixed, conservative sensitivity threshold, preserving all speech content with small safety margins. No user-configurable sensitivity setting is required.
- **FR-007**: System MUST preserve brief pauses within speech (under 1 second) during silence trimming — only extended silence at the start and end should be removed.
- **FR-008**: System MUST apply background noise reduction when the underlying platform supports it, without degrading speech quality.
- **FR-009**: System MUST gracefully handle scenarios where the native library is present but specific capabilities (encoding, capture, noise suppression) fail at runtime, falling back to available alternatives.
- **FR-010**: System MUST support recordings up to at least 20 minutes in duration within the AI service's inline data limit.
- **FR-011**: System MUST properly release all native resources (memory, audio devices, encoding contexts) when recording stops or the application exits, preventing resource leaks.
- **FR-012**: System MUST maintain strong references to any callbacks passed to native code for the entire duration they may be invoked, preventing garbage collection-related crashes.
- **FR-013**: System MUST detect native pipeline availability at startup and select the appropriate recording path (native or fallback) once, maintaining that selection for the session.
- **FR-014**: System MUST pass the correct audio format identifier to the AI service based on which recording path (native compressed or fallback uncompressed) is active.
- **FR-015**: System MUST log diagnostic events to the application log (not visible to users) when the native library fails to load, when fallback activates, and when encoding failures occur, to support debugging and support workflows.
- **FR-016**: System MUST stop recording gracefully when the audio input device is disconnected mid-recording, preserve all audio captured up to that point, and display a brief non-blocking notification to the user.

### Key Entities

- **Audio Recording**: A captured audio segment with properties including duration, sample rate, channel count, format (compressed or uncompressed), and raw audio data. Produced by either the native or fallback recording path.
- **Audio Pipeline**: The system component responsible for capturing, processing (VAD, noise suppression), and encoding audio. Exists in two variants: native (with compression) and fallback (uncompressed).
- **Amplitude Level**: A real-time signal (0.0 to 1.0 range) representing the current audio input volume, used to drive the visual recording indicator.
- **Voice Activity Detection (VAD) Result**: A per-frame classification of audio as either "speech" or "silence," used to determine trimming boundaries.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Audio upload payload for a 30-second recording is reduced from ~1.28 MB to ~480 KB (approximately 2.7x reduction).
- **SC-002**: The AI service (Gemini) correctly processes 100% of compressed audio submissions with the same accuracy as uncompressed audio.
- **SC-003**: Users on systems without the native library experience zero regressions — all existing functionality works identically to the pre-feature state.
- **SC-004**: Real-time amplitude feedback updates at least 20 times per second during recording, with less than 50ms visual delay from actual audio input.
- **SC-005**: Silence trimming removes at least 80% of leading and trailing silence (3+ seconds) from recordings while preserving 100% of speech content.
- **SC-006**: No native resource leaks (memory, audio devices, encoding contexts) after 100 consecutive record/stop cycles.
- **SC-007**: Application startup with the native library available adds no more than 500ms to initialization time.
- **SC-008**: The fallback path activates within 1 second if the native library fails to load, with no user-visible error.
- **SC-009**: Recordings up to 20 minutes produce compressed output under 20 MB.

## Assumptions

- The target deployment environment is Windows (64-bit), which is the primary platform for this desktop application.
- The AI service (Gemini) reliably supports AAC audio format as documented. If AAC is rejected, FLAC or MP3 are available as alternative compressed formats.
- Users have a functioning audio input device (microphone) connected to their system. The application does not need to handle the case of zero audio devices (existing behavior is unchanged).
- The native library will be prebuilt and bundled with the application distribution — contributors do not need to build it from source.
- The minimum supported Windows version is Windows 8, which provides the required audio encoding capabilities.
- The existing `AudioRecorder` interface is stable and will not change as part of this feature — the native recorder implements the same interface.
- JNA version 5.17.0 or later is available, providing the callback stability improvements needed for safe native interop.
