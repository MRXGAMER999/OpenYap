package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
data class HotkeyConfig(
    val startHotkey: HotkeyBinding? = HotkeyBinding(
        platformKeyCode = 0x52, // VK_R
        modifiers = setOf(HotkeyModifier.CTRL, HotkeyModifier.SHIFT),
    ),
    val stopHotkey: HotkeyBinding? = null,
    val holdHotkey: HotkeyBinding? = null,
)

@Serializable
data class HotkeyBinding(
    val platformKeyCode: Int,
    val modifiers: Set<HotkeyModifier>,
    val enabled: Boolean = true,
)

@Serializable
enum class HotkeyModifier { CTRL, ALT, SHIFT, META }

data class HotkeyCapture(
    val platformKeyCode: Int,
    val modifiers: Set<HotkeyModifier>,
    val displayLabel: String,
)

sealed interface HotkeyEvent {
    data object ToggleRecording : HotkeyEvent
    data object StartRecording : HotkeyEvent
    data object StopRecording : HotkeyEvent
    data object HoldDown : HotkeyEvent
    data object HoldUp : HotkeyEvent
    data object CancelRecording : HotkeyEvent
}
