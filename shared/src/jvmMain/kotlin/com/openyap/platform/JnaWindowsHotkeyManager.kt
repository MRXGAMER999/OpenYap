package com.openyap.platform

import com.openyap.model.HotkeyBinding
import com.openyap.model.HotkeyCapture
import com.openyap.model.HotkeyConfig
import com.openyap.model.HotkeyEvent
import com.openyap.model.HotkeyModifier
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.MSG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable

private const val WH_KEYBOARD_LL = 13
private const val WM_KEYDOWN = 0x0100
private const val WM_KEYUP = 0x0101
private const val WM_SYSKEYDOWN = 0x0104
private const val WM_SYSKEYUP = 0x0105
private const val VK_ESCAPE = 0x1B
private const val CAPTURE_TIMEOUT_MS = 10_000L
private const val LLKHF_INJECTED = 0x10

private val LETTER_SCAN_CODE_MAP = mapOf(
    0x1E to 0x41, // A
    0x30 to 0x42, // B
    0x2E to 0x43, // C
    0x20 to 0x44, // D
    0x12 to 0x45, // E
    0x21 to 0x46, // F
    0x22 to 0x47, // G
    0x23 to 0x48, // H
    0x17 to 0x49, // I
    0x24 to 0x4A, // J
    0x25 to 0x4B, // K
    0x26 to 0x4C, // L
    0x32 to 0x4D, // M
    0x31 to 0x4E, // N
    0x18 to 0x4F, // O
    0x19 to 0x50, // P
    0x10 to 0x51, // Q
    0x13 to 0x52, // R
    0x1F to 0x53, // S
    0x14 to 0x54, // T
    0x16 to 0x55, // U
    0x2F to 0x56, // V
    0x11 to 0x57, // W
    0x2D to 0x58, // X
    0x15 to 0x59, // Y
    0x2C to 0x5A, // Z
)

internal class JnaWindowsHotkeyManager : HotkeyManager, Closeable {

    private enum class HoldKind {
        DICTATION,
        COMMAND,
    }

    private data class MatchedBinding(
        val kind: HoldKind,
        val binding: HotkeyBinding,
    )

    companion object {
        private val MODIFIER_VK_CODES = setOf(
            0x10, 0x11, 0x12,
            0xA0, 0xA1,
            0xA2, 0xA3,
            0xA4, 0xA5,
            0x5B, 0x5C,
        )
    }

    @OptIn(DelicateCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val win32Dispatcher = newSingleThreadContext("HotkeyThread")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(win32Dispatcher + SupervisorJob())

    private val _hotkeyEvents = MutableSharedFlow<HotkeyEvent>(extraBufferCapacity = 16)
    override val hotkeyEvents: SharedFlow<HotkeyEvent> = _hotkeyEvents.asSharedFlow()

    @Volatile
    private var config: HotkeyConfig = HotkeyConfig()
    private var listenerJob: Job? = null
    private val formatter = WindowsHotkeyDisplayFormatter()

    @Volatile
    private var pendingCapture: CompletableDeferred<HotkeyCapture>? = null

    private val captureMutex = Mutex()

    @Volatile
    private var keyboardHook: WinUser.HHOOK? = null

    @Volatile
    private var activeHold: HoldKind? = null

    @Volatile
    private var pressedModifiers: Set<HotkeyModifier> = emptySet()

    @Volatile
    private var captureModifiers: Set<HotkeyModifier> = emptySet()

    private val keyboardProc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
        if (nCode >= 0) {
            val vkCode = normalizeKeyCode(info.vkCode, info.scanCode, info.flags)
            val msgType = wParam.toInt()
            val isInjected = (info.flags and LLKHF_INJECTED) != 0

            if (isInjected) {
                return@LowLevelKeyboardProc User32.INSTANCE.CallNextHookEx(
                    keyboardHook, nCode, wParam,
                    WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(info.pointer)),
                )
            }

            updatePressedModifiers(vkCode, msgType)

            val capture = pendingCapture
            if (capture != null) {
                handleCaptureKeyEvent(vkCode, msgType, capture)
                return@LowLevelKeyboardProc WinDef.LRESULT(1)
            } else {
                val consumed = handleHotkeyEvent(vkCode, msgType)
                if (consumed) {
                    return@LowLevelKeyboardProc WinDef.LRESULT(1)
                }
            }
        }
        User32.INSTANCE.CallNextHookEx(
            keyboardHook, nCode, wParam,
            WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(info.pointer)),
        )
    }

    private fun handleHotkeyEvent(vkCode: Int, msgType: Int): Boolean {
        val currentModifiers = pressedModifiers
        val matchedBinding = matchedBindingFor(vkCode, currentModifiers) ?: run {
            if (handleHoldRelease(vkCode, msgType)) {
                return true
            }
            if (vkCode == VK_ESCAPE && activeHold != null && (msgType == WM_KEYDOWN || msgType == WM_SYSKEYDOWN)) {
                activeHold = null
                _hotkeyEvents.tryEmit(HotkeyEvent.CancelRecording)
                return true
            }
            return false
        }

        when (msgType) {
            WM_KEYDOWN, WM_SYSKEYDOWN -> {
                if (activeHold != null && activeHold != matchedBinding.kind) {
                    return true
                }
                if (activeHold != matchedBinding.kind) {
                    activeHold = matchedBinding.kind
                    emitHoldDown(matchedBinding.kind)
                }
            }

            WM_KEYUP, WM_SYSKEYUP -> {
                if (activeHold == matchedBinding.kind) {
                    activeHold = null
                    emitHoldUp(matchedBinding.kind)
                }
            }
        }

        return true
    }

    private fun matchedBindingFor(
        vkCode: Int,
        currentModifiers: Set<HotkeyModifier>,
    ): MatchedBinding? {
        val dictationBinding = config.startHotkey
            ?.takeIf { it.enabled && matchesConfiguredHotkey(vkCode, currentModifiers, it) }
            ?.let { MatchedBinding(HoldKind.DICTATION, it) }
        if (dictationBinding != null) return dictationBinding

        val commandBinding = config.commandHotkey
            ?.takeIf { config.commandHotkeyEnabled && it.enabled && matchesConfiguredHotkey(vkCode, currentModifiers, it) }
            ?.let { MatchedBinding(HoldKind.COMMAND, it) }
        return commandBinding
    }

    private fun handleHoldRelease(vkCode: Int, msgType: Int): Boolean {
        if (activeHold == null || (msgType != WM_KEYUP && msgType != WM_SYSKEYUP)) return false
        val binding = activeBinding() ?: return false
        val modifierOnlyBinding = bindingUsesOnlyModifiers(binding)
        val isMainKey = if (modifierOnlyBinding) {
            isModifierPartOfBinding(vkCode, binding)
        } else {
            vkCode == binding.platformKeyCode
        }
        val isRequiredModifier = isModifierForBinding(vkCode, binding.modifiers)
        if (!isMainKey && !isRequiredModifier) return false

        val releasedKind = activeHold ?: return false
        activeHold = null
        emitHoldUp(releasedKind)
        return true
    }

    private fun activeBinding(): HotkeyBinding? {
        return when (activeHold) {
            HoldKind.DICTATION -> config.startHotkey
            HoldKind.COMMAND -> config.commandHotkey?.takeIf { config.commandHotkeyEnabled }
            null -> null
        }
    }

    private fun emitHoldDown(kind: HoldKind) {
        _hotkeyEvents.tryEmit(
            when (kind) {
                HoldKind.DICTATION -> HotkeyEvent.DictationHoldDown
                HoldKind.COMMAND -> HotkeyEvent.CommandHoldDown
            }
        )
    }

    private fun emitHoldUp(kind: HoldKind) {
        _hotkeyEvents.tryEmit(
            when (kind) {
                HoldKind.DICTATION -> HotkeyEvent.DictationHoldUp
                HoldKind.COMMAND -> HotkeyEvent.CommandHoldUp
            }
        )
    }

    private fun isModifierForBinding(vkCode: Int, requiredModifiers: Set<HotkeyModifier>): Boolean {
        return when (vkCode) {
            0x10, 0xA0, 0xA1 -> HotkeyModifier.SHIFT in requiredModifiers
            0x11, 0xA2, 0xA3 -> HotkeyModifier.CTRL in requiredModifiers
            0x12, 0xA4, 0xA5 -> HotkeyModifier.ALT in requiredModifiers
            0x5B, 0x5C -> HotkeyModifier.META in requiredModifiers
            else -> false
        }
    }

    private fun handleCaptureKeyEvent(
        vkCode: Int,
        msgType: Int,
        capture: CompletableDeferred<HotkeyCapture>,
    ) {
        val modifier = modifierForKey(vkCode)
        if (modifier != null) {
            val modifiersBeforeEvent = captureModifiers
            captureModifiers = when (msgType) {
                WM_KEYDOWN, WM_SYSKEYDOWN -> captureModifiers + modifier
                WM_KEYUP, WM_SYSKEYUP -> captureModifiers - modifier
                else -> captureModifiers
            }
            if ((msgType == WM_KEYUP || msgType == WM_SYSKEYUP) && modifiersBeforeEvent.size >= 2) {
                val binding = HotkeyBinding(
                    platformKeyCode = vkCode,
                    modifiers = modifiersBeforeEvent - modifier,
                )
                capture.complete(
                    HotkeyCapture(
                        platformKeyCode = binding.platformKeyCode,
                        modifiers = binding.modifiers,
                        displayLabel = formatter.format(binding),
                    )
                )
            }
            return
        }

        if (msgType != WM_KEYDOWN && msgType != WM_SYSKEYDOWN) return

        val modifiers = captureModifiers
        val binding = HotkeyBinding(vkCode, modifiers)
        capture.complete(
            HotkeyCapture(
                platformKeyCode = vkCode,
                modifiers = modifiers,
                displayLabel = formatter.format(binding),
            )
        )
    }

    private fun matchesConfiguredHotkey(
        vkCode: Int,
        currentModifiers: Set<HotkeyModifier>,
        binding: HotkeyBinding,
    ): Boolean {
        return if (bindingUsesOnlyModifiers(binding)) {
            isModifierPartOfBinding(vkCode, binding) && comboModifiers(binding) == currentModifiers
        } else {
            vkCode == binding.platformKeyCode &&
                    effectiveModifiersForKey(vkCode, currentModifiers) == binding.modifiers
        }
    }

    private fun bindingUsesOnlyModifiers(binding: HotkeyBinding): Boolean {
        return isModifierKey(binding.platformKeyCode)
    }

    private fun isModifierPartOfBinding(vkCode: Int, binding: HotkeyBinding): Boolean {
        val modifier = modifierForKey(vkCode) ?: return false
        return modifier == modifierForKey(binding.platformKeyCode) || modifier in binding.modifiers
    }

    private fun comboModifiers(binding: HotkeyBinding): Set<HotkeyModifier> {
        val keyModifier = modifierForKey(binding.platformKeyCode)
        return if (keyModifier != null) binding.modifiers + keyModifier else binding.modifiers
    }

    private fun effectiveModifiersForKey(
        vkCode: Int,
        currentModifiers: Set<HotkeyModifier>,
    ): Set<HotkeyModifier> {
        val keyModifier = modifierForKey(vkCode)
        return if (keyModifier != null) currentModifiers - keyModifier else currentModifiers
    }

    private fun isModifierKey(vkCode: Int): Boolean = modifierForKey(vkCode) != null

    private fun modifierForKey(vkCode: Int): HotkeyModifier? {
        return when (vkCode) {
            0x10, 0xA0, 0xA1 -> HotkeyModifier.SHIFT
            0x11, 0xA2, 0xA3 -> HotkeyModifier.CTRL
            0x12, 0xA4, 0xA5 -> HotkeyModifier.ALT
            0x5B, 0x5C -> HotkeyModifier.META
            else -> null
        }
    }

    override fun setConfig(config: HotkeyConfig) {
        this.config = config
    }

    override fun startListening() {
        if (listenerJob?.isCompleted == false) return

        listenerJob = scope.launch {
            try {
                ensureMessageQueue()
                installHook()

                val msg = MSG()
                while (isActive) {
                    val result = User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)
                    if (!result) {
                        delay(10)
                    }
                }
            } finally {
                uninstallHook()
            }
        }
    }

    override fun stopListening() {
        pendingCapture?.cancel(CancellationException("Hotkey listening stopped."))
        pendingCapture = null
        val job = listenerJob ?: return
        job.invokeOnCompletion {
            if (listenerJob === job) {
                listenerJob = null
            }
        }
        job.cancel()
    }

    override suspend fun captureNextHotkey(): HotkeyCapture = captureMutex.withLock {
        withTimeout(CAPTURE_TIMEOUT_MS) {
            if (listenerJob?.isActive == true) {
                val deferred = CompletableDeferred<HotkeyCapture>()
                captureModifiers = emptySet()
                pendingCapture = deferred
                try {
                    deferred.await()
                } finally {
                    deferred.cancel()
                    pendingCapture = null
                    captureModifiers = emptySet()
                }
            } else {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                withContext(win32Dispatcher) {
                    ensureMessageQueue()
                    installHook()
                    try {
                        val deferred = CompletableDeferred<HotkeyCapture>()
                        captureModifiers = emptySet()
                        pendingCapture = deferred
                        try {
                            val msg = MSG()
                            while (!deferred.isCompleted && !deferred.isCancelled) {
                                User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)
                                delay(10)
                            }
                            deferred.await()
                        } finally {
                            pendingCapture = null
                            captureModifiers = emptySet()
                        }
                    } finally {
                        uninstallHook()
                    }
                }
            }
        }
    }

    private fun installHook() {
        if (keyboardHook != null) return
        val hMod = Kernel32.INSTANCE.GetModuleHandle(null)
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(
            WH_KEYBOARD_LL,
            keyboardProc,
            hMod,
            0,
        )
        if (keyboardHook == null) {
            val err = Kernel32.INSTANCE.GetLastError()
            throw IllegalStateException("SetWindowsHookEx(WH_KEYBOARD_LL) failed: Win32 error code $err")
        }
    }

    private fun uninstallHook() {
        keyboardHook?.let { hook ->
            User32.INSTANCE.UnhookWindowsHookEx(hook)
        }
        keyboardHook = null
        activeHold = null
        pressedModifiers = emptySet()
        captureModifiers = emptySet()
    }

    private fun ensureMessageQueue() {
        val msg = MSG()
        User32.INSTANCE.PeekMessage(msg, null, 0, 0, 0)
    }

    private fun updatePressedModifiers(vkCode: Int, msgType: Int) {
        val modifier = modifierForKey(vkCode) ?: return
        pressedModifiers = when (msgType) {
            WM_KEYDOWN, WM_SYSKEYDOWN -> pressedModifiers + modifier
            WM_KEYUP, WM_SYSKEYUP -> pressedModifiers - modifier
            else -> pressedModifiers
        }
    }

    private fun normalizeKeyCode(vkCode: Int, scanCode: Int, flags: Int): Int {
        if (vkCode in 0x41..0x5A) return vkCode
        if (flags and LLKHF_INJECTED != 0) return vkCode
        return LETTER_SCAN_CODE_MAP[scanCode] ?: vkCode
    }

    override fun close() {
        stopListening()
        scope.cancel()
        @OptIn(DelicateCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        win32Dispatcher.close()
    }
}
