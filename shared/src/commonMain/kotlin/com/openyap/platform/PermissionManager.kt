package com.openyap.platform

import com.openyap.model.PermissionStatus

interface PermissionManager {
    suspend fun checkMicrophonePermission(): PermissionStatus

    /**
     * Attempts to open the OS microphone/privacy settings.
     * Returns `true` if the settings app was launched successfully,
     * `false` if the platform does not support this action or an error occurred.
     */
    fun openMicrophoneSettings(): Boolean
}
