package com.openyap.repository

import com.openyap.database.AppPromptEntity
import com.openyap.database.AppToneEntity
import com.openyap.database.OpenYapDatabase
import com.openyap.database.toDomain
import com.openyap.database.toEntity
import com.openyap.model.AppSettings
import com.openyap.platform.SecureStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

class RoomSettingsRepository(
    private val database: OpenYapDatabase,
    private val secureStorage: SecureStorage,
) : SettingsRepository {

    private data class CachedSecureValue(val value: String?)

    /** Guards load-modify-save sequences against concurrent overwrites. */
    private val settingsMutex = Mutex()
    private val cachedSettings = AtomicReference<AppSettings?>(null)
    private val cachedGeminiApiKey = AtomicReference<CachedSecureValue?>(null)
    private val cachedGroqApiKey = AtomicReference<CachedSecureValue?>(null)

    override suspend fun loadSettings(): AppSettings {
        cachedSettings.get()?.let { return it }

        val loaded = database.appSettingsDao().get()?.toDomain() ?: AppSettings()
        cachedSettings.compareAndSet(null, loaded)
        return cachedSettings.get() ?: loaded
    }

    override suspend fun saveSettings(settings: AppSettings) {
        database.appSettingsDao().upsert(settings.toEntity())
        cachedSettings.set(settings)
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        return settingsMutex.withLock {
            val current = loadSettings()
            val updated = transform(current)
            saveSettings(updated)
            updated
        }
    }

    override suspend fun loadApiKey(): String? =
        loadCachedSecureValue(cachedGeminiApiKey, "gemini_api_key")

    override suspend fun saveApiKey(key: String) {
        secureStorage.save("gemini_api_key", key)
        cachedGeminiApiKey.set(CachedSecureValue(key))
    }

    override suspend fun loadGroqApiKey(): String? =
        loadCachedSecureValue(cachedGroqApiKey, "groq_api_key")

    override suspend fun saveGroqApiKey(key: String) {
        secureStorage.save("groq_api_key", key)
        cachedGroqApiKey.set(CachedSecureValue(key))
    }

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

    override suspend fun clearAppCustomizations() {
        database.appToneDao().deleteAll()
        database.appPromptDao().deleteAll()
    }

    override suspend fun removeAppCustomization(appName: String) {
        database.appToneDao().deleteByAppName(appName)
        database.appPromptDao().deleteByAppName(appName)
    }

    private suspend fun loadCachedSecureValue(
        cache: AtomicReference<CachedSecureValue?>,
        key: String,
    ): String? {
        cache.get()?.let { return it.value }

        val loaded = secureStorage.load(key)
        cache.compareAndSet(null, CachedSecureValue(loaded))
        return cache.get()?.value ?: loaded
    }
}
