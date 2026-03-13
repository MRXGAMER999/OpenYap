package com.openyap.platform

interface StartupManager {
    val isSupported: Boolean

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)
}

class NoOpStartupManager : StartupManager {
    override val isSupported: Boolean = false

    override suspend fun isEnabled(): Boolean = false

    override suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            error("Launch on startup is not supported on this platform.")
        }
    }
}
