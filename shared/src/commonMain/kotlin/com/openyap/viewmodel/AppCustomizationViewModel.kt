package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppCustomizationUiState(
    val appTones: Map<String, String> = emptyMap(),
    val appPrompts: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
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
            try {
                updateStateFromRepository()
            } catch (e: Exception) {
                handleLoadFailure(e, "Failed to load app customizations")
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            try {
                settingsRepository.clearAppCustomizations()
                _state.value = AppCustomizationUiState()
            } catch (e: Exception) {
                handleMutationFailure(e, "Failed to reset app customizations")
            }
        }
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
        viewModelScope.launch {
            try {
                settingsRepository.saveAppTone(app, tone)
                updateStateFromRepository()
            } catch (e: Exception) {
                handleMutationFailure(e, "Failed to save app tone")
            }
        }
    }

    private fun savePrompt(app: String, prompt: String) {
        viewModelScope.launch {
            try {
                settingsRepository.saveAppPrompt(app, prompt)
                updateStateFromRepository()
            } catch (e: Exception) {
                handleMutationFailure(e, "Failed to save app prompt")
            }
        }
    }

    private fun removeApp(app: String) {
        viewModelScope.launch {
            try {
                settingsRepository.removeAppCustomization(app)
                updateStateFromRepository()
            } catch (e: Exception) {
                handleMutationFailure(e, "Failed to remove app customization")
            }
        }
    }

    private suspend fun updateStateFromRepository() {
        val tones = settingsRepository.loadAllAppTones()
        val prompts = settingsRepository.loadAllAppPrompts()
        _state.update {
            it.copy(
                appTones = tones,
                appPrompts = prompts,
                errorMessage = null,
            )
        }
    }

    private suspend fun handleMutationFailure(error: Throwable, fallbackMessage: String) {
        rethrowIfCancelled(error)
        try {
            updateStateFromRepository()
        } catch (refreshError: Throwable) {
            rethrowIfCancelled(refreshError)
        }
        _state.update {
            it.copy(errorMessage = error.message ?: fallbackMessage)
        }
    }

    private fun handleLoadFailure(error: Throwable, fallbackMessage: String) {
        rethrowIfCancelled(error)
        _state.update { it.copy(errorMessage = error.message ?: fallbackMessage) }
    }

    private fun rethrowIfCancelled(error: Throwable) {
        if (error is CancellationException) throw error
    }
}
