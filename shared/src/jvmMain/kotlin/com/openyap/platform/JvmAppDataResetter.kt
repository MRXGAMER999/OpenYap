package com.openyap.platform

import com.openyap.database.OpenYapDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
class JvmAppDataResetter(
    private val secureStorage: SecureStorage,
    private val database: OpenYapDatabase,
    private val dataDir: Path,
    private val tempDir: Path,
) : AppDataResetter {
    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            secureStorage.clear()

            // Clear all Room tables while the DB connection is still open.
            // On Windows, deleteRecursively() may fail because Room holds
            // the SQLite file locked, so this ensures the data is gone
            // regardless of whether the file deletion succeeds.
            database.deleteAllData()

            if (dataDir.exists()) {
                runCatching { dataDir.deleteRecursively() }
            }
            dataDir.createDirectories()

            if (tempDir.exists()) {
                runCatching { tempDir.deleteRecursively() }
            }
            tempDir.createDirectories()
        }
    }
}
