package com.openyap.platform

import com.openyap.model.HotkeyBinding
import com.openyap.model.HotkeyCapture
import com.openyap.model.HotkeyConfig
import com.openyap.model.HotkeyEvent
import com.openyap.model.HotkeyModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.Closeable

private const val CAPTURE_TIMEOUT_MS = 10_000L
private const val HOTKEY_EVENT_HOLD_DOWN = 1
private const val HOTKEY_EVENT_HOLD_UP = 2
private const val HOTKEY_EVENT_CANCEL_RECORDING = 3
private const val HOTKEY_EVENT_CAPTURED = 4
private const val MODIFIER_CTRL = 1 shl 0
private const val MODIFIER_ALT = 1 shl 1
private const val MODIFIER_SHIFT = 1 shl 2
private const val MODIFIER_META = 1 shl 3

class WindowsHotkeyManager : HotkeyManager, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _hotkeyEvents = MutableSharedFlow<HotkeyEvent>(extraBufferCapacity = 16)
    override val hotkeyEvents: SharedFlow<HotkeyEvent> = _hotkeyEvents.asSharedFlow()

    private val formatter = WindowsHotkeyDisplayFormatter()
    private val native = NativeAudioBridge.instance
    private val controlMutex = Mutex()
    private val captureMutex = Mutex()
    private val fallbackLock = Any()
    private var fallbackManager: JnaWindowsHotkeyManager? = null
    private var fallbackCollectorJob: Job? = null

    @Volatile
    private var usingFallback = native == null

    @Volatile
    private var config: HotkeyConfig = HotkeyConfig()

    @Volatile
    private var isListening = false

    @Volatile
    private var pendingCapture: CompletableDeferred<HotkeyCapture>? = null

    private val nativeCallback = NativeAudioBridge.OpenYapNative.HotkeyCallback { eventType, vkCode, modifiersMask, _ ->
        when (eventType) {
            HOTKEY_EVENT_HOLD_DOWN -> _hotkeyEvents.tryEmit(HotkeyEvent.HoldDown)
            HOTKEY_EVENT_HOLD_UP -> _hotkeyEvents.tryEmit(HotkeyEvent.HoldUp)
            HOTKEY_EVENT_CANCEL_RECORDING -> _hotkeyEvents.tryEmit(HotkeyEvent.CancelRecording)
            HOTKEY_EVENT_CAPTURED -> {
                val binding = HotkeyBinding(vkCode, modifiersFromMask(modifiersMask))
                pendingCapture?.complete(
                    HotkeyCapture(
                        platformKeyCode = vkCode,
                        modifiers = binding.modifiers,
                        displayLabel = formatter.format(binding),
                    )
                )
            }
        }
    }

    init {
        if (native == null) {
            val reason = NativeAudioBridge.failureReason
            if (!reason.isNullOrBlank()) {
                System.err.println("Native hotkeys unavailable, using JNA fallback: $reason")
            } else {
                System.err.println("Native hotkeys unavailable, using JNA fallback.")
            }
        } else {
            System.err.println("Native hotkeys active.")
        }
    }

    override fun setConfig(config: HotkeyConfig) {
        this.config = config
        if (usingFallback) {
            getOrCreateFallback().setConfig(config)
            return
        }

        val nativeInstance = native ?: run {
            switchToFallback("Native DLL is unavailable while updating hotkey config.")
            getOrCreateFallback().setConfig(config)
            return
        }

        val binding = config.startHotkey
        val result = runNativeIntCall(
            action = "update native hotkey config",
            fallbackReason = "Native DLL is missing required hotkey exports while updating hotkey config.",
        ) {
            nativeInstance.openyap_hotkey_set_config(
                keyCode = binding?.platformKeyCode ?: 0,
                modifiersMask = binding?.modifiers?.toMask() ?: 0,
                enabled = if (binding?.enabled == true) 1 else 0,
            )
        }
        if (usingFallback) {
            getOrCreateFallback().setConfig(config)
            return
        }
        if (result != 0) {
            switchToFallback(
                nativeInstance.openyap_last_error() ?: "Failed to update native hotkey config (code $result)."
            )
            getOrCreateFallback().setConfig(config)
        }
    }

    override fun startListening() {
        isListening = true
        if (usingFallback) {
            getOrCreateFallback().startListening()
            return
        }

        val nativeInstance = native ?: run {
            switchToFallback("Native DLL is unavailable while starting hotkeys.")
            getOrCreateFallback().startListening()
            return
        }

        val result = runNativeIntCall(
            action = "start native hotkey listener",
            fallbackReason = "Native DLL is missing required hotkey exports while starting hotkeys.",
        ) {
            nativeInstance.openyap_hotkey_start_listening(nativeCallback, null)
        }
        if (usingFallback) {
            getOrCreateFallback().startListening()
            return
        }
        if (result != 0) {
            switchToFallback(
                nativeInstance.openyap_last_error() ?: "Failed to start native hotkey listener (code $result)."
            )
            getOrCreateFallback().startListening()
        }
    }

    override fun stopListening() {
        isListening = false
        pendingCapture?.cancel(CancellationException("Hotkey listening stopped."))
        pendingCapture = null

        if (usingFallback) {
            fallbackManager?.stopListening()
            return
        }

        runCatching { native?.openyap_hotkey_cancel_capture() }
        runCatching { native?.openyap_hotkey_stop_listening() }
    }

    override suspend fun captureNextHotkey(): HotkeyCapture = captureMutex.withLock {
        if (usingFallback) {
            return@withLock getOrCreateFallback().captureNextHotkey()
        }

        val nativeInstance = native ?: run {
            switchToFallback("Native DLL is unavailable while capturing a hotkey.")
            return@withLock getOrCreateFallback().captureNextHotkey()
        }

        controlMutex.withLock {
            val wasListening = isListening
            if (!wasListening) {
                val startResult = runNativeIntCall(
                    action = "start native hotkey listener for capture",
                    fallbackReason = "Native DLL is missing required hotkey exports while capturing a hotkey.",
                ) {
                    nativeInstance.openyap_hotkey_start_listening(nativeCallback, null)
                }
                if (usingFallback) {
                    return@withLock getOrCreateFallback().captureNextHotkey()
                }
                if (startResult != 0) {
                    switchToFallback(
                        nativeInstance.openyap_last_error()
                            ?: "Failed to start native hotkey listener for capture (code $startResult)."
                    )
                    return@withLock getOrCreateFallback().captureNextHotkey()
                }
            }

            val deferred = CompletableDeferred<HotkeyCapture>()
            pendingCapture = deferred
            val beginResult = runNativeIntCall(
                action = "begin native hotkey capture",
                fallbackReason = "Native DLL is missing required hotkey exports while capturing a hotkey.",
            ) {
                nativeInstance.openyap_hotkey_begin_capture()
            }
            if (usingFallback) {
                pendingCapture = null
                if (!wasListening) {
                    runCatching { nativeInstance.openyap_hotkey_stop_listening() }
                }
                return@withLock getOrCreateFallback().captureNextHotkey()
            }
            if (beginResult != 0) {
                pendingCapture = null
                if (!wasListening) {
                    runCatching { nativeInstance.openyap_hotkey_stop_listening() }
                }
                switchToFallback(
                    nativeInstance.openyap_last_error() ?: "Failed to begin native hotkey capture (code $beginResult)."
                )
                return@withLock getOrCreateFallback().captureNextHotkey()
            }

            try {
                withTimeout(CAPTURE_TIMEOUT_MS) {
                    deferred.await()
                }
            } finally {
                pendingCapture = null
                runCatching { nativeInstance.openyap_hotkey_cancel_capture() }
                deferred.cancel()
                if (!wasListening) {
                    runCatching { nativeInstance.openyap_hotkey_stop_listening() }
                }
            }
        }
    }

    private fun getOrCreateFallback(): JnaWindowsHotkeyManager {
        synchronized(fallbackLock) {
            fallbackManager?.let { return it }
            val manager = JnaWindowsHotkeyManager()
            manager.setConfig(config)
            fallbackManager = manager
            System.err.println("JNA hotkey fallback active.")
            attachFallbackCollector(manager)
            return manager
        }
    }

    private fun attachFallbackCollector(manager: JnaWindowsHotkeyManager) {
        check(Thread.holdsLock(fallbackLock))
        if (fallbackCollectorJob != null) return
        fallbackCollectorJob = scope.launch {
            manager.hotkeyEvents.collect { event ->
                _hotkeyEvents.emit(event)
            }
        }
    }

    private fun switchToFallback(reason: String) {
        synchronized(fallbackLock) {
            if (usingFallback) return
            usingFallback = true
        }
        runCatching { native?.openyap_hotkey_cancel_capture() }
        runCatching { native?.openyap_hotkey_stop_listening() }
        pendingCapture?.cancel(CancellationException(reason))
        pendingCapture = null
        System.err.println("Native hotkeys failed, using JNA fallback: $reason")

        val fallback = getOrCreateFallback()
        fallback.setConfig(config)
        if (isListening) {
            fallback.startListening()
        }
    }

    private fun runNativeIntCall(
        action: String,
        fallbackReason: String,
        block: () -> Int,
    ): Int {
        return try {
            block()
        } catch (_: UnsatisfiedLinkError) {
            switchToFallback(fallbackReason)
            -1
        } catch (error: NoSuchMethodError) {
            switchToFallback("$fallbackReason (${error.message ?: action})")
            -1
        }
    }

    private fun modifiersFromMask(mask: Int): Set<HotkeyModifier> = buildSet {
        if (mask and MODIFIER_CTRL != 0) add(HotkeyModifier.CTRL)
        if (mask and MODIFIER_ALT != 0) add(HotkeyModifier.ALT)
        if (mask and MODIFIER_SHIFT != 0) add(HotkeyModifier.SHIFT)
        if (mask and MODIFIER_META != 0) add(HotkeyModifier.META)
    }

    private fun Set<HotkeyModifier>.toMask(): Int {
        var mask = 0
        if (HotkeyModifier.CTRL in this) mask = mask or MODIFIER_CTRL
        if (HotkeyModifier.ALT in this) mask = mask or MODIFIER_ALT
        if (HotkeyModifier.SHIFT in this) mask = mask or MODIFIER_SHIFT
        if (HotkeyModifier.META in this) mask = mask or MODIFIER_META
        return mask
    }

    override fun close() {
        stopListening()
        fallbackCollectorJob?.cancel()
        fallbackCollectorJob = null
        fallbackManager?.close()
        fallbackManager = null
        scope.cancel()
    }
}
