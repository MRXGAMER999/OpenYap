package com.openyap.repository

import com.openyap.model.DictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JvmDictionaryRepository(private val dataDir: Path) : DictionaryRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file get() = dataDir.resolve("dictionary.json")

    override suspend fun loadEntries(): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) {
                json.decodeFromString<List<DictionaryEntry>>(file.readText())
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun saveEntries(entries: List<DictionaryEntry>) = withContext(Dispatchers.IO) {
        dataDir.createDirectories()
        file.writeText(json.encodeToString(entries))
    }

    override suspend fun addOrUpdate(entry: DictionaryEntry) = withContext(Dispatchers.IO) {
        val entries = loadEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            entries[index] = entry
        } else {
            entries.add(0, entry)
        }
        saveEntries(entries)
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val entries = loadEntries().filter { it.id != id }
        saveEntries(entries)
    }
}
