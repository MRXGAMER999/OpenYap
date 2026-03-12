package com.openyap.repository

import com.openyap.model.AppSettings
import com.openyap.model.HotkeyConfig

interface SettingsRepository {
    suspend fun loadSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)

    suspend fun loadApiKey(): String?
    suspend fun saveApiKey(key: String)

    suspend fun loadAppTone(appName: String): String?
    suspend fun saveAppTone(appName: String, tone: String)
    suspend fun loadAllAppTones(): Map<String, String>

    suspend fun loadAppPrompt(appName: String): String?
    suspend fun saveAppPrompt(appName: String, prompt: String)
    suspend fun loadAllAppPrompts(): Map<String, String>
}
