#pragma once

#include <string>

namespace openyap::paste {

    /// Copies `text` to the clipboard using the Win32 clipboard API and
    /// immediately simulates Ctrl+V via SendInput.
    ///
    /// If `restore_clipboard` is true, the original clipboard **Unicode text**
    /// (CF_UNICODETEXT) is saved before the operation and restored after a
    /// brief delay to allow the target application to consume the paste.
    /// Non-text clipboard formats (images, files, custom formats) are NOT
    /// preserved — callers that need full-format preservation should manage
    /// the clipboard themselves and pass `restore_clipboard = false`.
    ///
    /// @return  0 on success, negative on failure.  On failure a
    ///          human-readable error message is written into the
    ///          `std::string` pointed to by @p error (if non-null).
    int paste_text(const wchar_t *text, bool restore_clipboard, std::string *error);

}  // namespace openyap::paste
