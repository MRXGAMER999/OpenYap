package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
enum class TranscriptionProvider {
    GEMINI,
    GROQ_WHISPER,
    GROQ_WHISPER_GEMINI,
    GROQ_WHISPER_GROQ,
}

@Serializable
enum class PrimaryUseCase {
    GENERAL,
    PROGRAMMING,
    BUSINESS,
    CREATIVE_WRITING,
}

@Serializable
data class AppSettings(
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
    val transcriptionProvider: TranscriptionProvider = TranscriptionProvider.GROQ_WHISPER_GROQ,
    val groqModel: String = "whisper-large-v3",
    val groqLLMModel: String = "moonshotai/kimi-k2-instruct-0905",
    val hotkeyConfig: HotkeyConfig = HotkeyConfig(),
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
    val primaryUseCase: PrimaryUseCase = PrimaryUseCase.GENERAL,
    val useCaseContext: String = "",
    val whisperLanguage: String = "en",
)
