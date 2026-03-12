package com.openyap.repository

import com.openyap.model.UserProfile

interface UserProfileRepository {
    suspend fun loadProfile(): UserProfile
    suspend fun saveProfile(profile: UserProfile)
}
