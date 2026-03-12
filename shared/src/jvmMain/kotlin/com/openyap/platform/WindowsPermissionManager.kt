package com.openyap.platform

import com.openyap.model.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class WindowsPermissionManager : PermissionManager {

    override suspend fun checkMicrophonePermission(): PermissionStatus = withContext(Dispatchers.IO) {
        try {
            val format = AudioFormat(16000f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (AudioSystem.isLineSupported(info)) {
                val line = AudioSystem.getLine(info) as TargetDataLine
                line.open(format)
                line.close()
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        } catch (_: Exception) {
            PermissionStatus.DENIED
        }
    }

    override fun openMicrophoneSettings() {
        try {
            ProcessBuilder("cmd", "/c", "start", "ms-settings:privacy-microphone").start()
        } catch (_: Exception) {
            // Best-effort
        }
    }
}
