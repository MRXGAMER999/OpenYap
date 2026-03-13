package com.openyap.platform

import com.openyap.model.HotkeyBinding
import com.openyap.model.HotkeyModifier
import java.awt.event.KeyEvent

class WindowsHotkeyDisplayFormatter : HotkeyDisplayFormatter {
    override fun format(binding: HotkeyBinding): String {
        val mods = binding.modifiers.sorted().joinToString("+") {
            when (it) {
                HotkeyModifier.CTRL -> "Ctrl"
                HotkeyModifier.ALT -> "Alt"
                HotkeyModifier.SHIFT -> "Shift"
                HotkeyModifier.META -> "Win"
            }
        }
        val keyName = getKeyName(binding.platformKeyCode)
        return if (mods.isNotEmpty()) "$mods+$keyName" else keyName
    }

    /**
     * Maps a Win32 virtual-key code to a human-readable name.
     * Falls back to [KeyEvent.getKeyText] for standard keys, but overrides
     * codes that Java AWT maps incorrectly (e.g. VK_LWIN 0x5B → "Open Bracket").
     */
    private fun getKeyName(vkCode: Int): String = when (vkCode) {
        0x5B -> "Left Win"
        0x5C -> "Right Win"
        else -> KeyEvent.getKeyText(vkCode)
    }
}
