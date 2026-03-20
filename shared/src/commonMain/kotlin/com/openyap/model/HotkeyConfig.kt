package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
data class HotkeyConfig(
    val startHotkey: HotkeyBinding? = HotkeyBinding(
        platformKeyCode = 0x52, // VK_R
        modifiers = setOf(HotkeyModifier.CTRL, HotkeyModifier.SHIFT),
    ),
    val commandHotkey: HotkeyBinding? = HotkeyBinding(
        platformKeyCode = 0x43, // VK_C
        modifiers = setOf(HotkeyModifier.CTRL, HotkeyModifier.SHIFT),
    ),
    val commandHotkeyEnabled: Boolean = true,
    val stopHotkey: HotkeyBinding? = null,
    val holdHotkey: HotkeyBinding? = null,
)

fun HotkeyBinding.matches(other: HotkeyBinding): Boolean {
    return platformKeyCode == other.platformKeyCode && modifiers == other.modifiers
}

fun HotkeyConfig.hasCommandHotkeyConflict(): Boolean {
    val dictation = startHotkey ?: return false
    val command = commandHotkey ?: return false
    if (!commandHotkeyEnabled || !dictation.enabled || !command.enabled) return false
    return dictation.matches(command)
}

fun HotkeyConfig.commandHotkeyValidationError(): String? {
    if (!commandHotkeyEnabled) return null
    val command = commandHotkey ?: return "Command hotkey is required when command mode is enabled."
    if (!command.enabled) return "Command hotkey is disabled."
    if (command.modifiers.isEmpty()) {
        return "Command hotkey must include at least one modifier."
    }
    if (hasCommandHotkeyConflict()) {
        return "Command hotkey must be different from the dictation hotkey."
    }
    return null
}

fun HotkeyConfig.dictationHotkeyValidationError(): String? {
    val dictation = startHotkey ?: return null
    if (!dictation.enabled) return null
    if (dictation.modifiers.isEmpty()) {
        return "Dictation hotkey must include at least one modifier."
    }
    return null
}

fun HotkeyConfig.effectiveRuntimeConfig(): HotkeyConfig {
    val dictationBinding = startHotkey?.takeIf { dictationHotkeyValidationError() == null }
    val commandEnabled = commandHotkeyEnabled && commandHotkeyValidationError() == null
    return copy(
        startHotkey = dictationBinding,
        commandHotkeyEnabled = commandEnabled,
    )
}

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
    data object DictationHoldDown : HotkeyEvent
    data object DictationHoldUp : HotkeyEvent
    data object CommandHoldDown : HotkeyEvent
    data object CommandHoldUp : HotkeyEvent
    data object CancelRecording : HotkeyEvent
}
