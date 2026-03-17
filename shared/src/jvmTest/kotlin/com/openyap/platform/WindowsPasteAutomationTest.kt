package com.openyap.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.awt.datatransfer.StringSelection

class WindowsPasteAutomationTest {

    @Test
    fun failedRestoreKeepsSnapshotAvailableForRetry() = runTest {
        var attempts = 0
        val automation = WindowsPasteAutomation(
            clipboardContentWriter = {
                attempts += 1
                error("clipboard write failed")
            },
        )
        val token = automation.createClipboardSnapshotForTest(StringSelection("original"))

        val result = runCatching { automation.restoreClipboard(token) }

        assertTrue(result.isFailure)
        assertTrue(automation.hasClipboardSnapshotForTest(token))
        assertEquals(1, attempts)
    }

    @Test
    fun successfulRestoreRemovesSnapshot() = runTest {
        val automation = WindowsPasteAutomation(
            clipboardContentWriter = { },
        )
        val token = automation.createClipboardSnapshotForTest(StringSelection("original"))

        automation.restoreClipboard(token)

        assertFalse(automation.hasClipboardSnapshotForTest(token))
    }

    @Test
    fun restoreIfUnchangedSkipsRestoreWhenClipboardOwnershipIsStale() = runTest {
        var attempts = 0
        val automation = WindowsPasteAutomation(
            clipboardContentWriter = {
                attempts += 1
            },
        )
        val token = automation.createClipboardSnapshotForTest(StringSelection("original"))
        automation.markClipboardOwnershipForTest(token, sequenceNumber = -1L)

        automation.restoreClipboardIfUnchanged(token)

        assertEquals(0, attempts)
        assertTrue(automation.hasClipboardSnapshotForTest(token))
        assertNull(automation.getCurrentClipboardSnapshotToken())
    }
}
