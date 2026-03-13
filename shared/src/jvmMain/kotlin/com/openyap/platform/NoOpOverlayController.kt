package com.openyap.platform

import com.openyap.model.OverlayState

/**
 * No-op overlay for MVP. Real overlay (second Compose Window) added in Phase 3.
 */
class NoOpOverlayController : OverlayController {
    override fun show() {}
    override fun updateState(state: OverlayState) {}
    override fun updateLevel(level: Float) {}
    override fun updateDuration(seconds: Int) {}
    override fun dismiss() {}
    override fun flashMessage(message: String) {}
    override fun flashProcessing() {}
}
