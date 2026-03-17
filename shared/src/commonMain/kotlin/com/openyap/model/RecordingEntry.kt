package com.openyap.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class RecordingWorkflowType {
    DICTATION,
    COMMAND,
}

@Serializable
data class RecordingEntry(
    val id: String,
    val recordedAt: Instant,
    val durationSeconds: Int,
    val response: String,
    val targetApp: String = "",
    val model: String = "",
    val isFallback: Boolean = false,
    val workflowType: RecordingWorkflowType = RecordingWorkflowType.DICTATION,
)
