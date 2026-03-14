package com.openyap.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)
}
