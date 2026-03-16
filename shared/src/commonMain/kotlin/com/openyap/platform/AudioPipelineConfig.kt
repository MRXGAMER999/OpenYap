package com.openyap.platform

data class AudioPipelineConfig(
    val audioRecorder: AudioRecorder,
    val audioMimeType: String,
    val audioFileExtension: String,
)
