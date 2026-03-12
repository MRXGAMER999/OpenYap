package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.PermissionStatus
import com.openyap.platform.PermissionManager
import com.openyap.repository.SettingsRepository
import com.openyap.service.GeminiClient
import com.openyap.service.GeminiModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val micPermission: PermissionStatus = PermissionStatus.UNKNOWN,
    val apiKey: String = "",
    val isComplete: Boolean = false,
    val currentStep: Int = 0,
    val availableModels: List<GeminiModelInfo> = emptyList(),
    val selectedModel: String = "gemini-2.0-flash",
    val isLoadingModels: Boolean = false,
)

sealed interface OnboardingEvent {
    data object CheckMicPermission : OnboardingEvent
    data object OpenMicSettings : OnboardingEvent
    data class SaveApiKey(val key: String) : OnboardingEvent
    data class SelectModel(val modelId: String) : OnboardingEvent
    data object CompleteOnboarding : OnboardingEvent
}

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissionManager: PermissionManager,
    private val geminiClient: GeminiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val micPerm = permissionManager.checkMicrophonePermission()
            val apiKey = settingsRepository.loadApiKey() ?: ""
            val settings = settingsRepository.loadSettings()
            _state.update {
                it.copy(
                    micPermission = micPerm,
                    apiKey = apiKey,
                    isComplete = settings.onboardingCompleted,
                    currentStep = computeStep(micPerm, apiKey),
                    selectedModel = settings.geminiModel,
                )
            }
            if (apiKey.isNotBlank()) fetchModels(apiKey)
        }
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.CheckMicPermission -> checkMic()
            is OnboardingEvent.OpenMicSettings -> permissionManager.openMicrophoneSettings()
            is OnboardingEvent.SaveApiKey -> saveApiKey(event.key)
            is OnboardingEvent.SelectModel -> selectModel(event.modelId)
            is OnboardingEvent.CompleteOnboarding -> completeOnboarding()
        }
    }

    private fun checkMic() {
        viewModelScope.launch {
            val perm = permissionManager.checkMicrophonePermission()
            _state.update {
                it.copy(micPermission = perm, currentStep = computeStep(perm, it.apiKey))
            }
        }
    }

    private fun saveApiKey(key: String) {
        viewModelScope.launch {
            val trimmed = key.trim()
            settingsRepository.saveApiKey(trimmed)
            _state.update {
                it.copy(apiKey = trimmed, currentStep = computeStep(it.micPermission, trimmed))
            }
            if (trimmed.isNotBlank()) fetchModels(trimmed)
        }
    }

    private fun selectModel(modelId: String) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(geminiModel = modelId))
            _state.update { it.copy(selectedModel = modelId) }
        }
    }

    private suspend fun fetchModels(apiKey: String) {
        _state.update { it.copy(isLoadingModels = true) }
        try {
            val models = geminiClient.listModels(apiKey)
            _state.update { it.copy(availableModels = models, isLoadingModels = false) }
            if (models.isNotEmpty() && models.none { it.id == _state.value.selectedModel }) {
                selectModel(models.first().id)
            }
        } catch (_: Exception) {
            _state.update { it.copy(isLoadingModels = false) }
        }
    }

    private fun computeStep(micPerm: PermissionStatus, apiKey: String): Int = when {
        micPerm != PermissionStatus.GRANTED -> 0
        apiKey.isBlank() -> 1
        else -> 2
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            settingsRepository.saveSettings(settings.copy(onboardingCompleted = true))
            _state.update { it.copy(isComplete = true) }
        }
    }
}
