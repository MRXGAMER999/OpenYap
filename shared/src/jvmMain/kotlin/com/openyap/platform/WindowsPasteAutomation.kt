package com.openyap.platform

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
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            var originalContent: Transferable? = null

            if (restoreClipboard) {
                originalContent = clipboard.getContents(null)
            }

            clipboard.setContents(StringSelection(text), null)

            delay(150)

            sendCtrlV()

            if (restoreClipboard && originalContent != null) {
                // Wait for the target app to consume the paste before restoring
                delay(700)
                try {
                    clipboard.setContents(originalContent, null)
                } catch (_: Exception) {
                    // Ignore — previous clipboard owner may have gone away
                }
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
