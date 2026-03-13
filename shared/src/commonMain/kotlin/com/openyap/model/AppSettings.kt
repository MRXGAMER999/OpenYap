package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val geminiModel: String = "gemini-3.1-flash-lite-preview",
    val hotkeyConfig: HotkeyConfig = HotkeyConfig(),
    val genZEnabled: Boolean = false,
    val phraseExpansionEnabled: Boolean = false,
    val dictionaryEnabled: Boolean = true,
    val dismissedUpdateVersion: String? = null,
    val onboardingCompleted: Boolean = false,
    val audioFeedbackEnabled: Boolean = true,
    val startMinimized: Boolean = false,
)
