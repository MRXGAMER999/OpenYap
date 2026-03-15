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

internal class JnaWindowsHotkeyManager : HotkeyManager, Closeable {

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
    private var isHoldDown = false

    private val keyboardProc = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
        if (nCode >= 0) {
            val vkCode = info.vkCode
            val msgType = wParam.toInt()
            val isInjected = (info.flags and LLKHF_INJECTED) != 0

            if (isInjected) {
                return@LowLevelKeyboardProc User32.INSTANCE.CallNextHookEx(
                    keyboardHook, nCode, wParam,
                    WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(info.pointer)),
                )
            }

            val capture = pendingCapture
            if (capture != null) {
                handleCaptureKeyEvent(vkCode, msgType, capture)
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
        val currentModifiers = detectCurrentModifiers()
        val binding = config.startHotkey ?: return false
        if (!binding.enabled) return false
        val modifierOnlyBinding = bindingUsesOnlyModifiers(binding)

        if (matchesConfiguredHotkey(vkCode, currentModifiers, binding)) {
            when (msgType) {
                WM_KEYDOWN, WM_SYSKEYDOWN -> {
                    if (!isHoldDown) {
                        isHoldDown = true
                        _hotkeyEvents.tryEmit(HotkeyEvent.HoldDown)
                    }
                }

                WM_KEYUP, WM_SYSKEYUP -> {
                    isHoldDown = false
                    _hotkeyEvents.tryEmit(HotkeyEvent.HoldUp)
                }
            }
            return !modifierOnlyBinding
        }

        if (isHoldDown && (msgType == WM_KEYUP || msgType == WM_SYSKEYUP)) {
            val isMainKey = if (modifierOnlyBinding) {
                isModifierPartOfBinding(vkCode, binding)
            } else {
                vkCode == binding.platformKeyCode
            }
            val isRequiredModifier = isModifierForBinding(vkCode, binding.modifiers)
            if (isMainKey || isRequiredModifier) {
                isHoldDown = false
                _hotkeyEvents.tryEmit(HotkeyEvent.HoldUp)
                return !modifierOnlyBinding && !isModifierKey(vkCode)
            }
        }

        if (vkCode == VK_ESCAPE && isHoldDown && (msgType == WM_KEYDOWN || msgType == WM_SYSKEYDOWN)) {
            isHoldDown = false
            _hotkeyEvents.tryEmit(HotkeyEvent.CancelRecording)
            return true
        }

        return false
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
        if (msgType != WM_KEYDOWN && msgType != WM_SYSKEYDOWN) return

        val modifiers = effectiveModifiersForKey(vkCode, detectCurrentModifiers())
        if (vkCode in MODIFIER_VK_CODES && modifiers.isEmpty()) return
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
        if (listenerJob?.isActive == true) return

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
        val job = listenerJob
        listenerJob = null
        job?.cancel()
    }

    override suspend fun captureNextHotkey(): HotkeyCapture = captureMutex.withLock {
        withTimeout(CAPTURE_TIMEOUT_MS) {
            if (listenerJob?.isActive == true) {
                val deferred = CompletableDeferred<HotkeyCapture>()
                pendingCapture = deferred
                try {
                    deferred.await()
                } finally {
                    deferred.cancel()
                    pendingCapture = null
                }
            } else {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                withContext(win32Dispatcher) {
                    ensureMessageQueue()
                    installHook()
                    try {
                        val deferred = CompletableDeferred<HotkeyCapture>()
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
        isHoldDown = false
    }

    private fun ensureMessageQueue() {
        val msg = MSG()
        User32.INSTANCE.PeekMessage(msg, null, 0, 0, 0)
    }

    private fun detectCurrentModifiers(): Set<HotkeyModifier> {
        val user32 = User32.INSTANCE
        return buildSet {
            if (user32.GetAsyncKeyState(0x11).toInt() and 0x8000 != 0) add(HotkeyModifier.CTRL)
            if (user32.GetAsyncKeyState(0x10).toInt() and 0x8000 != 0) add(HotkeyModifier.SHIFT)
            if (user32.GetAsyncKeyState(0x12).toInt() and 0x8000 != 0) add(HotkeyModifier.ALT)
            if (user32.GetAsyncKeyState(0x5B).toInt() and 0x8000 != 0 ||
                user32.GetAsyncKeyState(0x5C).toInt() and 0x8000 != 0
            ) add(HotkeyModifier.META)
        }
    }

    override fun close() {
        stopListening()
        scope.cancel()
        @OptIn(DelicateCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        win32Dispatcher.close()
    }
}
