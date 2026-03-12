package com.openyap.platform

import com.openyap.model.HotkeyBinding

interface HotkeyDisplayFormatter {
    fun format(binding: HotkeyBinding): String
}
