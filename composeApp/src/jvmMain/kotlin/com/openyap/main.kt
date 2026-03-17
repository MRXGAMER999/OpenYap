package com.openyap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.openyap.di.ComposeAppModule
import com.openyap.di.PlatformModule
import com.openyap.di.sharedModule
import com.openyap.platform.AudioFeedbackService
import com.openyap.platform.AudioRecorder
import com.openyap.platform.ComposeOverlayController
import com.openyap.platform.ComposeOverlayWindow
import com.openyap.platform.HotkeyManager
import com.openyap.platform.OverlayController
import com.openyap.platform.WindowsThemeHelper
import com.openyap.repository.SettingsRepository
import com.openyap.ui.navigation.AppShell
import com.openyap.ui.navigation.Route
import com.openyap.ui.theme.AppTheme
import com.openyap.viewmodel.AppCustomizationViewModel
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingEvent
import com.openyap.viewmodel.OnboardingViewModel
import kotlinx.coroutines.flow.first
import com.openyap.viewmodel.RecordingEffect
import com.openyap.viewmodel.RecordingEvent
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsEvent
import com.openyap.viewmodel.SettingsEffect
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.ksp.generated.module
import openyap.composeapp.generated.resources.Res
import openyap.composeapp.generated.resources.ic_app_logo
import org.jetbrains.compose.resources.painterResource

fun main() {
    System.setProperty("sun.awt.noerasebackground", "true")

    application {
        KoinApplication(application = {
            modules(
                PlatformModule().module,
                ComposeAppModule().module,
                sharedModule,
            )
        }) {
            val settingsRepo = koinInject<SettingsRepository>()
            val hotkeyManager = koinInject<HotkeyManager>()
            val audioRecorder = koinInject<AudioRecorder>()
            val audioFeedbackService = koinInject<AudioFeedbackService>()
            val overlayController = koinInject<OverlayController>()

            val recordingViewModel = koinInject<RecordingViewModel>()
            val settingsViewModel = koinInject<SettingsViewModel>()
            val historyViewModel = koinInject<HistoryViewModel>()
            val onboardingViewModel = koinInject<OnboardingViewModel>()
            val dictionaryViewModel = koinInject<DictionaryViewModel>()
            val userProfileViewModel = koinInject<UserProfileViewModel>()
            val statsViewModel = koinInject<StatsViewModel>()
            val appCustomizationViewModel = koinInject<AppCustomizationViewModel>()

            val settingsStateForVolume by settingsViewModel.state.collectAsState()
            LaunchedEffect(settingsStateForVolume.soundFeedbackVolume) {
                audioFeedbackService.setVolume(settingsStateForVolume.soundFeedbackVolume)
            }

            var isVisible by remember { mutableStateOf(true) }

            LaunchedEffect(onboardingViewModel) {
                val envGroqKey = System.getenv("groq api key")
                    ?: System.getenv("GROQ_API_KEY")
                    ?: ""
                if (envGroqKey.isNotBlank()) {
                    onboardingViewModel.state.first { it.isLoaded }
                    val initial = onboardingViewModel.state.value
                    if (!initial.isComplete && initial.apiKey.isBlank()) {
                        onboardingViewModel.onEvent(OnboardingEvent.SkipMicPermission)
                        onboardingViewModel.onEvent(OnboardingEvent.SaveApiKey(envGroqKey))
                        kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                            onboardingViewModel.state.first { state ->
                                !state.isValidatingKey && !state.isLoadingModels &&
                                    state.keyValidationSuccess == true
                            }
                        }?.let {
                            val afterFetch = onboardingViewModel.state.value
                            if (afterFetch.availableModels.isNotEmpty() && afterFetch.selectedModel.isBlank()) {
                                onboardingViewModel.onEvent(
                                    OnboardingEvent.SelectModel(afterFetch.availableModels.first().id)
                                )
                            }
                            kotlinx.coroutines.delay(100)
                            onboardingViewModel.onEvent(OnboardingEvent.CompleteOnboarding)
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                val settings = settingsRepo.loadSettings()
                hotkeyManager.setConfig(settings.hotkeyConfig)
                hotkeyManager.startListening()
                appCustomizationViewModel.refresh()

                if (settings.startMinimized) {
                    isVisible = false
                }

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
                        runCatching { (overlayController as? java.io.Closeable)?.close() }.onFailure { System.err.println("Failed to close overlayController: $it") }
                        runCatching { audioFeedbackService.close() }.onFailure { System.err.println("Failed to close audioFeedbackService: $it") }
                        runCatching { hotkeyManager.close() }.onFailure { System.err.println("Failed to close hotkeyManager: $it") }
                        exitApplication()
                    })
                },
            )

            val composeOverlayController = koinInject<ComposeOverlayController>()
            val overlayState by composeOverlayController.uiState.collectAsState()
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

                    LaunchedEffect(settingsViewModel) {
                        settingsViewModel.effects.collect { effect ->
                            when (effect) {
                                SettingsEffect.ResetAppDataSucceeded -> {
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                    historyViewModel.refresh()
                                    onboardingViewModel.resetState()
                                    onboardingViewModel.refresh()
                                    dictionaryViewModel.refresh()
                                    userProfileViewModel.refresh()
                                    statsViewModel.refresh()
                                    appCustomizationViewModel.refresh()
                                    backStack.clear()
                                    backStack += Route.Home
                                }
                            }
                        }
                    }

                    AppTheme {
                        AppShell(
                            backStack = backStack,
                            recordingViewModel = recordingViewModel,
                            settingsViewModel = settingsViewModel,
                            onRecordingEvent = recordingViewModel::onEvent,
                            onSettingsEvent = { event ->
                                settingsViewModel.onEvent(event)
                                if (event is SettingsEvent.SaveApiKey || event is SettingsEvent.SaveGroqApiKey || event is SettingsEvent.SelectProvider) {
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                }
                            },
                            onOnboardingEvent = { event ->
                                onboardingViewModel.onEvent(event)
                                if (event is OnboardingEvent.CompleteOnboarding) {
                                    settingsViewModel.refresh()
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                } else if (event is OnboardingEvent.SaveApiKey) {
                                    recordingViewModel.onEvent(RecordingEvent.RefreshState)
                                }
                            },
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
}
