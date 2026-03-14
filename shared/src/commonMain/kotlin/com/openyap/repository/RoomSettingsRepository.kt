package com.openyap.repository

import com.openyap.database.OpenYapDatabase
import com.openyap.database.toDomain
import com.openyap.database.toEntity
import com.openyap.model.AppSettings
import com.openyap.platform.SecureStorage
import com.openyap.database.AppToneEntity
import com.openyap.database.AppPromptEntity

class RoomSettingsRepository(
    private val database: OpenYapDatabase,
    private val secureStorage: SecureStorage,
) : SettingsRepository {

    override suspend fun loadSettings(): AppSettings {
        return database.appSettingsDao().get()?.toDomain() ?: AppSettings()
    }

    override suspend fun saveSettings(settings: AppSettings) {
        database.appSettingsDao().upsert(settings.toEntity())
    }

    override suspend fun loadApiKey(): String? = secureStorage.load("gemini_api_key")

    override suspend fun saveApiKey(key: String) = secureStorage.save("gemini_api_key", key)

    override suspend fun loadGroqApiKey(): String? = secureStorage.load("groq_api_key")

    override suspend fun saveGroqApiKey(key: String) = secureStorage.save("groq_api_key", key)

    override suspend fun loadAppTone(appName: String): String? {
        return database.appToneDao().getByAppName(appName)?.tone
    }

    override suspend fun saveAppTone(appName: String, tone: String) {
        database.appToneDao().upsert(AppToneEntity(appName = appName, tone = tone))
    }

    override suspend fun loadAllAppTones(): Map<String, String> {
        return database.appToneDao().getAll().associate { it.appName to it.tone }
    }

    override suspend fun loadAppPrompt(appName: String): String? {
        return database.appPromptDao().getByAppName(appName)?.prompt
    }

    override suspend fun saveAppPrompt(appName: String, prompt: String) {
        database.appPromptDao().upsert(AppPromptEntity(appName = appName, prompt = prompt))
    }

    override suspend fun loadAllAppPrompts(): Map<String, String> {
        return database.appPromptDao().getAll().associate { it.appName to it.prompt }
    }

    override suspend fun removeAppCustomization(appName: String) {
        database.appToneDao().deleteByAppName(appName)
        database.appPromptDao().deleteByAppName(appName)
    }
}
