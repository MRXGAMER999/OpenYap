package com.openyap.platform

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object PlatformInit {
    val dataDir: Path by lazy {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        Path(appData, "OpenYap").also { if (!it.exists()) it.createDirectories() }
    }

    val tempDir: Path by lazy {
        Path(System.getProperty("java.io.tmpdir"), "OpenYap")
            .also { if (!it.exists()) it.createDirectories() }
    }
}
