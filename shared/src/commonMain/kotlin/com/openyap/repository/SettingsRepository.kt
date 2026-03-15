package com.openyap.repository

import com.openyap.model.AppSettings

interface SettingsRepository {
    suspend fun loadSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)

    /**
     * Atomically loads the current settings, applies [transform], and saves
     * the result. This avoids the race condition where two concurrent
     * load-modify-save sequences overwrite each other's changes.
     *
     * Returns the settings after the transform has been applied and saved.
     */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings

    suspend fun loadApiKey(): String?
    suspend fun saveApiKey(key: String)

    suspend fun loadGroqApiKey(): String?
    suspend fun saveGroqApiKey(key: String)

    suspend fun loadAppTone(appName: String): String?
    suspend fun saveAppTone(appName: String, tone: String)
    suspend fun loadAllAppTones(): Map<String, String>

    suspend fun loadAppPrompt(appName: String): String?
    suspend fun saveAppPrompt(appName: String, prompt: String)
    suspend fun loadAllAppPrompts(): Map<String, String>

    /** Removes all tone and prompt customizations for [appName]. */
    suspend fun removeAppCustomization(appName: String)
}
