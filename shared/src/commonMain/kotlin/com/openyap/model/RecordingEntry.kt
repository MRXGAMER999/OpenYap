package com.openyap.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RecordingEntry(
    val id: String,
    val recordedAt: Instant,
    val durationSeconds: Int,
    val response: String,
    val targetApp: String = "",
    val model: String = "",
)
