package com.openyap.repository

import com.openyap.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*

class JvmUserProfileRepository(private val dataDir: Path) : UserProfileRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file get() = dataDir.resolve("user_profile.json")

    override suspend fun loadProfile(): UserProfile = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) {
                json.decodeFromString<UserProfile>(file.readText())
            } else UserProfile()
        } catch (_: Exception) {
            UserProfile()
        }
    }

    override suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        dataDir.createDirectories()
        file.writeText(json.encodeToString(UserProfile.serializer(), profile))
    }
}
