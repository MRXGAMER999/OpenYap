package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.*
import com.openyap.platform.*
import com.openyap.repository.*
import com.openyap.service.DictionaryEngine
import com.openyap.service.GeminiClient
import com.openyap.service.PhraseExpansionEngine
import com.openyap.service.PromptBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RecordingUiState(
    val recordingState: RecordingState = RecordingState.Idle,
    val amplitude: Float = 0f,
    val durationSeconds: Int = 0,
    val lastResultText: String? = null,
    val error: String? = null,
    val hasApiKey: Boolean = false,
    val hasMicPermission: Boolean = false,
)

sealed interface RecordingEffect {
    data class ShowError(val message: String) : RecordingEffect
    data object PasteSuccess : RecordingEffect
}

sealed interface RecordingEvent {
    data object ToggleRecording : RecordingEvent
    data object CancelRecording : RecordingEvent
    data object DismissError : RecordingEvent
    data object RefreshState : RecordingEvent
}

class RecordingViewModel(
    private val hotkeyManager: HotkeyManager,
    private val audioRecorder: AudioRecorder,
    private val geminiClient: GeminiClient,
    private val pasteAutomation: PasteAutomation,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val permissionManager: PermissionManager,
    private val dictionaryEngine: DictionaryEngine,
    private val tempDirProvider: () -> String,
    private val fileReader: (String) -> ByteArray,
    private val fileDeleter: (String) -> Unit,
) : ViewModel() {

    companion object {
        private const val MIN_DURATION_SECONDS = 0.5
    }

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<RecordingEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<RecordingEffect> = _effects.asSharedFlow()

    @Volatile
    private var processingGeneration = 0
    private var durationJob: Job? = null
    private var currentRecordingPath: String? = null

    init {
        viewModelScope.launch {
            hotkeyManager.hotkeyEvents.collect { event ->
                when (event) {
                    is HotkeyEvent.ToggleRecording -> toggleRecording()
                    is HotkeyEvent.StartRecording -> startRecording()
                    is HotkeyEvent.StopRecording -> stopRecording()
                    is HotkeyEvent.HoldDown -> startRecording()
                    is HotkeyEvent.HoldUp -> stopRecording()
                }
            }
        }

        viewModelScope.launch {
            audioRecorder.amplitudeFlow.collect { amplitude ->
                _state.update { it.copy(amplitude = amplitude) }
            }
        }

        viewModelScope.launch {
            val apiKey = settingsRepository.loadApiKey()
            val micPerm = permissionManager.checkMicrophonePermission()
            _state.update {
                it.copy(
                    hasApiKey = !apiKey.isNullOrBlank(),
                    hasMicPermission = micPerm == PermissionStatus.GRANTED,
                )
            }
        }
    }

    fun onEvent(event: RecordingEvent) {
        when (event) {
            is RecordingEvent.ToggleRecording -> viewModelScope.launch { toggleRecording() }
            is RecordingEvent.CancelRecording -> viewModelScope.launch { cancelRecording() }
            is RecordingEvent.DismissError -> _state.update { it.copy(error = null) }
            is RecordingEvent.RefreshState -> refreshPermissions()
        }
    }

    private suspend fun toggleRecording() {
        val current = _state.value.recordingState
        if (current is RecordingState.Recording) {
            stopRecording()
        } else if (current is RecordingState.Idle || current is RecordingState.Error) {
            startRecording()
        }
    }

    private suspend fun startRecording() {
        if (_state.value.recordingState !is RecordingState.Idle &&
            _state.value.recordingState !is RecordingState.Error &&
            _state.value.recordingState !is RecordingState.Success
        ) return

        val apiKey = settingsRepository.loadApiKey()
        if (apiKey.isNullOrBlank()) {
            _state.update { it.copy(error = "Please set your Gemini API key in Settings.") }
            return
        }

        if (!audioRecorder.hasPermission()) {
            _state.update { it.copy(error = "Microphone permission denied.") }
            return
        }

        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val path = "${tempDirProvider()}/openyap_$timestamp.wav"
        currentRecordingPath = path

        audioRecorder.startRecording(path)

        _state.update {
            it.copy(
                recordingState = RecordingState.Recording(),
                error = null,
                durationSeconds = 0,
                amplitude = 0f,
            )
        }

        durationJob = viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                _state.update {
                    val rs = it.recordingState
                    if (rs is RecordingState.Recording) {
                        it.copy(
                            recordingState = rs.copy(durationSeconds = seconds),
                            durationSeconds = seconds,
                        )
                    } else it
                }
            }
        }
    }

    private suspend fun stopRecording() {
        if (_state.value.recordingState !is RecordingState.Recording) return

        durationJob?.cancel()
        durationJob = null

        val duration = _state.value.durationSeconds

        if (duration < 1) {
            audioRecorder.stopRecording()
            _state.update {
                it.copy(
                    recordingState = RecordingState.Error("Recording too short. Please speak for at least 0.5 seconds."),
                    error = "Recording too short.",
                )
            }
            currentRecordingPath?.let { deleteFile(it) }
            currentRecordingPath = null
            return
        }

        val path = audioRecorder.stopRecording()
        val generation = ++processingGeneration

        _state.update { it.copy(recordingState = RecordingState.Processing) }

        viewModelScope.launch {
            try {
                val targetApp = foregroundAppDetector.getForegroundAppName()
                val settings = settingsRepository.loadSettings()
                val appKey = targetApp ?: "Default"
                val tone = settingsRepository.loadAppTone(appKey) ?: "normal"
                val customPrompt = settingsRepository.loadAppPrompt(appKey)
                val systemPrompt = PromptBuilder.build(
                    tone = tone,
                    targetApp = targetApp,
                    customPrompt = customPrompt,
                    genZ = settings.genZEnabled,
                )
                val apiKey = settingsRepository.loadApiKey()!!
                val audioBytes = readFileBytes(path)

                val response = geminiClient.processAudio(
                    audioBytes = audioBytes,
                    systemPrompt = systemPrompt,
                    apiKey = apiKey,
                    model = settings.geminiModel,
                )

                if (generation != processingGeneration) {
                    deleteFile(path)
                    return@launch
                }

                val profile = userProfileRepository.loadProfile()
                val dictEntries = dictionaryRepository.loadEntries()
                val expandedResponse = PhraseExpansionEngine.expandText(
                    text = response,
                    profile = profile,
                    dictionaryEntries = dictEntries,
                    enabled = settings.phraseExpansionEnabled,
                )

                if (generation != processingGeneration) {
                    deleteFile(path)
                    return@launch
                }

                pasteAutomation.pasteText(expandedResponse, restoreClipboard = true)

                if (generation != processingGeneration) {
                    deleteFile(path)
                    return@launch
                }

                historyRepository.addEntry(
                    RecordingEntry(
                        id = kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                        recordedAt = kotlin.time.Clock.System.now(),
                        durationSeconds = duration,
                        response = expandedResponse,
                        targetApp = targetApp ?: "",
                        model = settings.geminiModel,
                    )
                )

                _state.update {
                    it.copy(
                        recordingState = RecordingState.Success(expandedResponse, expandedResponse.length),
                        lastResultText = expandedResponse,
                    )
                }
                _effects.emit(RecordingEffect.PasteSuccess)

                try { dictionaryEngine.ingestObservedText(expandedResponse) } catch (_: Exception) {}

                delay(3000)
                _state.update {
                    if (it.recordingState is RecordingState.Success) {
                        it.copy(recordingState = RecordingState.Idle)
                    } else it
                }

                deleteFile(path)
            } catch (e: Exception) {
                if (generation == processingGeneration) {
                    _state.update {
                        it.copy(
                            recordingState = RecordingState.Error(e.message ?: "Processing failed"),
                            error = e.message,
                        )
                    }
                    _effects.emit(RecordingEffect.ShowError(e.message ?: "Processing failed"))
                }
                deleteFile(path)
            }
        }
    }

    private suspend fun cancelRecording() {
        val current = _state.value.recordingState
        when (current) {
            is RecordingState.Recording -> {
                durationJob?.cancel()
                durationJob = null
                audioRecorder.stopRecording()
                currentRecordingPath?.let { deleteFile(it) }
                currentRecordingPath = null
                _state.update { it.copy(recordingState = RecordingState.Idle) }
            }
            is RecordingState.Processing -> {
                processingGeneration++
                _state.update { it.copy(recordingState = RecordingState.Idle) }
            }
            else -> {}
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val apiKey = settingsRepository.loadApiKey()
            val micPerm = permissionManager.checkMicrophonePermission()
            _state.update {
                it.copy(
                    hasApiKey = !apiKey.isNullOrBlank(),
                    hasMicPermission = micPerm == PermissionStatus.GRANTED,
                )
            }
        }
    }

    private fun readFileBytes(path: String): ByteArray = fileReader(path)

    private fun deleteFile(path: String) {
        try { fileDeleter(path) } catch (_: Exception) {}
    }
}
