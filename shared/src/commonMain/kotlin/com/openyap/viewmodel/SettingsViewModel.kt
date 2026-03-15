package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.AppSettings
import com.openyap.model.AudioDevice
import com.openyap.model.HotkeyBinding
import com.openyap.model.PrimaryUseCase
import com.openyap.model.TranscriptionProvider
import com.openyap.platform.AudioRecorder
import com.openyap.platform.HotkeyDisplayFormatter
import com.openyap.platform.HotkeyManager
import com.openyap.platform.NoOpStartupManager
import com.openyap.platform.StartupManager
import com.openyap.repository.SettingsRepository
import com.openyap.service.GeminiClient
import com.openyap.service.GroqWhisperClient
import com.openyap.service.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val transcriptionProvider: TranscriptionProvider = TranscriptionProvider.GEMINI,
    val apiKey: String = "",
    val groqApiKey: String = "",
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
    val groqModel: String = "whisper-large-v3",
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
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val hotkeyLabel: String = "Ctrl+Shift+R",
    val isCapturingHotkey: Boolean = false,
    val hotkeyError: String? = null,
    val appVersion: String = "",
    val audioDevices: List<AudioDevice> = emptyList(),
    val selectedAudioDeviceId: String? = null,
    val isLoadingDevices: Boolean = false,
    val devicesFetchError: String? = null,
    val primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL,
    val useCaseContext: String = "",
)

sealed interface SettingsEvent {
    data class SelectProvider(val provider: TranscriptionProvider) : SettingsEvent
    data class SaveApiKey(val key: String) : SettingsEvent
    data class SaveGroqApiKey(val key: String) : SettingsEvent
    data class SelectModel(val modelId: String) : SettingsEvent
    data class SelectGroqModel(val modelId: String) : SettingsEvent
    data class ToggleGenZ(val enabled: Boolean) : SettingsEvent
    data class TogglePhraseExpansion(val enabled: Boolean) : SettingsEvent
    data class ToggleDictionary(val enabled: Boolean) : SettingsEvent
    data class ToggleAudioFeedback(val enabled: Boolean) : SettingsEvent
    data class SetSoundFeedbackVolume(val volume: Float) : SettingsEvent
    data class ToggleStartMinimized(val enabled: Boolean) : SettingsEvent
    data class ToggleLaunchOnStartup(val enabled: Boolean) : SettingsEvent
    data object ResetAppData : SettingsEvent
    data object RefreshModels : SettingsEvent
    data class SelectAudioDevice(val deviceId: String?) : SettingsEvent
    data object RefreshDevices : SettingsEvent
    data object CaptureHotkey : SettingsEvent
    data object ClearHotkeyMessage : SettingsEvent
    data object DismissSaveMessage : SettingsEvent
    data class SelectUseCase(val useCase: PrimaryUseCase) : SettingsEvent
    data class SaveUseCaseContext(val context: String) : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient,
    private val groqWhisperClient: GroqWhisperClient,
    private val hotkeyManager: HotkeyManager,
    private val hotkeyDisplayFormatter: HotkeyDisplayFormatter,
    private val audioRecorder: AudioRecorder,
    private val startupManager: StartupManager = NoOpStartupManager(),
    private val resetAppDataAction: suspend () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
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
            _state.update {
                it.copy(
                    transcriptionProvider = settings.transcriptionProvider,
                    apiKey = apiKey,
                    groqApiKey = groqApiKey,
                    geminiModel = settings.geminiModel,
                    groqModel = settings.groqModel,
                    genZEnabled = settings.genZEnabled,
                    phraseExpansionEnabled = settings.phraseExpansionEnabled,
                    dictionaryEnabled = settings.dictionaryEnabled,
                    audioFeedbackEnabled = settings.audioFeedbackEnabled,
                    soundFeedbackVolume = settings.soundFeedbackVolume,
                    startMinimized = settings.startMinimized,
                    launchOnStartup = launchOnStartup,
                    startupSupported = startupManager.isSupported,
                    hotkeyLabel = formatHotkey(settings.hotkeyConfig.startHotkey),
                    appVersion = version,
                    selectedAudioDeviceId = settings.audioDeviceId,
                    primaryUseCase = settings.primaryUseCase,
                    useCaseContext = settings.useCaseContext,
                )
            }
            if (apiKey.isNotBlank()) fetchModels(apiKey)
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
            is SettingsEvent.ToggleGenZ -> toggleGenZ(event.enabled)
            is SettingsEvent.TogglePhraseExpansion -> togglePhraseExpansion(event.enabled)
            is SettingsEvent.ToggleDictionary -> toggleDictionary(event.enabled)
            is SettingsEvent.ToggleAudioFeedback -> toggleAudioFeedback(event.enabled)
            is SettingsEvent.SetSoundFeedbackVolume -> setSoundFeedbackVolume(event.volume)
            is SettingsEvent.ToggleStartMinimized -> toggleStartMinimized(event.enabled)
            is SettingsEvent.ToggleLaunchOnStartup -> toggleLaunchOnStartup(event.enabled)
            is SettingsEvent.ResetAppData -> resetAppData()
            is SettingsEvent.RefreshModels -> refreshModels()
            is SettingsEvent.CaptureHotkey -> captureHotkey()
            is SettingsEvent.ClearHotkeyMessage -> _state.update { it.copy(hotkeyError = null) }
            is SettingsEvent.DismissSaveMessage -> _state.update { it.copy(saveMessage = null) }
            is SettingsEvent.SelectAudioDevice -> selectAudioDevice(event.deviceId)
            is SettingsEvent.RefreshDevices -> refreshDevices()
            is SettingsEvent.SelectUseCase -> selectUseCase(event.useCase)
            is SettingsEvent.SaveUseCaseContext -> saveUseCaseContext(event.context)
        }
    }

    private fun captureHotkey() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCapturingHotkey = true,
                    hotkeyError = null,
                    saveMessage = null
                )
            }
            try {
                val capture = hotkeyManager.captureNextHotkey()
                val binding = HotkeyBinding(
                    platformKeyCode = capture.platformKeyCode,
                    modifiers = capture.modifiers,
                )
                val settings = settingsRepository.loadSettings()
                val updatedSettings = settings.copy(
                    hotkeyConfig = settings.hotkeyConfig.copy(startHotkey = binding)
                )
                settingsRepository.saveSettings(updatedSettings)
                hotkeyManager.setConfig(updatedSettings.hotkeyConfig)
                _state.update {
                    it.copy(
                        hotkeyLabel = capture.displayLabel,
                        isCapturingHotkey = false,
                        saveMessage = "Hotkey updated to ${capture.displayLabel}",
                    )
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _state.update {
                    it.copy(
                        isCapturingHotkey = false,
                        hotkeyError = "Hotkey capture timed out. Please try again.",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isCapturingHotkey = false,
                        hotkeyError = e.message ?: "Failed to capture hotkey",
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
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(geminiModel = modelId))
            _state.update { it.copy(geminiModel = modelId) }
        }
    }

    private fun selectProvider(provider: TranscriptionProvider) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(transcriptionProvider = provider))
            _state.update { it.copy(transcriptionProvider = provider) }
            if (provider != TranscriptionProvider.GROQ_WHISPER && _state.value.apiKey.isNotBlank() && _state.value.availableModels.isEmpty()) {
                fetchModels(_state.value.apiKey)
            }
        }
    }

    private fun saveGroqApiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.saveGroqApiKey(trimmed)
            _state.update { it.copy(groqApiKey = trimmed, saveMessage = "Groq API key saved") }
        }
    }

    private fun selectGroqModel(modelId: String) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(groqModel = modelId))
            _state.update { it.copy(groqModel = modelId) }
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
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(genZEnabled = enabled))
            _state.update { it.copy(genZEnabled = enabled) }
        }
    }

    private fun togglePhraseExpansion(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(phraseExpansionEnabled = enabled))
            _state.update { it.copy(phraseExpansionEnabled = enabled) }
        }
    }

    private fun toggleAudioFeedback(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(audioFeedbackEnabled = enabled))
            _state.update { it.copy(audioFeedbackEnabled = enabled) }
        }
    }

    private fun setSoundFeedbackVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(soundFeedbackVolume = clamped))
            _state.update { it.copy(soundFeedbackVolume = clamped) }
        }
    }

    private fun toggleDictionary(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(dictionaryEnabled = enabled))
            _state.update { it.copy(dictionaryEnabled = enabled) }
        }
    }

    private fun toggleStartMinimized(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(startMinimized = enabled))
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
                val settings = settingsRepository.loadSettings()
                settingsRepository.saveSettings(settings.copy(launchOnStartup = enabled))
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
                resetAppDataAction.invoke()
                runCatching { startupManager.setEnabled(false) }
                hotkeyManager.setConfig(defaults.hotkeyConfig)
                _state.update {
                    it.copy(
                        transcriptionProvider = defaults.transcriptionProvider,
                        apiKey = "",
                        groqApiKey = "",
                        geminiModel = defaults.geminiModel,
                        groqModel = defaults.groqModel,
                        genZEnabled = defaults.genZEnabled,
                        phraseExpansionEnabled = defaults.phraseExpansionEnabled,
                        dictionaryEnabled = defaults.dictionaryEnabled,
                        audioFeedbackEnabled = defaults.audioFeedbackEnabled,
                        soundFeedbackVolume = defaults.soundFeedbackVolume,
                        startMinimized = defaults.startMinimized,
                        launchOnStartup = defaults.launchOnStartup,
                        primaryUseCase = defaults.primaryUseCase,
                        useCaseContext = defaults.useCaseContext,
                        availableModels = emptyList(),
                        isLoadingModels = false,
                        modelsFetchError = null,
                        hotkeyLabel = formatHotkey(defaults.hotkeyConfig.startHotkey),
                        isResettingData = false,
                        saveMessage = "App data reset. OpenYap will walk you through onboarding again.",
                        selectedAudioDeviceId = null,
                    )
                }
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
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(primaryUseCase = useCase))
            _state.update { it.copy(primaryUseCase = useCase) }
        }
    }

    private fun saveUseCaseContext(context: String) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(useCaseContext = context))
            _state.update { it.copy(useCaseContext = context) }
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
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(audioDeviceId = deviceId))
            _state.update { it.copy(selectedAudioDeviceId = deviceId) }
        }
    }

    private fun formatHotkey(binding: HotkeyBinding?): String {
        return binding?.let(hotkeyDisplayFormatter::format) ?: "Not set"
    }
}
