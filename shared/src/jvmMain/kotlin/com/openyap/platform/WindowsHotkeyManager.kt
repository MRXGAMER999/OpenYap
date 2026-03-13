package com.openyap.platform

import com.openyap.model.HotkeyBinding
import com.openyap.model.HotkeyCapture
import com.openyap.model.HotkeyConfig
import com.openyap.model.HotkeyEvent
import com.openyap.model.HotkeyModifier
import com.openyap.platform.WindowsHotkeyManager.Companion.CAPTURE_TIMEOUT_MS
import com.sun.jna.platform.win32.User32
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable

/**
 * Windows hotkey manager using Win32 RegisterHotKey / PeekMessage.
 *
 * All Win32 calls run on a single dedicated thread ([win32Dispatcher]) to
 * satisfy the Win32 thread-affinity requirement: RegisterHotKey,
 * UnregisterHotKey, and PeekMessage must all execute on the same thread.
 */
class WindowsHotkeyManager : HotkeyManager, Closeable {

    companion object {
        private const val HOTKEY_ID_START = 1
        private const val HOTKEY_ID_STOP = 2
        private const val HOTKEY_ID_HOLD = 3
        private const val WM_HOTKEY = 0x0312
        private const val MOD_NOREPEAT = 0x4000
        private const val CAPTURE_TIMEOUT_MS = 10_000L

        /** Virtual key codes for modifier keys — ignored during capture. */
        private val MODIFIER_VK_CODES = setOf(
            0x10, 0x11, 0x12,   // VK_SHIFT, VK_CONTROL, VK_MENU
            0xA0, 0xA1,         // VK_LSHIFT, VK_RSHIFT
            0xA2, 0xA3,         // VK_LCONTROL, VK_RCONTROL
            0xA4, 0xA5,         // VK_LMENU, VK_RMENU
        )
    }

    /**
     * Single-thread dispatcher guarantees Win32 thread affinity.
     * Every RegisterHotKey / UnregisterHotKey / PeekMessage call is
     * dispatched here, so they always execute on the same OS thread.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private val win32Dispatcher = newSingleThreadContext("HotkeyThread")
    private val scope = CoroutineScope(win32Dispatcher + SupervisorJob())

    private val _hotkeyEvents = MutableSharedFlow<HotkeyEvent>(extraBufferCapacity = 16)
    override val hotkeyEvents: SharedFlow<HotkeyEvent> = _hotkeyEvents.asSharedFlow()

    private var config: HotkeyConfig = HotkeyConfig()
    private var listenerJob: Job? = null
    private val formatter = WindowsHotkeyDisplayFormatter()

    /**
     * When non-null the message loop enters capture mode: it unregisters
     * hotkeys, polls for a raw key-down, completes the [CompletableDeferred],
     * and re-registers hotkeys.
     */
    @Volatile
    private var pendingCapture: CompletableDeferred<HotkeyCapture>? = null

    /**
     * Updates the hotkey configuration. If the listener is active the
     * hotkeys are unregistered, the config is swapped, and they are
     * re-registered — all on the Win32 thread.
     */
    override fun setConfig(config: HotkeyConfig) {
        // Dispatch to the win32 thread so register/unregister calls
        // satisfy thread affinity. Coroutines on a single-thread
        // dispatcher are serialised, so rapid calls cannot interleave.
        scope.launch {
            val wasListening = listenerJob?.isActive == true
            if (wasListening) {
                unregisterHotkeys()
            }
            this@WindowsHotkeyManager.config = config
            if (wasListening) {
                registerHotkeys()
            }
        }
    }

    override fun startListening() {
        if (listenerJob?.isActive == true) return

        listenerJob = scope.launch {
            try {
                ensureMessageQueue()
                registerHotkeys()

                val msg = MSG()
                while (isActive) {
                    // ── Capture mode ──────────────────────────────
                    val captureDeferred = pendingCapture
                    if (captureDeferred != null) {
                        unregisterHotkeys()
                        try {
                            val capture = runCaptureLoop(captureDeferred)
                            captureDeferred.complete(capture)
                        } catch (e: CancellationException) {
                            captureDeferred.cancel(e)
                        } catch (e: Exception) {
                            captureDeferred.completeExceptionally(e)
                        } finally {
                            pendingCapture = null
                            registerHotkeys()
                        }
                        continue
                    }

                    // ── Normal hotkey processing ─────────────────
                    val result = User32.INSTANCE.PeekMessage(msg, null, 0, 0, 1)
                    if (result) {
                        if (msg.message == WM_HOTKEY) {
                            val id = msg.wParam.toInt()
                            val event = when (id) {
                                HOTKEY_ID_START -> HotkeyEvent.ToggleRecording
                                HOTKEY_ID_STOP -> HotkeyEvent.StopRecording
                                // Hold hotkey is registered as a toggle because
                                // RegisterHotKey cannot detect key-up. True
                                // hold-to-record requires SetWindowsHookEx which
                                // is not yet implemented.
                                HOTKEY_ID_HOLD -> HotkeyEvent.ToggleRecording
                                else -> null
                            }
                            event?.let { _hotkeyEvents.tryEmit(it) }
                        }
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

    /**
     * Captures the next key the user presses and returns it as a
     * [HotkeyCapture]. Times out after [CAPTURE_TIMEOUT_MS] ms.
     *
     * If the listener is active the capture runs inside the existing
     * message loop (same Win32 thread). Otherwise a temporary loop
     * is created on the Win32 thread.
     */
    override suspend fun captureNextHotkey(): HotkeyCapture {
        return withTimeout(CAPTURE_TIMEOUT_MS) {
            if (listenerJob?.isActive == true) {
                // Signal the message loop to enter capture mode
                val deferred = CompletableDeferred<HotkeyCapture>()
                pendingCapture = deferred
                try {
                    deferred.await()
                } finally {
                    // On timeout / cancellation: cancel the deferred so the
                    // capture loop sees isCancelled and exits promptly.
                    deferred.cancel()
                    pendingCapture = null
                }
            } else {
                // No active listener — run capture directly on the Win32 thread
                withContext(win32Dispatcher) {
                    ensureMessageQueue()
                    val sentinel = CompletableDeferred<HotkeyCapture>()
                    runCaptureLoop(sentinel)
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Capture loop (runs on win32Dispatcher)
    // ────────────────────────────────────────────────────────────────

    /**
     * Polls the Win32 message queue for WM_KEYDOWN / WM_SYSKEYDOWN,
     * ignoring pure modifier presses. Returns as soon as a real key
     * is pressed, or throws [CancellationException] when the
     * [deferred] is cancelled (e.g. by a timeout).
     */
    private suspend fun runCaptureLoop(
        deferred: CompletableDeferred<HotkeyCapture>,
    ): HotkeyCapture {
        // We poll GetAsyncKeyState since our background thread doesn't own
        // the foreground window and therefore never receives WM_KEYDOWN messages.
        while (!deferred.isCancelled) {
            // Check all virtual keys from 0x08 (Backspace) to 0xFE
            for (vkCode in 0x08..0xFE) {
                if (vkCode in MODIFIER_VK_CODES) continue

                val state = User32.INSTANCE.GetAsyncKeyState(vkCode).toInt()
                // If the most significant bit is set, the key is currently down
                if ((state and 0x8000) != 0) {
                    val modifiers = detectCurrentModifiers()
                    val binding = HotkeyBinding(vkCode, modifiers)

                    // Wait until they release the key so we don't immediately trigger it
                    // if it happens to be the same as the new hotkey.
                    while ((User32.INSTANCE.GetAsyncKeyState(vkCode).toInt() and 0x8000) != 0) {
                        delay(10)
                    }

                    return HotkeyCapture(
                        platformKeyCode = vkCode,
                        modifiers = modifiers,
                        displayLabel = formatter.format(binding),
                    )
                }
            }
            delay(10)
        }
        throw CancellationException("Hotkey capture cancelled")
    }

    // ────────────────────────────────────────────────────────────────
    //  Win32 helpers — ALL called on win32Dispatcher
    // ────────────────────────────────────────────────────────────────

    private fun registerHotkeys() {
        var anyRegistered = false

        config.startHotkey?.let { binding ->
            if (binding.enabled) {
                anyRegistered = User32.INSTANCE.RegisterHotKey(
                    null, HOTKEY_ID_START,
                    modifiersToFlags(binding.modifiers) or MOD_NOREPEAT,
                    binding.platformKeyCode,
                ) || anyRegistered
            }
        }
        config.stopHotkey?.let { binding ->
            if (binding.enabled) {
                anyRegistered = User32.INSTANCE.RegisterHotKey(
                    null, HOTKEY_ID_STOP,
                    modifiersToFlags(binding.modifiers) or MOD_NOREPEAT,
                    binding.platformKeyCode,
                ) || anyRegistered
            }
        }
        config.holdHotkey?.let { binding ->
            if (binding.enabled) {
                anyRegistered = User32.INSTANCE.RegisterHotKey(
                    null, HOTKEY_ID_HOLD,
                    modifiersToFlags(binding.modifiers) or MOD_NOREPEAT,
                    binding.platformKeyCode,
                ) || anyRegistered
            }
        }

        if (!anyRegistered) {
            // Graceful warning instead of crashing — the user may have
            // disabled all hotkeys intentionally.
            System.err.println("Warning: No global hotkeys were registered. Check your hotkey settings.")
        }
    }

    private fun ensureMessageQueue() {
        val msg = MSG()
        User32.INSTANCE.PeekMessage(msg, null, 0, 0, 0)
    }

    private fun unregisterHotkeys() {
        User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_START)
        User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_STOP)
        User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID_HOLD)
    }

    private fun buildModFlags(): Int {
        val user32 = User32.INSTANCE
        var flags = 0
        if (user32.GetAsyncKeyState(0x11).toInt() and 0x8000 != 0) flags = flags or 0x0002 // CTRL
        if (user32.GetAsyncKeyState(0x10).toInt() and 0x8000 != 0) flags = flags or 0x0004 // SHIFT
        if (user32.GetAsyncKeyState(0x12).toInt() and 0x8000 != 0) flags = flags or 0x0001 // ALT
        if (user32.GetAsyncKeyState(0x5B).toInt() and 0x8000 != 0 ||
            user32.GetAsyncKeyState(0x5C).toInt() and 0x8000 != 0
        ) flags = flags or 0x0008 // WIN
        return flags
    }

    private fun detectCurrentModifiers(): Set<HotkeyModifier> = modFlagsToModifiers(buildModFlags())

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

    /** Releases the Win32 thread and cancels all coroutines. */
    override fun close() {
        stopListening()
        scope.cancel()
        @OptIn(DelicateCoroutinesApi::class)
        win32Dispatcher.close()
    }
}
