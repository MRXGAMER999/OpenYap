package com.openyap.platform

import com.openyap.model.AudioDevice
import kotlinx.coroutines.flow.StateFlow

interface AudioRecorder {
    suspend fun startRecording(
        outputPath: String,
        deviceId: String? = null,
        sensitivityPreset: RecordingSensitivityPreset = RecordingSensitivityPreset.NORMAL,
    )
    suspend fun stopRecording(): String
    val amplitudeFlow: StateFlow<Float>
    suspend fun hasPermission(): Boolean
    suspend fun listDevices(): List<AudioDevice>
}
