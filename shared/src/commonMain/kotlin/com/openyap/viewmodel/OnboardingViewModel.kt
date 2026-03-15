package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.PermissionStatus
import com.openyap.model.PrimaryUseCase
import com.openyap.platform.PermissionManager
import com.openyap.repository.SettingsRepository
import com.openyap.service.GeminiClient
import com.openyap.service.ModelInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OnboardingUiState(
    val micPermission: PermissionStatus = PermissionStatus.UNKNOWN,
    val micSkipped: Boolean = false,
    val apiKey: String = "",
    val isLoaded: Boolean = false,
    val isComplete: Boolean = false,
    val currentStep: Int = 0,
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val modelsFetchErrorId: Long = 0,
    val isValidatingKey: Boolean = false,
    val keyValidationSuccess: Boolean? = null,
    val primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL,
    val useCaseContext: String = "",
    val micSettingsUnavailable: Boolean = false,
) {
    val micStepComplete: Boolean
        get() = micPermission == PermissionStatus.GRANTED || micSkipped

    val apiKeyStepComplete: Boolean
        get() = apiKey.isNotBlank()

    val modelStepComplete: Boolean
        get() = selectedModel.isNotBlank()

    val useCaseStepComplete: Boolean
        get() = primaryUseCase != PrimaryUseCase.GENERAL || useCaseContext.isNotBlank()

    val completedStepCount: Int
        get() = listOf(micStepComplete, apiKeyStepComplete, modelStepComplete, useCaseStepComplete).count { it }

    val progress: Float
        get() = completedStepCount / 4f

    val canComplete: Boolean
        get() = micStepComplete && apiKeyStepComplete && modelStepComplete
}

sealed interface OnboardingEvent {
    data object CheckMicPermission : OnboardingEvent
    data object OpenMicSettings : OnboardingEvent
    data object SkipMicPermission : OnboardingEvent
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

    private var fetchJob: Job? = null
    private var onboardingJob: Job? = null
    private val apiKeyMutex = Mutex()
    private var errorCounter = 0L
    private var epoch = 0L

    private val eventChannel = Channel<OnboardingEvent>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            eventChannel.receiveAsFlow().collect { event ->
                processEvent(event)
            }
        }
        refresh()
    }

    fun resetState() {
        epoch++
        fetchJob?.cancel()
        fetchJob = null
        onboardingJob?.cancel()
        onboardingJob = null
        _state.value = OnboardingUiState(isLoaded = true)
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
                    currentStep = computeStep(micPerm, apiKey, false, settings.geminiModel),
                    selectedModel = settings.geminiModel,
                    primaryUseCase = settings.primaryUseCase,
                    useCaseContext = settings.useCaseContext,
                )
            }
            if (apiKey.isNotBlank()) fetchModels(apiKey)
        }
    }

    fun onEvent(event: OnboardingEvent) {
        eventChannel.trySend(event)
    }

    private suspend fun processEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.CheckMicPermission -> checkMic()
            is OnboardingEvent.OpenMicSettings -> openMicSettings()
            is OnboardingEvent.SkipMicPermission -> skipMic()
            is OnboardingEvent.SaveApiKey -> saveApiKey(event.key)
            is OnboardingEvent.SelectModel -> selectModel(event.modelId)
            is OnboardingEvent.RetryModelFetch -> retryModelFetch()
            is OnboardingEvent.SelectUseCase -> selectUseCase(event.useCase)
            is OnboardingEvent.SaveUseCaseContext -> saveUseCaseContext(event.context)
            is OnboardingEvent.CompleteOnboarding -> completeOnboarding()
        }
    }

    private suspend fun checkMic() {
        val perm = permissionManager.checkMicrophonePermission()
        _state.update {
            it.copy(
                micPermission = perm,
                currentStep = computeStep(perm, it.apiKey, it.micSkipped, it.selectedModel),
            )
        }
    }

    private fun openMicSettings() {
        val success = permissionManager.openMicrophoneSettings()
        if (!success) {
            _state.update { it.copy(micSettingsUnavailable = true) }
        }
    }

    private fun skipMic() {
        _state.update {
            it.copy(
                micSkipped = true,
                currentStep = computeStep(it.micPermission, it.apiKey, true, it.selectedModel),
            )
        }
    }

    private suspend fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        apiKeyMutex.withLock {
            _state.update { it.copy(isValidatingKey = true, keyValidationSuccess = null) }
            settingsRepository.saveApiKey(trimmed)
            _state.update {
                it.copy(
                    apiKey = trimmed,
                    currentStep = computeStep(it.micPermission, trimmed, it.micSkipped, it.selectedModel),
                )
            }
            fetchModels(trimmed)
            val success = _state.value.availableModels.isNotEmpty()
            _state.update { it.copy(isValidatingKey = false, keyValidationSuccess = success) }
        }
    }

    private suspend fun selectModel(modelId: String) {
        settingsRepository.updateSettings { it.copy(geminiModel = modelId) }
        _state.update {
            it.copy(
                selectedModel = modelId,
                currentStep = computeStep(it.micPermission, it.apiKey, it.micSkipped, modelId),
            )
        }
    }

    private suspend fun retryModelFetch() {
        val apiKey = _state.value.apiKey
        if (apiKey.isBlank()) return
        fetchModels(apiKey)
    }

    private suspend fun fetchModels(apiKey: String) {
        fetchJob?.cancel()
        val currentEpoch = epoch
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true, modelsFetchError = null) }
            try {
                val models = geminiClient.listModels(apiKey)
                if (currentEpoch != epoch) return@launch
                _state.update { it.copy(availableModels = models, isLoadingModels = false) }
                if (models.isNotEmpty() && models.none { it.id == _state.value.selectedModel }) {
                    settingsRepository.updateSettings { it.copy(geminiModel = models.first().id) }
                    _state.update {
                        it.copy(
                            selectedModel = models.first().id,
                            currentStep = computeStep(it.micPermission, it.apiKey, it.micSkipped, models.first().id),
                        )
                    }
                }
            } catch (e: Exception) {
                if (currentEpoch != epoch) return@launch
                val id = ++errorCounter
                _state.update {
                    it.copy(
                        isLoadingModels = false,
                        modelsFetchError = e.message ?: "Failed to fetch models",
                        modelsFetchErrorId = id,
                    )
                }
            }
        }
        fetchJob?.join()
    }

    private suspend fun selectUseCase(useCase: PrimaryUseCase) {
        settingsRepository.updateSettings { it.copy(primaryUseCase = useCase) }
        _state.update { it.copy(primaryUseCase = useCase) }
    }

    private suspend fun saveUseCaseContext(context: String) {
        settingsRepository.updateSettings { it.copy(useCaseContext = context) }
        _state.update { it.copy(useCaseContext = context) }
    }

    private fun computeStep(
        micPerm: PermissionStatus,
        apiKey: String,
        micSkipped: Boolean,
        selectedModel: String,
    ): Int = when {
        micPerm != PermissionStatus.GRANTED && !micSkipped -> 0
        apiKey.isBlank() -> 1
        selectedModel.isBlank() -> 2
        else -> 3
    }

    private fun completeOnboarding() {
        val currentEpoch = epoch
        onboardingJob?.cancel()
        onboardingJob = viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(onboardingCompleted = true) }
            if (currentEpoch == epoch) {
                _state.update { it.copy(isComplete = true) }
            }
        }
    }
}
