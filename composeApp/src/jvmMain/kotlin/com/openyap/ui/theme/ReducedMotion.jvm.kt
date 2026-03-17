package com.openyap.ui.theme

import java.awt.Toolkit

actual fun platformPrefersReducedMotion(): Boolean {
    return if (isWindows()) {
        windowsAnimationsEnabled()?.not() ?: false
    } else {
        desktopAnimationsEnabled()?.not() ?: false
    }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true

private fun windowsAnimationsEnabled(): Boolean? {
    val process = runCatching {
        ProcessBuilder(
            "reg",
            "query",
            "HKCU\\Control Panel\\Desktop",
            "/v",
            "UserPreferencesMask",
        )
            .redirectErrorStream(true)
            .start()
    }.getOrNull() ?: return null

    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (process.waitFor() != 0) return null

    val bytes = Regex("""([0-9A-Fa-f]{2})""")
        .findAll(output.substringAfter("UserPreferencesMask", ""))
        .map { it.value.toInt(16) }
        .take(4)
        .toList()

    if (bytes.size < 4) return null

    val uiEffectsEnabled = bytes[3] and 0x80 != 0
    return uiEffectsEnabled
}

private fun desktopAnimationsEnabled(): Boolean? {
    return sequenceOf(
        desktopBooleanProperty("awt.enableAnimations"),
        desktopBooleanProperty("gtk.enable-animations"),
        desktopBooleanProperty("apple.awt.reduceMotion")?.not(),
        systemBooleanProperty("awt.enableAnimations"),
        systemBooleanProperty("gtk.enable-animations"),
        systemBooleanProperty("apple.awt.reduceMotion")?.not(),
    ).firstOrNull()
}

private fun desktopBooleanProperty(name: String): Boolean? =
    runCatching { Toolkit.getDefaultToolkit().getDesktopProperty(name) }
        .getOrNull()
        .toBoolean()

private fun systemBooleanProperty(name: String): Boolean? =
    parseBoolean(System.getProperty(name))

private fun Any?.toBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is String -> parseBoolean(this)
    else -> null
}

private fun parseBoolean(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> null
}
