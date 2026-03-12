package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val availableModels: List<GeminiModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
)

sealed interface SettingsEvent {
    data class SaveApiKey(val key: String) : SettingsEvent
    data class SelectModel(val modelId: String) : SettingsEvent
    data class ToggleGenZ(val enabled: Boolean) : SettingsEvent
    data class TogglePhraseExpansion(val enabled: Boolean) : SettingsEvent
    data object RefreshModels : SettingsEvent
    data object DismissSaveMessage : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apiKey = settingsRepository.loadApiKey() ?: ""
            val settings = settingsRepository.loadSettings()
            _state.update {
                it.copy(
                    apiKey = apiKey,
                    geminiModel = settings.geminiModel,
                    genZEnabled = settings.genZEnabled,
                    phraseExpansionEnabled = settings.phraseExpansionEnabled,
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
            is SettingsEvent.RefreshModels -> refreshModels()
            is SettingsEvent.DismissSaveMessage -> _state.update { it.copy(saveMessage = null) }
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
}
