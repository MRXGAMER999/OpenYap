package com.openyap.platform

import com.openyap.model.*
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.MSG
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WindowsHotkeyManager : HotkeyManager {

    companion object {
        private const val HOTKEY_ID_START = 1
        private const val HOTKEY_ID_STOP = 2
        private const val WM_HOTKEY = 0x0312
    }

    private val _hotkeyEvents = MutableSharedFlow<HotkeyEvent>(extraBufferCapacity = 16)
    override val hotkeyEvents: SharedFlow<HotkeyEvent> = _hotkeyEvents.asSharedFlow()

    private var config: HotkeyConfig = HotkeyConfig()
    private var listenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var messageThreadId: Long = 0

    override fun setConfig(config: HotkeyConfig) {
        this.config = config
    }

    override fun startListening() {
        if (listenerJob?.isActive == true) return

        listenerJob = scope.launch(Dispatchers.IO) {
            messageThreadId = Thread.currentThread().id

            try {
                registerHotkeys()

                val msg = MSG()
                while (isActive) {
                    val result = User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)
                    if (result) {
                        if (msg.message == WM_HOTKEY) {
                            val id = msg.wParam.toInt()
                            val event = when (id) {
                                HOTKEY_ID_START -> HotkeyEvent.ToggleRecording
                                HOTKEY_ID_STOP -> HotkeyEvent.StopRecording
                                else -> null
                            }
                            event?.let { _hotkeyEvents.tryEmit(it) }
                        }
                        User32.INSTANCE.TranslateMessage(msg)
                        User32.INSTANCE.DispatchMessage(msg)
                    } else {
                        delay(10)
                    }
                }
            } finally {
                unregisterHotkeys()
            }
        }
    }

    override fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
    }

    override suspend fun captureNextHotkey(): HotkeyCapture {
        return withContext(Dispatchers.IO) {
            val msg = MSG()
            unregisterHotkeys()

            try {
                while (true) {
                    val result = User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)
                    if (result && msg.message == WM_HOTKEY) {
                        break
                    }
                    delay(10)
                }
            } finally {
                registerHotkeys()
            }

            val modFlags = (msg.lParam.toInt() and 0xFFFF)
            val vkCode = (msg.lParam.toInt() shr 16) and 0xFFFF

            HotkeyCapture(
                platformKeyCode = vkCode,
                modifiers = modFlagsToModifiers(modFlags),
                displayLabel = WindowsHotkeyDisplayFormatter().format(
                    HotkeyBinding(vkCode, modFlagsToModifiers(modFlags))
                ),
            )
        }
    }

    private fun registerHotkeys() {
        config.startHotkey?.let { binding ->
            if (binding.enabled) {
                User32.INSTANCE.RegisterHotKey(
                    null, HOTKEY_ID_START,
                    modifiersToFlags(binding.modifiers),
                    binding.platformKeyCode,
                )
            }
        }
        config.stopHotkey?.let { binding ->
            if (binding.enabled) {
                User32.INSTANCE.RegisterHotKey(
                    null, HOTKEY_ID_STOP,
                    modifiersToFlags(binding.modifiers),
                    binding.platformKeyCode,
                )
            }
        }
    }

    private fun unregisterHotkeys() {
        User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_START)
        User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_STOP)
    }

    private fun modifiersToFlags(modifiers: Set<HotkeyModifier>): Int {
        var flags = 0
        if (HotkeyModifier.ALT in modifiers) flags = flags or 0x0001
        if (HotkeyModifier.CTRL in modifiers) flags = flags or 0x0002
        if (HotkeyModifier.SHIFT in modifiers) flags = flags or 0x0004
        if (HotkeyModifier.META in modifiers) flags = flags or 0x0008
        return flags
    }

    private fun modFlagsToModifiers(flags: Int): Set<HotkeyModifier> = buildSet {
        if (flags and 0x0001 != 0) add(HotkeyModifier.ALT)
        if (flags and 0x0002 != 0) add(HotkeyModifier.CTRL)
        if (flags and 0x0004 != 0) add(HotkeyModifier.SHIFT)
        if (flags and 0x0008 != 0) add(HotkeyModifier.META)
    }
}
