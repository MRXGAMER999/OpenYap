package com.openyap.platform

data class ClipboardSnapshotToken(
    val id: String,
)

sealed interface SelectionCaptureResult {
    data class Success(
        val selectedText: String,
        val clipboardSnapshotToken: ClipboardSnapshotToken,
        val sourceWindow: ForegroundWindowContext,
    ) : SelectionCaptureResult

    data object Empty : SelectionCaptureResult

    data object Failure : SelectionCaptureResult
}
