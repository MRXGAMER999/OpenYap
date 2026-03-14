package com.openyap.repository

import com.openyap.database.OpenYapDatabase
import com.openyap.database.toDomain
import com.openyap.database.toEntity
import com.openyap.model.UserProfile

class RoomUserProfileRepository(
    private val database: OpenYapDatabase,
) : UserProfileRepository {

    override suspend fun loadProfile(): UserProfile {
        return database.userProfileDao().get()?.toDomain() ?: UserProfile()
    }

    override suspend fun saveProfile(profile: UserProfile) {
        database.userProfileDao().upsert(profile.toEntity())
    }
}
