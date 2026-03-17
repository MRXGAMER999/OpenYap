package com.openyap.platform

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.Pointer

class WindowsForegroundAppDetector : ForegroundAppDetector {

    override fun getForegroundWindowContext(): ForegroundWindowContext {
        return try {
            val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return ForegroundWindowContext(
                windowHandle = null,
                appName = null,
                windowTitle = null,
            )
            val windowTitle = getWindowTitle(hwnd)
            val windowHandle = Pointer.nativeValue(hwnd.pointer)
            val pidRef = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
            val pid = pidRef.value

            val processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                pid,
            ) ?: return ForegroundWindowContext(
                windowHandle = windowHandle,
                appName = null,
                windowTitle = windowTitle,
            )

            try {
                val buffer = CharArray(1024)
                val size = IntByReference(buffer.size)
                val success = Kernel32.INSTANCE.QueryFullProcessImageName(
                    processHandle, 0, buffer, size
                )
                val appName = if (success) {
                    val fullPath = String(buffer, 0, size.value)
                    fullPath.substringAfterLast("\\").removeSuffix(".exe")
                } else {
                    null
                }
                ForegroundWindowContext(
                    windowHandle = windowHandle,
                    appName = appName,
                    windowTitle = windowTitle,
                )
            } finally {
                Kernel32.INSTANCE.CloseHandle(processHandle)
            }
        } catch (_: Exception) {
            ForegroundWindowContext(
                windowHandle = null,
                appName = null,
                windowTitle = null,
            )
        }
    }

    private fun getWindowTitle(hwnd: WinDef.HWND): String? {
        val buffer = CharArray(512)
        val length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.size)
        return if (length > 0) String(buffer, 0, length) else null
    }
}
