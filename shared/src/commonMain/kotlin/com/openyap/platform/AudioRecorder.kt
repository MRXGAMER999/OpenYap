package com.openyap.platform

import kotlinx.coroutines.flow.StateFlow

interface AudioRecorder {
    suspend fun startRecording(outputPath: String)
    suspend fun stopRecording(): String
    val amplitudeFlow: StateFlow<Float>
    suspend fun hasPermission(): Boolean
}
