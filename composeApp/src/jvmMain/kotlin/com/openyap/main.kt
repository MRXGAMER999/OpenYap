package com.openyap

import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import java.awt.image.BufferedImage
import com.openyap.platform.*
import com.openyap.repository.*
import com.openyap.service.DictionaryEngine
import com.openyap.ui.navigation.AppShell
import com.openyap.ui.navigation.Route
import com.openyap.ui.theme.AppTheme
import com.openyap.viewmodel.*

fun main() {
    // Prevent AWT from erasing the window background to white during resize.
    // Without this, Windows sends WM_ERASEBKGND which fills newly-exposed
    // areas with white before Compose/Skia can repaint them.
    System.setProperty("sun.awt.noerasebackground", "true")

    application {
    val secureStorage = remember { WindowsCredentialStorage() }
    val settingsRepo = remember { JvmSettingsRepository(secureStorage, PlatformInit.dataDir) }
    val historyRepo = remember { JvmHistoryRepository(PlatformInit.dataDir) }
    val dictionaryRepo = remember { JvmDictionaryRepository(PlatformInit.dataDir) }
    val userProfileRepo = remember { JvmUserProfileRepository(PlatformInit.dataDir) }
    val hotkeyManager = remember { WindowsHotkeyManager() }
    val audioRecorder = remember { JvmAudioRecorder() }
    val pasteAutomation = remember { WindowsPasteAutomation() }
    val foregroundDetector = remember { WindowsForegroundAppDetector() }
    val permissionManager = remember { WindowsPermissionManager() }
    val geminiClient = remember { HttpClientFactory.createGeminiClient() }
    val dictionaryEngine = remember { DictionaryEngine(dictionaryRepo) }
    val hotkeyFormatter = remember { WindowsHotkeyDisplayFormatter() }

    val recordingViewModel = remember {
        RecordingViewModel(
            hotkeyManager = hotkeyManager,
            audioRecorder = audioRecorder,
            geminiClient = geminiClient,
            pasteAutomation = pasteAutomation,
            foregroundAppDetector = foregroundDetector,
            settingsRepository = settingsRepo,
            historyRepository = historyRepo,
            dictionaryRepository = dictionaryRepo,
            userProfileRepository = userProfileRepo,
            permissionManager = permissionManager,
            dictionaryEngine = dictionaryEngine,
            tempDirProvider = { PlatformInit.tempDir.toString() },
            fileReader = { path -> java.io.File(path).readBytes() },
            fileDeleter = { path -> java.io.File(path).delete() },
        )
    }
    val settingsViewModel = remember { SettingsViewModel(settingsRepo, geminiClient, hotkeyManager, hotkeyFormatter) }
    val historyViewModel = remember { HistoryViewModel(historyRepo) }
    val onboardingViewModel = remember { OnboardingViewModel(settingsRepo, permissionManager, geminiClient) }
    val dictionaryViewModel = remember { DictionaryViewModel(dictionaryRepo, dictionaryEngine) }
    val userProfileViewModel = remember { UserProfileViewModel(userProfileRepo) }
    val statsViewModel = remember { StatsViewModel(historyRepo) }

    var appTones by remember { mutableStateOf(emptyMap<String, String>()) }
    var appPrompts by remember { mutableStateOf(emptyMap<String, String>()) }

    LaunchedEffect(Unit) {
        val settings = settingsRepo.loadSettings()
        hotkeyManager.setConfig(settings.hotkeyConfig)
        hotkeyManager.startListening()
        appTones = settingsRepo.loadAllAppTones()
        appPrompts = settingsRepo.loadAllAppPrompts()
    }

    var isVisible by remember { mutableStateOf(true) }
    val trayState = rememberTrayState()
    val windowState = rememberWindowState(size = DpSize(900.dp, 700.dp))
    val backStack = remember { mutableStateListOf<Route>(Route.Home) }

    val trayIcon = remember {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = java.awt.Color(21, 101, 192)
            g.fillOval(0, 0, 16, 16)
            g.dispose()
        }
        BitmapPainter(img.toComposeImageBitmap())
    }

    Tray(
        state = trayState,
        tooltip = "OpenYap",
        icon = trayIcon,
        menu = {
            Item("Show", onClick = { isVisible = true })
            Item("Quit", onClick = {
                hotkeyManager.close()
                exitApplication()
            })
        },
    )

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
        ) {
            // Match native window chrome + background to the app theme
            val isDark = isSystemInDarkTheme()
            DisposableEffect(isDark) {
                val bgColor = if (isDark) {
                    java.awt.Color(0x1D, 0x18, 0x1A) // matches DarkColorScheme.surface
                } else {
                    java.awt.Color(0xFF, 0xF8, 0xF6) // matches LightColorScheme.surface
                }
                // Set background on every AWT layer to eliminate any white surface
                window.background = bgColor
                window.rootPane.background = bgColor
                window.contentPane.background = bgColor
                window.layeredPane.background = bgColor
                window.glassPane.background = bgColor

                // Set dark/light title bar via Windows DWM API.
                // Schedule on the AWT event thread so the native HWND is
                // guaranteed to exist (DisposableEffect can fire before the
                // window peer is fully realised).
                val applyTitleBar = Runnable {
                    WindowsThemeHelper.setDarkTitleBar(window, isDark)
                }
                javax.swing.SwingUtilities.invokeLater(applyTitleBar)

                // Also re-apply when the window is first shown / re-activated
                // to cover edge cases where the HWND is not yet available on
                // the first invokeLater.
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

            val recordingState by recordingViewModel.state.collectAsState()
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
                        if (event is SettingsEvent.SaveApiKey) {
                            recordingViewModel.onEvent(RecordingEvent.RefreshState)
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
