# Quickstart: Native Audio Pipeline

**Feature**: 001-native-audio-pipeline
**Prerequisites**: Windows 10+ (64-bit), Visual Studio 2022 with C++ Desktop workload, CMake 3.21+, JDK 17+

---

## 1. Build the Native DLL

```powershell
# From repository root
cmake -S native -B native/build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build native/build
```

If Ninja is not installed, use the Visual Studio generator:

```powershell
cmake -S native -B native/build -G "Visual Studio 17 2022" -A x64
cmake --build native/build --config Release
```

Output: `native/build/openyap_native.dll` (or `native/build/Release/openyap_native.dll` with VS generator)

---

## 2. Place DLL for Development

Copy the built DLL to a location on PATH, or place it where the Compose Desktop app can find it:

```powershell
# Option A: Copy to composeApp resources (simulates packaged app)
mkdir -Force composeApp/resources/windows-x64
copy native/build/openyap_native.dll composeApp/resources/windows-x64/

# Option B: Copy to a PATH directory (simpler for dev)
copy native/build/openyap_native.dll .
```

The `NativeAudioBridge` checks `compose.application.resources.dir` system property first (set by Compose Desktop in packaged builds), then falls back to the system `PATH` / working directory.

---

## 3. Run the Application

```powershell
.\gradlew.bat :composeApp:run
```

Check the application log for:
- `"Native audio pipeline available"` — DLL loaded and initialized successfully
- `"Native audio pipeline unavailable, using fallback"` — DLL not found, using Java Sound

---

## 4. Manual Verification Checklist

Per Constitution IV (manual testing only):

### P1: Compressed Audio Upload
- [ ] Record a ~30-second voice message
- [ ] Verify the temp file is `.aac` (not `.wav`)
- [ ] Verify the upload payload is ~480 KB (check via network inspector or log)
- [ ] Verify Gemini correctly processes the audio and returns accurate text
- [ ] Verify the temp file is deleted after processing

### P1: Graceful Fallback
- [ ] Remove/rename `openyap_native.dll`
- [ ] Restart the application
- [ ] Verify recording works with `.wav` output (same as before)
- [ ] Verify no error messages or crashes
- [ ] Restore the DLL and verify native pipeline resumes

### P2: Native Capture with Amplitude
- [ ] Start recording and observe the amplitude indicator
- [ ] Verify it responds to voice in real-time
- [ ] Verify captured audio is clear and complete

### P3: Silence Trimming
- [ ] Record with 5+ seconds of silence before and after speech
- [ ] Verify the uploaded audio is noticeably shorter than the recording duration
- [ ] Verify speech content is fully preserved

### P3: Noise Suppression
- [ ] Record in an environment with steady background noise (fan, AC)
- [ ] Compare audio quality with the fallback recorder
- [ ] Verify speech is not distorted

### Edge Cases
- [ ] Disconnect microphone during recording — verify graceful stop + notification
- [ ] Record in complete silence — verify minimal audio clip is still submitted
- [ ] Rapid start/stop cycles (10+) — verify no resource leaks or crashes
- [ ] Long recording (5+ minutes) — verify output stays under Gemini limits

---

## 5. Project Structure Reference

```
native/                          # C++ DLL project (build with CMake)
├── CMakeLists.txt
├── src/
│   ├── openyap_native.h         # Public C API
│   ├── openyap_native.cpp       # Entry point
│   ├── audio_capture.cpp        # WASAPI capture
│   ├── audio_encoder.cpp        # MF AAC encoder
│   ├── vad.cpp                  # Voice activity detection
│   └── noise_suppressor.cpp     # Noise reduction
└── prebuilt/windows-x64/        # Prebuilt DLL for distribution

shared/src/jvmMain/.../platform/
├── NativeAudioBridge.kt         # JNA bindings + DLL loading
├── NativeAudioRecorder.kt       # AudioRecorder impl (native)
└── JvmAudioRecorder.kt          # AudioRecorder impl (fallback)
```

---

## 6. Key Technical Constraints

| Constraint | Detail | Source |
|---|---|---|
| MF AAC sample rate | Only 44100 or 48000 Hz input | Windows MF docs |
| MF AAC payload type | Must be ADTS (`MF_MT_AAC_PAYLOAD_TYPE = 1`) | research.md R-001 |
| MF AAC min bitrate | 96 kbps (cannot go lower) | Windows MF docs |
| JNA callback safety | Store strong reference to callback as instance field | research.md R-006 |
| WASAPI resampling | Use `AUTOCONVERTPCM + SRC_DEFAULT_QUALITY` flags | research.md R-001 |
| Noise suppression | Set `AudioCategory_Communications`, never `AUDCLNT_STREAMOPTIONS_RAW` | research.md R-005 |
| Gemini inline limit | 20 MB max per request | Gemini API docs |
