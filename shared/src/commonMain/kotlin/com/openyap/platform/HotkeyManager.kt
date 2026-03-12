package com.openyap.platform

import com.openyap.model.HotkeyCapture
import com.openyap.model.HotkeyConfig
import com.openyap.model.HotkeyEvent
import kotlinx.coroutines.flow.SharedFlow

interface HotkeyManager {
    fun setConfig(config: HotkeyConfig)
    fun startListening()
    fun stopListening()
    suspend fun captureNextHotkey(): HotkeyCapture
    val hotkeyEvents: SharedFlow<HotkeyEvent>
}
