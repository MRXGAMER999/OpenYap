/// @file paste_automation.cpp
/// @brief  Win32 paste automation — clipboard-write + simulated Ctrl+V.
///
/// **Clipboard preservation limitation**: save_clipboard_text() and the
/// restore path only preserve CF_UNICODETEXT data.  Non-text clipboard
/// formats (images, files, custom formats) will be lost when
/// restore_clipboard is requested.  Callers that need to preserve
/// non-text content should save/restore the clipboard themselves and
/// pass restore_clipboard = false.
///
/// **Restore delay**: after the simulated Ctrl+V the module waits
/// CLIPBOARD_RESTORE_DELAY_MS (default 700 ms) before restoring the
/// original clipboard text.  Slow or async paste handlers may need a
/// larger value; override the constant at compile time if necessary
/// (e.g. -DCLIPBOARD_RESTORE_DELAY_MS=1000).

#include "paste_automation.h"

#include <windows.h>

#include <string>

#ifndef CLIPBOARD_RESTORE_DELAY_MS
#define CLIPBOARD_RESTORE_DELAY_MS 700
#endif

namespace {

    /// Save the current clipboard Unicode text (CF_UNICODETEXT only).
    /// Returns an empty string if the clipboard is empty, locked, or contains
    /// non-text data.  Other clipboard formats (images, file lists, custom
    /// formats) are NOT preserved — see the module-level comment.
    std::wstring save_clipboard_text() {
        std::wstring result;
        if (!OpenClipboard(nullptr)) return result;

        HANDLE data = GetClipboardData(CF_UNICODETEXT);
        if (data != nullptr) {
            const wchar_t *text = static_cast<const wchar_t *>(GlobalLock(data));
            if (text != nullptr) {
                result = text;
                GlobalUnlock(data);
            }
        }

        CloseClipboard();
        return result;
    }

    /// Write text to the clipboard using the Win32 clipboard API.
    /// Returns true on success.
    bool write_clipboard_text(const wchar_t *text) {
        const size_t len = wcslen(text);
        const size_t byte_count = (len + 1) * sizeof(wchar_t);

        HGLOBAL mem = GlobalAlloc(GMEM_MOVEABLE, byte_count);
        if (mem == nullptr) return false;

        wchar_t *dest = static_cast<wchar_t *>(GlobalLock(mem));
        if (dest == nullptr) {
            GlobalFree(mem);
            return false;
        }
        memcpy(dest, text, byte_count);
        GlobalUnlock(mem);

        if (!OpenClipboard(nullptr)) {
            GlobalFree(mem);
            return false;
        }

        EmptyClipboard();
        HANDLE result = SetClipboardData(CF_UNICODETEXT, mem);
        CloseClipboard();

        if (result == nullptr) {
            // SetClipboardData failed; Windows did NOT take ownership of mem.
            GlobalFree(mem);
            return false;
        }
        // On success, Windows takes ownership of mem — do NOT free it.
        return true;
    }

    /// Simulate Ctrl+V via SendInput. All four input events (Ctrl down, V down,
    /// V up, Ctrl up) are submitted in a single atomic SendInput call.
    bool send_ctrl_v() {
        INPUT inputs[4] = {};

        // Ctrl down
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].ki.wVk = VK_CONTROL;

        // V down
        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].ki.wVk = 0x56;  // VK_V

        // V up
        inputs[2].type = INPUT_KEYBOARD;
        inputs[2].ki.wVk = 0x56;
        inputs[2].ki.dwFlags = KEYEVENTF_KEYUP;

        // Ctrl up
        inputs[3].type = INPUT_KEYBOARD;
        inputs[3].ki.wVk = VK_CONTROL;
        inputs[3].ki.dwFlags = KEYEVENTF_KEYUP;

        const UINT sent = SendInput(4, inputs, sizeof(INPUT));
        return sent == 4;
    }

}  // namespace

namespace openyap::paste {

    int paste_text(const wchar_t *text, bool restore_clipboard, std::string *error) {
        if (text == nullptr) {
            if (error) *error = "text is null";
            return -1;
        }

        // Optionally save current clipboard content before we overwrite it.
        std::wstring original_text;
        if (restore_clipboard) {
            original_text = save_clipboard_text();
        }

        // Write the new text to the clipboard.
        if (!write_clipboard_text(text)) {
            if (error) *error = "Failed to write text to clipboard";
            return -2;
        }

        // Minimal delay: yield the thread briefly to let the clipboard
        // change notification propagate. 5 ms is enough for the vast
        // majority of applications (the old JNA path used 50 ms).
        Sleep(5);

        // Simulate Ctrl+V.
        if (!send_ctrl_v()) {
            if (error) *error = "SendInput failed";
            return -3;
        }

        // Restore the original clipboard content after a delay.
        // The delay gives the target app time to read the pasted data from
        // the clipboard.  Override CLIPBOARD_RESTORE_DELAY_MS at compile
        // time if slow/async paste handlers need a longer window.
        if (restore_clipboard && !original_text.empty()) {
            Sleep(CLIPBOARD_RESTORE_DELAY_MS);
            // Best-effort restore — ignore failures.
            write_clipboard_text(original_text.c_str());
        }

        return 0;
    }

}  // namespace openyap::paste
