package com.openyap.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface AppToneDao {

    @Query("SELECT * FROM app_tones")
    suspend fun getAll(): List<AppToneEntity>

    @Query("SELECT * FROM app_tones WHERE appName = :appName")
    suspend fun getByAppName(appName: String): AppToneEntity?

    @Upsert
    suspend fun upsert(tone: AppToneEntity)

    @Query("DELETE FROM app_tones WHERE appName = :appName")
    suspend fun deleteByAppName(appName: String)
}
