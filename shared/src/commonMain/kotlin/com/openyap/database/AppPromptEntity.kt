package com.openyap.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "app_prompts")
data class AppPromptEntity(
    @PrimaryKey val appName: String,
    val prompt: String,
)
