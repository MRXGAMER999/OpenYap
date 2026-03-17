package com.openyap.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "recording_entries")
data class RecordingEntryEntity(
    @PrimaryKey val id: String,
    val recordedAtMillis: Long,
    val durationSeconds: Int,
    val response: String,
    val targetApp: String = "",
    val model: String = "",
    val isFallback: Boolean = false,
    val workflowType: String = "DICTATION",
)
