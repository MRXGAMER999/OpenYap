package com.openyap.platform

import com.openyap.model.PermissionStatus

interface PermissionManager {
    suspend fun checkMicrophonePermission(): PermissionStatus
    fun openMicrophoneSettings()
}
