package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.AppSettings
import com.openyap.model.effectiveHotkeyConfig
import com.openyap.model.HotkeyEvent
import com.openyap.model.OverlayState
import com.openyap.model.PermissionStatus
import com.openyap.model.RecordingEntry
import com.openyap.model.RecordingState
import com.openyap.model.RecordingWorkflowType
import com.openyap.model.TranscriptionProvider
import com.openyap.platform.AudioRecorder
import com.openyap.platform.AudioRecorderDiagnostics
import com.openyap.platform.ClipboardSnapshotToken
import com.openyap.platform.FileOperations
import com.openyap.platform.ForegroundAppDetector
import com.openyap.platform.ForegroundWindowContext
import com.openyap.platform.HotkeyManager
import com.openyap.platform.OverlayController
import com.openyap.platform.PasteAutomation
import com.openyap.platform.PermissionManager
import com.openyap.platform.RecordingSensitivityPreset
import com.openyap.platform.SelectionCaptureResult
import com.openyap.repository.DictionaryRepository
import com.openyap.repository.HistoryRepository
import com.openyap.repository.SettingsRepository
import com.openyap.repository.UserProfileRepository
import com.openyap.service.DictionaryEngine
import com.openyap.service.GeminiClient
import com.openyap.service.GroqLLMClient
import com.openyap.service.PhraseExpansionEngine
import com.openyap.service.PromptBuilder
import com.openyap.service.TranscriptionService
import com.openyap.service.WhisperPromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
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

private sealed interface CorrectionOutcome {
    data class Applied(val text: String) : CorrectionOutcome
    data class FallbackRaw(val rawTranscript: String, val reason: String) : CorrectionOutcome
}

private sealed interface RecordingSession {
    val workflowType: RecordingWorkflowType

    data object Dictation : RecordingSession {
        override val workflowType = RecordingWorkflowType.DICTATION
    }

    data class Command(
        val selectedText: String,
        val clipboardSnapshotToken: ClipboardSnapshotToken,
        val sourceWindow: ForegroundWindowContext,
    ) : RecordingSession {
        override val workflowType = RecordingWorkflowType.COMMAND
    }
}

private class UserVisibleCommandFailure(message: String) : IllegalStateException(message)

private data class SessionContextEntry(
    val text: String,
    val capturedAt: Instant,
    val targetApp: String?,
)

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
    private val groqLLMClient: GroqLLMClient,
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
    private val fileOperations: FileOperations,
) : ViewModel() {

    companion object {
        private const val MIN_DURATION_SECONDS = 0.5
        private const val MAX_SESSION_CONTEXT_ENTRIES = 5
        private const val RECENT_APP_HISTORY_LIMIT = 5
        private const val MAX_CONTEXT_ITEMS_FOR_PROMPT = 5
        private const val MAX_CONTEXT_ITEM_CHARS = 400
        private const val MAX_TOTAL_CONTEXT_CHARS = 1800
        private const val MAX_WARNING_DETAIL_CHARS = 160
        private const val MAX_COMMAND_SELECTION_CHARS = 12_000
        private const val COMMAND_PROCESSING_MESSAGE = "Transforming selection..."
        private val SESSION_CONTEXT_IDLE_TIMEOUT = 30.minutes
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

    private val processingGeneration = AtomicInteger(0)
    private val recordingMutex = Mutex()
    @Volatile
    private var durationJob: Job? = null
    @Volatile
    private var processingJob: Job? = null
    @Volatile
    private var currentRecordingPath: String? = null

    @Volatile
    private var currentProcessingPath: String? = null
    private var recordingStartedAt: TimeMark? = null
    @Volatile
    private var activeSession: RecordingSession? = null
    private val sessionContextEntries = ArrayDeque<SessionContextEntry>()
    private var lastSuccessfulPasteAt: Instant? = null

    init {
        viewModelScope.launch {
            hotkeyManager.hotkeyEvents.collect { event ->
                when (event) {
                    is HotkeyEvent.DictationHoldDown -> startRecording(RecordingSession.Dictation)
                    is HotkeyEvent.DictationHoldUp -> stopRecording()
                    is HotkeyEvent.CommandHoldDown -> startRecording(null)
                    is HotkeyEvent.CommandHoldUp -> stopRecording()
                    is HotkeyEvent.CancelRecording -> cancelRecording()
                    is HotkeyEvent.ToggleRecording -> toggleRecording()
                    is HotkeyEvent.StartRecording -> startRecording(RecordingSession.Dictation)
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
            startRecording(RecordingSession.Dictation)
        }
    }

    private suspend fun startRecording(sessionOverride: RecordingSession?) {
        recordingMutex.withLock {
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
            TranscriptionProvider.GROQ_WHISPER_GROQ -> if (groqApiKey.isNullOrBlank()) "Please set your Groq API key in Settings." else null
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
        val session = sessionOverride ?: prepareCommandSession(settings) ?: return
        setActiveSession(session)

        if (settings.audioFeedbackEnabled) {
            audioFeedbackPlayer.playStart()
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()
        val path = "${fileOperations.tempDir()}/openyap_$timestamp$audioFileExtension"
        currentRecordingPath = path
        recordingStartedAt = TimeSource.Monotonic.markNow()

        try {
            audioRecorder.startRecording(
                outputPath = path,
                deviceId = settings.audioDeviceId,
                sensitivityPreset = settings.recordingSensitivityPreset(),
            )
        } catch (e: Exception) {
            currentRecordingPath = null
            recordingStartedAt = null
            setActiveSession(null)
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
    }

    private suspend fun stopRecording() {
        if (_state.value.recordingState !is RecordingState.Recording) return
        val session = activeSession ?: RecordingSession.Dictation

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
            setActiveSession(null)
            return
        }

        val settings = settingsRepository.loadSettings()

        val duration = elapsedSeconds.roundToInt().coerceAtLeast(1)
        val path = try {
            audioRecorder.stopRecording()
        } catch (e: Exception) {
            currentRecordingPath?.let { deleteFile(it) }
            currentRecordingPath = null
            currentProcessingPath = null
            setActiveSession(null)
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

        if (settings.audioFeedbackEnabled) {
            audioFeedbackPlayer.playStop()
        }

        currentRecordingPath = null
        currentProcessingPath = path
        val generation = processingGeneration.incrementAndGet()
        val recorderWarning = (audioRecorder as? AudioRecorderDiagnostics)?.consumeWarning()

        _state.update { it.copy(recordingState = RecordingState.Processing) }
        overlayController.updateState(OverlayState.PROCESSING)
        overlayController.updateProcessingMessage(
            if (session is RecordingSession.Command) COMMAND_PROCESSING_MESSAGE else null
        )
        recorderWarning?.let(overlayController::flashMessage)

        processingJob = viewModelScope.launch {
            try {
                val targetWindow = when (session) {
                    RecordingSession.Dictation -> foregroundAppDetector.getForegroundWindowContext()
                    is RecordingSession.Command -> session.sourceWindow
                }
                val targetApp = targetWindow.appName
                val windowTitle = targetWindow.windowTitle
                val geminiApiKey = settingsRepository.loadApiKey()
                val groqApiKey = settingsRepository.loadGroqApiKey()
                val audioBytes = readFileBytes(path)

                if (audioBytes.size < 100) {
                    throw IllegalStateException("Recording appears to be empty or corrupted. Please try again.")
                }

                val appKey = targetApp ?: "Default"
                val tone = settingsRepository.loadAppTone(appKey) ?: "normal"
                val customPrompt = settingsRepository.loadAppPrompt(appKey)
                val now = Clock.System.now()
                pruneExpiredSessionContext(now)
                val recentAppHistory = targetApp
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appName ->
                        boundedContextForPrompt(
                            historyRepository.loadRecentEntriesForApp(appName, RECENT_APP_HISTORY_LIMIT)
                                .map(RecordingEntry::response)
                        )
                    }
                    ?: emptyList()
                val recentSessionOutputs = if (
                    settings.transcriptionProvider == TranscriptionProvider.GROQ_WHISPER_GEMINI ||
                    settings.transcriptionProvider == TranscriptionProvider.GROQ_WHISPER_GROQ
                ) {
                    sessionContextEntries
                        .asSequence()
                        .filter { entry -> entry.matchesTargetApp(targetApp) }
                        .map(SessionContextEntry::text)
                        .toList()
                        .let(::boundedContextForPrompt)
                } else {
                    emptyList()
                }
                val transcriptionSystemPrompt = if (settings.transcriptionProvider == TranscriptionProvider.GEMINI) {
                    PromptBuilder.build(
                        tone = tone,
                        targetApp = targetApp,
                        customPrompt = customPrompt,
                        genZ = settings.genZEnabled,
                        useCaseContext = settings.useCaseContext,
                    )
                } else {
                    ""
                }
                val correctionSystemPrompt = when (settings.transcriptionProvider) {
                    TranscriptionProvider.GROQ_WHISPER_GEMINI,
                    TranscriptionProvider.GROQ_WHISPER_GROQ -> PromptBuilder.buildCorrectionSystemPrompt(
                        tone = tone,
                        targetApp = targetApp,
                        windowTitle = windowTitle,
                        customPrompt = customPrompt,
                        genZ = settings.genZEnabled,
                        useCaseContext = settings.useCaseContext,
                        whisperModelId = settings.groqModel,
                        recentSessionOutputs = recentSessionOutputs,
                        recentAppHistory = recentAppHistory,
                    )
                    else -> ""
                }
                val whisperPrompt = WhisperPromptBuilder.build(
                    useCase = settings.primaryUseCase,
                    context = settings.useCaseContext,
                )
                val correctionTemperature = buildCorrectionTemperature(settings)
                val directAudioTemperature = buildDirectAudioTemperature(settings)

                val modelUsed = when (settings.transcriptionProvider) {
                    TranscriptionProvider.GEMINI -> settings.geminiModel
                    TranscriptionProvider.GROQ_WHISPER -> settings.groqModel
                    TranscriptionProvider.GROQ_WHISPER_GEMINI -> "${settings.groqModel} -> ${settings.geminiModel}"
                    TranscriptionProvider.GROQ_WHISPER_GROQ -> "${settings.groqModel} -> ${settings.groqLLMModel}"
                }

                ensureProcessingStillActive(generation)

                var usedFallback = false
                val response = when (session) {
                    RecordingSession.Dictation -> {
                        val correctionOutcome = when (settings.transcriptionProvider) {
                            TranscriptionProvider.GEMINI -> {
                                CorrectionOutcome.Applied(
                                    geminiClient.transcribe(
                                        audioBytes = audioBytes,
                                        mimeType = audioMimeType,
                                        systemPrompt = transcriptionSystemPrompt,
                                        apiKey = geminiApiKey!!,
                                        model = settings.geminiModel,
                                        temperature = directAudioTemperature,
                                    )
                                )
                            }

                            TranscriptionProvider.GROQ_WHISPER -> {
                                CorrectionOutcome.Applied(
                                    groqWhisperClient.transcribe(
                                        audioBytes = audioBytes,
                                        mimeType = audioMimeType,
                                        systemPrompt = "",
                                        apiKey = groqApiKey!!,
                                        model = settings.groqModel,
                                        whisperPrompt = whisperPrompt,
                                        language = settings.whisperLanguage,
                                    )
                                )
                            }

                            TranscriptionProvider.GROQ_WHISPER_GEMINI -> {
                                val transcript = groqWhisperClient.transcribe(
                                    audioBytes = audioBytes,
                                    mimeType = audioMimeType,
                                    systemPrompt = "",
                                    apiKey = groqApiKey!!,
                                    model = settings.groqModel,
                                    whisperPrompt = whisperPrompt,
                                    language = settings.whisperLanguage,
                                )
                                rewriteWithGeminiFallback(
                                    transcript = transcript,
                                    systemPrompt = correctionSystemPrompt,
                                    apiKey = geminiApiKey!!,
                                    model = settings.geminiModel,
                                    temperature = correctionTemperature,
                                )
                            }

                            TranscriptionProvider.GROQ_WHISPER_GROQ -> {
                                val transcript = groqWhisperClient.transcribe(
                                    audioBytes = audioBytes,
                                    mimeType = audioMimeType,
                                    systemPrompt = "",
                                    apiKey = groqApiKey!!,
                                    model = settings.groqModel,
                                    whisperPrompt = whisperPrompt,
                                    language = settings.whisperLanguage,
                                )
                                rewriteWithGroqFallback(
                                    transcript = transcript,
                                    systemPrompt = correctionSystemPrompt,
                                    apiKey = groqApiKey,
                                    model = settings.groqLLMModel,
                                    temperature = correctionTemperature,
                                )
                            }
                        }

                        val dictationResponse = when (correctionOutcome) {
                            is CorrectionOutcome.Applied -> correctionOutcome.text
                            is CorrectionOutcome.FallbackRaw -> {
                                usedFallback = true
                                showCorrectionSkippedWarning(correctionOutcome.reason)
                                correctionOutcome.rawTranscript
                            }
                        }

                        val profile = userProfileRepository.loadProfile()
                        val dictEntries = if (settings.dictionaryEnabled) {
                            dictionaryRepository.loadEntries()
                        } else {
                            emptyList()
                        }
                        PhraseExpansionEngine.expandText(
                            text = dictationResponse,
                            profile = profile,
                            dictionaryEntries = dictEntries,
                            enabled = settings.phraseExpansionEnabled,
                        )
                    }

                    is RecordingSession.Command -> runCommandWorkflow(
                        session = session,
                        settings = settings,
                        audioBytes = audioBytes,
                        geminiApiKey = geminiApiKey,
                        groqApiKey = groqApiKey,
                        whisperPrompt = whisperPrompt,
                        targetApp = targetApp,
                        windowTitle = windowTitle,
                        directAudioTemperature = directAudioTemperature,
                        correctionTemperature = correctionTemperature,
                    )
                }

                ensureProcessingStillActive(generation)
                when (session) {
                    RecordingSession.Dictation -> pasteAutomation.pasteText(response, restoreClipboard = false)
                    is RecordingSession.Command -> pasteAutomation.pasteTextToWindow(
                        text = response,
                        targetWindow = session.sourceWindow,
                        snapshotToken = session.clipboardSnapshotToken,
                    )
                }

                ensureProcessingStillActive(generation)

                val successfulPasteAt = Clock.System.now()
                if (session is RecordingSession.Dictation) {
                    rememberSessionContext(
                        text = response,
                        capturedAt = successfulPasteAt,
                        targetApp = targetApp,
                    )
                }
                lastSuccessfulPasteAt = successfulPasteAt

                ensureProcessingStillActive(generation)
                historyRepository.addEntry(
                    RecordingEntry(
                        id = successfulPasteAt.toEpochMilliseconds().toString(),
                        recordedAt = successfulPasteAt,
                        durationSeconds = duration,
                        response = response,
                        targetApp = targetApp ?: "",
                        model = modelUsed,
                        isFallback = usedFallback,
                        workflowType = session.workflowType,
                    )
                )

                ensureProcessingStillActive(generation)
                _state.update {
                    it.copy(
                        recordingState = RecordingState.Success(
                            response,
                            response.length
                        ),
                        lastResultText = response,
                    )
                }
                _effects.emit(RecordingEffect.PasteSuccess)

                overlayController.updateState(OverlayState.SUCCESS)

                if (settings.dictionaryEnabled) {
                    try {
                        if (session is RecordingSession.Dictation) {
                            dictionaryEngine.ingestObservedText(response)
                        }
                    } catch (_: Exception) {
                    }
                }

                setActiveSession(null)
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
            } catch (e: CancellationException) {
                currentProcessingPath = null
                deleteFile(path)
            } catch (e: Exception) {
                currentProcessingPath = null
                if (generation == processingGeneration.get()) {
                    setActiveSession(null)
                    _state.update {
                        it.copy(
                            recordingState = RecordingState.Error(e.message ?: "Processing failed"),
                            error = e.message,
                        )
                    }
                    overlayController.updateState(OverlayState.ERROR)
                    _effects.emit(RecordingEffect.ShowError(e.message ?: "Processing failed"))
                    delay(2000)
                    overlayController.dismiss()
                }
                deleteFile(path)
            } finally {
                if (processingJob === coroutineContext[Job]) {
                    processingJob = null
                }
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
                setActiveSession(null)
            }

            is RecordingState.Processing -> {
                processingGeneration.incrementAndGet()
                processingJob?.cancelAndJoin()
                processingJob = null
                currentProcessingPath?.let { deleteFile(it) }
                currentProcessingPath = null
                overlayController.dismiss()
                _state.update { it.copy(recordingState = RecordingState.Idle) }
                setActiveSession(null)
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
            TranscriptionProvider.GROQ_WHISPER_GROQ -> !groqApiKey.isNullOrBlank()
        }
    }

    override fun onCleared() {
        runBlocking {
            processingGeneration.incrementAndGet()
            processingJob?.cancel()
            processingJob = null
            setActiveSession(null)
        }
        clearSessionContext()
        super.onCleared()
    }

    private fun buildCorrectionTemperature(settings: AppSettings): Float = when (settings.transcriptionProvider) {
        TranscriptionProvider.GROQ_WHISPER_GEMINI -> if (settings.genZEnabled) 0.5f else 0.2f
        TranscriptionProvider.GROQ_WHISPER_GROQ -> 0.2f
        else -> 0.2f
    }

    private fun buildDirectAudioTemperature(settings: AppSettings): Float = when (settings.transcriptionProvider) {
        TranscriptionProvider.GEMINI -> if (settings.genZEnabled) 0.5f else 0.3f
        else -> 0.3f
    }

    private suspend fun prepareCommandSession(settings: AppSettings): RecordingSession.Command? {
        val effectiveHotkeys = settings.effectiveHotkeyConfig()
        val commandHotkey = effectiveHotkeys.commandHotkey?.takeIf {
            effectiveHotkeys.commandHotkeyEnabled && it.enabled
        }
        return when (val captureResult = pasteAutomation.captureSelectedText(commandHotkey)) {
            SelectionCaptureResult.Empty -> {
                overlayController.flashMessage("No text selected")
                null
            }

            SelectionCaptureResult.Failure -> {
                overlayController.flashMessage("Selection capture failed")
                null
            }

            is SelectionCaptureResult.Success -> {
                val session = RecordingSession.Command(
                    selectedText = captureResult.selectedText,
                    clipboardSnapshotToken = captureResult.clipboardSnapshotToken,
                    sourceWindow = captureResult.sourceWindow,
                )
                if (!settings.transcriptionProvider.supportsCommandWorkflow()) {
                    restoreCommandClipboardIfNeeded(session)
                    overlayController.flashMessage("Command Mode requires a rewrite model")
                    return null
                }
                if (captureResult.selectedText.length > MAX_COMMAND_SELECTION_CHARS) {
                    restoreCommandClipboardIfNeeded(session)
                    overlayController.flashMessage("Selection too long")
                    return null
                }
                session
            }
        }
    }

    private suspend fun runCommandWorkflow(
        session: RecordingSession.Command,
        settings: AppSettings,
        audioBytes: ByteArray,
        geminiApiKey: String?,
        groqApiKey: String?,
        whisperPrompt: String,
        targetApp: String?,
        windowTitle: String?,
        directAudioTemperature: Float,
        correctionTemperature: Float,
    ): String {
        val spokenInstruction = transcribeCommandInstruction(
            settings = settings,
            audioBytes = audioBytes,
            geminiApiKey = geminiApiKey,
            groqApiKey = groqApiKey,
            whisperPrompt = whisperPrompt,
            directAudioTemperature = directAudioTemperature,
        ).trim()
        if (!PromptBuilder.isMeaningfulCommandInstruction(spokenInstruction)) {
            val message = "Didn't catch a clear command. Try again with a specific editing instruction."
            overlayController.flashMessage(message)
            throw UserVisibleCommandFailure(message)
        }

        val commandPrompt = PromptBuilder.buildCommandTransformationPrompt(
            targetApp = targetApp,
            windowTitle = windowTitle,
        )
        val commandInput = PromptBuilder.buildCommandTransformationInput(
            selectedText = session.selectedText,
            spokenInstruction = spokenInstruction,
        )
        val transformed = when (settings.transcriptionProvider) {
            TranscriptionProvider.GEMINI,
            TranscriptionProvider.GROQ_WHISPER_GEMINI -> geminiClient.rewriteText(
                text = commandInput,
                systemPrompt = commandPrompt,
                apiKey = geminiApiKey!!,
                model = settings.geminiModel,
                temperature = correctionTemperature,
            )

            TranscriptionProvider.GROQ_WHISPER_GROQ -> groqLLMClient.rewriteText(
                text = commandInput,
                systemPrompt = commandPrompt,
                apiKey = groqApiKey!!,
                model = settings.groqLLMModel,
                temperature = correctionTemperature,
            )

            TranscriptionProvider.GROQ_WHISPER -> error("Command workflow should not run without a rewrite model.")
        }

        return PromptBuilder.sanitizeCommandOutput(
            rawOutput = transformed,
            spokenInstruction = spokenInstruction,
        ).ifBlank {
            overlayController.flashMessage("Command produced no text")
            throw UserVisibleCommandFailure("Command produced no text")
        }
    }

    private suspend fun transcribeCommandInstruction(
        settings: AppSettings,
        audioBytes: ByteArray,
        geminiApiKey: String?,
        groqApiKey: String?,
        whisperPrompt: String,
        directAudioTemperature: Float,
    ): String {
        return when (settings.transcriptionProvider) {
            TranscriptionProvider.GEMINI -> geminiClient.transcribe(
                audioBytes = audioBytes,
                mimeType = audioMimeType,
                systemPrompt = PromptBuilder.buildCommandInstructionTranscriptionPrompt(),
                apiKey = geminiApiKey!!,
                model = settings.geminiModel,
                temperature = directAudioTemperature,
            )

            TranscriptionProvider.GROQ_WHISPER_GEMINI,
            TranscriptionProvider.GROQ_WHISPER_GROQ -> groqWhisperClient.transcribe(
                audioBytes = audioBytes,
                mimeType = audioMimeType,
                systemPrompt = "",
                apiKey = groqApiKey!!,
                model = settings.groqModel,
                whisperPrompt = whisperPrompt,
                language = settings.whisperLanguage,
            )

            TranscriptionProvider.GROQ_WHISPER -> error("Command workflow should not transcribe without a rewrite model.")
        }
    }

    private suspend fun restoreCommandClipboardIfNeeded(session: RecordingSession) {
        if (session is RecordingSession.Command) {
            pasteAutomation.restoreClipboard(session.clipboardSnapshotToken)
        }
    }

    private suspend fun setActiveSession(session: RecordingSession?) {
        val previousSession = activeSession
        if (previousSession != null && previousSession !== session) {
            restoreCommandClipboardIfNeeded(previousSession)
        }
        activeSession = session
    }

    private suspend fun ensureProcessingStillActive(generation: Int) {
        coroutineContext.ensureActive()
        if (generation != processingGeneration.get()) {
            throw CancellationException("Stale processing request")
        }
    }

    private suspend fun rewriteWithGeminiFallback(
        transcript: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
        temperature: Float,
    ): CorrectionOutcome {
        return rewriteWithFallback(
            transcript = transcript,
            systemPrompt = systemPrompt,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            defaultErrorMessage = "Gemini correction failed",
            rewrite = geminiClient::rewriteText,
        )
    }

    private suspend fun rewriteWithGroqFallback(
        transcript: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
        temperature: Float,
    ): CorrectionOutcome {
        return rewriteWithFallback(
            transcript = transcript,
            systemPrompt = systemPrompt,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            defaultErrorMessage = "Groq correction failed",
            rewrite = groqLLMClient::rewriteText,
        )
    }

    private suspend fun rewriteWithFallback(
        transcript: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
        temperature: Float,
        defaultErrorMessage: String,
        rewrite: suspend (String, String, String, String, Float) -> String,
    ): CorrectionOutcome {
        return try {
            val rewritten = rewrite(
                transcript,
                systemPrompt,
                apiKey,
                model,
                temperature,
            )
            validateCorrectionRewrite(
                original = transcript,
                rewritten = rewritten,
            )?.let { reason ->
                CorrectionOutcome.FallbackRaw(
                    rawTranscript = transcript,
                    reason = reason,
                )
            } ?: CorrectionOutcome.Applied(rewritten)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CorrectionOutcome.FallbackRaw(
                rawTranscript = transcript,
                reason = e.message ?: defaultErrorMessage,
            )
        }
    }

    private fun showCorrectionSkippedWarning(reason: String) {
        val detail = sanitizeWarningDetail(reason)
        overlayController.flashMessage("Correction skipped, using raw transcript: $detail")
    }

    private fun validateCorrectionRewrite(original: String, rewritten: String): String? {
        val normalizedOriginal = original.trim()
        val normalizedRewritten = rewritten.trim()
        if (normalizedRewritten.isEmpty()) {
            return "correction returned blank output"
        }
        if (normalizedOriginal.isEmpty()) {
            return null
        }

        val ratio = normalizedRewritten.length.toDouble() / normalizedOriginal.length.toDouble()
        return if (ratio in 0.4..2.5) {
            null
        } else {
            "correction rewrite length looked unsafe"
        }
    }

    private fun pruneExpiredSessionContext(now: Instant) {
        val lastPasteAt = lastSuccessfulPasteAt ?: return
        if (now - lastPasteAt > SESSION_CONTEXT_IDLE_TIMEOUT) {
            clearSessionContext()
        }
    }

    private fun rememberSessionContext(text: String, capturedAt: Instant, targetApp: String?) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return

        sessionContextEntries.addLast(
            SessionContextEntry(
                text = normalizedText,
                capturedAt = capturedAt,
                targetApp = targetApp,
            )
        )
        while (sessionContextEntries.size > MAX_SESSION_CONTEXT_ENTRIES) {
            sessionContextEntries.removeFirst()
        }
    }

    private fun clearSessionContext() {
        sessionContextEntries.clear()
        lastSuccessfulPasteAt = null
    }

    private fun SessionContextEntry.matchesTargetApp(targetApp: String?): Boolean {
        return normalizeTargetApp(this.targetApp) == normalizeTargetApp(targetApp)
    }

    private fun normalizeTargetApp(targetApp: String?): String? {
        return targetApp?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
    }

    private fun boundedContextForPrompt(values: List<String>): List<String> {
        val bounded = values
            .asSequence()
            .map(::normalizeContextSnippet)
            .filter(String::isNotEmpty)
            .toList()
            .takeLast(MAX_CONTEXT_ITEMS_FOR_PROMPT)
            .toMutableList()

        while (bounded.sumOf { it.length } > MAX_TOTAL_CONTEXT_CHARS && bounded.size > 1) {
            bounded.removeAt(0)
        }

        if (bounded.size == 1 && bounded[0].length > MAX_TOTAL_CONTEXT_CHARS) {
            bounded[0] = bounded[0].takeLast(MAX_TOTAL_CONTEXT_CHARS)
        }

        return bounded
    }

    private fun normalizeContextSnippet(value: String): String {
        return value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", " ")
            .trim()
            .take(MAX_CONTEXT_ITEM_CHARS)
    }

    private fun sanitizeWarningDetail(reason: String): String {
        val collapsed = reason
            .replace("\\s+".toRegex(), " ")
            .trim()
            .ifBlank { "correction failed" }

        return if (collapsed.length <= MAX_WARNING_DETAIL_CHARS) {
            collapsed
        } else {
            collapsed.take(MAX_WARNING_DETAIL_CHARS - 1).trimEnd() + "..."
        }
    }

    private fun readFileBytes(path: String): ByteArray = fileOperations.readFile(path)

    private fun deleteFile(path: String) {
        try {
            fileOperations.deleteFile(path)
        } catch (_: Exception) {
        }
    }

    private fun AppSettings.recordingSensitivityPreset(): RecordingSensitivityPreset {
        return if (whisperModeEnabled) RecordingSensitivityPreset.WHISPER else RecordingSensitivityPreset.NORMAL
    }

    private fun TranscriptionProvider.supportsCommandWorkflow(): Boolean {
        return this != TranscriptionProvider.GROQ_WHISPER
    }
}
