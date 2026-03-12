package com.openyap.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference

class WindowsForegroundAppDetector : ForegroundAppDetector {

    override fun getForegroundAppName(): String? {
        return try {
            val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return null
            val pidRef = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
            val pid = pidRef.value

            val processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                pid,
            ) ?: return getWindowTitle(hwnd)

            try {
                val buffer = CharArray(1024)
                val size = IntByReference(buffer.size)
                val success = Kernel32.INSTANCE.QueryFullProcessImageName(
                    processHandle, 0, buffer, size
                )
                if (success) {
                    val fullPath = String(buffer, 0, size.value)
                    fullPath.substringAfterLast("\\").removeSuffix(".exe")
                } else {
                    getWindowTitle(hwnd)
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(processHandle)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getWindowTitle(hwnd: com.sun.jna.platform.win32.WinDef.HWND): String? {
        val buffer = CharArray(512)
        val length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.size)
        return if (length > 0) String(buffer, 0, length) else null
    }
}
