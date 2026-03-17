package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.AppSettings
import com.openyap.model.AudioDevice
import com.openyap.model.HotkeyBinding
import com.openyap.model.HotkeyConfig
import com.openyap.model.commandHotkeyValidationError
import com.openyap.model.effectiveHotkeyConfig
import com.openyap.model.hasCommandHotkeyConflict
import com.openyap.model.PrimaryUseCase
import com.openyap.model.TranscriptionProvider
import com.openyap.platform.AppDataResetter
import com.openyap.platform.AudioRecorder
import com.openyap.platform.HotkeyDisplayFormatter
import com.openyap.platform.NoOpAppDataResetter
import com.openyap.platform.HotkeyManager
import com.openyap.platform.NoOpStartupManager
import com.openyap.platform.StartupManager
import com.openyap.repository.SettingsRepository
import com.openyap.service.GeminiClient
import com.openyap.service.GroqLLMClient
import com.openyap.service.GroqWhisperClient
import com.openyap.service.ModelInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val transcriptionProvider: TranscriptionProvider = TranscriptionProvider.GROQ_WHISPER_GROQ,
    val apiKey: String = "",
    val groqApiKey: String = "",
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
    val groqModel: String = "whisper-large-v3",
    val groqLLMModel: String = "moonshotai/kimi-k2-instruct-0905",
    val genZEnabled: Boolean = false,
    val phraseExpansionEnabled: Boolean = false,
    val dictionaryEnabled: Boolean = true,
    val audioFeedbackEnabled: Boolean = true,
    val soundFeedbackVolume: Float = 0.5f,
    val startMinimized: Boolean = false,
    val launchOnStartup: Boolean = false,
    val startupSupported: Boolean = false,
    val isSaving: Boolean = false,
    val isResettingData: Boolean = false,
    val saveMessage: String? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val groqModels: List<ModelInfo> = listOf(
        ModelInfo("whisper-large-v3", "Whisper Large V3"),
        ModelInfo("whisper-large-v3-turbo", "Whisper Large V3 Turbo"),
    ),
    val groqLLMModels: List<ModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isLoadingGroqLLMModels: Boolean = false,
    val modelsFetchError: String? = null,
    val groqLLMModelsFetchError: String? = null,
    val whisperModeEnabled: Boolean = false,
    val dictationHotkeyLabel: String = "Ctrl+Shift+R",
    val commandHotkeyLabel: String = "Ctrl+Shift+C",
    val commandHotkeyEnabled: Boolean = true,
    val isCapturingDictationHotkey: Boolean = false,
    val isCapturingCommandHotkey: Boolean = false,
    val dictationHotkeyError: String? = null,
    val commandHotkeyError: String? = null,
    val appVersion: String = "",
    val audioDevices: List<AudioDevice> = emptyList(),
    val selectedAudioDeviceId: String? = null,
    val isLoadingDevices: Boolean = false,
    val devicesFetchError: String? = null,
    val primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL,
    val useCaseContext: String = "",
    val whisperLanguage: String = "en",
) {
    val hotkeyLabel: String
        get() = dictationHotkeyLabel
}

sealed interface SettingsEffect {
    data object ResetAppDataSucceeded : SettingsEffect
}

sealed interface SettingsEvent {
    data class SelectProvider(val provider: TranscriptionProvider) : SettingsEvent
    data class SaveApiKey(val key: String) : SettingsEvent
    data class SaveGroqApiKey(val key: String) : SettingsEvent
    data class SelectModel(val modelId: String) : SettingsEvent
    data class SelectGroqModel(val modelId: String) : SettingsEvent
    data class SelectGroqLLMModel(val modelId: String) : SettingsEvent
    data class ToggleGenZ(val enabled: Boolean) : SettingsEvent
    data class TogglePhraseExpansion(val enabled: Boolean) : SettingsEvent
    data class ToggleDictionary(val enabled: Boolean) : SettingsEvent
    data class ToggleAudioFeedback(val enabled: Boolean) : SettingsEvent
    data class SetSoundFeedbackVolume(val volume: Float) : SettingsEvent
    data class ToggleStartMinimized(val enabled: Boolean) : SettingsEvent
    data class ToggleLaunchOnStartup(val enabled: Boolean) : SettingsEvent
    data object ResetAppData : SettingsEvent
    data object RefreshModels : SettingsEvent
    data object RefreshGroqLLMModels : SettingsEvent
    data class SelectAudioDevice(val deviceId: String?) : SettingsEvent
    data object RefreshDevices : SettingsEvent
    data class ToggleWhisperMode(val enabled: Boolean) : SettingsEvent
    data class ToggleCommandHotkey(val enabled: Boolean) : SettingsEvent
    data object CaptureDictationHotkey : SettingsEvent
    data object CaptureCommandHotkey : SettingsEvent
    data object DismissSaveMessage : SettingsEvent
    data class SelectUseCase(val useCase: PrimaryUseCase) : SettingsEvent
    data class SaveUseCaseContext(val context: String) : SettingsEvent
    data class SelectWhisperLanguage(val language: String) : SettingsEvent
}

private enum class HotkeyCaptureTarget {
    DICTATION,
    COMMAND,
}

private data class HotkeyValidationState(
    val dictationError: String? = null,
    val commandError: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient,
    private val groqWhisperClient: GroqWhisperClient,
    private val groqLLMClient: GroqLLMClient,
    private val hotkeyManager: HotkeyManager,
    private val hotkeyDisplayFormatter: HotkeyDisplayFormatter,
    private val audioRecorder: AudioRecorder,
    private val startupManager: StartupManager = NoOpStartupManager(),
    private val appDataResetter: AppDataResetter = NoOpAppDataResetter(),
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val apiKey = settingsRepository.loadApiKey() ?: ""
            val groqApiKey = settingsRepository.loadGroqApiKey() ?: ""
            val settings = settingsRepository.loadSettings()
            val launchOnStartup = runCatching { startupManager.isEnabled() }
                .getOrDefault(settings.launchOnStartup)
            // Read version from manifest/resources
            val version = runCatching {
                SettingsViewModel::class.java.getResourceAsStream("/version.properties")
                    ?.bufferedReader()
                    ?.readText()
                    ?.lines()
                    ?.firstOrNull { it.startsWith("version=") }
                    ?.removePrefix("version=")
                    ?.trim()
                    ?: ""
            }.getOrDefault("")
            val hotkeyValidation = validateHotkeys(settings.hotkeyConfig)
            _state.update {
                it.copy(
                    transcriptionProvider = settings.transcriptionProvider,
                    apiKey = apiKey,
                    groqApiKey = groqApiKey,
                    geminiModel = settings.geminiModel,
                    groqModel = settings.groqModel,
                    groqLLMModel = settings.groqLLMModel,
                    genZEnabled = settings.genZEnabled,
                    phraseExpansionEnabled = settings.phraseExpansionEnabled,
                    dictionaryEnabled = settings.dictionaryEnabled,
                    audioFeedbackEnabled = settings.audioFeedbackEnabled,
                    soundFeedbackVolume = settings.soundFeedbackVolume,
                    startMinimized = settings.startMinimized,
                    launchOnStartup = launchOnStartup,
                    startupSupported = startupManager.isSupported,
                    whisperModeEnabled = settings.whisperModeEnabled,
                    dictationHotkeyLabel = formatHotkey(settings.hotkeyConfig.startHotkey),
                    commandHotkeyLabel = formatHotkey(settings.hotkeyConfig.commandHotkey),
                    commandHotkeyEnabled = settings.hotkeyConfig.commandHotkeyEnabled,
                    dictationHotkeyError = hotkeyValidation.dictationError,
                    commandHotkeyError = hotkeyValidation.commandError,
                    appVersion = version,
                    selectedAudioDeviceId = settings.audioDeviceId,
                    primaryUseCase = settings.primaryUseCase,
                    useCaseContext = settings.useCaseContext,
                    whisperLanguage = settings.whisperLanguage,
                )
            }
            if (apiKey.isNotBlank()) fetchModels(apiKey)
            if (groqApiKey.isNotBlank()) fetchGroqLLMModels(groqApiKey)
            fetchDevices()
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectProvider -> selectProvider(event.provider)
            is SettingsEvent.SaveApiKey -> saveApiKey(event.key)
            is SettingsEvent.SaveGroqApiKey -> saveGroqApiKey(event.key)
            is SettingsEvent.SelectModel -> selectModel(event.modelId)
            is SettingsEvent.SelectGroqModel -> selectGroqModel(event.modelId)
            is SettingsEvent.SelectGroqLLMModel -> selectGroqLLMModel(event.modelId)
            is SettingsEvent.ToggleGenZ -> toggleGenZ(event.enabled)
            is SettingsEvent.TogglePhraseExpansion -> togglePhraseExpansion(event.enabled)
            is SettingsEvent.ToggleDictionary -> toggleDictionary(event.enabled)
            is SettingsEvent.ToggleAudioFeedback -> toggleAudioFeedback(event.enabled)
            is SettingsEvent.SetSoundFeedbackVolume -> setSoundFeedbackVolume(event.volume)
            is SettingsEvent.ToggleStartMinimized -> toggleStartMinimized(event.enabled)
            is SettingsEvent.ToggleLaunchOnStartup -> toggleLaunchOnStartup(event.enabled)
            is SettingsEvent.ResetAppData -> resetAppData()
            is SettingsEvent.RefreshModels -> refreshModels()
            is SettingsEvent.RefreshGroqLLMModels -> refreshGroqLLMModels()
            is SettingsEvent.ToggleWhisperMode -> toggleWhisperMode(event.enabled)
            is SettingsEvent.ToggleCommandHotkey -> toggleCommandHotkey(event.enabled)
            is SettingsEvent.CaptureDictationHotkey -> captureHotkey(HotkeyCaptureTarget.DICTATION)
            is SettingsEvent.CaptureCommandHotkey -> captureHotkey(HotkeyCaptureTarget.COMMAND)
            is SettingsEvent.DismissSaveMessage -> _state.update { it.copy(saveMessage = null) }
            is SettingsEvent.SelectAudioDevice -> selectAudioDevice(event.deviceId)
            is SettingsEvent.RefreshDevices -> refreshDevices()
            is SettingsEvent.SelectUseCase -> selectUseCase(event.useCase)
            is SettingsEvent.SaveUseCaseContext -> saveUseCaseContext(event.context)
            is SettingsEvent.SelectWhisperLanguage -> selectWhisperLanguage(event.language)
        }
    }

    private fun captureHotkey(target: HotkeyCaptureTarget) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCapturingDictationHotkey = target == HotkeyCaptureTarget.DICTATION,
                    isCapturingCommandHotkey = target == HotkeyCaptureTarget.COMMAND,
                    dictationHotkeyError = if (target == HotkeyCaptureTarget.DICTATION) null else it.dictationHotkeyError,
                    commandHotkeyError = if (target == HotkeyCaptureTarget.COMMAND) null else it.commandHotkeyError,
                    saveMessage = null
                )
            }
            try {
                val capture = hotkeyManager.captureNextHotkey()
                val binding = HotkeyBinding(
                    platformKeyCode = capture.platformKeyCode,
                    modifiers = capture.modifiers,
                )
                val currentSettings = settingsRepository.loadSettings()
                val updatedHotkeyConfig = when (target) {
                    HotkeyCaptureTarget.DICTATION -> currentSettings.hotkeyConfig.copy(startHotkey = binding)
                    HotkeyCaptureTarget.COMMAND -> currentSettings.hotkeyConfig.copy(commandHotkey = binding)
                }

                val validation = validateHotkeys(updatedHotkeyConfig)
                if (target == HotkeyCaptureTarget.COMMAND && validation.commandError != null) {
                    _state.update {
                        it.copy(
                            isCapturingDictationHotkey = false,
                            isCapturingCommandHotkey = false,
                            dictationHotkeyError = validation.dictationError,
                            commandHotkeyError = validation.commandError,
                        )
                    }
                    return@launch
                }
                if (target == HotkeyCaptureTarget.DICTATION && validation.dictationError != null) {
                    _state.update {
                        it.copy(
                            isCapturingDictationHotkey = false,
                            isCapturingCommandHotkey = false,
                            dictationHotkeyError = validation.dictationError,
                            commandHotkeyError = validation.commandError,
                        )
                    }
                    return@launch
                }

                val updatedSettings = settingsRepository.updateSettings { settings ->
                    settings.copy(hotkeyConfig = updatedHotkeyConfig)
                }
                hotkeyManager.setConfig(updatedSettings.effectiveHotkeyConfig())
                _state.update {
                    it.copy(
                        dictationHotkeyLabel = formatHotkey(updatedSettings.hotkeyConfig.startHotkey),
                        commandHotkeyLabel = formatHotkey(updatedSettings.hotkeyConfig.commandHotkey),
                        commandHotkeyEnabled = updatedSettings.hotkeyConfig.commandHotkeyEnabled,
                        isCapturingDictationHotkey = false,
                        isCapturingCommandHotkey = false,
                        dictationHotkeyError = validation.dictationError,
                        commandHotkeyError = validation.commandError,
                        saveMessage = when (target) {
                            HotkeyCaptureTarget.DICTATION -> "Dictation hotkey updated to ${capture.displayLabel}"
                            HotkeyCaptureTarget.COMMAND -> "Command hotkey updated to ${capture.displayLabel}"
                        },
                    )
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.update {
                    it.copy(
                        isCapturingDictationHotkey = false,
                        isCapturingCommandHotkey = false,
                        dictationHotkeyError = if (target == HotkeyCaptureTarget.DICTATION) {
                            "Hotkey capture timed out. Please try again."
                        } else {
                            it.dictationHotkeyError
                        },
                        commandHotkeyError = if (target == HotkeyCaptureTarget.COMMAND) {
                            "Hotkey capture timed out. Please try again."
                        } else {
                            it.commandHotkeyError
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isCapturingDictationHotkey = false,
                        isCapturingCommandHotkey = false,
                        dictationHotkeyError = if (target == HotkeyCaptureTarget.DICTATION) {
                            e.message ?: "Failed to capture hotkey"
                        } else {
                            it.dictationHotkeyError
                        },
                        commandHotkeyError = if (target == HotkeyCaptureTarget.COMMAND) {
                            e.message ?: "Failed to capture hotkey"
                        } else {
                            it.commandHotkeyError
                        },
                    )
                }
            }
        }
    }

    private fun saveApiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.saveApiKey(trimmed)
            _state.update { it.copy(apiKey = trimmed, saveMessage = "API key saved") }

            if (trimmed.isNotBlank()) {
                fetchModels(trimmed)
            } else {
                _state.update {
                    it.copy(availableModels = emptyList(), modelsFetchError = null)
                }
            }
        }
    }

    private fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(geminiModel = modelId) }
            _state.update { it.copy(geminiModel = modelId) }
        }
    }

    private fun selectProvider(provider: TranscriptionProvider) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(transcriptionProvider = provider) }
            _state.update { it.copy(transcriptionProvider = provider) }
            if (provider != TranscriptionProvider.GROQ_WHISPER && provider != TranscriptionProvider.GROQ_WHISPER_GROQ && _state.value.apiKey.isNotBlank() && _state.value.availableModels.isEmpty()) {
                fetchModels(_state.value.apiKey)
            }
            if (provider == TranscriptionProvider.GROQ_WHISPER_GROQ && _state.value.groqApiKey.isNotBlank() && _state.value.groqLLMModels.isEmpty()) {
                fetchGroqLLMModels(_state.value.groqApiKey)
            }
        }
    }

    private fun saveGroqApiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.saveGroqApiKey(trimmed)
            _state.update { it.copy(groqApiKey = trimmed, saveMessage = "Groq API key saved") }

            if (trimmed.isNotBlank()) {
                fetchGroqLLMModels(trimmed)
            } else {
                _state.update {
                    it.copy(
                        groqLLMModels = emptyList(),
                        groqLLMModelsFetchError = null,
                        isLoadingGroqLLMModels = false,
                    )
                }
            }
        }
    }

    private fun selectGroqModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(groqModel = modelId) }
            _state.update { it.copy(groqModel = modelId) }
        }
    }

    private fun selectGroqLLMModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(groqLLMModel = modelId) }
            _state.update { it.copy(groqLLMModel = modelId) }
        }
    }

    private fun refreshGroqLLMModels() {
        val groqApiKey = _state.value.groqApiKey
        if (groqApiKey.isNotBlank()) {
            viewModelScope.launch { fetchGroqLLMModels(groqApiKey) }
        }
    }

    private suspend fun fetchGroqLLMModels(apiKey: String) {
        val expectedKey = apiKey
        _state.update { it.copy(isLoadingGroqLLMModels = true, groqLLMModelsFetchError = null) }
        try {
            val models = groqLLMClient.listModels(apiKey)
            if (_state.value.groqApiKey != expectedKey) return

            _state.update { it.copy(groqLLMModels = models, isLoadingGroqLLMModels = false) }

            if (models.isNotEmpty() && models.none { it.id == _state.value.groqLLMModel }) {
                selectGroqLLMModel(models.first().id)
            }
        } catch (e: Exception) {
            if (_state.value.groqApiKey != expectedKey) return

            _state.update {
                it.copy(
                    isLoadingGroqLLMModels = false,
                    groqLLMModelsFetchError = e.message ?: "Failed to fetch Groq LLM models",
                )
            }
        }
    }

    private fun refreshModels() {
        val apiKey = _state.value.apiKey
        if (apiKey.isNotBlank()) {
            viewModelScope.launch { fetchModels(apiKey) }
        }
    }

    private suspend fun fetchModels(apiKey: String) {
        _state.update { it.copy(isLoadingModels = true, modelsFetchError = null) }
        try {
            val models = geminiClient.listModels(apiKey)
            _state.update { it.copy(availableModels = models, isLoadingModels = false) }

            if (models.isNotEmpty() && models.none { it.id == _state.value.geminiModel }) {
                selectModel(models.first().id)
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingModels = false,
                    modelsFetchError = e.message ?: "Failed to fetch models",
                )
            }
        }
    }

    private fun toggleGenZ(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(genZEnabled = enabled) }
            _state.update { it.copy(genZEnabled = enabled) }
        }
    }

    private fun toggleWhisperMode(enabled: Boolean) {
        viewModelScope.launch {
            val updatedSettings = settingsRepository.updateSettings { it.copy(whisperModeEnabled = enabled) }
            _state.update {
                it.copy(
                    whisperModeEnabled = enabled,
                    dictationHotkeyError = validateHotkeys(updatedSettings.hotkeyConfig).dictationError,
                    commandHotkeyError = validateHotkeys(updatedSettings.hotkeyConfig).commandError,
                )
            }
        }
    }

    private fun toggleCommandHotkey(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settingsRepository.loadSettings()
            val updatedHotkeyConfig = currentSettings.hotkeyConfig.copy(commandHotkeyEnabled = enabled)
            val validation = validateHotkeys(updatedHotkeyConfig)
            if (enabled && validation.commandError != null) {
                _state.update {
                    it.copy(
                        commandHotkeyEnabled = false,
                        dictationHotkeyError = validation.dictationError,
                        commandHotkeyError = validation.commandError,
                    )
                }
                return@launch
            }

            val updatedSettings = settingsRepository.updateSettings { settings ->
                settings.copy(hotkeyConfig = updatedHotkeyConfig)
            }
            hotkeyManager.setConfig(updatedSettings.effectiveHotkeyConfig())
            _state.update {
                it.copy(
                    commandHotkeyEnabled = updatedSettings.hotkeyConfig.commandHotkeyEnabled,
                    dictationHotkeyError = validation.dictationError,
                    commandHotkeyError = validation.commandError,
                )
            }
        }
    }

    private fun togglePhraseExpansion(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(phraseExpansionEnabled = enabled) }
            _state.update { it.copy(phraseExpansionEnabled = enabled) }
        }
    }

    private fun toggleAudioFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(audioFeedbackEnabled = enabled) }
            _state.update { it.copy(audioFeedbackEnabled = enabled) }
        }
    }

    private fun setSoundFeedbackVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(soundFeedbackVolume = clamped) }
            _state.update { it.copy(soundFeedbackVolume = clamped) }
        }
    }

    private fun toggleDictionary(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(dictionaryEnabled = enabled) }
            _state.update { it.copy(dictionaryEnabled = enabled) }
        }
    }

    private fun toggleStartMinimized(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(startMinimized = enabled) }
            _state.update { it.copy(startMinimized = enabled) }
        }
    }

    private fun toggleLaunchOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            if (!startupManager.isSupported) {
                _state.update { it.copy(saveMessage = "Launch on startup is only available in the installed desktop app.") }
                return@launch
            }

            try {
                startupManager.setEnabled(enabled)
                settingsRepository.updateSettings { it.copy(launchOnStartup = enabled) }
                _state.update {
                    it.copy(
                        launchOnStartup = enabled,
                        saveMessage = if (enabled) {
                            "OpenYap will launch when Windows starts"
                        } else {
                            "Launch on startup disabled"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        launchOnStartup = !enabled,
                        saveMessage = e.message ?: "Failed to update startup setting",
                    )
                }
            }
        }
    }

    private fun resetAppData() {
        viewModelScope.launch {
            val defaults = AppSettings()
            _state.update { it.copy(isResettingData = true, saveMessage = null) }
            try {
                appDataResetter.reset()
                runCatching { startupManager.setEnabled(false) }
                hotkeyManager.setConfig(defaults.effectiveHotkeyConfig())
                val hotkeyValidation = validateHotkeys(defaults.hotkeyConfig)
                _state.update {
                    it.copy(
                        transcriptionProvider = defaults.transcriptionProvider,
                        apiKey = "",
                        groqApiKey = "",
                        geminiModel = defaults.geminiModel,
                        groqModel = defaults.groqModel,
                        groqLLMModel = defaults.groqLLMModel,
                        genZEnabled = defaults.genZEnabled,
                        phraseExpansionEnabled = defaults.phraseExpansionEnabled,
                        dictionaryEnabled = defaults.dictionaryEnabled,
                        audioFeedbackEnabled = defaults.audioFeedbackEnabled,
                        soundFeedbackVolume = defaults.soundFeedbackVolume,
                        startMinimized = defaults.startMinimized,
                        launchOnStartup = defaults.launchOnStartup,
                        whisperModeEnabled = defaults.whisperModeEnabled,
                        primaryUseCase = defaults.primaryUseCase,
                        useCaseContext = defaults.useCaseContext,
                        whisperLanguage = defaults.whisperLanguage,
                        availableModels = emptyList(),
                        groqLLMModels = emptyList(),
                        isLoadingModels = false,
                        isLoadingGroqLLMModels = false,
                        modelsFetchError = null,
                        groqLLMModelsFetchError = null,
                        dictationHotkeyLabel = formatHotkey(defaults.hotkeyConfig.startHotkey),
                        commandHotkeyLabel = formatHotkey(defaults.hotkeyConfig.commandHotkey),
                        commandHotkeyEnabled = defaults.hotkeyConfig.commandHotkeyEnabled,
                        isCapturingDictationHotkey = false,
                        isCapturingCommandHotkey = false,
                        dictationHotkeyError = hotkeyValidation.dictationError,
                        commandHotkeyError = hotkeyValidation.commandError,
                        isResettingData = false,
                        saveMessage = "App data reset. OpenYap will walk you through onboarding again.",
                        selectedAudioDeviceId = null,
                    )
                }
                _effects.emit(SettingsEffect.ResetAppDataSucceeded)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isResettingData = false,
                        saveMessage = e.message ?: "Failed to reset app data",
                    )
                }
            }
        }
    }

    private fun refreshDevices() {
        viewModelScope.launch { fetchDevices() }
    }

    private fun selectUseCase(useCase: PrimaryUseCase) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(primaryUseCase = useCase) }
            _state.update { it.copy(primaryUseCase = useCase) }
        }
    }

    private fun saveUseCaseContext(context: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(useCaseContext = context) }
            _state.update { it.copy(useCaseContext = context) }
        }
    }

    private fun selectWhisperLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(whisperLanguage = language) }
            _state.update { it.copy(whisperLanguage = language) }
        }
    }

    private suspend fun fetchDevices() {
        _state.update { it.copy(isLoadingDevices = true, devicesFetchError = null) }
        try {
            val devices = audioRecorder.listDevices()
            _state.update { it.copy(audioDevices = devices, isLoadingDevices = false) }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingDevices = false,
                    devicesFetchError = e.message ?: "Failed to enumerate audio devices",
                )
            }
        }
    }

    private fun selectAudioDevice(deviceId: String?) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(audioDeviceId = deviceId) }
            _state.update { it.copy(selectedAudioDeviceId = deviceId) }
        }
    }

    private fun validateHotkeys(config: HotkeyConfig): HotkeyValidationState {
        val commandError = config.commandHotkeyValidationError()
        val dictationError = if (config.hasCommandHotkeyConflict()) {
            "Dictation hotkey must be different from the command hotkey."
        } else {
            null
        }
        return HotkeyValidationState(
            dictationError = dictationError,
            commandError = commandError,
        )
    }

    private fun formatHotkey(binding: HotkeyBinding?): String {
        return binding?.let(hotkeyDisplayFormatter::format) ?: "Not set"
    }
}
