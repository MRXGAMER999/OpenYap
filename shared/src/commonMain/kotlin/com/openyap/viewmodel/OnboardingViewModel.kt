package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.PermissionStatus
import com.openyap.model.PrimaryUseCase
import com.openyap.platform.PermissionManager
import com.openyap.repository.SettingsRepository
import com.openyap.service.GeminiClient
import com.openyap.service.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val micPermission: PermissionStatus = PermissionStatus.UNKNOWN,
    val apiKey: String = "",
    val isLoaded: Boolean = false,
    val isComplete: Boolean = false,
    val currentStep: Int = 0,
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL,
    val useCaseContext: String = "",
)

sealed interface OnboardingEvent {
    data object CheckMicPermission : OnboardingEvent
    data object OpenMicSettings : OnboardingEvent
    data class SaveApiKey(val key: String) : OnboardingEvent
    data class SelectModel(val modelId: String) : OnboardingEvent
    data object RetryModelFetch : OnboardingEvent
    data class SelectUseCase(val useCase: PrimaryUseCase) : OnboardingEvent
    data class SaveUseCaseContext(val context: String) : OnboardingEvent
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
        refresh()
    }

    fun resetState() {
        _state.value = OnboardingUiState()
    }

    fun refresh() {
        viewModelScope.launch {
            val micPerm = permissionManager.checkMicrophonePermission()
            val apiKey = settingsRepository.loadApiKey() ?: ""
            val settings = settingsRepository.loadSettings()
            _state.update {
                it.copy(
                    micPermission = micPerm,
                    apiKey = apiKey,
                    isLoaded = true,
                    isComplete = settings.onboardingCompleted,
                    currentStep = computeStep(micPerm, apiKey),
                    selectedModel = settings.geminiModel,
                    primaryUseCase = settings.primaryUseCase,
                    useCaseContext = settings.useCaseContext,
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
            is OnboardingEvent.RetryModelFetch -> retryModelFetch()
            is OnboardingEvent.SelectUseCase -> selectUseCase(event.useCase)
            is OnboardingEvent.SaveUseCaseContext -> saveUseCaseContext(event.context)
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

    private fun retryModelFetch() {
        val apiKey = _state.value.apiKey
        if (apiKey.isBlank()) return
        viewModelScope.launch { fetchModels(apiKey) }
    }

    private suspend fun fetchModels(apiKey: String) {
        _state.update { it.copy(isLoadingModels = true, modelsFetchError = null) }
        try {
            val models = geminiClient.listModels(apiKey)
            _state.update { it.copy(availableModels = models, isLoadingModels = false) }
            if (models.isNotEmpty() && models.none { it.id == _state.value.selectedModel }) {
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
