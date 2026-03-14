package com.openyap.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
}
