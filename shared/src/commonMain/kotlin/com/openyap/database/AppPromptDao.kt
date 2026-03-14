package com.openyap.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface AppPromptDao {

    @Query("SELECT * FROM app_prompts")
    suspend fun getAll(): List<AppPromptEntity>

    @Query("SELECT * FROM app_prompts WHERE appName = :appName")
    suspend fun getByAppName(appName: String): AppPromptEntity?

    @Upsert
    suspend fun upsert(prompt: AppPromptEntity)

    @Query("DELETE FROM app_prompts WHERE appName = :appName")
    suspend fun deleteByAppName(appName: String)
}
