package com.openyap

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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

fun main() = application {
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
    val settingsViewModel = remember { SettingsViewModel(settingsRepo, geminiClient) }
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
                hotkeyManager.stopListening()
                exitApplication()
            })
        },
    )

    if (isVisible) {
        Window(
            onCloseRequest = { isVisible = false },
            title = "OpenYap",
            state = rememberWindowState(size = DpSize(900.dp, 700.dp)),
        ) {
            val backStack = remember { mutableStateListOf<Route>(Route.Home) }

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
