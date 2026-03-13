package com.openyap.platform

import com.openyap.model.OverlayState

interface OverlayController {
    fun show()
    fun updateState(state: OverlayState)
    fun updateLevel(level: Float)
    fun updateDuration(seconds: Int)
    fun dismiss()
    fun flashProcessing()
}
