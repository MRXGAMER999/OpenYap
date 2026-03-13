package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.HotkeyBinding
import com.openyap.platform.HotkeyDisplayFormatter
import com.openyap.platform.HotkeyManager
import com.openyap.repository.SettingsRepository
import com.openyap.service.GeminiClient
import com.openyap.service.GeminiModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val geminiModel: String = "gemini-2.0-flash",
    val genZEnabled: Boolean = false,
    val phraseExpansionEnabled: Boolean = true,
    val audioFeedbackEnabled: Boolean = true,
    val startMinimized: Boolean = false,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val availableModels: List<GeminiModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val hotkeyLabel: String = "Ctrl+Shift+R",
    val isCapturingHotkey: Boolean = false,
    val hotkeyError: String? = null,
    val appVersion: String = "",
)

sealed interface SettingsEvent {
    data class SaveApiKey(val key: String) : SettingsEvent
    data class SelectModel(val modelId: String) : SettingsEvent
    data class ToggleGenZ(val enabled: Boolean) : SettingsEvent
    data class TogglePhraseExpansion(val enabled: Boolean) : SettingsEvent
    data class ToggleAudioFeedback(val enabled: Boolean) : SettingsEvent
    data class ToggleStartMinimized(val enabled: Boolean) : SettingsEvent
    data object RefreshModels : SettingsEvent
    data object CaptureHotkey : SettingsEvent
    data object ClearHotkeyMessage : SettingsEvent
    data object DismissSaveMessage : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient,
    private val hotkeyManager: HotkeyManager,
    private val hotkeyDisplayFormatter: HotkeyDisplayFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apiKey = settingsRepository.loadApiKey() ?: ""
            val settings = settingsRepository.loadSettings()
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
                    apiKey = apiKey,
                    geminiModel = settings.geminiModel,
                    genZEnabled = settings.genZEnabled,
                    phraseExpansionEnabled = settings.phraseExpansionEnabled,
                    audioFeedbackEnabled = settings.audioFeedbackEnabled,
                    startMinimized = settings.startMinimized,
                    hotkeyLabel = formatHotkey(settings.hotkeyConfig.startHotkey),
                    appVersion = version,
                )
            }
            if (apiKey.isNotBlank()) fetchModels(apiKey)
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SaveApiKey -> saveApiKey(event.key)
            is SettingsEvent.SelectModel -> selectModel(event.modelId)
            is SettingsEvent.ToggleGenZ -> toggleGenZ(event.enabled)
            is SettingsEvent.TogglePhraseExpansion -> togglePhraseExpansion(event.enabled)
            is SettingsEvent.ToggleAudioFeedback -> toggleAudioFeedback(event.enabled)
            is SettingsEvent.ToggleStartMinimized -> toggleStartMinimized(event.enabled)
            is SettingsEvent.RefreshModels -> refreshModels()
            is SettingsEvent.CaptureHotkey -> captureHotkey()
            is SettingsEvent.ClearHotkeyMessage -> _state.update { it.copy(hotkeyError = null) }
            is SettingsEvent.DismissSaveMessage -> _state.update { it.copy(saveMessage = null) }
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

    private fun toggleStartMinimized(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(startMinimized = enabled))
            _state.update { it.copy(startMinimized = enabled) }
        }
    }

    private fun formatHotkey(binding: HotkeyBinding?): String {
        return binding?.let(hotkeyDisplayFormatter::format) ?: "Not set"
    }
}
