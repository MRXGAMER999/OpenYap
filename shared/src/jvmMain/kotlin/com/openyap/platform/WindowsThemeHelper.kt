package com.openyap.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Calls the Windows DWM API to enable immersive dark mode on the title bar,
 * set the caption color, and configure the window border to match the app's
 * theme. Without this, the title bar stays white even when the app content
 * is dark.
 */
object WindowsThemeHelper {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20        // Windows 11 / Win10 20H1+
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19 // Older Win10 builds
    private const val DWMWA_CAPTION_COLOR = 35                  // Windows 11 Build 22000+
    private const val DWMWA_BORDER_COLOR = 34                   // Windows 11 Build 22000+

    /**
     * JNA interface to dwmapi.dll.
     *
     * The `pvAttribute` parameter is declared as [Pointer] to match the
     * Win32 `LPCVOID` type. We write the actual value into a [Memory]
     * block before each call.
     */
    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int,
        ): Int

        companion object {
            val INSTANCE: Dwmapi =
                Native.load("dwmapi", Dwmapi::class.java) as Dwmapi
        }
    }

    /** Writes a 32-bit int into a 4-byte native [Memory] block. */
    private fun intPointer(value: Int): Memory =
        Memory(4).also { it.setInt(0, value) }

    /**
     * Enables or disables the dark title bar on the given AWT [window],
     * and sets the caption/border color to match the app background.
     *
     * @param window  The AWT window (in Compose Desktop, use `window`)
     * @param dark    `true` to request a dark caption/title bar
     */
    fun setDarkTitleBar(window: Window, dark: Boolean) {
        try {
            if (!window.isDisplayable) return
            val hwnd = getHwnd(window) ?: return

            // ── 1. Enable / disable immersive dark mode ────────────
            val darkModePtr = intPointer(if (dark) 1 else 0)
            var hr = Dwmapi.INSTANCE.DwmSetWindowAttribute(
                hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkModePtr, 4,
            )
            if (hr != 0) {
                // Fall back to the legacy attribute for older Win10 builds
                hr = Dwmapi.INSTANCE.DwmSetWindowAttribute(
                    hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, darkModePtr, 4,
                )
                if (hr != 0) {
                    System.err.println(
                        "WindowsThemeHelper: dark-mode HRESULT=0x${
                            hr.toUInt().toString(16)
                        }"
                    )
                }
            }

            // ── 2. Set caption and border color (Win11 22000+) ─────
            // COLORREF format: 0x00BBGGRR
            val colorRef = if (dark) {
                // surface = Color(0xFF1F2937) → R=0x1F, G=0x29, B=0x37
                0x0037291F
            } else {
                // surface = Color(0xFFFBFCFF) → R=0xFB, G=0xFC, B=0xFF
                0x00FFFCFB
            }
            val colorPtr = intPointer(colorRef)

            val captionHr = Dwmapi.INSTANCE.DwmSetWindowAttribute(
                hwnd, DWMWA_CAPTION_COLOR, colorPtr, 4,
            )
            if (captionHr != 0) {
                System.err.println(
                    "WindowsThemeHelper: caption-color HRESULT=0x${
                        captionHr.toUInt().toString(16)
                    }"
                )
            }

            val borderHr = Dwmapi.INSTANCE.DwmSetWindowAttribute(
                hwnd, DWMWA_BORDER_COLOR, colorPtr, 4,
            )
            if (borderHr != 0) {
                System.err.println(
                    "WindowsThemeHelper: border-color HRESULT=0x${
                        borderHr.toUInt().toString(16)
                    }"
                )
            }

        } catch (e: Exception) {
            System.err.println("WindowsThemeHelper: ${e.message}")
        }
    }

    /**
     * Retrieves the native HWND from a Java AWT [Window] using JNA.
     */
    private fun getHwnd(window: Window): WinDef.HWND? {
        return try {
            val hwndLong = Native.getWindowPointer(window)
            WinDef.HWND(hwndLong)
        } catch (e: Exception) {
            System.err.println("WindowsThemeHelper: failed to get HWND: ${e.message}")
            null
        }
    }
}
