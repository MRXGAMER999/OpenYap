package com.openyap.repository

import com.openyap.model.RecordingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JvmHistoryRepository(private val dataDir: Path) : HistoryRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file get() = dataDir.resolve("history.json")

    override suspend fun loadEntries(): List<RecordingEntry> = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) {
                json.decodeFromString<List<RecordingEntry>>(file.readText())
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun addEntry(entry: RecordingEntry) = withContext(Dispatchers.IO) {
        val entries = loadEntries().toMutableList()
        entries.add(0, entry)
        save(entries)
    }

    override suspend fun removeEntry(id: String) = withContext(Dispatchers.IO) {
        val entries = loadEntries().filter { it.id != id }
        save(entries)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        save(emptyList())
    }

    private fun save(entries: List<RecordingEntry>) {
        dataDir.createDirectories()
        file.writeText(json.encodeToString(entries))
    }
}
