package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.HotkeyEvent
import com.openyap.model.OverlayState
import com.openyap.model.PermissionStatus
import com.openyap.model.RecordingEntry
import com.openyap.model.RecordingState
import com.openyap.model.TranscriptionProvider
import com.openyap.platform.AudioRecorder
import com.openyap.platform.AudioRecorderDiagnostics
import com.openyap.platform.ForegroundAppDetector
import com.openyap.platform.HotkeyManager
import com.openyap.platform.OverlayController
import com.openyap.platform.PasteAutomation
import com.openyap.platform.PermissionManager
import com.openyap.repository.DictionaryRepository
import com.openyap.repository.HistoryRepository
import com.openyap.repository.SettingsRepository
import com.openyap.repository.UserProfileRepository
import com.openyap.service.DictionaryEngine
import com.openyap.service.GeminiClient
import com.openyap.service.PhraseExpansionEngine
import com.openyap.service.PromptBuilder
import com.openyap.service.TranscriptionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class RecordingUiState(
    val recordingState: RecordingState = RecordingState.Idle,
    val amplitude: Float = 0f,
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

/**
 * Callback interface for audio feedback — defined in commonMain so RecordingViewModel
 * stays platform-agnostic. The JVM implementation delegates to AudioFeedbackService.
 */
interface AudioFeedbackPlayer {
    fun playStart()
    fun playStop()
    fun playTooShort()
    fun playError()
}

class NoOpAudioFeedbackPlayer : AudioFeedbackPlayer {
    override fun playStart() {}
    override fun playStop() {}
    override fun playTooShort() {}
    override fun playError() {}
}

class RecordingViewModel(
    private val hotkeyManager: HotkeyManager,
    private val audioRecorder: AudioRecorder,
    private val geminiClient: GeminiClient,
    private val groqWhisperClient: TranscriptionService,
    private val pasteAutomation: PasteAutomation,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val permissionManager: PermissionManager,
    private val dictionaryEngine: DictionaryEngine,
    private val overlayController: OverlayController,
    private val audioFeedbackPlayer: AudioFeedbackPlayer = NoOpAudioFeedbackPlayer(),
    private val audioMimeType: String,
    audioFileExtension: String,
    private val tempDirProvider: () -> String,
    private val fileReader: (String) -> ByteArray,
    private val fileDeleter: (String) -> Unit,
) : ViewModel() {

    companion object {
        private const val MIN_DURATION_SECONDS = 0.5
    }

    private val audioFileExtension = if (audioFileExtension.startsWith(".")) {
        audioFileExtension
    } else {
        ".${audioFileExtension}"
    }

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<RecordingEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<RecordingEffect> = _effects.asSharedFlow()

    @Volatile
    private var processingGeneration = 0
    private var durationJob: Job? = null
    private var currentRecordingPath: String? = null

    @Volatile
    private var currentProcessingPath: String? = null
    private var recordingStartedAt: TimeMark? = null

    init {
        viewModelScope.launch {
            hotkeyManager.hotkeyEvents.collect { event ->
                when (event) {
                    is HotkeyEvent.HoldDown -> startRecording()
                    is HotkeyEvent.HoldUp -> stopRecording()
                    is HotkeyEvent.CancelRecording -> cancelRecording()
                    is HotkeyEvent.ToggleRecording -> toggleRecording()
                    is HotkeyEvent.StartRecording -> startRecording()
                    is HotkeyEvent.StopRecording -> stopRecording()
                }
            }
        }

        viewModelScope.launch {
            audioRecorder.amplitudeFlow.collect { amplitude ->
                _state.update { it.copy(amplitude = amplitude) }
                overlayController.updateLevel(amplitude)
            }
        }

        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            val micPerm = permissionManager.checkMicrophonePermission()
            _state.update {
                it.copy(
                    hasApiKey = hasRequiredApiKeys(settings.transcriptionProvider),
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
        } else if (
            current is RecordingState.Idle ||
            current is RecordingState.Error ||
            current is RecordingState.Success
        ) {
            startRecording()
        }
    }

    private suspend fun startRecording() {
        val currentState = _state.value.recordingState

        if (currentState is RecordingState.Processing) {
            overlayController.flashProcessing()
            return
        }

        if (currentState !is RecordingState.Idle &&
            currentState !is RecordingState.Error &&
            currentState !is RecordingState.Success
        ) return

        val settings = settingsRepository.loadSettings()

        val geminiApiKey = settingsRepository.loadApiKey()
        val groqApiKey = settingsRepository.loadGroqApiKey()
        val apiKeyError = when (settings.transcriptionProvider) {
            TranscriptionProvider.GEMINI -> if (geminiApiKey.isNullOrBlank()) "Please set your Gemini API key in Settings." else null
            TranscriptionProvider.GROQ_WHISPER -> if (groqApiKey.isNullOrBlank()) "Please set your Groq API key in Settings." else null
            TranscriptionProvider.GROQ_WHISPER_GEMINI -> when {
                groqApiKey.isNullOrBlank() && geminiApiKey.isNullOrBlank() -> "Please set both Groq and Gemini API keys in Settings."
                groqApiKey.isNullOrBlank() -> "Please set your Groq API key in Settings."
                geminiApiKey.isNullOrBlank() -> "Please set your Gemini API key in Settings."
                else -> null
            }
        }
        if (apiKeyError != null) {
            _state.update { it.copy(error = apiKeyError) }
            if (settings.audioFeedbackEnabled) {
                audioFeedbackPlayer.playError()
            }
            return
        }

        if (!audioRecorder.hasPermission()) {
            _state.update { it.copy(error = "Microphone permission denied.") }
            if (settings.audioFeedbackEnabled) {
                audioFeedbackPlayer.playError()
            }
            return
        }
        if (settings.audioFeedbackEnabled) {
            audioFeedbackPlayer.playStart()
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()
        val path = "${tempDirProvider()}/openyap_$timestamp$audioFileExtension"
        currentRecordingPath = path
        recordingStartedAt = TimeSource.Monotonic.markNow()

        try {
            audioRecorder.startRecording(path, settings.audioDeviceId)
        } catch (e: Exception) {
            currentRecordingPath = null
            recordingStartedAt = null
            _state.update {
                it.copy(
                    recordingState = RecordingState.Error(e.message ?: "Failed to start recording"),
                    error = e.message,
                )
            }
            if (settings.audioFeedbackEnabled) {
                audioFeedbackPlayer.playError()
            }
            return
        }

        _state.update {
            it.copy(
                recordingState = RecordingState.Recording(),
                error = null,
                amplitude = 0f,
            )
        }

        overlayController.show()

        durationJob = viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                _state.update {
                    val rs = it.recordingState
                    if (rs is RecordingState.Recording) {
                        it.copy(recordingState = rs.copy(durationSeconds = seconds))
                    } else it
                }
                overlayController.updateDuration(seconds)
            }
        }
    }

    private suspend fun stopRecording() {
        if (_state.value.recordingState !is RecordingState.Recording) return

        durationJob?.cancel()
        durationJob = null

        val elapsedSeconds = recordingStartedAt
            ?.elapsedNow()
            ?.inWholeMilliseconds
            ?.toDouble()
            ?.div(1000)
            ?: (_state.value.recordingState as? RecordingState.Recording)?.durationSeconds?.toDouble()
            ?: 0.0
        recordingStartedAt = null

        if (elapsedSeconds < MIN_DURATION_SECONDS) {
            runCatching { audioRecorder.stopRecording() }

            val settings = settingsRepository.loadSettings()
            if (settings.audioFeedbackEnabled) {
                audioFeedbackPlayer.playTooShort()
            }

            _state.update {
                it.copy(
                    recordingState = RecordingState.Error("Recording too short. Please speak for at least 0.5 seconds."),
                    error = "Recording too short.",
                )
            }
            overlayController.dismiss()
            currentRecordingPath?.let { deleteFile(it) }
            currentRecordingPath = null
            return
        }

        val settings = settingsRepository.loadSettings()
        if (settings.audioFeedbackEnabled) {
            audioFeedbackPlayer.playStop()
        }

        val duration = elapsedSeconds.roundToInt().coerceAtLeast(1)
        val path = try {
            audioRecorder.stopRecording()
        } catch (e: Exception) {
            currentRecordingPath?.let { deleteFile(it) }
            currentRecordingPath = null
            currentProcessingPath = null
            overlayController.dismiss()
            _state.update {
                it.copy(
                    recordingState = RecordingState.Error(e.message ?: "Recording failed"),
                    error = e.message,
                )
            }
            if (settings.audioFeedbackEnabled) {
                audioFeedbackPlayer.playError()
            }
            _effects.emit(RecordingEffect.ShowError(e.message ?: "Recording failed"))
            return
        }
        currentRecordingPath = null
        currentProcessingPath = path
        val generation = ++processingGeneration
        val recorderWarning = (audioRecorder as? AudioRecorderDiagnostics)?.consumeWarning()

        _state.update { it.copy(recordingState = RecordingState.Processing) }
        overlayController.updateState(OverlayState.PROCESSING)
        recorderWarning?.let(overlayController::flashMessage)

        viewModelScope.launch {
            try {
                val targetApp = foregroundAppDetector.getForegroundAppName()
                val geminiApiKey = settingsRepository.loadApiKey()
                val groqApiKey = settingsRepository.loadGroqApiKey()
                val audioBytes = readFileBytes(path)

                if (audioBytes.size < 100) {
                    throw IllegalStateException("Recording appears to be empty or corrupted. Please try again.")
                }

                val appKey = targetApp ?: "Default"
                val tone = settingsRepository.loadAppTone(appKey) ?: "normal"
                val customPrompt = settingsRepository.loadAppPrompt(appKey)
                val systemPrompt = PromptBuilder.build(
                    tone = tone,
                    targetApp = targetApp,
                    customPrompt = customPrompt,
                    genZ = settings.genZEnabled,
                )

                val response = when (settings.transcriptionProvider) {
                    TranscriptionProvider.GEMINI -> {
                        geminiClient.transcribe(
                            audioBytes = audioBytes,
                            mimeType = audioMimeType,
                            systemPrompt = systemPrompt,
                            apiKey = geminiApiKey!!,
                            model = settings.geminiModel,
                        )
                    }

                    TranscriptionProvider.GROQ_WHISPER -> {
                        groqWhisperClient.transcribe(
                            audioBytes = audioBytes,
                            mimeType = audioMimeType,
                            systemPrompt = "", // ignored by Whisper
                            apiKey = groqApiKey!!,
                            model = settings.groqModel,
                        )
                    }

                    TranscriptionProvider.GROQ_WHISPER_GEMINI -> {
                        val transcript = groqWhisperClient.transcribe(
                            audioBytes = audioBytes,
                            mimeType = audioMimeType,
                            systemPrompt = "", // ignored by Whisper
                            apiKey = groqApiKey!!,
                            model = settings.groqModel,
                        )
                        geminiClient.rewriteText(
                            text = transcript,
                            systemPrompt = systemPrompt,
                            apiKey = geminiApiKey!!,
                            model = settings.geminiModel,
                        )
                    }
                }

                val modelUsed = when (settings.transcriptionProvider) {
                    TranscriptionProvider.GEMINI -> settings.geminiModel
                    TranscriptionProvider.GROQ_WHISPER -> settings.groqModel
                    TranscriptionProvider.GROQ_WHISPER_GEMINI -> "${settings.groqModel} -> ${settings.geminiModel}"
                }

                if (generation != processingGeneration) {
                    deleteFile(path)
                    return@launch
                }

                val profile = userProfileRepository.loadProfile()
                val dictEntries = if (settings.dictionaryEnabled) {
                    dictionaryRepository.loadEntries()
                } else {
                    emptyList()
                }
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

                pasteAutomation.pasteText(expandedResponse, restoreClipboard = false)

                if (generation != processingGeneration) {
                    deleteFile(path)
                    return@launch
                }

                historyRepository.addEntry(
                    RecordingEntry(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        recordedAt = Clock.System.now(),
                        durationSeconds = duration,
                        response = expandedResponse,
                        targetApp = targetApp ?: "",
                        model = modelUsed,
                    )
                )

                _state.update {
                    it.copy(
                        recordingState = RecordingState.Success(
                            expandedResponse,
                            expandedResponse.length
                        ),
                        lastResultText = expandedResponse,
                    )
                }
                _effects.emit(RecordingEffect.PasteSuccess)

                overlayController.updateState(OverlayState.SUCCESS)

                if (settings.dictionaryEnabled) {
                    try {
                        dictionaryEngine.ingestObservedText(expandedResponse)
                    } catch (_: Exception) {
                    }
                }

                currentProcessingPath = null
                delay(2000)
                overlayController.dismiss()

                delay(1000)
                _state.update {
                    if (it.recordingState is RecordingState.Success) {
                        it.copy(recordingState = RecordingState.Idle)
                    } else it
                }

                deleteFile(path)
            } catch (e: Exception) {
                currentProcessingPath = null
                overlayController.dismiss()
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
                recordingStartedAt = null
                runCatching { audioRecorder.stopRecording() }
                currentRecordingPath?.let { deleteFile(it) }
                currentRecordingPath = null
                overlayController.dismiss()
                _state.update { it.copy(recordingState = RecordingState.Idle) }
            }

            is RecordingState.Processing -> {
                processingGeneration++
                currentProcessingPath?.let { deleteFile(it) }
                currentProcessingPath = null
                overlayController.dismiss()
                _state.update { it.copy(recordingState = RecordingState.Idle) }
            }

            else -> {}
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            val micPerm = permissionManager.checkMicrophonePermission()
            _state.update {
                it.copy(
                    hasApiKey = hasRequiredApiKeys(settings.transcriptionProvider),
                    hasMicPermission = micPerm == PermissionStatus.GRANTED,
                )
            }
        }
    }

    private suspend fun hasRequiredApiKeys(provider: TranscriptionProvider): Boolean {
        val geminiApiKey = settingsRepository.loadApiKey()
        val groqApiKey = settingsRepository.loadGroqApiKey()
        return when (provider) {
            TranscriptionProvider.GEMINI -> !geminiApiKey.isNullOrBlank()
            TranscriptionProvider.GROQ_WHISPER -> !groqApiKey.isNullOrBlank()
            TranscriptionProvider.GROQ_WHISPER_GEMINI -> !geminiApiKey.isNullOrBlank() && !groqApiKey.isNullOrBlank()
        }
    }

    private fun readFileBytes(path: String): ByteArray = fileReader(path)

    private fun deleteFile(path: String) {
        try {
            fileDeleter(path)
        } catch (_: Exception) {
        }
    }
}
