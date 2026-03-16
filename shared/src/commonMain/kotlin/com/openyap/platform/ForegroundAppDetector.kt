package com.openyap.platform

data class ForegroundWindowContext(
    val appName: String?,
    val windowTitle: String?,
)

interface ForegroundAppDetector {
    fun getForegroundWindowContext(): ForegroundWindowContext
}
