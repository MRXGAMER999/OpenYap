package com.openyap.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
    val transcriptionProvider: String = "GROQ_WHISPER_GROQ",
    val groqModel: String = "whisper-large-v3",
    val groqLLMModel: String = "moonshotai/kimi-k2-instruct-0905",
    val hotkeyConfigJson: String = "{}",
    val genZEnabled: Boolean = false,
    val phraseExpansionEnabled: Boolean = false,
    val dictionaryEnabled: Boolean = true,
    val dismissedUpdateVersion: String? = null,
    val onboardingCompleted: Boolean = false,
    val audioFeedbackEnabled: Boolean = true,
    val soundFeedbackVolume: Float = 0.5f,
    val startMinimized: Boolean = false,
    val launchOnStartup: Boolean = false,
    val audioDeviceId: String? = null,
    val primaryUseCase: String = "GENERAL",
    val useCaseContext: String = "",
    val whisperLanguage: String = "en",
)
