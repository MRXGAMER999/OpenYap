# Koin DI Refactor Plan

## Overview

This plan replaces all manual dependency wiring in `composeApp/src/jvmMain/kotlin/com/openyap/main.kt` with Koin dependency injection (BOM 4.2.0 + Annotations 2.3.0 + KSP). After execution, `main.kt` calls `startKoin { modules(...) }` before `application {}`, screens retrieve their own ViewModels via `koinViewModel()`, and `AppShell`'s parameter count drops from 23 to ~8. Cross-ViewModel event orchestration remains in `main.kt` wrapper lambdas. The refactor is phased: Gradle config → new abstractions → annotate existing classes → define Koin modules → wire `main.kt` → simplify `AppShell` → final verification.

---

## Phase 1: Gradle Configuration

### Step 1.1: Add Koin version catalog entries to `gradle/libs.versions.toml`

- [ ] Open `gradle/libs.versions.toml` and add the Koin version entries to `[versions]` and library entries to `[libraries]`.

**Before** (`gradle/libs.versions.toml`, `[versions]` section, after the `sqlite` line):

```toml
sqlite = "2.7.0-alpha01"
```

**After**:

```toml
sqlite = "2.7.0-alpha01"
koin-bom = "4.2.0"
koin-annotations = "2.3.0"
```

**Before** (`gradle/libs.versions.toml`, `[libraries]` section, after the `sqlite-bundled` line):

```toml
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
```

**After**:

```toml
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
koin-bom = { module = "io.insert-koin:koin-bom", version.ref = "koin-bom" }
koin-core = { module = "io.insert-koin:koin-core" }
koin-compose = { module = "io.insert-koin:koin-compose" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel" }
koin-annotations = { module = "io.insert-koin:koin-annotations", version.ref = "koin-annotations" }
koin-ksp-compiler = { module = "io.insert-koin:koin-ksp-compiler", version.ref = "koin-annotations" }
```

### Step 1.2: Add Koin dependencies to `shared/build.gradle.kts`

- [ ] Add Koin BOM, core, and annotations to `commonMain.dependencies`.
- [ ] Add the Koin KSP compiler to the `dependencies` block.
- [ ] Add the `ksp` configuration block with `KOIN_CONFIG_CHECK` and `KOIN_DEFAULT_MODULE`.

**Before** (`shared/build.gradle.kts`, lines 11-23):

```kotlin
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
```

**After**:

```kotlin
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
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
        }
```

**Before** (`shared/build.gradle.kts`, lines 36-38):

```kotlin
dependencies {
    add("kspJvm", libs.room3.compiler)
}
```

**After**:

```kotlin
dependencies {
    add("kspJvm", libs.room3.compiler)
    add("kspJvm", libs.koin.ksp.compiler)
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
    arg("KOIN_DEFAULT_MODULE", "false")
}
```

### Step 1.3: Add Koin dependencies to `composeApp/build.gradle.kts`

- [ ] Add Koin BOM, core, compose, compose-viewmodel, and annotations to `commonMain.dependencies`.
- [ ] Add the KSP plugin to the plugins block.
- [ ] Add a `dependencies` block for the Koin KSP compiler.
- [ ] Add the `ksp` configuration block.

**Before** (`composeApp/build.gradle.kts`, plugins block, lines 47-53):

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
}
```

**After**:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}
```

**Before** (`composeApp/build.gradle.kts`, `commonMain.dependencies`, lines 73-88):

```kotlin
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
```

**After**:

```kotlin
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
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.annotations)
        }
```

**Add after the closing `}` of the `kotlin {}` block** (before `compose.desktop {`):

```kotlin
dependencies {
    add("kspJvm", libs.koin.ksp.compiler)
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
    arg("KOIN_DEFAULT_MODULE", "false")
}
```

### Verification Checkpoint

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm
```

- Expected result: BUILD SUCCESSFUL. No errors from Koin dependencies. KSP runs but generates no modules yet (no `@Module` classes exist).

---

## Phase 2: New Abstractions

### Step 2.1: Create `FileOperations` interface

- [ ] Create `shared/src/commonMain/kotlin/com/openyap/platform/FileOperations.kt`.

```kotlin
package com.openyap.platform

interface FileOperations {
    fun tempDir(): String
    fun readFile(path: String): ByteArray
    fun deleteFile(path: String): Boolean
}
```

### Step 2.2: Create `JvmFileOperations` implementation

- [ ] Create `shared/src/jvmMain/kotlin/com/openyap/platform/JvmFileOperations.kt`.

```kotlin
package com.openyap.platform

import org.koin.core.annotation.Single
import java.io.File

@Single(binds = [FileOperations::class])
class JvmFileOperations : FileOperations {
    override fun tempDir(): String = PlatformInit.tempDir.toString()
    override fun readFile(path: String): ByteArray = File(path).readBytes()
    override fun deleteFile(path: String): Boolean = File(path).delete()
}
```

### Step 2.3: Create `AppDataResetter` interface

- [ ] Create `shared/src/commonMain/kotlin/com/openyap/platform/AppDataResetter.kt`.

```kotlin
package com.openyap.platform

interface AppDataResetter {
    suspend fun reset()
}

class NoOpAppDataResetter : AppDataResetter {
    override suspend fun reset() {}
}
```

### Step 2.4: Create `AudioFeedbackPlayerImpl`

- [ ] Create `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackPlayerImpl.kt`.

```kotlin
package com.openyap.platform

import com.openyap.viewmodel.AudioFeedbackPlayer
import org.koin.core.annotation.Single

@Single(binds = [AudioFeedbackPlayer::class])
class AudioFeedbackPlayerImpl(
    private val audioFeedbackService: AudioFeedbackService,
) : AudioFeedbackPlayer {
    override fun playStart() = audioFeedbackService.play(AudioFeedbackService.Tone.START)
    override fun playStop() = audioFeedbackService.play(AudioFeedbackService.Tone.STOP)
    override fun playTooShort() = audioFeedbackService.play(AudioFeedbackService.Tone.TOO_SHORT)
    override fun playError() = audioFeedbackService.play(AudioFeedbackService.Tone.ERROR)
}
```

### Step 2.5: Move `AudioPipelineConfig` to shared

- [ ] Create `shared/src/commonMain/kotlin/com/openyap/platform/AudioPipelineConfig.kt`.

```kotlin
package com.openyap.platform

data class AudioPipelineConfig(
    val audioRecorder: AudioRecorder,
    val audioMimeType: String,
    val audioFileExtension: String,
)
```

- [ ] Remove the `private data class AudioPipelineConfig` at the bottom of `composeApp/src/jvmMain/kotlin/com/openyap/main.kt` (lines 405–409).

**Before** (`composeApp/src/jvmMain/kotlin/com/openyap/main.kt`, lines 405–409):

```kotlin
private data class AudioPipelineConfig(
    val audioRecorder: AudioRecorder,
    val audioMimeType: String,
    val audioFileExtension: String,
)
```

**After**: Delete these 5 lines entirely. The import of `AudioPipelineConfig` will resolve automatically to the new shared location via the `projects.shared` dependency.

### Step 2.6: Create `AppCustomizationViewModel`

- [ ] Create `shared/src/commonMain/kotlin/com/openyap/viewmodel/AppCustomizationViewModel.kt`.

```kotlin
package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class AppCustomizationUiState(
    val appTones: Map<String, String> = emptyMap(),
    val appPrompts: Map<String, String> = emptyMap(),
)

sealed interface AppCustomizationEvent {
    data class SaveTone(val app: String, val tone: String) : AppCustomizationEvent
    data class SavePrompt(val app: String, val prompt: String) : AppCustomizationEvent
    data class RemoveApp(val app: String) : AppCustomizationEvent
    data object Reset : AppCustomizationEvent
    data object Refresh : AppCustomizationEvent
}

@KoinViewModel
class AppCustomizationViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppCustomizationUiState())
    val state: StateFlow<AppCustomizationUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val tones = settingsRepository.loadAllAppTones()
            val prompts = settingsRepository.loadAllAppPrompts()
            _state.update { it.copy(appTones = tones, appPrompts = prompts) }
        }
    }

    fun reset() {
        _state.update { AppCustomizationUiState() }
    }

    fun onEvent(event: AppCustomizationEvent) {
        when (event) {
            is AppCustomizationEvent.SaveTone -> saveTone(event.app, event.tone)
            is AppCustomizationEvent.SavePrompt -> savePrompt(event.app, event.prompt)
            is AppCustomizationEvent.RemoveApp -> removeApp(event.app)
            AppCustomizationEvent.Reset -> reset()
            AppCustomizationEvent.Refresh -> refresh()
        }
    }

    private fun saveTone(app: String, tone: String) {
        _state.update { it.copy(appTones = it.appTones + (app to tone)) }
        viewModelScope.launch { settingsRepository.saveAppTone(app, tone) }
    }

    private fun savePrompt(app: String, prompt: String) {
        _state.update { it.copy(appPrompts = it.appPrompts + (app to prompt)) }
        viewModelScope.launch { settingsRepository.saveAppPrompt(app, prompt) }
    }

    private fun removeApp(app: String) {
        _state.update {
            it.copy(
                appTones = it.appTones - app,
                appPrompts = it.appPrompts - app,
            )
        }
        viewModelScope.launch { settingsRepository.removeAppCustomization(app) }
    }
}
```

### Verification Checkpoint

```bash
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm
```

- Expected result: BUILD SUCCESSFUL. New abstractions compile. `main.kt` still compiles because it still has its own `AudioPipelineConfig` removed and now uses the shared one (or this step may require temporarily commenting out the old one — if the removal causes an error, leave this for Phase 5 and keep both temporarily).

> **Note**: If removing the `private data class AudioPipelineConfig` from `main.kt` in Step 2.5 causes a compile error because the import in `main.kt` does not auto-resolve, add an explicit import `import com.openyap.platform.AudioPipelineConfig` to `main.kt`. However, since `main.kt` already imports `com.openyap.platform.AudioRecorder` from `shared`, and `composeApp` depends on `shared`, the unqualified name should resolve to the shared class. If it does not, add the import explicitly.

---

## Phase 3: Annotate Existing Classes

### Step 3.1: Update `RecordingViewModel` constructor

- [ ] Replace the three lambda parameters with `FileOperations` and add `@KoinViewModel`.
- [ ] Update internal call sites.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt`, lines 88–109):

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
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class RecordingViewModel(
    private val hotkeyManager: HotkeyManager,
    private val audioRecorder: AudioRecorder,
    @Named("geminiClient") private val geminiClient: GeminiClient,
    @Named("groqWhisperClient") private val groqWhisperClient: TranscriptionService,
    @Named("groqLLMClient") private val groqLLMClient: GroqLLMClient,
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
    @Named("audioMimeType") private val audioMimeType: String,
    @Named("audioFileExtension") audioFileExtension: String,
    private val fileOperations: FileOperations,
) : ViewModel() {
```

- [ ] Add the required imports to the top of `RecordingViewModel.kt`:

```kotlin
import com.openyap.platform.FileOperations
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Named
```

- [ ] Replace `tempDirProvider()` with `fileOperations.tempDir()` at line 242:

**Before** (line 242):

```kotlin
        val path = "${tempDirProvider()}/openyap_$timestamp$audioFileExtension"
```

**After**:

```kotlin
        val path = "${fileOperations.tempDir()}/openyap_$timestamp$audioFileExtension"
```

- [ ] Replace `fileReader(path)` with `fileOperations.readFile(path)` at line 602:

**Before** (line 602):

```kotlin
    private fun readFileBytes(path: String): ByteArray = fileReader(path)
```

**After**:

```kotlin
    private fun readFileBytes(path: String): ByteArray = fileOperations.readFile(path)
```

- [ ] Replace `fileDeleter(path)` with `fileOperations.deleteFile(path)` at line 606:

**Before** (lines 604–609):

```kotlin
    private fun deleteFile(path: String) {
        try {
            fileDeleter(path)
        } catch (_: Exception) {
        }
    }
```

**After**:

```kotlin
    private fun deleteFile(path: String) {
        try {
            fileOperations.deleteFile(path)
        } catch (_: Exception) {
        }
    }
```

### Step 3.2: Update `SettingsViewModel` constructor

- [ ] Replace the `resetAppDataAction` lambda with `AppDataResetter` and add `@KoinViewModel`.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt`, lines 94–104):

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
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    @Named("geminiClient") private val geminiClient: GeminiClient,
    @Named("groqWhisperClient") private val groqWhisperClient: GroqWhisperClient,
    @Named("groqLLMClient") private val groqLLMClient: GroqLLMClient,
    private val hotkeyManager: HotkeyManager,
    private val hotkeyDisplayFormatter: HotkeyDisplayFormatter,
    private val audioRecorder: AudioRecorder,
    private val startupManager: StartupManager = NoOpStartupManager(),
    private val appDataResetter: AppDataResetter = NoOpAppDataResetter(),
) : ViewModel() {
```

- [ ] Add the required imports to the top of `SettingsViewModel.kt`:

```kotlin
import com.openyap.platform.AppDataResetter
import com.openyap.platform.NoOpAppDataResetter
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Named
```

- [ ] Replace `resetAppDataAction.invoke()` with `appDataResetter.reset()` at line 442:

**Before** (line 442):

```kotlin
                resetAppDataAction.invoke()
```

**After**:

```kotlin
                appDataResetter.reset()
```

### Step 3.3: Annotate `HistoryViewModel`

- [ ] Add `@KoinViewModel` annotation.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt`, class declaration):

```kotlin
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
```

- [ ] Add import: `import org.koin.core.annotation.KoinViewModel`

### Step 3.4: Annotate `OnboardingViewModel`

- [ ] Add `@KoinViewModel` annotation and `@Named` qualifier.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt`, class declaration):

```kotlin
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissionManager: PermissionManager,
    private val groqLLMClient: GroqLLMClient,
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissionManager: PermissionManager,
    @Named("groqLLMClient") private val groqLLMClient: GroqLLMClient,
) : ViewModel() {
```

- [ ] Add imports:

```kotlin
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Named
```

### Step 3.5: Annotate `DictionaryViewModel`

- [ ] Add `@KoinViewModel` annotation.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt`, class declaration):

```kotlin
class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val dictionaryEngine: DictionaryEngine,
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val dictionaryEngine: DictionaryEngine,
) : ViewModel() {
```

- [ ] Add import: `import org.koin.core.annotation.KoinViewModel`

### Step 3.6: Annotate `UserProfileViewModel`

- [ ] Add `@KoinViewModel` annotation.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt`, class declaration):

```kotlin
class UserProfileViewModel(
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class UserProfileViewModel(
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {
```

- [ ] Add import: `import org.koin.core.annotation.KoinViewModel`

### Step 3.7: Annotate `StatsViewModel`

- [ ] Add `@KoinViewModel` annotation.

**Before** (`shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt`, class declaration):

```kotlin
class StatsViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
```

**After**:

```kotlin
@KoinViewModel
class StatsViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {
```

- [ ] Add import: `import org.koin.core.annotation.KoinViewModel`

### Step 3.8: Annotate `DictionaryEngine`

- [ ] Add `@Single` annotation.

**Before** (`shared/src/commonMain/kotlin/com/openyap/service/DictionaryEngine.kt`, line 8):

```kotlin
class DictionaryEngine(private val repository: DictionaryRepository) {
```

**After**:

```kotlin
@Single
class DictionaryEngine(private val repository: DictionaryRepository) {
```

- [ ] Add import: `import org.koin.core.annotation.Single`

### Step 3.9: Update `JvmAppDataResetter` to implement `AppDataResetter`

- [ ] Add `@Single` annotation and `AppDataResetter` interface.

**Before** (`shared/src/jvmMain/kotlin/com/openyap/platform/JvmAppDataResetter.kt`, lines 10–16):

```kotlin
@OptIn(ExperimentalPathApi::class)
class JvmAppDataResetter(
    private val secureStorage: SecureStorage,
    private val database: OpenYapDatabase,
    private val dataDir: Path,
    private val tempDir: Path,
) {
```

**After**:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Single(binds = [AppDataResetter::class])
class JvmAppDataResetter(
    private val secureStorage: SecureStorage,
    private val database: OpenYapDatabase,
    private val dataDir: Path,
    private val tempDir: Path,
) : AppDataResetter {
```

- [ ] Add import: `import org.koin.core.annotation.Single`

### Step 3.10: Update `main.kt` `RecordingViewModel` instantiation (temporary)

- [ ] Update the `RecordingViewModel` constructor call in `main.kt` to use `fileOperations` instead of lambdas, so the project compiles during intermediate steps.

**Before** (`composeApp/src/jvmMain/kotlin/com/openyap/main.kt`, lines 131–154):

```kotlin
        val recordingViewModel = remember {
            RecordingViewModel(
                hotkeyManager = hotkeyManager,
                audioRecorder = audioRecorder,
                geminiClient = geminiClient,
                groqWhisperClient = groqWhisperClient,
                groqLLMClient = groqLLMClient,
                pasteAutomation = pasteAutomation,
                foregroundAppDetector = foregroundDetector,
                settingsRepository = settingsRepo,
                historyRepository = historyRepo,
                dictionaryRepository = dictionaryRepo,
                userProfileRepository = userProfileRepo,
                permissionManager = permissionManager,
                dictionaryEngine = dictionaryEngine,
                overlayController = overlayController,
                audioFeedbackPlayer = audioFeedbackPlayer,
                audioMimeType = audioPipeline.audioMimeType,
                audioFileExtension = audioPipeline.audioFileExtension,
                tempDirProvider = { PlatformInit.tempDir.toString() },
                fileReader = { path -> java.io.File(path).readBytes() },
                fileDeleter = { path -> java.io.File(path).delete() },
            )
        }
```

**After**:

```kotlin
        val recordingViewModel = remember {
            RecordingViewModel(
                hotkeyManager = hotkeyManager,
                audioRecorder = audioRecorder,
                geminiClient = geminiClient,
                groqWhisperClient = groqWhisperClient,
                groqLLMClient = groqLLMClient,
                pasteAutomation = pasteAutomation,
                foregroundAppDetector = foregroundDetector,
                settingsRepository = settingsRepo,
                historyRepository = historyRepo,
                dictionaryRepository = dictionaryRepo,
                userProfileRepository = userProfileRepo,
                permissionManager = permissionManager,
                dictionaryEngine = dictionaryEngine,
                overlayController = overlayController,
                audioFeedbackPlayer = audioFeedbackPlayer,
                audioMimeType = audioPipeline.audioMimeType,
                audioFileExtension = audioPipeline.audioFileExtension,
                fileOperations = JvmFileOperations(),
            )
        }
```

- [ ] Add import to `main.kt`: `import com.openyap.platform.JvmFileOperations`

### Step 3.11: Update `main.kt` `SettingsViewModel` instantiation (temporary)

- [ ] Update the `SettingsViewModel` constructor call to use `appDataResetter` instead of lambda.

**Before** (`composeApp/src/jvmMain/kotlin/com/openyap/main.kt`, lines 155–167):

```kotlin
        val settingsViewModel = remember {
            SettingsViewModel(
                settingsRepo,
                geminiClient,
                groqWhisperClient,
                groqLLMClient,
                hotkeyManager,
                hotkeyFormatter,
                audioRecorder,
                startupManager,
                resetAppDataAction = { appDataResetter.reset() },
            )
        }
```

**After**:

```kotlin
        val settingsViewModel = remember {
            SettingsViewModel(
                settingsRepo,
                geminiClient,
                groqWhisperClient,
                groqLLMClient,
                hotkeyManager,
                hotkeyFormatter,
                audioRecorder,
                startupManager,
                appDataResetter = appDataResetter,
            )
        }
```

### Verification Checkpoint

```bash
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm
```

- Expected result: BUILD SUCCESSFUL. All annotations compile. KSP generates metadata for annotated classes but no modules are defined yet to reference them.

---

## Phase 4: Define Koin Modules

### Step 4.1: Create `SharedModule`

- [ ] Create `shared/src/jvmMain/kotlin/com/openyap/di/SharedModule.kt`.

```kotlin
package com.openyap.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.openyap")
class SharedModule
```

This auto-discovers via KSP:
- `JvmFileOperations` → `FileOperations` (`@Single`)
- `AudioFeedbackPlayerImpl` → `AudioFeedbackPlayer` (`@Single`)
- `JvmAppDataResetter` → `AppDataResetter` (`@Single`)
- `DictionaryEngine` (`@Single`)
- `RecordingViewModel` (`@KoinViewModel`)
- `SettingsViewModel` (`@KoinViewModel`)
- `HistoryViewModel` (`@KoinViewModel`)
- `OnboardingViewModel` (`@KoinViewModel`)
- `DictionaryViewModel` (`@KoinViewModel`)
- `UserProfileViewModel` (`@KoinViewModel`)
- `StatsViewModel` (`@KoinViewModel`)
- `AppCustomizationViewModel` (`@KoinViewModel`)

### Step 4.2: Create `PlatformModule`

- [ ] Create `shared/src/jvmMain/kotlin/com/openyap/di/PlatformModule.kt`.

```kotlin
package com.openyap.di

import com.openyap.database.OpenYapDatabase
import com.openyap.database.createOpenYapDatabase
import com.openyap.platform.AudioFeedbackService
import com.openyap.platform.AudioPipelineConfig
import com.openyap.platform.AudioRecorder
import com.openyap.platform.ForegroundAppDetector
import com.openyap.platform.HotkeyDisplayFormatter
import com.openyap.platform.HotkeyManager
import com.openyap.platform.HttpClientFactory
import com.openyap.platform.JvmAudioRecorder
import com.openyap.platform.NativeAudioBridge
import com.openyap.platform.NativeAudioRecorder
import com.openyap.platform.PasteAutomation
import com.openyap.platform.PermissionManager
import com.openyap.platform.PlatformInit
import com.openyap.platform.SecureStorage
import com.openyap.platform.StartupManager
import com.openyap.platform.WindowsCredentialStorage
import com.openyap.platform.WindowsForegroundAppDetector
import com.openyap.platform.WindowsHotkeyDisplayFormatter
import com.openyap.platform.WindowsHotkeyManager
import com.openyap.platform.WindowsPasteAutomation
import com.openyap.platform.WindowsPermissionManager
import com.openyap.platform.WindowsStartupManager
import com.openyap.repository.DictionaryRepository
import com.openyap.repository.HistoryRepository
import com.openyap.repository.RoomDictionaryRepository
import com.openyap.repository.RoomHistoryRepository
import com.openyap.repository.RoomSettingsRepository
import com.openyap.repository.RoomUserProfileRepository
import com.openyap.repository.SettingsRepository
import com.openyap.repository.UserProfileRepository
import com.openyap.service.GeminiClient
import com.openyap.service.GroqLLMClient
import com.openyap.service.GroqWhisperClient
import com.openyap.service.TranscriptionService
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.nio.file.Path

@Module
class PlatformModule {

    @Single
    fun provideSecureStorage(): SecureStorage = WindowsCredentialStorage()

    @Single
    fun provideDatabase(): OpenYapDatabase {
        val dbPath = PlatformInit.dataDir.resolve("openyap.db").toString()
        return createOpenYapDatabase(dbPath)
    }

    @Single
    fun provideSettingsRepository(db: OpenYapDatabase, ss: SecureStorage): SettingsRepository =
        RoomSettingsRepository(db, ss)

    @Single
    fun provideHistoryRepository(db: OpenYapDatabase): HistoryRepository =
        RoomHistoryRepository(db)

    @Single
    fun provideDictionaryRepository(db: OpenYapDatabase): DictionaryRepository =
        RoomDictionaryRepository(db)

    @Single
    fun provideUserProfileRepository(db: OpenYapDatabase): UserProfileRepository =
        RoomUserProfileRepository(db)

    @Single
    fun provideHotkeyManager(): HotkeyManager = WindowsHotkeyManager()

    @Single
    fun provideAudioPipelineConfig(): AudioPipelineConfig {
        val nativeAudio = NativeAudioBridge.instance
        return if (nativeAudio != null) {
            System.err.println("Native audio pipeline available")
            AudioPipelineConfig(
                audioRecorder = NativeAudioRecorder(nativeAudio),
                audioMimeType = "audio/mp4",
                audioFileExtension = ".m4a",
            )
        } else {
            System.err.println("Native audio pipeline unavailable, using fallback")
            AudioPipelineConfig(
                audioRecorder = JvmAudioRecorder(),
                audioMimeType = "audio/wav",
                audioFileExtension = ".wav",
            )
        }
    }

    @Single
    fun provideAudioRecorder(config: AudioPipelineConfig): AudioRecorder = config.audioRecorder

    @Single
    @Named("audioMimeType")
    fun provideAudioMimeType(config: AudioPipelineConfig): String = config.audioMimeType

    @Single
    @Named("audioFileExtension")
    fun provideAudioFileExtension(config: AudioPipelineConfig): String = config.audioFileExtension

    @Single
    fun providePasteAutomation(): PasteAutomation = WindowsPasteAutomation()

    @Single
    fun provideForegroundAppDetector(): ForegroundAppDetector = WindowsForegroundAppDetector()

    @Single
    fun providePermissionManager(): PermissionManager = WindowsPermissionManager()

    @Single
    fun provideStartupManager(): StartupManager = WindowsStartupManager()

    @Single
    @Named("geminiClient")
    fun provideGeminiClient(): GeminiClient = HttpClientFactory.createGeminiClient()

    @Single
    @Named("groqWhisperClient")
    fun provideGroqWhisperClient(): GroqWhisperClient = HttpClientFactory.createGroqWhisperClient()

    @Single
    @Named("groqLLMClient")
    fun provideGroqLLMClient(): GroqLLMClient = HttpClientFactory.createGroqLLMClient()

    @Single
    fun provideHotkeyDisplayFormatter(): HotkeyDisplayFormatter = WindowsHotkeyDisplayFormatter()

    @Single
    fun provideAudioFeedbackService(): AudioFeedbackService = AudioFeedbackService()

    @Single
    @Named("dataDir")
    fun provideDataDir(): Path = PlatformInit.dataDir

    @Single
    @Named("tempDir")
    fun provideTempDir(): Path = PlatformInit.tempDir
}
```

> **Important note on `@Named("groqWhisperClient")`**: The return type is `GroqWhisperClient` (the concrete class). `SettingsViewModel` expects `GroqWhisperClient` and `RecordingViewModel` expects `TranscriptionService`. Since `GroqWhisperClient` implements `TranscriptionService`, Koin's annotation processor with `@Named` resolves by qualifier name and then checks type compatibility. The `@Named("groqWhisperClient")` on `RecordingViewModel`'s `TranscriptionService` param will resolve to the `GroqWhisperClient` instance, which satisfies `TranscriptionService`. If this causes a KSP compile-time type mismatch, add a second factory:
>
> ```kotlin
> @Single
> @Named("groqWhisperTranscription")
> fun provideGroqWhisperTranscriptionService(
>     @Named("groqWhisperClient") client: GroqWhisperClient
> ): TranscriptionService = client
> ```
>
> And change `RecordingViewModel`'s qualifier to `@Named("groqWhisperTranscription")`. Test this during the Phase 4 verification checkpoint.

> **Important note on `JvmAppDataResetter`**: Its constructor takes `dataDir: Path` and `tempDir: Path`. Since these are both `Path` types, Koin cannot distinguish them without qualifiers. Update `JvmAppDataResetter` constructor to use `@Named`:

### Step 4.2a: Add `@Named` qualifiers to `JvmAppDataResetter` constructor

- [ ] Update `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAppDataResetter.kt`.

**Before**:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Single(binds = [AppDataResetter::class])
class JvmAppDataResetter(
    private val secureStorage: SecureStorage,
    private val database: OpenYapDatabase,
    private val dataDir: Path,
    private val tempDir: Path,
) : AppDataResetter {
```

**After**:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Single(binds = [AppDataResetter::class])
class JvmAppDataResetter(
    private val secureStorage: SecureStorage,
    private val database: OpenYapDatabase,
    @Named("dataDir") private val dataDir: Path,
    @Named("tempDir") private val tempDir: Path,
) : AppDataResetter {
```

- [ ] Add import: `import org.koin.core.annotation.Named`

### Step 4.3: Create `ComposeAppModule`

- [ ] Create `composeApp/src/jvmMain/kotlin/com/openyap/di/ComposeAppModule.kt`.

```kotlin
package com.openyap.di

import com.openyap.platform.ComposeOverlayController
import com.openyap.platform.OverlayController
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class ComposeAppModule {

    @Single
    fun provideOverlayController(): OverlayController = ComposeOverlayController()
}
```

### Verification Checkpoint

```bash
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm
```

- Expected result: BUILD SUCCESSFUL. KSP generates `SharedModuleGenKt`, `PlatformModuleGenKt`, and `ComposeAppModuleGenKt` extension properties. The build may emit warnings about unused generated code since `startKoin` isn't called yet.

---

## Phase 5: Wire `main.kt` to Koin

### Step 5.1: Rewrite `main.kt`

- [ ] Replace the entire content of `composeApp/src/jvmMain/kotlin/com/openyap/main.kt` with the Koin-wired version.

The new `main.kt` should be:

```kotlin
package com.openyap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.openyap.di.ComposeAppModule
import com.openyap.di.ComposeAppModuleModule
import com.openyap.di.PlatformModule
import com.openyap.di.PlatformModuleModule
import com.openyap.di.SharedModule
import com.openyap.di.SharedModuleModule
import com.openyap.platform.AudioFeedbackService
import com.openyap.platform.AudioRecorder
import com.openyap.platform.ComposeOverlayController
import com.openyap.platform.ComposeOverlayWindow
import com.openyap.platform.HotkeyManager
import com.openyap.platform.OverlayController
import com.openyap.platform.WindowsThemeHelper
import com.openyap.repository.SettingsRepository
import com.openyap.ui.navigation.AppShell
import com.openyap.ui.navigation.Route
import com.openyap.ui.theme.AppTheme
import com.openyap.viewmodel.AppCustomizationViewModel
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingViewModel
import com.openyap.viewmodel.RecordingEffect
import com.openyap.viewmodel.RecordingEvent
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.ksp.generated.module
import openyap.composeapp.generated.resources.Res
import openyap.composeapp.generated.resources.ic_app_logo
import org.jetbrains.compose.resources.painterResource

fun main() {
    System.setProperty("sun.awt.noerasebackground", "true")

    application {
        KoinApplication(application = {
            modules(
                SharedModule().module,
                PlatformModule().module,
                ComposeAppModule().module,
            )
        }) {
            val settingsRepo = koinInject<SettingsRepository>()
            val hotkeyManager = koinInject<HotkeyManager>()
            val audioRecorder = koinInject<AudioRecorder>()
            val audioFeedbackService = koinInject<AudioFeedbackService>()
            val overlayController = koinInject<OverlayController>()

            val recordingViewModel = koinViewModel<RecordingViewModel>()
            val settingsViewModel = koinViewModel<SettingsViewModel>()
            val historyViewModel = koinViewModel<HistoryViewModel>()
            val onboardingViewModel = koinViewModel<OnboardingViewModel>()
            val dictionaryViewModel = koinViewModel<DictionaryViewModel>()
            val userProfileViewModel = koinViewModel<UserProfileViewModel>()
            val statsViewModel = koinViewModel<StatsViewModel>()
            val appCustomizationViewModel = koinViewModel<AppCustomizationViewModel>()

            val settingsStateForVolume by settingsViewModel.state.collectAsState()
            LaunchedEffect(settingsStateForVolume.soundFeedbackVolume) {
                audioFeedbackService.setVolume(settingsStateForVolume.soundFeedbackVolume)
            }

            var isVisible by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val settings = settingsRepo.loadSettings()
                hotkeyManager.setConfig(settings.hotkeyConfig)
                hotkeyManager.startListening()
                appCustomizationViewModel.refresh()

                if (settings.startMinimized) {
                    isVisible = false
                }

                try {
                    val startToneBytes =
                        javaClass.getResourceAsStream("/composeResources/openyap.composeapp.generated.resources/files/start_tone.wav")
                            ?.use { it.readBytes() }
                    val stopToneBytes =
                        javaClass.getResourceAsStream("/composeResources/openyap.composeapp.generated.resources/files/stop_tone.wav")
                            ?.use { it.readBytes() }

                    val toneMap = buildMap {
                        startToneBytes?.let { put(AudioFeedbackService.Tone.START, it) }
                        stopToneBytes?.let {
                            put(AudioFeedbackService.Tone.STOP, it)
                            put(AudioFeedbackService.Tone.TOO_SHORT, it)
                        }
                    }
                    audioFeedbackService.preload(toneMap)
                } catch (_: Exception) {
                    // Audio feedback unavailable — visual-only fallback
                }
            }

            val trayState = rememberTrayState()
            val windowState = rememberWindowState(size = DpSize(1100.dp, 800.dp))
            val backStack = remember { mutableStateListOf<Route>(Route.Home) }

            DisposableEffect(audioRecorder) {
                onDispose {
                    runCatching { (audioRecorder as? java.io.Closeable)?.close() }
                }
            }

            val recordingState by recordingViewModel.state.collectAsState()
            val appIcon = painterResource(Res.drawable.ic_app_logo)

            Tray(
                state = trayState,
                tooltip = "OpenYap",
                icon = appIcon,
                menu = {
                    Item("Show", onClick = { isVisible = true })
                    Item("Quit", onClick = {
                        runCatching { (audioRecorder as? java.io.Closeable)?.close() }
                        (overlayController as? java.io.Closeable)?.close()
                        audioFeedbackService.close()
                        hotkeyManager.close()
                        exitApplication()
                    })
                },
            )

            val composeOverlayController = overlayController as ComposeOverlayController
            val overlayState by composeOverlayController.uiState.collectAsState()
            ComposeOverlayWindow(overlayState)

            LaunchedEffect(recordingViewModel, historyViewModel, statsViewModel) {
                recordingViewModel.effects.collect { effect ->
                    when (effect) {
                        RecordingEffect.PasteSuccess -> {
                            historyViewModel.refresh()
                            statsViewModel.refresh()
                        }

                        is RecordingEffect.ShowError -> Unit
                    }
                }
            }

            if (isVisible) {
                Window(
                    onCloseRequest = { isVisible = false },
                    title = "OpenYap",
                    state = windowState,
                    icon = appIcon,
                ) {
                    val isDark = isSystemInDarkTheme()
                    DisposableEffect(isDark) {
                        val bgColor = if (isDark) {
                            java.awt.Color(0x1F, 0x29, 0x37)
                        } else {
                            java.awt.Color(0xFB, 0xFC, 0xFF)
                        }
                        window.background = bgColor
                        window.rootPane.background = bgColor
                        window.contentPane.background = bgColor
                        window.layeredPane.background = bgColor
                        window.glassPane.background = bgColor

                        val applyTitleBar = Runnable {
                            WindowsThemeHelper.setDarkTitleBar(window, isDark)
                        }
                        javax.swing.SwingUtilities.invokeLater(applyTitleBar)

                        val windowListener = object : java.awt.event.WindowAdapter() {
                            override fun windowOpened(e: java.awt.event.WindowEvent?) {
                                applyTitleBar.run()
                            }

                            override fun windowActivated(e: java.awt.event.WindowEvent?) {
                                applyTitleBar.run()
                            }
                        }
                        window.addWindowListener(windowListener)

                        onDispose {
                            window.removeWindowListener(windowListener)
                        }
                    }

                    LaunchedEffect(Unit) {
                        var wasResetting = false
                        settingsViewModel.state.collect { state ->
                            if (state.isResettingData) {
                                wasResetting = true
                            } else if (wasResetting) {
                                wasResetting = false
                                recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                historyViewModel.refresh()
                                onboardingViewModel.refresh()
                                dictionaryViewModel.refresh()
                                userProfileViewModel.refresh()
                                statsViewModel.refresh()
                                appCustomizationViewModel.refresh()
                            }
                        }
                    }

                    AppTheme {
                        AppShell(
                            backStack = backStack,
                            recordingViewModel = recordingViewModel,
                            settingsViewModel = settingsViewModel,
                            onRecordingEvent = recordingViewModel::onEvent,
                            onSettingsEvent = { event ->
                                settingsViewModel.onEvent(event)
                                if (event is SettingsEvent.SaveApiKey || event is SettingsEvent.SaveGroqApiKey || event is SettingsEvent.SelectProvider) {
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                }
                                if (event is SettingsEvent.ResetAppData) {
                                    onboardingViewModel.resetState()
                                    appCustomizationViewModel.reset()
                                    backStack.clear()
                                    backStack += Route.Home
                                }
                            },
                            onOnboardingEvent = { event ->
                                onboardingViewModel.onEvent(event)
                                if (event is OnboardingEvent.CompleteOnboarding) {
                                    settingsViewModel.refresh()
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                } else if (event is OnboardingEvent.SaveApiKey) {
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                }
                            },
                            onCopyToClipboard = { text ->
                                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(java.awt.datatransfer.StringSelection(text), null)
                            },
                        )
                    }
                }
            }
        }
    }
}
```

> **Key changes from the original `main.kt`**:
> 1. `startKoin` is replaced with `KoinApplication` composable wrapping the entire `application {}` body (this is the recommended approach for Compose Desktop since the Koin context needs to be available throughout composition).
> 2. All `remember { ... }` blocks for services and ViewModels are removed.
> 3. Services are retrieved via `koinInject<T>()`, ViewModels via `koinViewModel<T>()`.
> 4. `appTones`/`appPrompts` orphan state is replaced by `AppCustomizationViewModel`.
> 5. `AppShell` receives fewer parameters (see Phase 6).
> 6. The tray quit handler preserves the exact close order: audioRecorder → overlayController → audioFeedbackService → hotkeyManager → exitApplication.
> 7. All cross-ViewModel orchestration (recording→history fan-out, settings interception, onboarding interception, reset watcher, volume sync) remains in `main.kt`.

> **Note on `KoinApplication` vs `startKoin`**: For Compose Desktop apps, `KoinApplication` is preferred because it scopes the Koin instance to the composition. If `startKoin` is preferred (e.g., to start Koin before any composition), place it before `application {}`:
>
> ```kotlin
> fun main() {
>     System.setProperty("sun.awt.noerasebackground", "true")
>     startKoin {
>         modules(
>             SharedModule().module,
>             PlatformModule().module,
>             ComposeAppModule().module,
>         )
>     }
>     application { ... }
> }
> ```
>
> In that case, use `koinInject()` and `koinViewModel()` inside composition without a `KoinApplication` wrapper. The plan above uses `KoinApplication` because it is more composable and does not require a global singleton.

> **Note on KSP-generated `.module` extension**: KSP generates `val SharedModule.module: org.koin.core.module.Module` as an extension property in a file like `com/openyap/di/SharedModuleGenKt`. The import `import org.koin.ksp.generated.module` provides access to all generated `.module` extensions. If this import does not resolve, verify KSP ran successfully in Phase 4.

### Verification Checkpoint

```bash
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm
```

- Expected result: BUILD SUCCESSFUL. `main.kt` compiles with Koin wiring. `AppShell` may not yet compile because its signature has changed — proceed to Phase 6.

---

## Phase 6: Simplify `AppShell`

### Step 6.1: Rewrite `AppShell` signature and body

- [ ] Update `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt` to have screens retrieve their own ViewModels.

**Before** (`AppShell.kt`, lines 99–123):

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppShell(
    backStack: MutableList<Route>,
    recordingState: RecordingUiState,
    settingsState: SettingsUiState,
    historyState: HistoryUiState,
    onboardingState: OnboardingUiState,
    dictionaryState: DictionaryUiState,
    userProfileState: UserProfileUiState,
    statsState: StatsUiState,
    appTones: Map<String, String>,
    appPrompts: Map<String, String>,
    onRecordingEvent: (RecordingEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
    onHistoryEvent: (HistoryEvent) -> Unit,
    onOnboardingEvent: (OnboardingEvent) -> Unit,
    onDictionaryEvent: (DictionaryEvent) -> Unit,
    onUserProfileEvent: (UserProfileEvent) -> Unit,
    onSaveTone: (String, String) -> Unit,
    onSavePrompt: (String, String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onStatsRefresh: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
) {
```

**After**:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppShell(
    backStack: MutableList<Route>,
    recordingViewModel: RecordingViewModel,
    settingsViewModel: SettingsViewModel,
    onRecordingEvent: (RecordingEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
    onOnboardingEvent: (OnboardingEvent) -> Unit,
    onCopyToClipboard: (String) -> Unit,
) {
```

- [ ] Add new imports to `AppShell.kt`:

```kotlin
import androidx.compose.runtime.collectAsState
import com.openyap.viewmodel.AppCustomizationEvent
import com.openyap.viewmodel.AppCustomizationViewModel
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingViewModel
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
```

- [ ] Update the body of `AppShell` to collect state from the injected/koin ViewModels.

**Replace the opening of `AppShell` body** (after the signature, before the `if (!onboardingState.isLoaded)` check):

**Before** (lines 124–157 of the original):

```kotlin
    if (!onboardingState.isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            ...
        }
        return
    }

    if (!onboardingState.isComplete) {
        OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)
        return
    }

    val currentRoute = backStack.lastOrNull() ?: Route.Home
```

**After**:

```kotlin
    val recordingState by recordingViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val onboardingViewModel = koinViewModel<OnboardingViewModel>()
    val onboardingState by onboardingViewModel.state.collectAsState()

    if (!onboardingState.isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "OpenYap",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Spacing.sm))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }

    if (!onboardingState.isComplete) {
        OnboardingScreen(state = onboardingState, onEvent = onOnboardingEvent)
        return
    }

    val currentRoute = backStack.lastOrNull() ?: Route.Home
```

- [ ] Update the `NavDisplay` `entryProvider` block to retrieve ViewModels via `koinViewModel()`.

**Before** (the `entryProvider` block, lines 278–327):

```kotlin
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<Route.Home> {
                                HomeContent(
                                    recordingState,
                                    settingsState,
                                    onNavigateToSettings = {
                                        if (currentRoute != Route.Settings) {
                                            backStack.clear()
                                            backStack.add(Route.Settings)
                                        }
                                    },
                                    onRecordingEvent,
                                    snackbarHostState,
                                )
                            }
                            entry<Route.History> {
                                HistoryScreen(historyState, onHistoryEvent, onCopyToClipboard)
                            }
                            entry<Route.Dictionary> {
                                DictionaryScreen(
                                    state = dictionaryState,
                                    isDictionaryEnabled = settingsState.dictionaryEnabled,
                                    onEvent = onDictionaryEvent,
                                )
                            }
                            entry<Route.UserInfo> {
                                UserInfoScreen(userProfileState, onUserProfileEvent)
                            }
                            entry<Route.Stats> {
                                StatsScreen(statsState, onRefresh = onStatsRefresh)
                            }
                            entry<Route.Customization> {
                                CustomizationScreen(
                                    appTones,
                                    appPrompts,
                                    onSaveTone,
                                    onSavePrompt,
                                    onRemoveApp
                                )
                            }
                            entry<Route.Settings> {
                                SettingsScreen(settingsState, onSettingsEvent)
                            }
                            entry<Route.Onboarding> {
                                OnboardingScreen(
                                    state = onboardingState,
                                    onEvent = onOnboardingEvent
                                )
                            }
                        },
                    )
```

**After**:

```kotlin
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<Route.Home> {
                                HomeContent(
                                    recordingState,
                                    settingsState,
                                    onNavigateToSettings = {
                                        if (currentRoute != Route.Settings) {
                                            backStack.clear()
                                            backStack.add(Route.Settings)
                                        }
                                    },
                                    onRecordingEvent,
                                    snackbarHostState,
                                )
                            }
                            entry<Route.History> {
                                val vm = koinViewModel<HistoryViewModel>()
                                val state by vm.state.collectAsState()
                                HistoryScreen(state, vm::onEvent, onCopyToClipboard)
                            }
                            entry<Route.Dictionary> {
                                val vm = koinViewModel<DictionaryViewModel>()
                                val state by vm.state.collectAsState()
                                DictionaryScreen(
                                    state = state,
                                    isDictionaryEnabled = settingsState.dictionaryEnabled,
                                    onEvent = vm::onEvent,
                                )
                            }
                            entry<Route.UserInfo> {
                                val vm = koinViewModel<UserProfileViewModel>()
                                val state by vm.state.collectAsState()
                                UserInfoScreen(state, vm::onEvent)
                            }
                            entry<Route.Stats> {
                                val vm = koinViewModel<StatsViewModel>()
                                val state by vm.state.collectAsState()
                                StatsScreen(state, onRefresh = vm::refresh)
                            }
                            entry<Route.Customization> {
                                val vm = koinViewModel<AppCustomizationViewModel>()
                                val state by vm.state.collectAsState()
                                CustomizationScreen(
                                    state.appTones,
                                    state.appPrompts,
                                    onSaveTone = { app, tone -> vm.onEvent(AppCustomizationEvent.SaveTone(app, tone)) },
                                    onSavePrompt = { app, prompt -> vm.onEvent(AppCustomizationEvent.SavePrompt(app, prompt)) },
                                    onRemoveApp = { app -> vm.onEvent(AppCustomizationEvent.RemoveApp(app)) },
                                )
                            }
                            entry<Route.Settings> {
                                SettingsScreen(settingsState, onSettingsEvent)
                            }
                            entry<Route.Onboarding> {
                                OnboardingScreen(
                                    state = onboardingState,
                                    onEvent = onOnboardingEvent
                                )
                            }
                        },
                    )
```

- [ ] Remove the old unused imports from `AppShell.kt` that are no longer needed:

Remove these imports (they are now resolved inside the entry blocks or no longer passed as parameters):

```kotlin
import com.openyap.viewmodel.DictionaryEvent
import com.openyap.viewmodel.DictionaryUiState
import com.openyap.viewmodel.HistoryEvent
import com.openyap.viewmodel.HistoryUiState
import com.openyap.viewmodel.StatsUiState
import com.openyap.viewmodel.UserProfileEvent
import com.openyap.viewmodel.UserProfileUiState
```

> **Why `recordingViewModel` and `settingsViewModel` are passed as parameters instead of injected via `koinViewModel()` inside `AppShell`**:
>
> 1. `recordingState` is used by the nav-rail recording pulse indicator and `RecordingIndicator` — both at the `AppShell` root level, not inside a specific route entry.
> 2. `settingsState` is used for the loading gate, the `HomeContent` (which needs `settingsState.hotkeyLabel`), and the dictionary enabled flag.
> 3. `onRecordingEvent` wraps `recordingViewModel::onEvent` and is used by `RecordingIndicator` at the `AppShell` level.
> 4. `onSettingsEvent` is the intercepting wrapper lambda from `main.kt` — it cannot be replaced by a simple `vm::onEvent`.
> 5. `onOnboardingEvent` is similarly an intercepting wrapper.
>
> These ViewModels must be the **same instances** used in `main.kt` for cross-ViewModel orchestration. Since Koin singletons are scoped per `KoinApplication`, `koinViewModel<RecordingViewModel>()` called inside `AppShell` would return the same instance as in `main.kt`. However, `main.kt` needs direct references for the effect collector, volume sync, and reset watcher. Passing them explicitly makes the data flow clear and avoids hiding the orchestration dependency.

### Verification Checkpoint

```bash
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm
```

- Expected result: BUILD SUCCESSFUL. `AppShell` has 7 parameters instead of 23. All routes resolve their own ViewModels.

---

## Phase 7: Final Verification

### Step 7.1: Full check

- [ ] Run the full Gradle check.

```bash
./gradlew check
```

- Expected result: BUILD SUCCESSFUL. All compilation checks pass.

### Step 7.2: Run the app

- [ ] Run the app to verify startup.

```bash
./gradlew :composeApp:run
```

- Expected result: The app launches. On Linux (cloud agent), expect non-fatal warnings about `dwmapi` and `openyap_native` DLLs. The app should open the main window with the onboarding flow (since no settings are persisted).

### Step 7.3: Manual smoke test (on Windows)

- [ ] Verify hotkey recording works: press the configured hotkey, speak, release, verify transcription and paste.
- [ ] Verify settings save: change a setting, close and reopen, verify persistence.
- [ ] Verify onboarding flow: complete onboarding, verify main UI appears.
- [ ] Verify tray quit: click Quit in tray, verify clean shutdown (no crashes or hung processes).
- [ ] Verify per-app customization: add a tone/prompt for an app, verify it appears in the customization screen.
- [ ] Verify app data reset: trigger reset from settings, verify all data is cleared and onboarding appears.

---

## Summary of Files Changed

| File | Action | Phase |
|------|--------|-------|
| `gradle/libs.versions.toml` | Modified (add Koin versions + libraries) | 1 |
| `shared/build.gradle.kts` | Modified (add Koin deps + KSP) | 1 |
| `composeApp/build.gradle.kts` | Modified (add KSP plugin + Koin deps + KSP config) | 1 |
| `shared/src/commonMain/kotlin/com/openyap/platform/FileOperations.kt` | **New** | 2 |
| `shared/src/jvmMain/kotlin/com/openyap/platform/JvmFileOperations.kt` | **New** | 2 |
| `shared/src/commonMain/kotlin/com/openyap/platform/AppDataResetter.kt` | **New** | 2 |
| `shared/src/jvmMain/kotlin/com/openyap/platform/AudioFeedbackPlayerImpl.kt` | **New** | 2 |
| `shared/src/commonMain/kotlin/com/openyap/platform/AudioPipelineConfig.kt` | **New** | 2 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/AppCustomizationViewModel.kt` | **New** | 2 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/RecordingViewModel.kt` | Modified (constructor, lambdas → FileOperations, add annotations) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/SettingsViewModel.kt` | Modified (constructor, lambda → AppDataResetter, add annotations) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/HistoryViewModel.kt` | Modified (add `@KoinViewModel`) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/OnboardingViewModel.kt` | Modified (add `@KoinViewModel` + `@Named`) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/DictionaryViewModel.kt` | Modified (add `@KoinViewModel`) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/UserProfileViewModel.kt` | Modified (add `@KoinViewModel`) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/viewmodel/StatsViewModel.kt` | Modified (add `@KoinViewModel`) | 3 |
| `shared/src/commonMain/kotlin/com/openyap/service/DictionaryEngine.kt` | Modified (add `@Single`) | 3 |
| `shared/src/jvmMain/kotlin/com/openyap/platform/JvmAppDataResetter.kt` | Modified (add `@Single`, implement interface, `@Named` paths) | 3, 4 |
| `shared/src/jvmMain/kotlin/com/openyap/di/SharedModule.kt` | **New** | 4 |
| `shared/src/jvmMain/kotlin/com/openyap/di/PlatformModule.kt` | **New** | 4 |
| `composeApp/src/jvmMain/kotlin/com/openyap/di/ComposeAppModule.kt` | **New** | 4 |
| `composeApp/src/jvmMain/kotlin/com/openyap/main.kt` | **Rewritten** | 5 |
| `composeApp/src/commonMain/kotlin/com/openyap/ui/navigation/AppShell.kt` | Modified (reduced params, koinViewModel in entries) | 6 |

## Summary of New Packages

| Package | Module | Files |
|---------|--------|-------|
| `com.openyap.di` | `shared` | `SharedModule.kt`, `PlatformModule.kt` |
| `com.openyap.di` | `composeApp` | `ComposeAppModule.kt` |

## Dependency Artifacts Added

| Artifact | Module | Scope |
|----------|--------|-------|
| `io.insert-koin:koin-bom:4.2.0` (platform) | `shared`, `composeApp` | `commonMain` |
| `io.insert-koin:koin-core` | `shared`, `composeApp` | `commonMain` |
| `io.insert-koin:koin-annotations:2.3.0` | `shared`, `composeApp` | `commonMain` |
| `io.insert-koin:koin-compose` | `composeApp` | `commonMain` |
| `io.insert-koin:koin-compose-viewmodel` | `composeApp` | `commonMain` |
| `io.insert-koin:koin-ksp-compiler:2.3.0` | `shared`, `composeApp` | `kspJvm` |
