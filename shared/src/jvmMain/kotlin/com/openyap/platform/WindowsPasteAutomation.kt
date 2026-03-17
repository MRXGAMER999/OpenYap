package com.openyap.platform

import com.openyap.model.HotkeyBinding
import com.openyap.model.HotkeyModifier
import com.openyap.platform.NativeAudioBridge.readLastError
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinUser.INPUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WindowsPasteAutomation(
    private val clipboardContentWriter: (Transferable) -> Unit = {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(it, null)
    },
) : PasteAutomation {

    private interface ClipboardUser32 : com.sun.jna.win32.StdCallLibrary {
        fun GetClipboardSequenceNumber(): Int
        fun IsIconic(hWnd: HWND): Boolean
    }

    companion object {
        private const val CLIPBOARD_SETTLE_DELAY_MS = 100L
        private const val PASTE_SETTLE_DELAY_MS = 150L
        private const val CLIPBOARD_WRITE_DELAY_MS = 50L
        private const val FOCUS_SETTLE_DELAY_MS = 80L
        private const val VK_CONTROL = 0x11
        private const val VK_SHIFT = 0x10
        private const val VK_MENU = 0x12
        private const val VK_LWIN = 0x5B
        private const val VK_RWIN = 0x5C
        private const val VK_C = 0x43
        private const val VK_V = 0x56
        private const val KEYEVENTF_KEYUP = 0x0002
        private const val INPUT_KEYBOARD = 1
        private const val SW_RESTORE = 9
    }

    private data class ClipboardSnapshot(
        val contents: Transferable,
    )

    private val clipboardUser32 by lazy {
        Native.load("user32", ClipboardUser32::class.java)
    }

    private val foregroundDetector by lazy { WindowsForegroundAppDetector() }
    private val clipboardSnapshots = ConcurrentHashMap<String, ClipboardSnapshot>()

    override suspend fun captureSelectedText(activeCommandHotkey: HotkeyBinding?): SelectionCaptureResult = withContext(Dispatchers.IO) {
        val sourceWindow = foregroundDetector.getForegroundWindowContext()
        if (sourceWindow.windowHandle == null) {
            return@withContext SelectionCaptureResult.Failure
        }

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val snapshotToken = try {
            storeClipboardSnapshot(clipboard.getContents(null))
        } catch (_: Exception) {
            return@withContext SelectionCaptureResult.Failure
        }

        try {
            val sequenceBefore = clipboardUser32.GetClipboardSequenceNumber().toLong()
            sendPlainCtrlC(activeCommandHotkey)
            delay(CLIPBOARD_SETTLE_DELAY_MS)

            val sequenceAfter = clipboardUser32.GetClipboardSequenceNumber().toLong()
            if (sequenceAfter == sequenceBefore) {
                restoreClipboard(snapshotToken)
                return@withContext SelectionCaptureResult.Empty
            }

            val clipboardText = readClipboardText(clipboard)
            if (clipboardText == null) {
                restoreClipboard(snapshotToken)
                return@withContext SelectionCaptureResult.Failure
            }
            if (clipboardText.isBlank()) {
                restoreClipboard(snapshotToken)
                return@withContext SelectionCaptureResult.Empty
            }

            SelectionCaptureResult.Success(
                selectedText = clipboardText,
                clipboardSnapshotToken = snapshotToken,
                sourceWindow = sourceWindow,
            )
        } catch (_: Exception) {
            runCatching { restoreClipboard(snapshotToken) }
            SelectionCaptureResult.Failure
        }
    }

    override suspend fun restoreClipboard(snapshotToken: ClipboardSnapshotToken) = withContext(Dispatchers.IO) {
        val snapshot = clipboardSnapshots[snapshotToken.id] ?: return@withContext
        writeClipboardContents(snapshot.contents)
        clipboardSnapshots.remove(snapshotToken.id, snapshot)
    }

    override suspend fun pasteTextToWindow(
        text: String,
        targetWindow: ForegroundWindowContext,
        snapshotToken: ClipboardSnapshotToken?,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val targetHandle = targetWindow.windowHandle
                    ?: throw IllegalStateException("Selection source window handle is unavailable.")
                if (!restoreFocusToWindow(targetHandle)) {
                    throw IllegalStateException("Failed to restore focus to the original window.")
                }

                val foregroundHandle = currentForegroundHandle()
                if (foregroundHandle != targetHandle) {
                    throw IllegalStateException("Paste target changed before replacement.")
                }

                writeClipboardContents(StringSelection(text))
                delay(CLIPBOARD_WRITE_DELAY_MS)
                val pasteForegroundHandle = currentForegroundHandle()
                if (pasteForegroundHandle != targetHandle) {
                    throw IllegalStateException("Paste target changed before sending paste input.")
                }
                sendCtrlV()
                delay(PASTE_SETTLE_DELAY_MS)
            } finally {
                snapshotToken?.let { restoreClipboard(it) }
            }
        }
    }

    override suspend fun pasteText(text: String, restoreClipboard: Boolean) {
        withContext(Dispatchers.IO) {
            val native = NativeAudioBridge.instance
            if (native != null) {
                pasteNative(native, text, restoreClipboard)
            } else {
                pasteJna(text, restoreClipboard)
            }
        }
    }

    private fun pasteNative(
        native: NativeAudioBridge.OpenYapNative,
        text: String,
        restoreClipboard: Boolean,
    ) {
        val result = native.openyap_paste_text(
            WString(text),
            if (restoreClipboard) 1 else 0,
        )
        if (result != 0) {
            val reason = native.readLastError() ?: "unknown error (code $result)"
            System.err.println("Native paste failed: $reason - falling back to JNA path")
            pasteJnaBlocking(text, restoreClipboard)
        }
    }

    private suspend fun pasteJna(text: String, restoreClipboard: Boolean) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val originalContent = clipboard.getContents(null)
        writeClipboardContents(StringSelection(text))
        delay(CLIPBOARD_WRITE_DELAY_MS)
        sendCtrlV()
        if (restoreClipboard) {
            delay(PASTE_SETTLE_DELAY_MS)
            writeClipboardContents(originalContent)
        }
    }

    private fun pasteJnaBlocking(text: String, restoreClipboard: Boolean) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val originalContent = clipboard.getContents(null)
        writeClipboardContents(StringSelection(text))
        Thread.sleep(CLIPBOARD_WRITE_DELAY_MS)
        sendCtrlV()
        if (restoreClipboard) {
            Thread.sleep(PASTE_SETTLE_DELAY_MS)
            writeClipboardContents(originalContent)
        }
    }

    private fun storeClipboardSnapshot(originalContent: Transferable?): ClipboardSnapshotToken {
        val token = ClipboardSnapshotToken(UUID.randomUUID().toString())
        val snapshot = ClipboardSnapshot(originalContent ?: StringSelection(""))
        clipboardSnapshots[token.id] = snapshot
        return token
    }

    internal fun createClipboardSnapshotForTest(originalContent: Transferable?): ClipboardSnapshotToken {
        return storeClipboardSnapshot(originalContent)
    }

    internal fun hasClipboardSnapshotForTest(snapshotToken: ClipboardSnapshotToken): Boolean {
        return clipboardSnapshots.containsKey(snapshotToken.id)
    }

    private fun writeClipboardContents(contents: Transferable) {
        clipboardContentWriter(contents)
    }

    private fun readClipboardText(clipboard: java.awt.datatransfer.Clipboard): String? {
        val contents = clipboard.getContents(null) ?: return null
        if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return null
        }
        return contents.getTransferData(DataFlavor.stringFlavor) as? String
    }

    private fun restoreFocusToWindow(windowHandle: Long): Boolean {
        val hwnd = HWND(Pointer.createConstant(windowHandle))
        if (clipboardUser32.IsIconic(hwnd)) {
            User32.INSTANCE.ShowWindow(hwnd, SW_RESTORE)
        }
        User32.INSTANCE.BringWindowToTop(hwnd)
        val focused = User32.INSTANCE.SetForegroundWindow(hwnd)
        if (!focused) {
            return false
        }
        Thread.sleep(FOCUS_SETTLE_DELAY_MS)
        return currentForegroundHandle() == windowHandle
    }

    private fun currentForegroundHandle(): Long? {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return null
        return Pointer.nativeValue(hwnd.pointer)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendPlainCtrlC(activeCommandHotkey: HotkeyBinding?) {
        val neutralizingKeyUps = buildList {
            activeCommandHotkey
                ?.takeIf { it.enabled }
                ?.let { binding ->
                    add(binding.platformKeyCode)
                    addAll(binding.modifiers.flatMap(::modifierKeyUps))
                }
                ?: run {
                    add(VK_SHIFT)
                    add(VK_C)
                }
        }
        val inputs = INPUT().toArray(neutralizingKeyUps.size + 4) as Array<INPUT>
        neutralizingKeyUps.forEachIndexed { index, keyCode ->
            keyInput(inputs[index], keyCode, true)
        }
        val copyStart = neutralizingKeyUps.size
        keyInput(inputs[copyStart], VK_CONTROL, false)
        keyInput(inputs[copyStart + 1], VK_C, false)
        keyInput(inputs[copyStart + 2], VK_C, true)
        keyInput(inputs[copyStart + 3], VK_CONTROL, true)
        User32.INSTANCE.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendCtrlV() {
        val inputs = INPUT().toArray(4) as Array<INPUT>
        keyInput(inputs[0], VK_CONTROL, false)
        keyInput(inputs[1], VK_V, false)
        keyInput(inputs[2], VK_V, true)
        keyInput(inputs[3], VK_CONTROL, true)
        User32.INSTANCE.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())
    }

    private fun keyInput(input: INPUT, keyCode: Int, keyUp: Boolean) {
        input.type = DWORD(INPUT_KEYBOARD.toLong())
        input.input.setType("ki")
        input.input.ki.wVk = WORD(keyCode.toLong())
        input.input.ki.dwFlags = DWORD(if (keyUp) KEYEVENTF_KEYUP.toLong() else 0L)
    }

    private fun modifierKeyUps(modifier: HotkeyModifier): List<Int> {
        return when (modifier) {
            HotkeyModifier.CTRL -> listOf(VK_CONTROL)
            HotkeyModifier.SHIFT -> listOf(VK_SHIFT)
            HotkeyModifier.ALT -> listOf(VK_MENU)
            HotkeyModifier.META -> listOf(VK_LWIN, VK_RWIN)
        }
    }
}
