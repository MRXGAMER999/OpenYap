package com.openyap.di

import com.openyap.service.DictionaryEngine
import com.openyap.service.GeminiClient
import com.openyap.service.GroqWhisperClient
import com.openyap.viewmodel.AppCustomizationViewModel
import com.openyap.viewmodel.DictionaryViewModel
import com.openyap.viewmodel.HistoryViewModel
import com.openyap.viewmodel.OnboardingViewModel
import com.openyap.viewmodel.RecordingViewModel
import com.openyap.viewmodel.SettingsViewModel
import com.openyap.viewmodel.StatsViewModel
import com.openyap.viewmodel.UserProfileViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedModule = module {
    singleOf(::DictionaryEngine)
    singleOf(::SettingsViewModel)
    singleOf(::HistoryViewModel)
    singleOf(::OnboardingViewModel)
    singleOf(::DictionaryViewModel)
    singleOf(::UserProfileViewModel)
    singleOf(::StatsViewModel)
    singleOf(::AppCustomizationViewModel)

    // RecordingViewModel needs explicit wiring: two String params require named
    // qualifiers, and groqWhisperClient must resolve to GroqWhisperClient (not
    // the ambiguous TranscriptionService interface).
    single {
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
}
