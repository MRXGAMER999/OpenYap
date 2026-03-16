# OpenYap Codebase Connectivity

This document is intentionally exhaustive and is written for a follow-on agent that must produce a Koin dependency-injection refactor plan without opening source files.

All file references include line numbers.

## Section 1: Complete Dependency Graph

This section covers every class/object instantiated in `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:68-173`.

### Startup Remember Order In `main.kt`

1. `WindowsCredentialStorage()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:68`
2. `createOpenYapDatabase(dbPath)` after `PlatformInit.dataDir.resolve("openyap.db")` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
3. `RoomSettingsRepository(database, secureStorage)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:73`
4. `RoomHistoryRepository(database)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:74`
5. `RoomDictionaryRepository(database)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:75`
6. `RoomUserProfileRepository(database)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:76`
7. `WindowsHotkeyManager()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:77`
8. `AudioPipelineConfig(...)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:78-95`
9. `WindowsPasteAutomation()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:97`
10. `WindowsForegroundAppDetector()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:98`
11. `WindowsPermissionManager()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:99`
12. `WindowsStartupManager()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:100`
13. `HttpClientFactory.createGeminiClient()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:101`
14. `HttpClientFactory.createGroqWhisperClient()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:102`
15. `HttpClientFactory.createGroqLLMClient()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:103`
16. `DictionaryEngine(dictionaryRepo)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:104`
17. `WindowsHotkeyDisplayFormatter()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:105`
18. `ComposeOverlayController()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:106`
19. `AudioFeedbackService()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:107`
20. `JvmAppDataResetter(...)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:108-115`
21. Anonymous `object : AudioFeedbackPlayer` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:117-129`
22. `RecordingViewModel(...)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:131-154`
23. `SettingsViewModel(...)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:155-167`
24. `HistoryViewModel(historyRepo)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:168`
25. `OnboardingViewModel(settingsRepo, permissionManager, groqLLMClient)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:169-170`
26. `DictionaryViewModel(dictionaryRepo, dictionaryEngine)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:171`
27. `UserProfileViewModel(userProfileRepo)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:172`
28. `StatsViewModel(historyRepo)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:173`

### 1.1 `WindowsCredentialStorage`

- Class: `WindowsCredentialStorage`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:17`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Instantiated by remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:68`
- Interfaces implemented: `SecureStorage` at `shared/src/commonMain/kotlin/com/openyap/platform/SecureStorage.kt:3-7`
- Internal state:
  - `prefs: Preferences` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:19` stores encrypted blobs in Java Preferences
  - `isWindows: Boolean` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:20` controls DPAPI vs plaintext fallback behavior
- Behavior detail:
  - `save()` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:22-32` writes plaintext on non-Windows, otherwise DPAPI-encrypts then Base64-encodes into Preferences
  - `load()` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:34-58` reads Preferences, decrypts with DPAPI on Windows, and transparently migrates old plaintext values by re-encrypting them
  - `delete()` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:60-63`
  - `clear()` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt:65-68`

### 1.2 `OpenYapDatabase` via `createOpenYapDatabase`

- Factory function: `createOpenYapDatabase(dbFilePath: String): OpenYapDatabase`
- File: `shared/src/jvmMain/kotlin/com/openyap/database/DatabaseFactory.kt:6-10`
- Source set: `shared:jvmMain`
- Constructor parameters: not a class constructor; the factory has one parameter:
  - `dbFilePath: String` - concrete type `String`
- Instantiated by remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
- Concrete argument origin:
  - `val dbPath = PlatformInit.dataDir.resolve("openyap.db").toString()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:70`
  - `PlatformInit.dataDir` is lazily created at `shared/src/jvmMain/kotlin/com/openyap/platform/PlatformInit.kt:9-12`
- What the factory does:
  - Calls `Room.databaseBuilder<OpenYapDatabase>(dbFilePath)` at `shared/src/jvmMain/kotlin/com/openyap/database/DatabaseFactory.kt:7`
  - Sets bundled SQLite driver with `BundledSQLiteDriver()` at `shared/src/jvmMain/kotlin/com/openyap/database/DatabaseFactory.kt:8`
  - Adds migrations `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, `MIGRATION_4_5` at `shared/src/jvmMain/kotlin/com/openyap/database/DatabaseFactory.kt:9`
  - Builds the database at `shared/src/jvmMain/kotlin/com/openyap/database/DatabaseFactory.kt:10`
- Database declaration:
  - `OpenYapDatabase : RoomDatabase` at `shared/src/commonMain/kotlin/com/openyap/database/OpenYapDatabase.kt:20`
  - DAOs exposed at `shared/src/commonMain/kotlin/com/openyap/database/OpenYapDatabase.kt:21-26`
  - `deleteAllData()` clears all tables at `shared/src/commonMain/kotlin/com/openyap/database/OpenYapDatabase.kt:28-35`
- State held:
  - Room database connection state is internal to Room; the class itself has no explicit Kotlin `StateFlow`, `MutableState`, or mutable properties in source

### 1.3 `RoomSettingsRepository`

- Class: `RoomSettingsRepository`
- File: `shared/src/commonMain/kotlin/com/openyap/repository/RoomSettingsRepository.kt:13-74`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `database: OpenYapDatabase` - concrete class
  - `secureStorage: SecureStorage` - interface
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:73`
- Concrete satisfiers in `main.kt`:
  - `database` comes from the remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
  - `secureStorage` comes from the remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:68`; concrete instance is `WindowsCredentialStorage`
- Interfaces implemented: `SettingsRepository` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:5-34`
- Internal state:
  - `settingsMutex: Mutex` at `shared/src/commonMain/kotlin/com/openyap/repository/RoomSettingsRepository.kt:19` serializes `updateSettings()` load-modify-save cycles

### 1.4 `RoomHistoryRepository`

- Class: `RoomHistoryRepository`
- File: `shared/src/commonMain/kotlin/com/openyap/repository/RoomHistoryRepository.kt:8-27`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `database: OpenYapDatabase` - concrete class
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:74`
- Concrete satisfier: database remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
- Interfaces implemented: `HistoryRepository` at `shared/src/commonMain/kotlin/com/openyap/repository/HistoryRepository.kt:5-9`
- Internal state: none beyond captured constructor reference

### 1.5 `RoomDictionaryRepository`

- Class: `RoomDictionaryRepository`
- File: `shared/src/commonMain/kotlin/com/openyap/repository/RoomDictionaryRepository.kt:8-40`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `database: OpenYapDatabase` - concrete class
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:75`
- Concrete satisfier: database remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
- Interfaces implemented: `DictionaryRepository` at `shared/src/commonMain/kotlin/com/openyap/repository/DictionaryRepository.kt:5-9`
- Internal state: none beyond captured constructor reference

### 1.6 `RoomUserProfileRepository`

- Class: `RoomUserProfileRepository`
- File: `shared/src/commonMain/kotlin/com/openyap/repository/RoomUserProfileRepository.kt:8-19`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `database: OpenYapDatabase` - concrete class
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:76`
- Concrete satisfier: database remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
- Interfaces implemented: `UserProfileRepository` at `shared/src/commonMain/kotlin/com/openyap/repository/UserProfileRepository.kt:5-7`
- Internal state: none beyond captured constructor reference

### 1.7 `WindowsHotkeyManager`

- Class: `WindowsHotkeyManager`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:35-329`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:77`
- Interfaces implemented: `HotkeyManager`, `Closeable` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:35`
- Internal state:
  - `scope: CoroutineScope` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:37`
  - `_hotkeyEvents: MutableSharedFlow<HotkeyEvent>` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:38`
  - `hotkeyEvents: SharedFlow<HotkeyEvent>` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:39`
  - `formatter: WindowsHotkeyDisplayFormatter` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:41`
  - `native: NativeAudioBridge.OpenYapNative?` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:42`
  - `controlMutex` and `captureMutex` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:43-44`
  - `fallbackLock`, `fallbackManager`, `fallbackCollectorJob` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:45-47`
  - `usingFallback` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:49-50`
  - `config: HotkeyConfig` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:52-53`
  - `isListening: Boolean` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:55-56`
  - `pendingCapture: CompletableDeferred<HotkeyCapture>?` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:58-59`
  - `nativeCallback` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:61-78`
- Fallback logic:
  - If `NativeAudioBridge.instance` is `null`, `usingFallback` starts as `true` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:42`, `50`
  - Constructor logs native vs JNA status at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:80-91`
  - `getOrCreateFallback()` creates `JnaWindowsHotkeyManager` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:249-259`
  - `switchToFallback(reason)` flips `usingFallback`, cancels native capture/listening, logs, sets config on fallback, and restarts listening if necessary at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:271-287`

### 1.8 `NativeAudioBridge`

- Object: `NativeAudioBridge`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:12-207`
- Source set: `shared:jvmMain`
- Instantiation: Kotlin object singleton; accessed in `main.kt` through `NativeAudioBridge.instance` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:79`
- Important properties:
  - `loadResult: LoadResult by lazy` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:19`
  - `instance: OpenYapNative?` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:21-22`
  - `isAvailable` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:24-25`
  - `failureReason` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:27-28`
- Fallback logic used by `main.kt`:
  - `main.kt` reads `val nativeAudio = NativeAudioBridge.instance` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:79`
  - If non-null, native path uses `NativeAudioRecorder` with `audio/mp4` and `.m4a` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:80-86`
  - If null, fallback path uses `JvmAudioRecorder` with `audio/wav` and `.wav` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:87-93`
- What `loadLibrary()` does:
  - Builds candidate DLL paths at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:68-115`
  - Tries each candidate with `Native.load(candidate, OpenYapNative::class.java)` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:39-41`
  - Calls `openyap_init()` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:42`
  - On success, installs a JVM shutdown hook that calls `openyap_capture_stop()` then `openyap_shutdown()` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:44-47`
  - On init failure, reads native last error and stores failure reason at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioBridge.kt:51-59`

### 1.9 `NativeAudioRecorder`

- Class: `NativeAudioRecorder`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:21-245`
- Source set: `shared:jvmMain`
- Constructor parameters:
  - `native: NativeAudioBridge.OpenYapNative` - concrete native bridge interface; default is `NativeAudioBridge.instance ?: error(...)` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:21-24`
- Instantiated inside remember block:
  - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:78-86`
  - Exact concrete argument: local `nativeAudio` from `val nativeAudio = NativeAudioBridge.instance` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:79`
- Interfaces implemented: `AudioRecorder`, `AudioRecorderDiagnostics`, `Closeable` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:24`
- Internal state:
  - `_amplitudeFlow` and `amplitudeFlow` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:36-37`
  - `pcmBuffer: ConcurrentLinkedQueue<ShortArray>` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:39`
  - `isRecording: AtomicBoolean` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:40`
  - `pendingWarning: AtomicReference<String?>` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:41`
  - `outputPath: String?` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:43-45`
  - `captureCallback` at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:46-51`
- What its state represents:
  - `amplitudeFlow` is live mic amplitude for the overlay and UI
  - `pcmBuffer` accumulates PCM frames from native callback until stop
  - `pendingWarning` stores microphone disconnect warning for later overlay flash
  - `outputPath` stores the target AAC output file being written

### 1.10 `JvmAudioRecorder`

- Class: `JvmAudioRecorder`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:24-133`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Instantiated inside fallback branch of remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:87-93`, exact instantiation at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:90`
- Interfaces implemented: `AudioRecorder` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:24`
- Internal state:
  - `_amplitudeFlow` and `amplitudeFlow` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:26-27`
  - `targetDataLine` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:29`
  - `recordingJob` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:30`
  - `audioData: ByteArrayOutputStream?` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:31`
  - `scope: CoroutineScope` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:32`
  - `audioFormat` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:34-40`
  - `currentOutputPath` at `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt:42-43`

### 1.11 `AudioPipelineConfig`

- Class: private data class `AudioPipelineConfig`
- File: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:405-409`
- Source set: `composeApp:jvmMain`
- Constructor parameters:
  - `audioRecorder: AudioRecorder` - interface
  - `audioMimeType: String` - concrete type
  - `audioFileExtension: String` - concrete type
- Instantiated by remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:78-95`
- Conditional logic:
  - Reads `NativeAudioBridge.instance` into `nativeAudio` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:79`
  - If non-null:
    - logs `Native audio pipeline available` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:81`
    - constructs `AudioPipelineConfig(audioRecorder = NativeAudioRecorder(nativeAudio), audioMimeType = "audio/mp4", audioFileExtension = ".m4a")` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:82-86`
  - Else:
    - logs `Native audio pipeline unavailable, using fallback` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:88`
    - constructs `AudioPipelineConfig(audioRecorder = JvmAudioRecorder(), audioMimeType = "audio/wav", audioFileExtension = ".wav")` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:89-93`
- State held: immutable data class only

### 1.12 `WindowsPasteAutomation`

- Class: `WindowsPasteAutomation`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsPasteAutomation.kt:16-139`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:97`
- Interfaces implemented: `PasteAutomation` at `shared/src/commonMain/kotlin/com/openyap/platform/PasteAutomation.kt:3-4`
- Internal state: no persistent mutable fields; behavior branches on `NativeAudioBridge.instance` inside `pasteText()` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsPasteAutomation.kt:25-33`

### 1.13 `WindowsForegroundAppDetector`

- Class: `WindowsForegroundAppDetector`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsForegroundAppDetector.kt:8-48`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:98`
- Interfaces implemented: `ForegroundAppDetector` at `shared/src/commonMain/kotlin/com/openyap/platform/ForegroundAppDetector.kt:3-4`
- Internal state: none

### 1.14 `WindowsPermissionManager`

- Class: `WindowsPermissionManager`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsPermissionManager.kt:11-49`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:99`
- Interfaces implemented: `PermissionManager` at `shared/src/commonMain/kotlin/com/openyap/platform/PermissionManager.kt:5-13`
- Internal state: none

### 1.15 `WindowsStartupManager`

- Class: `WindowsStartupManager`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsStartupManager.kt:7-116`
- Source set: `shared:jvmMain`
- Constructor parameters:
  - `appName: String = "OpenYap"` - concrete type with default value
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:100`
- Interfaces implemented: `StartupManager` at `shared/src/commonMain/kotlin/com/openyap/platform/StartupManager.kt:3-9`
- Internal state:
  - immutable constructor property `appName` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsStartupManager.kt:8`
  - computed property `isSupported` at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsStartupManager.kt:15-16`

### 1.16 `GeminiClient`

- Class: `GeminiClient`
- File: `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:18-327`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `client: HttpClient` - concrete Ktor class
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:101`
- Concrete satisfier:
  - `HttpClientFactory.createGeminiClient()` from `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:26-28`
  - That factory calls `GeminiClient(createHttpClient())` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:27`
- Interfaces implemented: `TranscriptionService` at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:18`
- Internal state:
  - immutable `client` constructor field
  - companion constants `BASE_URL`, `MAX_RETRIES`, `RETRY_DELAYS_MS`, `DEFAULT_MODELS`, `VERSIONED_SUFFIX`, `ALL_DISABLED_SAFETY_SETTINGS` at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:20-46`
- Http behavior:
  - `listModels()` calls `GET https://generativelanguage.googleapis.com/v1beta/models?key=...` at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:49-85`
  - `transcribe()` delegates to `processAudio()` at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:89-97`
  - `rewriteText()` posts to `.../models/{model}:generateContent` at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:147-173`
  - `processAudio()` posts inline Base64 audio to the same generateContent API at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:177-240`

### 1.17 `GroqWhisperClient`

- Class: `GroqWhisperClient`
- File: `shared/src/commonMain/kotlin/com/openyap/service/GroqWhisperClient.kt:18-157`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `client: HttpClient` - concrete Ktor class
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:102`
- Concrete satisfier:
  - `HttpClientFactory.createGroqWhisperClient()` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:30-32`
  - That factory calls `GroqWhisperClient(createHttpClient())` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:31`
- Interfaces implemented: `TranscriptionService` at `shared/src/commonMain/kotlin/com/openyap/service/GroqWhisperClient.kt:18`
- Internal state:
  - immutable `client`
  - companion constants `BASE_URL` and `AVAILABLE_MODELS` at `shared/src/commonMain/kotlin/com/openyap/service/GroqWhisperClient.kt:20-26`

### 1.18 `GroqLLMClient`

- Class: `GroqLLMClient`
- File: `shared/src/commonMain/kotlin/com/openyap/service/GroqLLMClient.kt:18-241`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `client: HttpClient` - concrete Ktor class
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:103`
- Concrete satisfier:
  - `HttpClientFactory.createGroqLLMClient()` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:34-36`
  - That factory calls `GroqLLMClient(createHttpClient())` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:35`
- Interfaces implemented: none
- Internal state:
  - immutable `client`
  - companion constants `BASE_URL`, retry constants, `DEFAULT_MODELS`, exclusion lists at `shared/src/commonMain/kotlin/com/openyap/service/GroqLLMClient.kt:20-44`

### 1.19 `DictionaryEngine`

- Class: `DictionaryEngine`
- File: `shared/src/commonMain/kotlin/com/openyap/service/DictionaryEngine.kt:8-182`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `repository: DictionaryRepository` - interface
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:104`
- Concrete satisfier:
  - `dictionaryRepo` from remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:75`; concrete class `RoomDictionaryRepository`
- Interfaces implemented: none
- Internal state:
  - companion thresholds and `STOP_WORDS` set at `shared/src/commonMain/kotlin/com/openyap/service/DictionaryEngine.kt:10-19`
  - no `StateFlow` or mutable class properties outside local method variables

### 1.20 `WindowsHotkeyDisplayFormatter`

- Class: `WindowsHotkeyDisplayFormatter`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyDisplayFormatter.kt:7-31`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:105`
- Interfaces implemented: `HotkeyDisplayFormatter` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyDisplayFormatter.kt:5-6`
- Internal state: none

### 1.21 `ComposeOverlayController`

- Class: `ComposeOverlayController`
- File: `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:25-84`
- Source set: `composeApp:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:106`
- Interfaces implemented: `OverlayController`, `Closeable` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:25`
- Internal state:
  - `_uiState: MutableStateFlow<OverlayUiState>` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:27`
  - `uiState: StateFlow<OverlayUiState>` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:28`
  - `scope: CoroutineScope` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:30`
  - `flashLock` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:31`
  - `lastFlashJob: Job?` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:32`
- What state represents:
  - `visible` controls whether `ComposeOverlayWindow` renders at all
  - `state` is one of `OverlayState.RECORDING`, `PROCESSING`, `SUCCESS`, `ERROR`
  - `level` is live input amplitude
  - `durationSeconds` is live recording duration
  - `flashMessage` is the transient overlay callout text

### 1.22 `AudioFeedbackService`

- Class: `AudioFeedbackService`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackService.kt:12-74`
- Source set: `shared:jvmMain`
- Constructor parameters: none
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:107`
- Interfaces implemented: `Closeable` at `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackService.kt:12`
- Internal state:
  - `clips: MutableMap<Tone, Clip>` at `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackService.kt:16`
  - `volume: Float` at `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackService.kt:17`

### 1.23 `JvmAppDataResetter`

- Class: `JvmAppDataResetter`
- File: `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAppDataResetter.kt:11-36`
- Source set: `shared:jvmMain`
- Constructor parameters:
  - `secureStorage: SecureStorage` - interface
  - `database: OpenYapDatabase` - concrete class
  - `dataDir: Path` - concrete `java.nio.file.Path`
  - `tempDir: Path` - concrete `java.nio.file.Path`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:108-115`
- Concrete satisfiers:
  - `secureStorage` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:68`
  - `database` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
  - `dataDir = PlatformInit.dataDir` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:112`
  - `tempDir = PlatformInit.tempDir` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:113`
- Interfaces implemented: none
- Internal state: constructor references only

### 1.24 Anonymous `AudioFeedbackPlayer` object

- Object type: anonymous `object : AudioFeedbackPlayer`
- File of interface: `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:74-78`
- Source set of object creation: `composeApp:jvmMain`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:117-129`
- Captured dependency:
  - `audioFeedbackService: AudioFeedbackService` from remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:107`
- Methods implemented:
  - `playStart()` delegates to `audioFeedbackService.play(AudioFeedbackService.Tone.START)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:119-120`
  - `playStop()` delegates to `...Tone.STOP` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:122`
  - `playTooShort()` delegates to `...Tone.TOO_SHORT` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:123-124`
  - `playError()` delegates to `...Tone.ERROR` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:126-127`
- State held: no own state; it closes over `audioFeedbackService`

### 1.25 `RecordingViewModel`

- Class: `RecordingViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:88-610`
- Source set: `shared:commonMain`
- Constructor parameter mapping to `main.kt` remember block `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:131-154`:
  - `hotkeyManager: HotkeyManager` - interface - satisfied by `hotkeyManager` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:77`
  - `audioRecorder: AudioRecorder` - interface - satisfied by `audioRecorder = audioPipeline.audioRecorder` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:96`
  - `geminiClient: GeminiClient` - concrete - satisfied by remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:101`
  - `groqWhisperClient: TranscriptionService` - interface - satisfied by `groqWhisperClient` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:102`, concrete `GroqWhisperClient`
  - `groqLLMClient: GroqLLMClient` - concrete - satisfied by remember block at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:103`
  - `pasteAutomation: PasteAutomation` - interface - satisfied by `WindowsPasteAutomation` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:97`
  - `foregroundAppDetector: ForegroundAppDetector` - interface - satisfied by `WindowsForegroundAppDetector` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:98`
  - `settingsRepository: SettingsRepository` - interface - satisfied by `RoomSettingsRepository` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:73`
  - `historyRepository: HistoryRepository` - interface - satisfied by `RoomHistoryRepository` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:74`
  - `dictionaryRepository: DictionaryRepository` - interface - satisfied by `RoomDictionaryRepository` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:75`
  - `userProfileRepository: UserProfileRepository` - interface - satisfied by `RoomUserProfileRepository` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:76`
  - `permissionManager: PermissionManager` - interface - satisfied by `WindowsPermissionManager` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:99`
  - `dictionaryEngine: DictionaryEngine` - concrete - satisfied by `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:104`
  - `overlayController: OverlayController` - interface - satisfied by `ComposeOverlayController` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:106`
  - `audioFeedbackPlayer: AudioFeedbackPlayer = NoOpAudioFeedbackPlayer()` - interface - overridden by anonymous object from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:117-129`
  - `audioMimeType: String` - concrete - satisfied by `audioPipeline.audioMimeType` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:148`
  - `audioFileExtension: String` - concrete - satisfied by `audioPipeline.audioFileExtension` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:149`
  - `tempDirProvider: () -> String` - lambda - satisfied by `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:150`
  - `fileReader: (String) -> ByteArray` - lambda - satisfied by `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:151`
  - `fileDeleter: (String) -> Unit` - lambda - satisfied by `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:152`
- Interfaces implemented: extends `ViewModel` only
- State held:
  - `_state: MutableStateFlow<RecordingUiState>` and `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:121-122`
  - `_effects: MutableSharedFlow<RecordingEffect>` and `effects` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:124-125`
  - `processingGeneration: AtomicInteger` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:127`
  - `recordingMutex: Mutex` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:128`
  - `durationJob: Job?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:129-130`
  - `currentRecordingPath: String?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:131-132`
  - `currentProcessingPath: String?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:134-135`
  - `recordingStartedAt: TimeMark?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:136`

### 1.26 `SettingsViewModel`

- Class: `SettingsViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:94-536`
- Source set: `shared:commonMain`
- Constructor parameter mapping to remember block `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:155-167`:
  - `settingsRepository: SettingsRepository` - interface - `settingsRepo` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:73`
  - `geminiClient: GeminiClient` - concrete - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:101`
  - `groqWhisperClient: GroqWhisperClient` - concrete - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:102`
  - `groqLLMClient: GroqLLMClient` - concrete - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:103`
  - `hotkeyManager: HotkeyManager` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:77`
  - `hotkeyDisplayFormatter: HotkeyDisplayFormatter` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:105`
  - `audioRecorder: AudioRecorder` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:96`
  - `startupManager: StartupManager = NoOpStartupManager()` - interface with default - overridden by `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:100`
  - `resetAppDataAction: suspend () -> Unit = {}` - lambda with default - overridden by `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:165`
- Interfaces implemented: extends `ViewModel` only
- State held: `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:106-107`

### 1.27 `HistoryViewModel`

- Class: `HistoryViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:25-70`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `historyRepository: HistoryRepository` - interface - satisfied by `historyRepo` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:74`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:168`
- State held: `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:29-30`

### 1.28 `OnboardingViewModel`

- Class: `OnboardingViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:75-281`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `settingsRepository: SettingsRepository` - interface - `settingsRepo` from `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:73`
  - `permissionManager: PermissionManager` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:99`
  - `groqLLMClient: GroqLLMClient` - concrete - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:103`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:169-170`
- State held:
  - `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:81-82`
  - `fetchJob` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:84`
  - `onboardingJob` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:85`
  - `apiKeyMutex` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:86`
  - `errorCounter` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:87`
  - `epoch` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:88`
  - `eventChannel: Channel<OnboardingEvent>` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:90`

### 1.29 `DictionaryViewModel`

- Class: `DictionaryViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:26-81`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `dictionaryRepository: DictionaryRepository` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:75`
  - `dictionaryEngine: DictionaryEngine` - concrete - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:104`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:171`
- State held: `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:31-32`

### 1.30 `UserProfileViewModel`

- Class: `UserProfileViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:29-94`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `userProfileRepository: UserProfileRepository` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:76`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:172`
- State held: `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:33-34`

### 1.31 `StatsViewModel`

- Class: `StatsViewModel`
- File: `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:21-62`
- Source set: `shared:commonMain`
- Constructor parameters:
  - `historyRepository: HistoryRepository` - interface - `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:74`
- Remember block: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:173`
- State held: `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:25-26`

### 1.32 `HttpClientFactory` internals

- File: `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:13-37`
- Source set: `shared:jvmMain`
- Factory methods and return types:
  - `createGeminiClient(): GeminiClient` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:26-28`
  - `createGroqWhisperClient(): GroqWhisperClient` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:30-32`
  - `createGroqLLMClient(): GroqLLMClient` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:34-36`
- Shared-client behavior:
  - Clients do not share a single `HttpClient`
  - Each factory method calls `createHttpClient()` independently at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:27`, `31`, `35`
  - Therefore each service receives a separate `HttpClient(CIO)` instance
- `createHttpClient()` details:
  - Return type `HttpClient`
  - Uses CIO engine with `HttpClient(CIO)` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:15`
  - Installs `ContentNegotiation` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:16-18`
  - Configures JSON with `Json { ignoreUnknownKeys = true }` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:17`
  - Installs `HttpTimeout` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:19-23`
  - Timeout values:
    - `connectTimeoutMillis = 15_000` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:20`
    - `requestTimeoutMillis = 120_000` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:21`
    - `socketTimeoutMillis = 120_000` at `shared/src/jvmMain/kotlin/com/openyap/platform/HttpClientFactory.kt:22`
- Base URLs used by the service classes, not by the factory:
  - Gemini uses `https://generativelanguage.googleapis.com/v1beta` at `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt:21`
  - Groq Whisper uses `https://api.groq.com/openai/v1` at `shared/src/commonMain/kotlin/com/openyap/service/GroqWhisperClient.kt:21`
  - Groq LLM uses `https://api.groq.com/openai/v1` at `shared/src/commonMain/kotlin/com/openyap/service/GroqLLMClient.kt:21`

## Section 2: Interface-to-Implementation Mapping

| Interface (commonMain) | File path | Implementation | File path | Module |
| --- | --- | --- | --- | --- |
| `AudioRecorder` | `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt` | `NativeAudioRecorder` and `JvmAudioRecorder` | `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt`, `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAudioRecorder.kt` | `shared` |
| `AudioRecorderDiagnostics` | `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorderDiagnostics.kt` | `NativeAudioRecorder` | `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt` | `shared` |
| `ForegroundAppDetector` | `shared/src/commonMain/kotlin/com/openyap/platform/ForegroundAppDetector.kt` | `WindowsForegroundAppDetector` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsForegroundAppDetector.kt` | `shared` |
| `HotkeyDisplayFormatter` | `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyDisplayFormatter.kt` | `WindowsHotkeyDisplayFormatter` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyDisplayFormatter.kt` | `shared` |
| `HotkeyManager` | `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyManager.kt` | `WindowsHotkeyManager` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt` | `shared` |
| `OverlayController` | `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt` | `ComposeOverlayController`, `NoOpOverlayController` | `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt`, `shared/src/jvmMain/kotlin/com/openyap/platform/NoOpOverlayController.kt` | `composeApp`, `shared` |
| `PasteAutomation` | `shared/src/commonMain/kotlin/com/openyap/platform/PasteAutomation.kt` | `WindowsPasteAutomation` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsPasteAutomation.kt` | `shared` |
| `PermissionManager` | `shared/src/commonMain/kotlin/com/openyap/platform/PermissionManager.kt` | `WindowsPermissionManager` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsPermissionManager.kt` | `shared` |
| `SecureStorage` | `shared/src/commonMain/kotlin/com/openyap/platform/SecureStorage.kt` | `WindowsCredentialStorage` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsCredentialStorage.kt` | `shared` |
| `StartupManager` | `shared/src/commonMain/kotlin/com/openyap/platform/StartupManager.kt` | `WindowsStartupManager`, `NoOpStartupManager` | `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsStartupManager.kt`, `shared/src/commonMain/kotlin/com/openyap/platform/StartupManager.kt` | `shared` |
| `AppEnumerator` | `shared/src/commonMain/kotlin/com/openyap/platform/AppEnumerator.kt` | `NoOpAppEnumerator` | `shared/src/jvmMain/kotlin/com/openyap/platform/NoOpAppEnumerator.kt` | `shared` |
| `SettingsRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt` | `RoomSettingsRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/RoomSettingsRepository.kt` | `shared` |
| `HistoryRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/HistoryRepository.kt` | `RoomHistoryRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/RoomHistoryRepository.kt` | `shared` |
| `DictionaryRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/DictionaryRepository.kt` | `RoomDictionaryRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/RoomDictionaryRepository.kt` | `shared` |
| `UserProfileRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/UserProfileRepository.kt` | `RoomUserProfileRepository` | `shared/src/commonMain/kotlin/com/openyap/repository/RoomUserProfileRepository.kt` | `shared` |
| `TranscriptionService` | `shared/src/commonMain/kotlin/com/openyap/service/TranscriptionService.kt` | `GeminiClient`, `GroqWhisperClient` | `shared/src/commonMain/kotlin/com/openyap/service/GeminiClient.kt`, `shared/src/commonMain/kotlin/com/openyap/service/GroqWhisperClient.kt` | `shared` |

### Interface method signatures and direct ViewModel dependencies

#### `AudioRecorder`

- Methods:
  - `suspend fun startRecording(outputPath: String, deviceId: String? = null)` at `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt:7`
  - `suspend fun stopRecording(): String` at `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt:8`
  - `val amplitudeFlow: StateFlow<Float>` at `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt:9`
  - `suspend fun hasPermission(): Boolean` at `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt:10`
  - `suspend fun listDevices(): List<AudioDevice>` at `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorder.kt:11`
- ViewModels depending on interface directly in constructor:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:90`
  - `SettingsViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:101`

#### `AudioRecorderDiagnostics`

- Methods:
  - `fun consumeWarning(): String?` at `shared/src/commonMain/kotlin/com/openyap/platform/AudioRecorderDiagnostics.kt:4`
- Direct constructor dependencies: none; `RecordingViewModel` uses a runtime cast inside `stopRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:355`

#### `ForegroundAppDetector`

- Methods:
  - `fun getForegroundAppName(): String?` at `shared/src/commonMain/kotlin/com/openyap/platform/ForegroundAppDetector.kt:4`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:95`

#### `HotkeyDisplayFormatter`

- Methods:
  - `fun format(binding: HotkeyBinding): String` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyDisplayFormatter.kt:6`
- ViewModels depending directly:
  - `SettingsViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:100`

#### `HotkeyManager`

- Methods:
  - `fun setConfig(config: HotkeyConfig)` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyManager.kt:9`
  - `fun startListening()` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyManager.kt:10`
  - `fun stopListening()` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyManager.kt:11`
  - `suspend fun captureNextHotkey(): HotkeyCapture` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyManager.kt:12`
  - `val hotkeyEvents: SharedFlow<HotkeyEvent>` at `shared/src/commonMain/kotlin/com/openyap/platform/HotkeyManager.kt:13`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:89`
  - `SettingsViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:99`

#### `OverlayController`

- Methods:
  - `fun show()` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:6`
  - `fun updateState(state: OverlayState)` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:7`
  - `fun updateLevel(level: Float)` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:8`
  - `fun updateDuration(seconds: Int)` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:9`
  - `fun dismiss()` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:10`
  - `fun flashMessage(message: String)` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:11`
  - `fun flashProcessing()` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:12`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:102`

#### `PasteAutomation`

- Methods:
  - `suspend fun pasteText(text: String, restoreClipboard: Boolean = true)` at `shared/src/commonMain/kotlin/com/openyap/platform/PasteAutomation.kt:4`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:94`

#### `PermissionManager`

- Methods:
  - `suspend fun checkMicrophonePermission(): PermissionStatus` at `shared/src/commonMain/kotlin/com/openyap/platform/PermissionManager.kt:6`
  - `fun openMicrophoneSettings(): Boolean` at `shared/src/commonMain/kotlin/com/openyap/platform/PermissionManager.kt:13`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:100`
  - `OnboardingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:77`

#### `SecureStorage`

- Methods:
  - `suspend fun save(key: String, value: String)` at `shared/src/commonMain/kotlin/com/openyap/platform/SecureStorage.kt:4`
  - `suspend fun load(key: String): String?` at `shared/src/commonMain/kotlin/com/openyap/platform/SecureStorage.kt:5`
  - `suspend fun delete(key: String)` at `shared/src/commonMain/kotlin/com/openyap/platform/SecureStorage.kt:6`
  - `suspend fun clear()` at `shared/src/commonMain/kotlin/com/openyap/platform/SecureStorage.kt:7`
- ViewModels depending directly: none

#### `StartupManager`

- Methods:
  - `val isSupported: Boolean` at `shared/src/commonMain/kotlin/com/openyap/platform/StartupManager.kt:4`
  - `suspend fun isEnabled(): Boolean` at `shared/src/commonMain/kotlin/com/openyap/platform/StartupManager.kt:6`
  - `suspend fun setEnabled(enabled: Boolean)` at `shared/src/commonMain/kotlin/com/openyap/platform/StartupManager.kt:8`
- ViewModels depending directly:
  - `SettingsViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:102`

#### `AppEnumerator`

- Methods:
  - `fun getInstalledApps(): List<InstalledApp>` at `shared/src/commonMain/kotlin/com/openyap/platform/AppEnumerator.kt:6`
- ViewModels depending directly: none

#### `SettingsRepository`

- Methods:
  - `suspend fun loadSettings(): AppSettings` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:6`
  - `suspend fun saveSettings(settings: AppSettings)` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:7`
  - `suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:16`
  - `suspend fun loadApiKey(): String?` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:18`
  - `suspend fun saveApiKey(key: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:19`
  - `suspend fun loadGroqApiKey(): String?` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:21`
  - `suspend fun saveGroqApiKey(key: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:22`
  - `suspend fun loadAppTone(appName: String): String?` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:24`
  - `suspend fun saveAppTone(appName: String, tone: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:25`
  - `suspend fun loadAllAppTones(): Map<String, String>` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:26`
  - `suspend fun loadAppPrompt(appName: String): String?` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:28`
  - `suspend fun saveAppPrompt(appName: String, prompt: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:29`
  - `suspend fun loadAllAppPrompts(): Map<String, String>` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:30`
  - `suspend fun removeAppCustomization(appName: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/SettingsRepository.kt:33`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:96`
  - `SettingsViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:95`
  - `OnboardingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:76`

#### `HistoryRepository`

- Methods:
  - `suspend fun loadEntries(): List<RecordingEntry>` at `shared/src/commonMain/kotlin/com/openyap/repository/HistoryRepository.kt:6`
  - `suspend fun addEntry(entry: RecordingEntry)` at `shared/src/commonMain/kotlin/com/openyap/repository/HistoryRepository.kt:7`
  - `suspend fun removeEntry(id: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/HistoryRepository.kt:8`
  - `suspend fun clearAll()` at `shared/src/commonMain/kotlin/com/openyap/repository/HistoryRepository.kt:9`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:97`
  - `HistoryViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:26`
  - `StatsViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:22`

#### `DictionaryRepository`

- Methods:
  - `suspend fun loadEntries(): List<DictionaryEntry>` at `shared/src/commonMain/kotlin/com/openyap/repository/DictionaryRepository.kt:6`
  - `suspend fun saveEntries(entries: List<DictionaryEntry>)` at `shared/src/commonMain/kotlin/com/openyap/repository/DictionaryRepository.kt:7`
  - `suspend fun addOrUpdate(entry: DictionaryEntry)` at `shared/src/commonMain/kotlin/com/openyap/repository/DictionaryRepository.kt:8`
  - `suspend fun remove(id: String)` at `shared/src/commonMain/kotlin/com/openyap/repository/DictionaryRepository.kt:9`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:98`
  - `DictionaryViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:27`

#### `UserProfileRepository`

- Methods:
  - `suspend fun loadProfile(): UserProfile` at `shared/src/commonMain/kotlin/com/openyap/repository/UserProfileRepository.kt:6`
  - `suspend fun saveProfile(profile: UserProfile)` at `shared/src/commonMain/kotlin/com/openyap/repository/UserProfileRepository.kt:7`
- ViewModels depending directly:
  - `RecordingViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:99`
  - `UserProfileViewModel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:30`

#### `TranscriptionService`

- Methods:
  - `suspend fun transcribe(audioBytes: ByteArray, mimeType: String, systemPrompt: String, apiKey: String, model: String, whisperPrompt: String = "", language: String = "en"): String` at `shared/src/commonMain/kotlin/com/openyap/service/TranscriptionService.kt:26-34`
  - `suspend fun listModels(apiKey: String): List<ModelInfo>` at `shared/src/commonMain/kotlin/com/openyap/service/TranscriptionService.kt:39`
- ViewModels depending directly:
  - `RecordingViewModel` depends on `TranscriptionService` for `groqWhisperClient` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:92`
  - no ViewModel depends on `TranscriptionService` for Gemini; `SettingsViewModel` depends on concrete `GeminiClient` and concrete `GroqWhisperClient`

## Section 3: ViewModel Full Specification

### 3.1 `RecordingViewModel`

#### 3.1.a Constructor

Verbatim constructor signature from `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:88-109`:

```kotlin
class RecordingViewModel(
    private val hotkeyManager: HotkeyManager,
    private val audioRecorder: AudioRecorder,
    private val geminiClient: GeminiClient,
    private val groqWhisperClient: TranscriptionService,
    private val groqLLMClient: GroqLLMClient,
    private val pasteAutomation: PasteAutomation,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val permissionManager: PermissionManager,
    private val dictionaryEngine: DictionaryEngine,
    private val overlayController: OverlayController,
    private val audioFeedbackPlayer: AudioFeedbackPlayer = NoOpAudioFeedbackPlayer(),
    private val audioMimeType: String,
    audioFileExtension: String,
    private val tempDirProvider: () -> String,
    private val fileReader: (String) -> ByteArray,
    private val fileDeleter: (String) -> Unit,
) : ViewModel()
```

#### 3.1.b State

- `RecordingUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:49-56`:
  - `recordingState: RecordingState = RecordingState.Idle`
  - `amplitude: Float = 0f`
  - `lastResultText: String? = null`
  - `error: String? = null`
  - `hasApiKey: Boolean = false`
  - `hasMicPermission: Boolean = false`
- `_state` / `state` pattern at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:121-122`
- Other state fields:
  - `_effects: MutableSharedFlow<RecordingEffect>(extraBufferCapacity = 8)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:124`
  - `effects: SharedFlow<RecordingEffect>` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:125`
  - `processingGeneration: AtomicInteger` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:127` tracks cancellation/invalidation of in-flight processing pipelines
  - `recordingMutex: Mutex` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:128` serializes `startRecording()`
  - `durationJob: Job?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:129-130` updates live duration every second
  - `currentRecordingPath: String?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:131-132` points to current raw output path while recording
  - `currentProcessingPath: String?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:134-135` points to the file currently being transcribed/rewritten/pasted
  - `recordingStartedAt: TimeMark?` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:136` supports monotonic elapsed-time calculation

#### 3.1.c Events

- `RecordingEvent` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:63-68`:
  - `ToggleRecording`
  - `CancelRecording`
  - `DismissError`
  - `RefreshState`

#### 3.1.d Effects

- `RecordingEffect` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:58-61`:
  - `ShowError(message: String)`
  - `PasteSuccess`

#### 3.1.e Public API

- `fun onEvent(event: RecordingEvent)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:171-178` dispatches UI and main-level recording commands
- `fun refreshPermissions()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:578-589` reloads settings and microphone permission, then recomputes `hasApiKey` and `hasMicPermission`

#### 3.1.f Internal orchestration

- Hotkey event flow:
  - In `init`, `viewModelScope.launch { hotkeyManager.hotkeyEvents.collect { ... } }` starts at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:138-149`
  - Mappings:
    - `HotkeyEvent.HoldDown -> startRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:142`
    - `HotkeyEvent.HoldUp -> stopRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:143`
    - `HotkeyEvent.CancelRecording -> cancelRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:144`
    - `HotkeyEvent.ToggleRecording -> toggleRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:145`
    - `HotkeyEvent.StartRecording -> startRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:146`
    - `HotkeyEvent.StopRecording -> stopRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:147`
- Stop pipeline step-by-step from `stopRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:290-550`:
  1. Return immediately unless state is `RecordingState.Recording` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:291`
  2. Cancel `durationJob` and null it at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:293-294`
  3. Compute elapsed seconds from `recordingStartedAt.elapsedNow()` or fallback UI duration at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:296-303`
  4. Clear `recordingStartedAt` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:303`
  5. If elapsed < `MIN_DURATION_SECONDS`:
     - best-effort `audioRecorder.stopRecording()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:305-306`
     - reload settings at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:308`
     - play too-short tone when enabled at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:309-311`
     - set `RecordingState.Error` and `error` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:313-318`
     - dismiss overlay at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:319`
     - delete temp file and clear `currentRecordingPath` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:320-321`
     - return at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:322`
  6. Load settings for the normal stop path at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:325`
  7. Calculate rounded duration at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:327`
  8. Call `audioRecorder.stopRecording()` in try/catch at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:328-346`
  9. If `stopRecording()` throws:
     - delete `currentRecordingPath` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:331`
     - clear `currentRecordingPath` and `currentProcessingPath` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:332-333`
     - dismiss overlay at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:334`
     - set error state at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:335-340`
     - play error tone if enabled at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:341-343`
     - emit `RecordingEffect.ShowError` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:344`
     - return at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:345`
  10. Play stop tone if enabled at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:348-350`
  11. Move current file tracking from recording to processing at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:352-353`
  12. Increment `processingGeneration` and consume recorder warning at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:354-355`
  13. Set UI state to `Processing` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:357`
  14. Set overlay state to `PROCESSING` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:358`
  15. Flash any recorder warning on overlay at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:359`
  16. Launch processing coroutine at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:361`
  17. In processing coroutine:
     - detect foreground app at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:363`
     - load Gemini API key at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:364`
     - load Groq API key at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:365`
     - read audio bytes from injected `fileReader` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:366`, `602`
     - reject files smaller than 100 bytes at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:368-370`
     - load per-app tone and prompt via `settingsRepository.loadAppTone/loadAppPrompt` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:372-374`
     - build `systemPrompt` with `PromptBuilder.build(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:375-381`
     - build `whisperPrompt` with `WhisperPromptBuilder.build(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:382-385`
- Provider decision logic:
  - `TranscriptionProvider.GEMINI`: direct `geminiClient.transcribe(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:388-396`
  - `TranscriptionProvider.GROQ_WHISPER`: direct `groqWhisperClient.transcribe(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:398-408`
  - `TranscriptionProvider.GROQ_WHISPER_GEMINI`:
    - first `groqWhisperClient.transcribe(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:410-419`
    - then `geminiClient.rewriteText(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:420-425`
  - `TranscriptionProvider.GROQ_WHISPER_GROQ`:
    - first `groqWhisperClient.transcribe(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:428-437`
    - then `groqLLMClient.rewriteText(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:439-444`
    - if rewrite throws `CancellationException`, it is rethrown at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:445-446`
    - if rewrite throws any other exception, it falls back to the raw transcript at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:447-448`
- Phrase expansion:
  - loads profile at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:465`
  - conditionally loads dictionary entries when `settings.dictionaryEnabled` is true at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:466-470`
  - calls `PhraseExpansionEngine.expandText(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:471-476`
- Save to history:
  - after successful paste and generation checks, adds `RecordingEntry` through `historyRepository.addEntry(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:490-499`
- Paste automation:
  - `pasteAutomation.pasteText(expandedResponse, restoreClipboard = false)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:483`
- Overlay control:
  - `overlayController.show()` at start at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:271`
  - `overlayController.updateDuration(seconds)` inside duration ticker at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:284`
  - `overlayController.updateLevel(amplitude)` from amplitude collector at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:153-155`
  - `overlayController.updateState(OverlayState.PROCESSING)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:358`
  - `overlayController.flashMessage(recorderWarning)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:359`
  - `overlayController.updateState(OverlayState.SUCCESS)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:512`
  - `overlayController.updateState(OverlayState.ERROR)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:542`
  - `overlayController.dismiss()` on short recording, stop failure, post-success delay, processing failure, and cancel paths at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:319`, `334`, `523`, `545`, `562`, `570`
- Audio feedback:
  - start tone at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:237-239`
  - error tone for missing API key or permission at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:224-226`, `232-234`
  - error tone on recorder start failure at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:257-259`
  - too-short tone at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:309-311`
  - stop tone at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:348-350`
  - stop failure tone at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:341-343`

### 3.2 `SettingsViewModel`

#### 3.2.a Constructor

Verbatim constructor signature from `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:94-104`:

```kotlin
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient,
    private val groqWhisperClient: GroqWhisperClient,
    private val groqLLMClient: GroqLLMClient,
    private val hotkeyManager: HotkeyManager,
    private val hotkeyDisplayFormatter: HotkeyDisplayFormatter,
    private val audioRecorder: AudioRecorder,
    private val startupManager: StartupManager = NoOpStartupManager(),
    private val resetAppDataAction: suspend () -> Unit = {},
) : ViewModel()
```

#### 3.2.b State

- `SettingsUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:26-65`:
  - `transcriptionProvider: TranscriptionProvider = TranscriptionProvider.GROQ_WHISPER_GROQ`
  - `apiKey: String = ""`
  - `groqApiKey: String = ""`
  - `geminiModel: String = "gemini-3.1-flash-lite-preview"`
  - `groqModel: String = "whisper-large-v3"`
  - `groqLLMModel: String = "moonshotai/kimi-k2-instruct-0905"`
  - `genZEnabled: Boolean = false`
  - `phraseExpansionEnabled: Boolean = false`
  - `dictionaryEnabled: Boolean = true`
  - `audioFeedbackEnabled: Boolean = true`
  - `soundFeedbackVolume: Float = 0.5f`
  - `startMinimized: Boolean = false`
  - `launchOnStartup: Boolean = false`
  - `startupSupported: Boolean = false`
  - `isSaving: Boolean = false`
  - `isResettingData: Boolean = false`
  - `saveMessage: String? = null`
  - `availableModels: List<ModelInfo> = emptyList()`
  - `groqModels: List<ModelInfo> = listOf(ModelInfo("whisper-large-v3", "Whisper Large V3"), ModelInfo("whisper-large-v3-turbo", "Whisper Large V3 Turbo"))`
  - `groqLLMModels: List<ModelInfo> = emptyList()`
  - `isLoadingModels: Boolean = false`
  - `isLoadingGroqLLMModels: Boolean = false`
  - `modelsFetchError: String? = null`
  - `groqLLMModelsFetchError: String? = null`
  - `hotkeyLabel: String = "Ctrl+Shift+R"`
  - `isCapturingHotkey: Boolean = false`
  - `hotkeyError: String? = null`
  - `appVersion: String = ""`
  - `audioDevices: List<AudioDevice> = emptyList()`
  - `selectedAudioDeviceId: String? = null`
  - `isLoadingDevices: Boolean = false`
  - `devicesFetchError: String? = null`
  - `primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL`
  - `useCaseContext: String = ""`
  - `whisperLanguage: String = "en"`
- `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:106-107`
- Other state fields: none beyond `_state`

#### 3.2.c Events

- `SettingsEvent` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:67-92`:
  - `SelectProvider(provider: TranscriptionProvider)`
  - `SaveApiKey(key: String)`
  - `SaveGroqApiKey(key: String)`
  - `SelectModel(modelId: String)`
  - `SelectGroqModel(modelId: String)`
  - `SelectGroqLLMModel(modelId: String)`
  - `ToggleGenZ(enabled: Boolean)`
  - `TogglePhraseExpansion(enabled: Boolean)`
  - `ToggleDictionary(enabled: Boolean)`
  - `ToggleAudioFeedback(enabled: Boolean)`
  - `SetSoundFeedbackVolume(volume: Float)`
  - `ToggleStartMinimized(enabled: Boolean)`
  - `ToggleLaunchOnStartup(enabled: Boolean)`
  - `ResetAppData`
  - `RefreshModels`
  - `RefreshGroqLLMModels`
  - `SelectAudioDevice(deviceId: String?)`
  - `RefreshDevices`
  - `CaptureHotkey`
  - `ClearHotkeyMessage`
  - `DismissSaveMessage`
  - `SelectUseCase(useCase: PrimaryUseCase)`
  - `SaveUseCaseContext(context: String)`
  - `SelectWhisperLanguage(language: String)`

#### 3.2.d Effects

- No separate sealed effect channel or flow

#### 3.2.e Public API

- `fun refresh()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:113-159` reloads persisted settings, keys, startup status, version, and then conditionally refreshes models and devices
- `fun onEvent(event: SettingsEvent)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:161-188` routes every UI event to internal handlers

#### 3.2.f Internal orchestration details requested

- `resetAppData()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:437-484`:
  1. Creates `defaults = AppSettings()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:439`
  2. Sets `isResettingData = true` and clears `saveMessage` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:440`
  3. Invokes injected `resetAppDataAction` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:442`
  4. Best-effort disables startup with `startupManager.setEnabled(false)` wrapped in `runCatching` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:443`
  5. Resets hotkey manager config to defaults at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:444`
  6. Resets state fields to defaults and sets `saveMessage = "App data reset. OpenYap will walk you through onboarding again."` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:445-474`
  7. On error, sets `isResettingData = false` and `saveMessage` to the exception message at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:475-481`
- Model list fetching:
  - `refresh()` loads both secure keys and persisted settings first at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:115-117`
  - if Gemini key is present, `fetchModels(apiKey)` is called at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:155`
  - if Groq key is present, `fetchGroqLLMModels(groqApiKey)` is called at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:156`
  - `fetchModels(apiKey)` sets loading true, calls `geminiClient.listModels(apiKey)`, updates `availableModels`, and auto-selects the first model if current selection is missing at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:344-361`
  - `fetchGroqLLMModels(apiKey)` sets loading true, calls `groqLLMClient.listModels(apiKey)`, ignores stale responses if `_state.value.groqApiKey != expectedKey`, updates `groqLLMModels`, and auto-selects the first model if current selection is missing at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:313-335`
  - `groqWhisperClient` is injected but not used in `SettingsViewModel` methods in current source
- Hotkey capture mode:
  - Enter capture mode by setting `isCapturingHotkey = true` and clearing messages at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:192-198`
  - Await `hotkeyManager.captureNextHotkey()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:200`
  - Convert capture to `HotkeyBinding` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:201-204`
  - Persist updated settings with `settingsRepository.updateSettings` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:205-209`
  - Apply the new config to the hotkey manager at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:210`
  - Update UI label and save message at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:211-216`
  - Timeout path sets `hotkeyError = "Hotkey capture timed out. Please try again."` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:218-224`
  - Generic failure path sets `hotkeyError = e.message ?: "Failed to capture hotkey"` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:225-231`

### 3.3 `HistoryViewModel`

#### 3.3.a Constructor

```kotlin
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel()
```

from `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:25-27`

#### 3.3.b State

- `HistoryUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:13-16`:
  - `entries: List<RecordingEntry> = emptyList()`
  - `isLoading: Boolean = true`
- `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:29-30`
- Other state: none

#### 3.3.c Events

- `HistoryEvent` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:18-23`:
  - `DeleteEntry(id: String)`
  - `CopyEntry(text: String)`
  - `ClearAll`
  - `Refresh`

#### 3.3.d Effects

- No effect flow

#### 3.3.e Public API

- `fun refresh()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:36` reloads entries from repository
- `fun onEvent(event: HistoryEvent)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:38-47` routes delete, clear, and refresh; copy is intentionally UI-handled only

### 3.4 `OnboardingViewModel`

#### 3.4.a Constructor

```kotlin
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissionManager: PermissionManager,
    private val groqLLMClient: GroqLLMClient,
) : ViewModel()
```

from `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:75-79`

#### 3.4.b State

- `OnboardingUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:23-40`:
  - `micPermission: PermissionStatus = PermissionStatus.UNKNOWN`
  - `micSkipped: Boolean = false`
  - `apiKey: String = ""`
  - `isLoaded: Boolean = false`
  - `isComplete: Boolean = false`
  - `currentStep: Int = 0`
  - `availableModels: List<ModelInfo> = emptyList()`
  - `selectedModel: String = ""`
  - `isLoadingModels: Boolean = false`
  - `modelsFetchError: String? = null`
  - `modelsFetchErrorId: Long = 0`
  - `isValidatingKey: Boolean = false`
  - `keyValidationSuccess: Boolean? = null`
  - `primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL`
  - `useCaseContext: String = ""`
  - `micSettingsUnavailable: Boolean = false`
- Computed properties from `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:41-60`:
  - `micStepComplete`
  - `apiKeyStepComplete`
  - `modelStepComplete`
  - `useCaseStepComplete`
  - `completedStepCount`
  - `progress`
  - `canComplete`
- `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:81-82`
- Other state:
  - `fetchJob`, `onboardingJob`, `apiKeyMutex`, `errorCounter`, `epoch`, `eventChannel` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:84-90`

#### 3.4.c Events

- `OnboardingEvent` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:63-73`:
  - `CheckMicPermission`
  - `OpenMicSettings`
  - `SkipMicPermission`
  - `SaveApiKey(key: String)`
  - `SelectModel(modelId: String)`
  - `RetryModelFetch`
  - `SelectUseCase(useCase: PrimaryUseCase)`
  - `SaveUseCaseContext(context: String)`
  - `CompleteOnboarding`

#### 3.4.d Effects

- No sealed effect flow, but there is an event serialization channel:
  - `eventChannel = Channel<OnboardingEvent>(Channel.UNLIMITED)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:90`
  - It is consumed in `init` via `eventChannel.receiveAsFlow().collect { event -> processEvent(event) }` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:92-96`

#### 3.4.e Public API

- `fun resetState()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:101-108` cancels in-flight onboarding work and replaces state with `OnboardingUiState(isLoaded = true)`
- `fun refresh()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:110-129` reloads permission, secure key, settings, step index, and optionally fetches models
- `fun onEvent(event: OnboardingEvent)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:131-133` enqueues the event into the unlimited channel

#### 3.4.f `completeOnboarding()` flow and onboarding-complete check

- How onboarding completion is checked:
  - `refresh()` loads settings with `val settings = settingsRepository.loadSettings()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:114`
  - It sets `isComplete = settings.onboardingCompleted` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:120`
  - `AppShell` uses `if (!onboardingState.isComplete)` to gate the whole app at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:152-154`
- `completeOnboarding()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:265-280`:
  1. Snapshot `currentEpoch = epoch` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:266`
  2. Cancel any previous `onboardingJob` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:267`
  3. Launch a new job at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:268`
  4. Call `settingsRepository.updateSettings { ... }` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:269`
  5. Persist, in this exact order inside the copied settings object:
     - `onboardingCompleted = true` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:271`
     - `transcriptionProvider = TranscriptionProvider.GROQ_WHISPER_GROQ` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:272`
     - `groqModel = "whisper-large-v3"` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:273`
  6. After the update completes, if the epoch still matches, set `isComplete = true` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt:276-277`

### 3.5 `DictionaryViewModel`

#### 3.5.a Constructor

```kotlin
class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val dictionaryEngine: DictionaryEngine,
) : ViewModel()
```

from `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:26-29`

#### 3.5.b State

- `DictionaryUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:14-17`:
  - `entries: List<DictionaryEntry> = emptyList()`
  - `isLoading: Boolean = true`
- `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:31-32`
- Other state: none

#### 3.5.c Events

- `DictionaryEvent` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:19-24`:
  - `AddEntry(original: String, replacement: String)`
  - `RemoveEntry(id: String)`
  - `ToggleEntry(id: String)`
  - `Refresh`

#### 3.5.d Effects

- No effect flow

#### 3.5.e Public API

- `fun refresh()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:38`
- `fun onEvent(event: DictionaryEvent)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt:40-47`

### 3.6 `UserProfileViewModel`

#### 3.6.a Constructor

```kotlin
class UserProfileViewModel(
    private val userProfileRepository: UserProfileRepository,
) : ViewModel()
```

from `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:29-31`

#### 3.6.b State

- `UserProfileUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:13-17`:
  - `profile: UserProfile = UserProfile()`
  - `isSaving: Boolean = false`
  - `saveMessage: String? = null`
- `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:33-34`
- Other state: none

#### 3.6.c Events

- `UserProfileEvent` from `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:19-27`:
  - `UpdateName(name: String)`
  - `UpdateEmail(email: String)`
  - `UpdatePhone(phone: String)`
  - `UpdateJobTitle(jobTitle: String)`
  - `UpdateCompany(company: String)`
  - `Save`
  - `DismissSaveMessage`

#### 3.6.d Effects

- No effect flow

#### 3.6.e Public API

- `fun refresh()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:40-45` reloads the profile and clears transient save state
- `fun onEvent(event: UserProfileEvent)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt:47-85` updates local form state or persists the profile

### 3.7 `StatsViewModel`

#### 3.7.a Constructor

```kotlin
class StatsViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel()
```

from `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:21-23`

#### 3.7.b State

- `StatsUiState` fields from `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:12-19`:
  - `totalRecordings: Int = 0`
  - `totalDurationSeconds: Int = 0`
  - `totalCharacters: Int = 0`
  - `averageDurationSeconds: Int = 0`
  - `topApps: List<Pair<String, Int>> = emptyList()`
  - `isLoading: Boolean = true`
- `_state` / `state` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:25-26`
- Other state: none

#### 3.7.c Events

- No event interface; only `refresh()` exists

#### 3.7.d Effects

- No effect flow

#### 3.7.e Public API

- `fun refresh()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:32` reloads stats from history entries

## Section 4: Cross-ViewModel Event Flows

### 4.1 Recording -> History and Stats refresh

- Where: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:250-261`
- Trigger collector: `LaunchedEffect(recordingViewModel, historyViewModel, statsViewModel)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:250`
- It collects `recordingViewModel.effects` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:251`
- On `RecordingEffect.PasteSuccess` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:253`:
  - calls `historyViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:254`
  - calls `statsViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:255`
- Data flow chain:
  - `RecordingViewModel` saves `RecordingEntry(...)` to `historyRepository.addEntry(...)` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:490-499`
  - then emits `RecordingEffect.PasteSuccess` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:510`
  - main collector receives effect and triggers refreshes
  - `HistoryViewModel.refresh()` loads entries at `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt:36`, `49-54`
  - `StatsViewModel.refresh()` recomputes totals from `historyRepository.loadEntries()` at `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt:32`, `34-60`

### 4.2 Settings event interception in `main.kt`

- Where: wrapper lambda passed as `onSettingsEvent` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:347-363`
- Every event first calls `settingsViewModel.onEvent(event)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:348`
- Intercepted events:
  - `SettingsEvent.SaveApiKey`
    - extra side-effect: `recordingViewModel.onEvent(RecordingEvent.RefreshState)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:349-350`
    - why: `RecordingViewModel` caches `hasApiKey` / `hasMicPermission` state and needs to recompute it immediately
  - `SettingsEvent.SaveGroqApiKey`
    - same extra side-effect and same reason at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:349-350`
  - `SettingsEvent.SelectProvider`
    - same extra side-effect and same reason at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:349-350`
  - `SettingsEvent.ResetAppData`
    - extra side-effects at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:352-361`:
      1. `onboardingViewModel.resetState()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:357`
      2. `appTones = emptyMap()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:358`
      3. `appPrompts = emptyMap()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:359`
      4. `backStack.clear()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:360`
      5. `backStack += Route.Home` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:361`
    - why:
      - `AppShell` gates on `onboardingState`; resetting it immediately hides the main UI before async disk wipe completes
      - `appTones` and `appPrompts` are local state outside ViewModels and would otherwise show stale customizations
      - `backStack` is local navigation state and must be reset to a safe route after destructive reset

### 4.3 Settings reset completion watcher

- Where: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:316-332`
- Pattern:
  - local `var wasResetting = false` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:317`
  - collect `settingsViewModel.state` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:318`
  - when `state.isResettingData` becomes true, set `wasResetting = true` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:319-320`
  - when `state.isResettingData` becomes false after previously being true, treat that as reset completion at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:321-323`
- Full refresh list triggered on true -> false transition:
  - `recordingViewModel.onEvent(RecordingEvent.RefreshState)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:324`
  - `historyViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:325`
  - `onboardingViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:326`
  - `dictionaryViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:327`
  - `userProfileViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:328`
  - `statsViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:329`

### 4.4 Onboarding event interception in `main.kt`

- Where: wrapper lambda passed as `onOnboardingEvent` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:365-377`
- Every event first calls `onboardingViewModel.onEvent(event)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:366`
- Intercepted events:
  - `OnboardingEvent.CompleteOnboarding`
    - extra side-effects:
      - `settingsViewModel.refresh()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:372`
      - `recordingViewModel.onEvent(RecordingEvent.RefreshState)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:373`
    - why: onboarding persisted settings after both ViewModels had already initialized, so both need an explicit refresh
  - `OnboardingEvent.SaveApiKey`
    - extra side-effect:
      - `recordingViewModel.onEvent(RecordingEvent.RefreshState)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:374-375`
    - why: home/recording readiness chips depend on `hasApiKey`

### 4.5 Volume sync

- Where: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:178-181`
- Watches: `settingsViewModel.state.collectAsState()` into `settingsStateForVolume` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:178`
- Effect key: `settingsStateForVolume.soundFeedbackVolume` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:179`
- Side-effect: `audioFeedbackService.setVolume(settingsStateForVolume.soundFeedbackVolume)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:180`

### 4.6 Other cross-cutting effects

- Startup settings load / hotkey init / customization load / start minimized / tone preload in `LaunchedEffect(Unit)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:185-216`
  - this effect is not ViewModel-owned; it mutates `hotkeyManager`, `appTones`, `appPrompts`, and `isVisible`
- `DisposableEffect(audioRecorder)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:222-226`
  - ensures the remembered recorder is closed when composition leaves
- Overlay state collection outside main window at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:247-248`
  - this means overlay lifecycle is independent from `Window` visibility
- `Window(onCloseRequest = { isVisible = false })` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:264-269`
  - closing the main window only hides the app, leaving tray, overlay, and hotkeys alive

## Section 5: Orphan State (State Outside ViewModels)

### 5.1 `appTones: Map<String, String>`

- Declared at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:175`
- Initialized with `emptyMap()` inside `remember { mutableStateOf(...) }`
- Loaded from repository in startup effect with `settingsRepo.loadAllAppTones()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:189`
- Read by `AppShell` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:344` and then by `CustomizationScreen` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:309-316`
- Mutated by:
  - `onSaveTone` via `appTones = appTones + (app to tone)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:380-382`
  - reset path `appTones = emptyMap()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:358`
  - remove path `appTones = appTones - app` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:388-390`
- After DI refactor, this state should move into a dedicated customization ViewModel or settings/customization state holder because it is repository-backed application state, not ephemeral UI-only state

### 5.2 `appPrompts: Map<String, String>`

- Declared at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:176`
- Initialized with `emptyMap()` inside `remember { mutableStateOf(...) }`
- Loaded from repository in startup effect with `settingsRepo.loadAllAppPrompts()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:190`
- Read by `AppShell` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:345` and then by `CustomizationScreen` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:309-316`
- Mutated by:
  - `onSavePrompt` via `appPrompts = appPrompts + (app to prompt)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:384-386`
  - reset path `appPrompts = emptyMap()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:359`
  - remove path `appPrompts = appPrompts - app` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:388-390`
- After DI refactor, this should live with `appTones` in the same customization-focused state holder or ViewModel

### 5.3 `isVisible: Boolean`

- Declared at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:183`
- Default value `true`
- Controls whether the main `Window` is composed at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:263`
- Mutated by:
  - startup effect when `settings.startMinimized` is true at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:192-193`
  - tray `Show` action at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:236`
  - window close request at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:265`
- After DI refactor, this can remain UI-shell state; it does not belong in a business ViewModel unless the goal is a global app-window controller service

### 5.4 `backStack: MutableList<Route>`

- Declared at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:220`
- Initialized with `mutableStateListOf<Route>(Route.Home)`
- Passed into `AppShell` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:336`
- Consumed for current route and navigation in `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:157`, `237-240`, `275-277`
- Reset during settings reset interception at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:360-361`
- After DI refactor, this can stay as app-shell UI navigation state unless the migration introduces a dedicated navigation coordinator

### 5.5 Audio tone preloading

- Happens in `LaunchedEffect(Unit)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:185-216`
- Loads two resource streams:
  - `/composeResources/openyap.composeapp.generated.resources/files/start_tone.wav` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:198-200`
  - `/composeResources/openyap.composeapp.generated.resources/files/stop_tone.wav` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:201-203`
- Builds `toneMap` and maps:
  - `Tone.START -> startToneBytes` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:205-206`
  - `Tone.STOP -> stopToneBytes` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:207-208`
  - `Tone.TOO_SHORT -> stopToneBytes` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:208-210`
- Calls `audioFeedbackService.preload(toneMap)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:212`
- Swallows exceptions as visual-only fallback at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:213-215`
- After DI refactor, this should move into app startup orchestration or an `AudioFeedbackInitializer` because it is service initialization, not composable view state

### 5.6 Hotkey initialization

- Happens in the same startup `LaunchedEffect(Unit)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:185-216`
- Loads settings at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:186`
- Applies config with `hotkeyManager.setConfig(settings.hotkeyConfig)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:187`
- Starts listener with `hotkeyManager.startListening()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:188`
- After DI refactor, this likely belongs in app startup composition or a lifecycle-aware startup coordinator, not inside a ViewModel

## Section 6: Lifecycle and Cleanup

### 6.1 Closeable instances

- `WindowsHotkeyManager : Closeable`
  - close implementation at `shared/src/jvmMain/kotlin/com/openyap/platform/WindowsHotkeyManager.kt:321-328`
  - does `stopListening()`, cancels fallback collector, closes fallback manager, clears refs, and cancels internal scope
  - cleaned up in tray Quit at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:241`
- `ComposeOverlayController : Closeable`
  - close implementation at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:81-82`
  - cancels internal coroutine scope only
  - cleaned up in tray Quit at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:239`
- `AudioFeedbackService : Closeable`
  - close implementation at `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackService.kt:70-73`
  - closes all preloaded `Clip`s and clears the map
  - cleaned up in tray Quit at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:240`
- `NativeAudioRecorder : Closeable`
  - close implementation at `shared/src/jvmMain/kotlin/com/openyap/platform/NativeAudioRecorder.kt:147-153`
  - stops capture if active, clears warning, resets buffers/path/amplitude
  - cleaned up by `DisposableEffect(audioRecorder)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:222-226` and also by tray Quit via cast-to-`Closeable` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:238`
- `JvmAudioRecorder`
  - no `Closeable` interface and no public `close()` method
  - only stopped by its own `stopRecording()` and internal `stopRecordingInternal()`

### 6.2 `DisposableEffect` blocks in `main.kt`

- `DisposableEffect(audioRecorder)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:222-226`
  - setup: none
  - onDispose: `runCatching { (audioRecorder as? java.io.Closeable)?.close() }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:224`
- `DisposableEffect(isDark)` inside `Window` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:271-302`
  - setup:
    - computes background color based on theme at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:272-276`
    - writes background to multiple Swing containers at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:277-281`
    - creates `applyTitleBar` runnable at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:283-285`
    - posts it via `SwingUtilities.invokeLater` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:286`
    - registers `windowOpened` and `windowActivated` listeners that reapply title bar styling at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:288-297`
  - onDispose:
    - removes the window listener at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:299-300`

### 6.3 Tray Quit handler

- Located at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:237-242`
- Exact sequence:
  1. `runCatching { (audioRecorder as? java.io.Closeable)?.close() }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:238`
  2. `overlayController.close()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:239`
  3. `audioFeedbackService.close()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:240`
  4. `hotkeyManager.close()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:241`
  5. `exitApplication()` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:242`
- Failure behavior:
  - only the audio-recorder close is wrapped in `runCatching`
  - `overlayController.close()`, `audioFeedbackService.close()`, and `hotkeyManager.close()` are not wrapped here, so an exception in those calls would abort the rest of the quit sequence

### 6.4 Window close behavior

- `onCloseRequest = { isVisible = false }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:265`
- This hides the window and does not exit the application
- Title bar theme effect is the `DisposableEffect(isDark)` described in Section 6.2

## Section 7: AppShell Parameter Threading

### 7.1 Parameter-to-screen mapping

| AppShell param | Passed to | Screen param name | Transformed? |
| --- | --- | --- | --- |
| `backStack` | `NavDisplay` | `backStack` | unchanged |
| `recordingState` | `HomeContent` | `state` | unchanged |
| `settingsState` | `HomeContent` | `settingsState` | unchanged |
| `historyState` | `HistoryScreen` | `state` | unchanged |
| `onHistoryEvent` | `HistoryScreen` | `onEvent` | unchanged |
| `onCopyToClipboard` | `HistoryScreen` | `onCopyToClipboard` | unchanged |
| `dictionaryState` | `DictionaryScreen` | `state` | unchanged |
| `settingsState.dictionaryEnabled` | `DictionaryScreen` | `isDictionaryEnabled` | transformed by field extraction |
| `onDictionaryEvent` | `DictionaryScreen` | `onEvent` | unchanged |
| `userProfileState` | `UserInfoScreen` | `state` | unchanged |
| `onUserProfileEvent` | `UserInfoScreen` | `onEvent` | unchanged |
| `statsState` | `StatsScreen` | `state` | unchanged |
| `onStatsRefresh` | `StatsScreen` | `onRefresh` | unchanged |
| `appTones` | `CustomizationScreen` | `appTones` | unchanged |
| `appPrompts` | `CustomizationScreen` | `appPrompts` | unchanged |
| `onSaveTone` | `CustomizationScreen` | `onSaveTone` | unchanged |
| `onSavePrompt` | `CustomizationScreen` | `onSavePrompt` | unchanged |
| `onRemoveApp` | `CustomizationScreen` | `onRemoveApp` | unchanged |
| `settingsState` | `SettingsScreen` | `state` | unchanged |
| `onSettingsEvent` | `SettingsScreen` | `onEvent` | unchanged |
| `onboardingState` | `OnboardingScreen` | `state` | unchanged |
| `onOnboardingEvent` | `OnboardingScreen` | `onEvent` | unchanged |

### 7.2 Parameters consumed by `AppShell` itself

- `backStack`
  - used to compute `currentRoute` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:157`
  - used by rail item selection and clicks at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:234-255`
  - passed into `NavDisplay` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:275-277`
- `recordingState`
  - used by nav-rail recording pulse at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:206-227`
  - used to decide whether to show `RecordingIndicator` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:332-340`
  - passed to `HomeContent` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:279-290`
- `onboardingState`
  - used for loading gate at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:124-149`
  - used for onboarding-complete gate at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:152-154`
  - also forwarded to `OnboardingScreen` route entry at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:321-325`
- `onRecordingEvent`
  - forwarded to `HomeContent`
  - also used directly by `RecordingIndicator` cancel/dismiss callbacks at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:334-339`

### 7.3 `HomeContent` requirements

- `HomeContent` signature at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:348-354`
- Parameters received:
  - `state: RecordingUiState`
  - `settingsState: SettingsUiState`
  - `onNavigateToSettings: () -> Unit`
  - `onEvent: (RecordingEvent) -> Unit`
  - `snackbarHostState: SnackbarHostState`
- `settingsState.hotkeyLabel` is used in display text at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:364`, `509`, `577`
- `settingsState.dictionaryEnabled` is not used in `HomeContent`; it is extracted by `AppShell` and passed to `DictionaryScreen` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:296-301`

### 7.4 `NavDisplay` `entryProvider`

- `entry<Route.Home>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:279-291`
  - calls `HomeContent(recordingState, settingsState, onNavigateToSettings = { ... }, onRecordingEvent, snackbarHostState)`
- `entry<Route.History>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:293-295`
  - calls `HistoryScreen(historyState, onHistoryEvent, onCopyToClipboard)`
- `entry<Route.Dictionary>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:296-301`
  - calls `DictionaryScreen(state = dictionaryState, isDictionaryEnabled = settingsState.dictionaryEnabled, onEvent = onDictionaryEvent)`
- `entry<Route.UserInfo>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:303-305`
  - calls `UserInfoScreen(userProfileState, onUserProfileEvent)`
- `entry<Route.Stats>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:306-308`
  - calls `StatsScreen(statsState, onRefresh = onStatsRefresh)`
- `entry<Route.Customization>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:309-316`
  - calls `CustomizationScreen(appTones, appPrompts, onSaveTone, onSavePrompt, onRemoveApp)`
- `entry<Route.Settings>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:318-320`
  - calls `SettingsScreen(settingsState, onSettingsEvent)`
- `entry<Route.Onboarding>` at `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:321-325`
  - calls `OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)`

## Section 8: Screen Composable Signatures

### 8.1 `SettingsScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/SettingsScreen.kt:79-82`:

```kotlin
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
)
```

- Consumes state type: `SettingsUiState`
- Emits event type: `SettingsEvent`
- Non-ViewModel params: none
- Internal stateful composable usage:
  - `remember` 11 times for input fields, dialog flags, snackbar host, and dropdown expansion state
  - `LaunchedEffect` 2 times for hotkey-error snackbar and timed save-message dismissal

### 8.2 `OnboardingScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/OnboardingScreen.kt:102-105`:

```kotlin
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
)
```

- Consumes state type: `OnboardingUiState`
- Emits event type: `OnboardingEvent`
- Non-ViewModel params: none
- Internal stateful composable usage:
  - `remember` 8 times for snackbar host, URL opener callback, focus requester, key input, visibility toggles, context input, dropdown state, and step entrance state
  - `LaunchedEffect` 4 times for model fetch snackbar/retry, mic settings snackbar, autofocus, and staggered step entrance

### 8.3 `HistoryScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/HistoryScreen.kt:40-44`:

```kotlin
fun HistoryScreen(
    state: HistoryUiState,
    onEvent: (HistoryEvent) -> Unit,
    onCopyToClipboard: (String) -> Unit = {},
)
```

- Consumes state type: `HistoryUiState`
- Emits event type: `HistoryEvent`
- Non-ViewModel params: `onCopyToClipboard`
- Internal stateful composable usage:
  - `remember` 1 time for clear-history confirmation dialog flag
  - `LaunchedEffect` 0 times

### 8.4 `DictionaryScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/DictionaryScreen.kt:40-44`:

```kotlin
fun DictionaryScreen(
    state: DictionaryUiState,
    isDictionaryEnabled: Boolean,
    onEvent: (DictionaryEvent) -> Unit,
)
```

- Consumes state type: `DictionaryUiState`
- Emits event type: `DictionaryEvent`
- Non-ViewModel params: `isDictionaryEnabled`
- Internal stateful composable usage:
  - `remember` 2 times for add-entry inputs
  - `LaunchedEffect` 0 times

### 8.5 `StatsScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/StatsScreen.kt:36`:

```kotlin
fun StatsScreen(state: StatsUiState, onRefresh: () -> Unit)
```

- Consumes state type: `StatsUiState`
- Emits no ViewModel event type; uses callback `onRefresh`
- Non-ViewModel params: `onRefresh`
- Internal stateful composable usage:
  - `remember` 1 time in integer `StatCard` for `Animatable`
  - `LaunchedEffect` 1 time in integer `StatCard` to animate displayed value on change

### 8.6 `UserInfoScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/UserInfoScreen.kt:29-32`:

```kotlin
fun UserInfoScreen(
    state: UserProfileUiState,
    onEvent: (UserProfileEvent) -> Unit,
)
```

- Consumes state type: `UserProfileUiState`
- Emits event type: `UserProfileEvent`
- Non-ViewModel params: none
- Internal stateful composable usage:
  - `remember` 0 explicit times
  - `LaunchedEffect` 1 time to auto-dismiss transient save message

### 8.7 `CustomizationScreen`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/screen/CustomizationScreen.kt:44-50`:

```kotlin
fun CustomizationScreen(
    appTones: Map<String, String>,
    appPrompts: Map<String, String>,
    onSaveTone: (String, String) -> Unit,
    onSavePrompt: (String, String) -> Unit,
    onRemoveApp: (String) -> Unit = {},
)
```

- Consumes no ViewModel `UiState`; it consumes raw `Map<String, String>` state from `main.kt`
- Emits no ViewModel event type; uses plain callbacks `onSaveTone`, `onSavePrompt`, `onRemoveApp`
- Non-ViewModel params: all params are non-ViewModel params
- Internal stateful composable usage:
  - `remember` 6 total across screen and `AppCustomizationCard`
  - `LaunchedEffect` 0 times

### 8.8 `HomeContent`

- Signature from `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt:348-354`:

```kotlin
private fun HomeContent(
    state: RecordingUiState,
    settingsState: SettingsUiState,
    onNavigateToSettings: () -> Unit,
    onEvent: (RecordingEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
)
```

- Consumes state type: `RecordingUiState`
- Emits event type: `RecordingEvent`
- Non-ViewModel params: `settingsState`, `onNavigateToSettings`, `snackbarHostState`
- Internal stateful composable usage:
  - `remember` 1 explicit time for `latestResultText`
  - `LaunchedEffect` 2 times for latest-result tracking and snackbar error handling

## Section 9: Build Configuration

### 9.1 `gradle/libs.versions.toml` verbatim

```toml
[versions]
androidx-lifecycle = "2.9.6"
composeHotReload = "1.0.0"
composeMultiplatform = "1.10.2"
junit = "4.13.2"
kotlin = "2.3.0"
ksp = "2.3.6"
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.10.0"
kotlinx-datetime = "0.7.1"
ktor = "3.4.1"
jna = "5.17.0"
material3 = "1.10.0-alpha05"
multiplatform-nav3-ui = "1.0.0-alpha05"
room3 = "3.0.0-alpha01"
sqlite = "2.7.0-alpha01"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlin-testJunit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composeMultiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "composeMultiplatform" }
kotlinx-coroutinesSwing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-contentNegotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }
jetbrains-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "multiplatform-nav3-ui" }
room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }

[plugins]
composeHotReload = { id = "org.jetbrains.compose.hot-reload", version.ref = "composeHotReload" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room3 = { id = "androidx.room3", version.ref = "room3" }
```

### 9.2 `shared/build.gradle.kts` verbatim

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.room3.runtime)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
    }
}

dependencies {
    add("kspJvm", libs.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
```

### 9.3 `composeApp/build.gradle.kts` verbatim

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

fun File.asJdkHomeCandidate(): File =
    if (name.equals("bin", ignoreCase = true)) parentFile ?: this else this

fun File.hasJPackage(): Boolean =
    resolve("bin/jpackage.exe").exists() || resolve("bin/jpackage").exists()

fun Project.readAppVersion(): String {
    val properties = Properties()
    layout.projectDirectory.file("src/commonMain/resources/version.properties").asFile.inputStream().use {
        properties.load(it)
    }
    return properties.getProperty("version")
        ?: error("Missing 'version' in composeApp/src/commonMain/resources/version.properties")
}

fun resolvePackagingJavaHome(): String {
    val explicitCandidates = listOfNotNull(
        System.getenv("JPACKAGE_JAVA_HOME"),
        System.getenv("JAVA_HOME"),
        System.getenv("JDK_HOME"),
        System.getProperty("java.home"),
    )
        .map(::File)
        .map { it.asJdkHomeCandidate() }

    val pathCandidates = System.getenv("PATH")
        ?.split(File.pathSeparator)
        .orEmpty()
        .asSequence()
        .map(::File)
        .filter { it.exists() }
        .map { it.asJdkHomeCandidate() }

    return (explicitCandidates.asSequence() + pathCandidates)
        .map { it.absoluteFile }
        .distinctBy { it.path.lowercase() }
        .firstOrNull { it.hasJPackage() }
        ?.absolutePath
        ?: error(
            "No full JDK with jpackage was found. Set JPACKAGE_JAVA_HOME or JAVA_HOME to a JDK 21+ installation."
        )
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
}

val nativeDll =
    rootProject.layout.projectDirectory.file("native/prebuilt/windows-x64/openyap_native.dll")

val copyNativeDll by tasks.registering(Copy::class) {
    from(nativeDll)
    into(layout.projectDirectory.dir("resources/windows-x64"))
}

val copyWindowsPackageResources by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("resources/windows"))
    into(layout.buildDirectory.dir("compose/tmp/resources"))
}

val appVersion = project.readAppVersion()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.openyap.MainKt"
        javaHome = resolvePackagingJavaHome()

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "OpenYap"
            packageVersion = appVersion
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/openyap.ico"))
                shortcut = true
                menuGroup = "OpenYap"
                upgradeUuid = "a6b7d58d-31be-42ce-a52f-0a8e5c7b8bf1"
            }
        }
    }
}

tasks.matching {
    it.name == "jvmProcessResources" ||
            it.name == "prepareAppResources" ||
            it.name == "run" ||
            it.name == "jvmRun" ||
            it.name == "runDistributable" ||
            it.name == "packageMsi" ||
            it.name == "packageDistributionForCurrentOS" ||
            it.name == "createDistributable"
}.configureEach {
    dependsOn(copyNativeDll)
}

tasks.matching {
    it.name == "packageMsi" ||
            it.name == "packageDistributionForCurrentOS" ||
            it.name == "createDistributable"
}.configureEach {
    dependsOn(copyWindowsPackageResources)
}

gradle.projectsEvaluated {
    tasks.findByName("jvmRun")?.let { task ->
        task.dependsOn("run")
        task.actions.clear()
    }
}
```

### 9.4 Exact versions

- Kotlin version: `2.3.0` from `gradle/libs.versions.toml:6`
- Compose Multiplatform version: `1.10.2` from `gradle/libs.versions.toml:4`
- Lifecycle version: `2.9.6` from `gradle/libs.versions.toml:2`

## Section 10: Platform Init and App Startup Sequence

1. `main()` begins at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:64`
2. `System.setProperty("sun.awt.noerasebackground", "true")` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:65`
3. `application { }` enters at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:67`
4. `remember { WindowsCredentialStorage() }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:68`
5. `remember { createOpenYapDatabase(...) }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:69-71`
6. Repository remembers at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:73-76`
7. `remember { WindowsHotkeyManager() }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:77`
8. `remember { AudioPipelineConfig(...) }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:78-95`
9. Remaining service/platform remembers at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:97-107`
10. `remember { JvmAppDataResetter(...) }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:108-115`
11. `remember { object : AudioFeedbackPlayer { ... } }` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:117-129`
12. ViewModel remembers at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:131-173`
13. Orphan state remembers at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:175-183`
14. Volume sync `LaunchedEffect(settingsStateForVolume.soundFeedbackVolume)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:178-181`
15. Startup bootstrap `LaunchedEffect(Unit)` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:185-216`
16. Tray state and window state are remembered at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:218-220`
17. Recorder cleanup `DisposableEffect(audioRecorder)` is registered at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:222-226`
18. Recording state is collected and tray icon resource loaded at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:228-229`
19. Tray is composed at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:231-245`
20. Overlay state is collected and overlay window is composed at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:247-248`
21. Recording effect collector is started at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:250-261`
22. Main `Window` is created only if `isVisible` is true at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:263-400`
23. Inside `Window`, title-bar/theme `DisposableEffect(isDark)` is installed at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:270-302`
24. All ViewModel states are collected at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:304-309`
25. Reset watcher `LaunchedEffect(Unit)` is installed at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:316-332`
26. `AppTheme` starts at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:334`
27. `AppShell(...)` renders at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:335-398`

## Section 11: Data Flow Diagrams (Text)

### 11.1 Recording flow

```text
User presses configured hotkey
-> WindowsHotkeyManager or Jna fallback emits HotkeyEvent on hotkeyEvents
-> RecordingViewModel init collector receives event
-> HoldDown/ToggleRecording/StartRecording path calls startRecording()
-> RecordingViewModel loads settings + Gemini key + Groq key
-> validates provider-specific key requirements
-> validates audioRecorder.hasPermission()
-> optionally plays start tone
-> builds temp file path from tempDirProvider() + timestamp + audioFileExtension
-> audioRecorder.startRecording(path, settings.audioDeviceId)
-> _state.recordingState = RecordingState.Recording
-> overlayController.show()
-> durationJob updates duration every second
-> audioRecorder.amplitudeFlow collector updates _state.amplitude and overlayController.updateLevel()

User releases hold hotkey or toggles stop
-> HotkeyEvent.HoldUp / StopRecording / ToggleRecording
-> RecordingViewModel.stopRecording()
-> cancel durationJob
-> compute elapsed duration from TimeMark
-> if duration < 0.5s:
   -> audioRecorder.stopRecording() best effort
   -> optional too-short tone
   -> set error state
   -> overlayController.dismiss()
   -> delete temp file
   -> stop
-> else:
   -> load settings again
   -> audioRecorder.stopRecording() returns file path
   -> optional stop tone
   -> currentProcessingPath = path
   -> processingGeneration.incrementAndGet()
   -> recorderWarning = (audioRecorder as? AudioRecorderDiagnostics)?.consumeWarning()
   -> _state.recordingState = Processing
   -> overlayController.updateState(PROCESSING)
   -> overlayController.flashMessage(recorderWarning) if warning exists
   -> launch processing coroutine
      -> foregroundAppDetector.getForegroundAppName()
      -> settingsRepository.loadApiKey()
      -> settingsRepository.loadGroqApiKey()
      -> fileReader(path)
      -> reject tiny/corrupt file if < 100 bytes
      -> settingsRepository.loadAppTone(appKey)
      -> settingsRepository.loadAppPrompt(appKey)
      -> PromptBuilder.build(...)
      -> WhisperPromptBuilder.build(...)
      -> provider switch:
         GEMINI
         -> GeminiClient.transcribe(...)
         GROQ_WHISPER
         -> GroqWhisperClient.transcribe(...)
         GROQ_WHISPER_GEMINI
         -> GroqWhisperClient.transcribe(...)
         -> GeminiClient.rewriteText(...)
         GROQ_WHISPER_GROQ
         -> GroqWhisperClient.transcribe(...)
         -> GroqLLMClient.rewriteText(...)
         -> if non-cancellation error, fall back to raw transcript
      -> compute modelUsed string
      -> generation guard
      -> userProfileRepository.loadProfile()
      -> if settings.dictionaryEnabled then dictionaryRepository.loadEntries()
      -> PhraseExpansionEngine.expandText(...)
      -> generation guard
      -> pasteAutomation.pasteText(expandedResponse, restoreClipboard = false)
      -> generation guard
      -> historyRepository.addEntry(RecordingEntry(...))
      -> _state.recordingState = Success(expandedResponse, expandedResponse.length)
      -> _state.lastResultText = expandedResponse
      -> emit RecordingEffect.PasteSuccess
      -> overlayController.updateState(SUCCESS)
      -> if settings.dictionaryEnabled then dictionaryEngine.ingestObservedText(expandedResponse)
      -> delay 2000
      -> overlayController.dismiss()
      -> delay 1000
      -> if state still Success, set state to Idle
      -> delete temp file

main.kt effect collector receives RecordingEffect.PasteSuccess
-> historyViewModel.refresh()
-> statsViewModel.refresh()
```

### 11.2 Settings change flow

```text
User changes API key
-> SettingsScreen Save button
-> onEvent(SettingsEvent.SaveApiKey(apiKeyInput))
-> AppShell passes to main.kt wrapper onSettingsEvent
-> wrapper calls settingsViewModel.onEvent(SaveApiKey)
-> SettingsViewModel.saveApiKey()
   -> trim key
   -> settingsRepository.saveApiKey(trimmed)
   -> _state.apiKey = trimmed
   -> _state.saveMessage = "API key saved"
   -> if not blank fetch Gemini models else clear model list state
-> main.kt wrapper also calls recordingViewModel.onEvent(RecordingEvent.RefreshState)
-> RecordingViewModel.refreshPermissions()
   -> settingsRepository.loadSettings()
   -> permissionManager.checkMicrophonePermission()
   -> recompute hasApiKey + hasMicPermission
-> HomeContent chips and status text update from new RecordingUiState
```

### 11.3 App reset flow

```text
User clicks reset
-> SettingsScreen confirm button
-> onEvent(SettingsEvent.ResetAppData)
-> AppShell passes to main.kt wrapper onSettingsEvent
-> wrapper calls settingsViewModel.onEvent(ResetAppData)
-> SettingsViewModel.resetAppData()
   -> _state.isResettingData = true
   -> resetAppDataAction()
      -> JvmAppDataResetter.reset()
         -> secureStorage.clear()
         -> database.deleteAllData()
         -> delete dataDir recursively if it exists
         -> recreate dataDir
         -> delete tempDir recursively if it exists
         -> recreate tempDir
   -> runCatching { startupManager.setEnabled(false) }
   -> hotkeyManager.setConfig(AppSettings().hotkeyConfig)
   -> _state reset to defaults and saveMessage set
   -> _state.isResettingData = false

Immediately in main.kt wrapper, before reset finishes:
-> onboardingViewModel.resetState()
-> appTones = emptyMap()
-> appPrompts = emptyMap()
-> backStack.clear()
-> backStack += Route.Home

Then main.kt reset watcher sees transition true -> false
-> recordingViewModel.onEvent(RefreshState)
-> historyViewModel.refresh()
-> onboardingViewModel.refresh()
-> dictionaryViewModel.refresh()
-> userProfileViewModel.refresh()
-> statsViewModel.refresh()
```

### 11.4 Onboarding completion flow

```text
User finishes onboarding
-> OnboardingScreen Enter OpenYap button
-> onEvent(OnboardingEvent.CompleteOnboarding)
-> AppShell passes to main.kt wrapper onOnboardingEvent
-> wrapper calls onboardingViewModel.onEvent(CompleteOnboarding)
-> OnboardingViewModel eventChannel receives event
-> processEvent(CompleteOnboarding)
-> completeOnboarding()
   -> cancel prior onboardingJob
   -> settingsRepository.updateSettings {
        onboardingCompleted = true
        transcriptionProvider = GROQ_WHISPER_GROQ
        groqModel = "whisper-large-v3"
      }
   -> _state.isComplete = true if epoch still matches

main.kt wrapper side-effects
-> settingsViewModel.refresh()
   -> reload persisted settings/keys/startup/version/models/devices
-> recordingViewModel.onEvent(RefreshState)
   -> reload hasApiKey + mic permission status

AppShell gate now sees onboardingState.isComplete == true
-> exits onboarding-only path
-> renders main application shell
```

## Section 12: Potential Complications for Koin Migration

### 12.1 Conditional `AudioPipelineConfig`

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:78-95`, data class at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:405-409`
- Why non-trivial:
  - DI needs to choose between `NativeAudioRecorder` and `JvmAudioRecorder`
  - DI also needs to bind matching `audioMimeType` and `audioFileExtension`
  - `NativeAudioBridge.instance` can be null only at runtime after DLL probing
- Options:
  - bind a single `AudioPipelineConfig` singleton in `composeApp:jvmMain`
  - bind named values for `AudioRecorder`, `audioMimeType`, and `audioFileExtension` together from one provider
  - wrap the selection inside a dedicated factory/provider object and inject that object instead of raw lambdas/strings

### 12.2 Lambda dependencies in `RecordingViewModel`

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:150-152`, constructor params at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:104-108`
- Why non-trivial:
  - Koin can inject lambdas, but these are opaque function dependencies rather than named service abstractions
  - they couple the ViewModel to filesystem operations hidden behind anonymous functions
- Options:
  - bind each lambda explicitly in Koin with qualifiers
  - replace them with a concrete `TempFileGateway` / `FileSystemGateway` interface before or during the DI migration

### 12.3 `resetAppDataAction` lambda

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:165`, constructor param at `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt:103`
- Why non-trivial:
  - another anonymous suspend dependency rather than a named abstraction
- Options:
  - bind a qualified suspend lambda in Koin
  - introduce an `AppDataResetter` interface implemented by `JvmAppDataResetter`

### 12.4 Anonymous `AudioFeedbackPlayer`

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:117-129`, interface at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:74-78`
- Why non-trivial:
  - the current implementation is anonymous and local to composition
  - DI containers work better with named classes or singletons
- Options:
  - bind the anonymous adapter as a singleton provider of `AudioFeedbackPlayer`
  - replace it with a named class such as `JvmAudioFeedbackPlayer`

### 12.5 `ComposeOverlayController` lives in `composeApp`, but `RecordingViewModel` lives in `shared`

- Exact code:
  - interface `OverlayController` at `shared/src/commonMain/kotlin/com/openyap/platform/OverlayController.kt:5-12`
  - concrete `ComposeOverlayController` at `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:25-84`
  - ViewModel constructor param at `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt:102`
  - binding site in main at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:106`, `146`
- Why non-trivial:
  - `shared` cannot directly reference the `composeApp` implementation
  - Koin module ownership must respect module boundaries
- Options:
  - define the `OverlayController` binding in a `composeApp:jvmMain` module and let shared depend only on the interface
  - keep a `NoOpOverlayController` default for non-Compose contexts or tests

### 12.6 `ComposeOverlayWindow` depends on `overlayController.uiState` outside the main `Window`

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:247-248`, UI state in `composeApp/src/jvmMain/kotlin/com/openyap/platform/ComposeOverlayController.kt:27-28`
- Why non-trivial:
  - overlay rendering is app-shell-level composition, not window-content-level composition
  - if Koin starts later than this point, overlay composition will lack its controller
- Options:
  - start Koin before entering composition or at the top of `application {}` before overlay collection
  - inject `ComposeOverlayController` at the app shell level and expose its `uiState` for composition

### 12.7 Cross-ViewModel effects coordinated in `main.kt`

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:250-261`, `347-377`, `316-332`
- Why non-trivial:
  - DI alone does not solve cross-ViewModel orchestration
  - these flows are composition-owned side effects, not constructor dependencies
- Options:
  - leave orchestration in `main.kt` / app shell and only migrate object creation to Koin
  - introduce coordinator/interactor classes for reset, onboarding completion, and recording-success fan-out

### 12.8 Window-level side effects and Swing interop

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:271-302`
- Why non-trivial:
  - title-bar theming and background mutation depend on live `Window` instance and theme state, not constructor injection
- Options:
  - keep this as composable lifecycle code outside Koin
  - if desired, extract `WindowChromeController` helper but still drive it from composition

### 12.9 Tray cleanup needs direct references to several services

- Exact code: `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:237-242`
- Dependencies involved:
  - `audioRecorder`
  - `overlayController`
  - `audioFeedbackService`
  - `hotkeyManager`
- Why non-trivial:
  - tray exit must close long-lived singleton-like services in a known order
- Options:
  - resolve them from Koin at composition root and keep the quit handler there
  - introduce an `AppShutdownManager` that owns the cleanup order

### 12.10 `PlatformInit.dataDir` and `PlatformInit.tempDir`

- Exact code: `shared/src/jvmMain/kotlin/com/openyap/platform/PlatformInit.kt:8-18`
- Why non-trivial:
  - these are `by lazy` object properties, not constructor-injected paths
  - first access has side effects because directories are created lazily
  - `dataDir` is first accessed during database creation at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:70`
  - `tempDir` is first accessed during resetter creation at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:113` and through `tempDirProvider` at `composeApp/src/jvmMain/kotlin/com/openyap/main.kt:150`
- Options:
  - keep `PlatformInit` and call it from Koin providers
  - or bind `Path` singletons for `dataDir` and `tempDir` so ordering becomes explicit in the DI graph
