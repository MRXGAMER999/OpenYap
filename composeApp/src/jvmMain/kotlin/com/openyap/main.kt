package com.openyap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.openyap.platform.AudioFeedbackService
import com.openyap.platform.AudioRecorder
import com.openyap.platform.ComposeOverlayController
import com.openyap.platform.ComposeOverlayWindow
import com.openyap.platform.HttpClientFactory
import com.openyap.platform.JvmAppDataResetter
import com.openyap.platform.JvmAudioRecorder
import com.openyap.platform.NativeAudioBridge
import com.openyap.platform.NativeAudioRecorder
import com.openyap.platform.PlatformInit
import com.openyap.platform.WindowsCredentialStorage
import com.openyap.platform.WindowsForegroundAppDetector
import com.openyap.platform.WindowsHotkeyDisplayFormatter
import com.openyap.platform.WindowsHotkeyManager
import com.openyap.platform.WindowsPasteAutomation
import com.openyap.platform.WindowsPermissionManager
import com.openyap.platform.WindowsThemeHelper
import com.openyap.repository.JvmDictionaryRepository
import com.openyap.repository.JvmHistoryRepository
import com.openyap.repository.JvmSettingsRepository
import com.openyap.repository.JvmUserProfileRepository
import com.openyap.service.DictionaryEngine
import com.openyap.ui.navigation.AppShell
import com.openyap.ui.navigation.Route
import com.openyap.ui.theme.AppTheme
import com.openyap.viewmodel.AudioFeedbackPlayer
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingViewModel
import com.openyap.viewmodel.RecordingEffect
import com.openyap.viewmodel.RecordingEvent
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import kotlinx.coroutines.launch
import openyap.composeapp.generated.resources.Res
import openyap.composeapp.generated.resources.ic_app_logo
import org.jetbrains.compose.resources.painterResource

fun main() {
    System.setProperty("sun.awt.noerasebackground", "true")

    application {
        val secureStorage = remember { WindowsCredentialStorage() }
        val settingsRepo = remember { JvmSettingsRepository(secureStorage, PlatformInit.dataDir) }
        val historyRepo = remember { JvmHistoryRepository(PlatformInit.dataDir) }
        val dictionaryRepo = remember { JvmDictionaryRepository(PlatformInit.dataDir) }
        val userProfileRepo = remember { JvmUserProfileRepository(PlatformInit.dataDir) }
        val hotkeyManager = remember { WindowsHotkeyManager() }
        val audioPipeline = remember {
            val nativeAudio = NativeAudioBridge.instance
            if (nativeAudio != null) {
                System.err.println("Native audio pipeline available")
                AudioPipelineConfig(
                    audioRecorder = NativeAudioRecorder(nativeAudio),
                    audioMimeType = "audio/mp4",
                    audioFileExtension = ".m4a",
                )
            } else {
                System.err.println("Native audio pipeline unavailable, using fallback")
                AudioPipelineConfig(
                    audioRecorder = JvmAudioRecorder(),
                    audioMimeType = "audio/wav",
                    audioFileExtension = ".wav",
                )
            }
        }
        val audioRecorder = audioPipeline.audioRecorder
        val pasteAutomation = remember { WindowsPasteAutomation() }
        val foregroundDetector = remember { WindowsForegroundAppDetector() }
        val permissionManager = remember { WindowsPermissionManager() }
        val geminiClient = remember { HttpClientFactory.createGeminiClient() }
        val groqWhisperClient = remember { HttpClientFactory.createGroqWhisperClient() }
        val dictionaryEngine = remember { DictionaryEngine(dictionaryRepo) }
        val hotkeyFormatter = remember { WindowsHotkeyDisplayFormatter() }
        val overlayController = remember { ComposeOverlayController() }
        val audioFeedbackService = remember { AudioFeedbackService() }
        val appDataResetter = remember {
            JvmAppDataResetter(
                secureStorage = secureStorage,
                dataDir = PlatformInit.dataDir,
                tempDir = PlatformInit.tempDir,
            )
        }

        val audioFeedbackPlayer = remember {
            object : AudioFeedbackPlayer {
                override fun playStart() =
                    audioFeedbackService.play(AudioFeedbackService.Tone.START)

                override fun playStop() = audioFeedbackService.play(AudioFeedbackService.Tone.STOP)
                override fun playTooShort() =
                    audioFeedbackService.play(AudioFeedbackService.Tone.TOO_SHORT)

                override fun playError() =
                    audioFeedbackService.play(AudioFeedbackService.Tone.ERROR)
            }
        }

        val recordingViewModel = remember {
            RecordingViewModel(
                hotkeyManager = hotkeyManager,
                audioRecorder = audioRecorder,
                geminiClient = geminiClient,
                groqWhisperClient = groqWhisperClient,
                pasteAutomation = pasteAutomation,
                foregroundAppDetector = foregroundDetector,
                settingsRepository = settingsRepo,
                historyRepository = historyRepo,
                dictionaryRepository = dictionaryRepo,
                userProfileRepository = userProfileRepo,
                permissionManager = permissionManager,
                dictionaryEngine = dictionaryEngine,
                overlayController = overlayController,
                audioFeedbackPlayer = audioFeedbackPlayer,
                audioMimeType = audioPipeline.audioMimeType,
                audioFileExtension = audioPipeline.audioFileExtension,
                tempDirProvider = { PlatformInit.tempDir.toString() },
                fileReader = { path -> java.io.File(path).readBytes() },
                fileDeleter = { path -> java.io.File(path).delete() },
            )
        }
        val settingsViewModel = remember {
            SettingsViewModel(
                settingsRepo,
                geminiClient,
                groqWhisperClient,
                hotkeyManager,
                hotkeyFormatter,
                audioRecorder,
                resetAppDataAction = { appDataResetter.reset() },
            )
        }
        val historyViewModel = remember { HistoryViewModel(historyRepo) }
        val onboardingViewModel =
            remember { OnboardingViewModel(settingsRepo, permissionManager, geminiClient) }
        val dictionaryViewModel = remember { DictionaryViewModel(dictionaryRepo, dictionaryEngine) }
        val userProfileViewModel = remember { UserProfileViewModel(userProfileRepo) }
        val statsViewModel = remember { StatsViewModel(historyRepo) }

        var appTones by remember { mutableStateOf(emptyMap<String, String>()) }
        var appPrompts by remember { mutableStateOf(emptyMap<String, String>()) }

        var isVisible by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            val settings = settingsRepo.loadSettings()
            hotkeyManager.setConfig(settings.hotkeyConfig)
            hotkeyManager.startListening()
            appTones = settingsRepo.loadAllAppTones()
            appPrompts = settingsRepo.loadAllAppPrompts()

            if (settings.startMinimized) {
                isVisible = false
            }

            // Preload audio tones using resource files from composeResources
            try {
                val startToneBytes =
                    javaClass.getResourceAsStream("/composeResources/openyap.composeapp.generated.resources/files/start_tone.wav")
                        ?.use { it.readBytes() }
                val stopToneBytes =
                    javaClass.getResourceAsStream("/composeResources/openyap.composeapp.generated.resources/files/stop_tone.wav")
                        ?.use { it.readBytes() }

                val toneMap = buildMap {
                    startToneBytes?.let { put(AudioFeedbackService.Tone.START, it) }
                    stopToneBytes?.let {
                        put(AudioFeedbackService.Tone.STOP, it)
                        put(AudioFeedbackService.Tone.TOO_SHORT, it)
                    }
                }
                audioFeedbackService.preload(toneMap)
            } catch (_: Exception) {
                // Audio feedback unavailable — visual-only fallback
            }
        }

        val trayState = rememberTrayState()
        val windowState = rememberWindowState(size = DpSize(1100.dp, 800.dp))
        val backStack = remember { mutableStateListOf<Route>(Route.Home) }

        DisposableEffect(audioRecorder) {
            onDispose {
                runCatching { (audioRecorder as? java.io.Closeable)?.close() }
            }
        }

        val recordingState by recordingViewModel.state.collectAsState()
        val appIcon = painterResource(Res.drawable.ic_app_logo)

        Tray(
            state = trayState,
            tooltip = "OpenYap",
            icon = appIcon,
            menu = {
                Item("Show", onClick = { isVisible = true })
                Item("Quit", onClick = {
                    runCatching { (audioRecorder as? java.io.Closeable)?.close() }
                    overlayController.close()
                    audioFeedbackService.close()
                    hotkeyManager.close()
                    exitApplication()
                })
            },
        )

        val overlayState by overlayController.uiState.collectAsState()
        ComposeOverlayWindow(overlayState)

        LaunchedEffect(recordingViewModel, historyViewModel, statsViewModel) {
            recordingViewModel.effects.collect { effect ->
                when (effect) {
                    RecordingEffect.PasteSuccess -> {
                        historyViewModel.refresh()
                        statsViewModel.refresh()
                    }

                    is RecordingEffect.ShowError -> Unit
                }
            }
        }

        if (isVisible) {
            Window(
                onCloseRequest = { isVisible = false },
                title = "OpenYap",
                state = windowState,
                icon = appIcon,
            ) {
                val isDark = isSystemInDarkTheme()
                DisposableEffect(isDark) {
                    val bgColor = if (isDark) {
                        java.awt.Color(0x1F, 0x29, 0x37)
                    } else {
                        java.awt.Color(0xFB, 0xFC, 0xFF)
                    }
                    window.background = bgColor
                    window.rootPane.background = bgColor
                    window.contentPane.background = bgColor
                    window.layeredPane.background = bgColor
                    window.glassPane.background = bgColor

                    val applyTitleBar = Runnable {
                        WindowsThemeHelper.setDarkTitleBar(window, isDark)
                    }
                    javax.swing.SwingUtilities.invokeLater(applyTitleBar)

                    val windowListener = object : java.awt.event.WindowAdapter() {
                        override fun windowOpened(e: java.awt.event.WindowEvent?) {
                            applyTitleBar.run()
                        }

                        override fun windowActivated(e: java.awt.event.WindowEvent?) {
                            applyTitleBar.run()
                        }
                    }
                    window.addWindowListener(windowListener)

                    onDispose {
                        window.removeWindowListener(windowListener)
                    }
                }

                val settingsState by settingsViewModel.state.collectAsState()
                val historyState by historyViewModel.state.collectAsState()
                val onboardingState by onboardingViewModel.state.collectAsState()
                val dictionaryState by dictionaryViewModel.state.collectAsState()
                val userProfileState by userProfileViewModel.state.collectAsState()
                val statsState by statsViewModel.state.collectAsState()
                val scope = rememberCoroutineScope()

                AppTheme {
                    AppShell(
                        backStack = backStack,
                        recordingState = recordingState,
                        settingsState = settingsState,
                        historyState = historyState,
                        onboardingState = onboardingState,
                        dictionaryState = dictionaryState,
                        userProfileState = userProfileState,
                        statsState = statsState,
                        appTones = appTones,
                        appPrompts = appPrompts,
                        onRecordingEvent = recordingViewModel::onEvent,
                        onSettingsEvent = { event ->
                            settingsViewModel.onEvent(event)
                            if (event is SettingsEvent.SaveApiKey || event is SettingsEvent.SaveGroqApiKey || event is SettingsEvent.SelectProvider) {
                                recordingViewModel.onEvent(RecordingEvent.RefreshState)
                            }
                            if (event is SettingsEvent.ResetAppData) {
                                recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                historyViewModel.refresh()
                                onboardingViewModel.refresh()
                                dictionaryViewModel.refresh()
                                userProfileViewModel.refresh()
                                statsViewModel.refresh()
                                appTones = emptyMap()
                                appPrompts = emptyMap()
                                backStack.clear()
                                backStack += Route.Home
                            }
                        },
                        onHistoryEvent = historyViewModel::onEvent,
                        onOnboardingEvent = { event ->
                            onboardingViewModel.onEvent(event)
                            if (event is OnboardingEvent.CompleteOnboarding || event is OnboardingEvent.SaveApiKey) {
                                recordingViewModel.onEvent(RecordingEvent.RefreshState)
                            }
                        },
                        onDictionaryEvent = dictionaryViewModel::onEvent,
                        onUserProfileEvent = userProfileViewModel::onEvent,
                        onSaveTone = { app, tone ->
                            appTones = appTones + (app to tone)
                            scope.launch { settingsRepo.saveAppTone(app, tone) }
                        },
                        onSavePrompt = { app, prompt ->
                            appPrompts = appPrompts + (app to prompt)
                            scope.launch { settingsRepo.saveAppPrompt(app, prompt) }
                        },
                        onRemoveApp = { app ->
                            appTones = appTones - app
                            appPrompts = appPrompts - app
                            scope.launch { settingsRepo.removeAppCustomization(app) }
                        },
                        onStatsRefresh = { statsViewModel.refresh() },
                        onCopyToClipboard = { text ->
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(java.awt.datatransfer.StringSelection(text), null)
                        },
                    )
                }
            }
        }
    }
}

private data class AudioPipelineConfig(
    val audioRecorder: AudioRecorder,
    val audioMimeType: String,
    val audioFileExtension: String,
)
