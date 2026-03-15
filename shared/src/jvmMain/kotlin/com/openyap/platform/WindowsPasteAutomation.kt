package com.openyap.platform

import com.openyap.platform.NativeAudioBridge.readLastError
import com.sun.jna.WString
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinUser.INPUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

class WindowsPasteAutomation : PasteAutomation {

    companion object {
        private const val VK_CONTROL = 0x11
        private const val VK_V = 0x56
        private const val KEYEVENTF_KEYUP = 0x0002
        private const val INPUT_KEYBOARD = 1
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

    // ── Native path: clipboard write + SendInput in a single native call ──

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
            System.err.println("Native paste failed: $reason — falling back to JNA path")
            pasteJnaBlocking(text, restoreClipboard)
        }
    }

    // ── JNA fallback: the original implementation for when the DLL isn't loaded ──

    private suspend fun pasteJna(text: String, restoreClipboard: Boolean) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        var originalContent: Transferable? = null

        if (restoreClipboard) {
            originalContent = clipboard.getContents(null)
        }

        clipboard.setContents(StringSelection(text), null)

        delay(50)

        sendCtrlV()

        if (restoreClipboard && originalContent != null) {
            delay(700)
            try {
                clipboard.setContents(originalContent, null)
            } catch (_: Exception) {
                // Ignore — previous clipboard owner may have gone away
            }
        }
    }

    /**
     * Blocking variant of the JNA fallback used when the native call fails
     * and we're already on [Dispatchers.IO]. Uses [Thread.sleep] instead of
     * coroutine [delay] because this is called from a non-suspend context.
     */
    private fun pasteJnaBlocking(text: String, restoreClipboard: Boolean) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        var originalContent: Transferable? = null

        if (restoreClipboard) {
            originalContent = clipboard.getContents(null)
        }

        clipboard.setContents(StringSelection(text), null)

        Thread.sleep(50)

        sendCtrlV()

        if (restoreClipboard && originalContent != null) {
            Thread.sleep(700)
            try {
                clipboard.setContents(originalContent, null)
            } catch (_: Exception) {
                // Ignore — previous clipboard owner may have gone away
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendCtrlV() {
        val inputs = INPUT().toArray(4) as Array<INPUT>

        // Ctrl down
        inputs[0].type = DWORD(INPUT_KEYBOARD.toLong())
        inputs[0].input.setType("ki")
        inputs[0].input.ki.wVk = WORD(VK_CONTROL.toLong())
        inputs[0].input.ki.dwFlags = DWORD(0)

        // V down
        inputs[1].type = DWORD(INPUT_KEYBOARD.toLong())
        inputs[1].input.setType("ki")
        inputs[1].input.ki.wVk = WORD(VK_V.toLong())
        inputs[1].input.ki.dwFlags = DWORD(0)

        // V up
        inputs[2].type = DWORD(INPUT_KEYBOARD.toLong())
        inputs[2].input.setType("ki")
        inputs[2].input.ki.wVk = WORD(VK_V.toLong())
        inputs[2].input.ki.dwFlags = DWORD(KEYEVENTF_KEYUP.toLong())

        // Ctrl up
        inputs[3].type = DWORD(INPUT_KEYBOARD.toLong())
        inputs[3].input.setType("ki")
        inputs[3].input.ki.wVk = WORD(VK_CONTROL.toLong())
        inputs[3].input.ki.dwFlags = DWORD(KEYEVENTF_KEYUP.toLong())

        User32.INSTANCE.SendInput(DWORD(4), inputs, inputs[0].size())
    }
}
