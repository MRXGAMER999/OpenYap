package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val geminiModel: String = "gemini-2.0-flash",
    val hotkeyConfig: HotkeyConfig = HotkeyConfig(),
    val genZEnabled: Boolean = false,
    val phraseExpansionEnabled: Boolean = true,
    val dismissedUpdateVersion: String? = null,
    val onboardingCompleted: Boolean = false,
    val audioFeedbackEnabled: Boolean = true,
    val startMinimized: Boolean = false,
)
