package com.openyap.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "app_tones")
data class AppToneEntity(
    @PrimaryKey val appName: String,
    val tone: String,
)
