package com.openyap.di

import com.openyap.platform.AudioRecorder
import com.openyap.platform.FileOperations
import com.openyap.platform.ForegroundAppDetector
import com.openyap.platform.HotkeyDisplayFormatter
import com.openyap.platform.HotkeyManager
import com.openyap.platform.OverlayController
import com.openyap.platform.PasteAutomation
import com.openyap.platform.PermissionManager
import com.openyap.platform.AppDataResetter
import com.openyap.platform.StartupManager
import com.openyap.repository.DictionaryRepository
import com.openyap.repository.HistoryRepository
import com.openyap.repository.SettingsRepository
import com.openyap.repository.UserProfileRepository
import com.openyap.service.DictionaryEngine
import com.openyap.service.GeminiClient
import com.openyap.service.GroqLLMClient
import com.openyap.service.GroqWhisperClient
import com.openyap.viewmodel.AppCustomizationViewModel
import com.openyap.viewmodel.AudioFeedbackPlayer
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingViewModel
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedModule = module {
    singleOf(::DictionaryEngine)

    viewModel {
        RecordingViewModel(
            hotkeyManager = get(),
            audioRecorder = get(),
            geminiClient = get<GeminiClient>(),
            groqWhisperClient = get<GroqWhisperClient>(),
            groqLLMClient = get(),
            pasteAutomation = get(),
            foregroundAppDetector = get(),
            settingsRepository = get(),
            historyRepository = get(),
            dictionaryRepository = get(),
            userProfileRepository = get(),
            permissionManager = get(),
            dictionaryEngine = get(),
            overlayController = get(),
            audioFeedbackPlayer = get(),
            audioMimeType = get(named("audioMimeType")),
            audioFileExtension = get(named("audioFileExtension")),
            fileOperations = get(),
        )
    }

    viewModel {
        SettingsViewModel(
            settingsRepository = get(),
            geminiClient = get(),
            groqWhisperClient = get(),
            groqLLMClient = get(),
            hotkeyManager = get(),
            hotkeyDisplayFormatter = get(),
            audioRecorder = get(),
            startupManager = get(),
            appDataResetter = get(),
        )
    }

    viewModelOf(::HistoryViewModel)
    viewModelOf(::DictionaryViewModel)
    viewModelOf(::UserProfileViewModel)
    viewModelOf(::StatsViewModel)
    viewModelOf(::AppCustomizationViewModel)

    viewModel {
        OnboardingViewModel(
            settingsRepository = get(),
            permissionManager = get(),
            groqLLMClient = get(),
        )
    }
}
