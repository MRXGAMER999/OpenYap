package com.openyap.platform

import com.openyap.model.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class WindowsPermissionManager : PermissionManager {

    override suspend fun checkMicrophonePermission(): PermissionStatus =
        withContext(Dispatchers.IO) {
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

    override fun openMicrophoneSettings(): Boolean {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                "win" in os -> {
                    ProcessBuilder("cmd", "/c", "start", "ms-settings:privacy-microphone").start()
                    true
                }
                "mac" in os || "darwin" in os -> {
                    ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone").start()
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
