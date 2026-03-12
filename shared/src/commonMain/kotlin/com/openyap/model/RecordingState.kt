package com.openyap.model

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val durationSeconds: Int = 0, val amplitude: Float = 0f) : RecordingState
    data object Processing : RecordingState
    data class Success(val text: String, val charCount: Int) : RecordingState
    data class Error(val message: String) : RecordingState
}
