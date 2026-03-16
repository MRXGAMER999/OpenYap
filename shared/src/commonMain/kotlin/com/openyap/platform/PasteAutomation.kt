package com.openyap.platform

import com.openyap.model.HotkeyBinding

interface PasteAutomation {
    suspend fun captureSelectedText(activeCommandHotkey: HotkeyBinding? = null): SelectionCaptureResult
    suspend fun restoreClipboard(snapshotToken: ClipboardSnapshotToken)
    suspend fun pasteTextToWindow(
        text: String,
        targetWindow: ForegroundWindowContext,
        snapshotToken: ClipboardSnapshotToken? = null,
    )

    suspend fun pasteText(text: String, restoreClipboard: Boolean = true)
}
