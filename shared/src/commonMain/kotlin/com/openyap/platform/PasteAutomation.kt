package com.openyap.platform

interface PasteAutomation {
    suspend fun pasteText(text: String, restoreClipboard: Boolean = true)
}
