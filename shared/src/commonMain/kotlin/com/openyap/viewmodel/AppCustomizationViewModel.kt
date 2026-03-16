package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
