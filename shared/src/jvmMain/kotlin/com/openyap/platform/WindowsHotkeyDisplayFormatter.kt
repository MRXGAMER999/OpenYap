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
        val keyName = KeyEvent.getKeyText(binding.platformKeyCode)
        return if (mods.isNotEmpty()) "$mods+$keyName" else keyName
    }
}
