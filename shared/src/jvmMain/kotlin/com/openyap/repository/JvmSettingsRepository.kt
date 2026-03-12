package com.openyap.repository

import com.openyap.model.AppSettings
import com.openyap.platform.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*

class JvmSettingsRepository(
    private val secureStorage: SecureStorage,
    private val dataDir: Path,
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val settingsFile get() = dataDir.resolve("settings.json")
    private val tonesFile get() = dataDir.resolve("app_tones.json")
    private val promptsFile get() = dataDir.resolve("app_prompts.json")

    override suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        try {
            if (settingsFile.exists()) {
                json.decodeFromString<AppSettings>(settingsFile.readText())
            } else AppSettings()
        } catch (_: Exception) {
            AppSettings()
        }
    }

    override suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        dataDir.createDirectories()
        settingsFile.writeText(json.encodeToString(settings))
    }

    override suspend fun loadApiKey(): String? = secureStorage.load("gemini_api_key")

    override suspend fun saveApiKey(key: String) = secureStorage.save("gemini_api_key", key)

    override suspend fun loadAppTone(appName: String): String? = withContext(Dispatchers.IO) {
        loadAllAppTones()[appName]
    }

    override suspend fun saveAppTone(appName: String, tone: String) = withContext(Dispatchers.IO) {
        val tones = loadAllAppTones().toMutableMap()
        tones[appName] = tone
        dataDir.createDirectories()
        tonesFile.writeText(json.encodeToString(tones))
    }

    override suspend fun loadAllAppTones(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            if (tonesFile.exists()) {
                json.decodeFromString<Map<String, String>>(tonesFile.readText())
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override suspend fun loadAppPrompt(appName: String): String? = withContext(Dispatchers.IO) {
        loadAllAppPrompts()[appName]
    }

    override suspend fun saveAppPrompt(appName: String, prompt: String) = withContext(Dispatchers.IO) {
        val prompts = loadAllAppPrompts().toMutableMap()
        prompts[appName] = prompt
        dataDir.createDirectories()
        promptsFile.writeText(json.encodeToString(prompts))
    }

    override suspend fun loadAllAppPrompts(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            if (promptsFile.exists()) {
                json.decodeFromString<Map<String, String>>(promptsFile.readText())
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
