package com.openyap.platform

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
class JvmAppDataResetter(
    private val secureStorage: SecureStorage,
    private val dataDir: Path,
    private val tempDir: Path,
) {
    suspend fun reset() {
        secureStorage.delete("gemini_api_key")

        if (dataDir.exists()) {
            dataDir.deleteRecursively()
        }
        dataDir.createDirectories()

        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.createDirectories()
    }
}
