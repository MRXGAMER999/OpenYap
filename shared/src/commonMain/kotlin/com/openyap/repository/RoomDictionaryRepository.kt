package com.openyap.repository

import com.openyap.database.OpenYapDatabase
import com.openyap.database.toDomain
import com.openyap.database.toEntity
import com.openyap.model.DictionaryEntry

class RoomDictionaryRepository(
    private val database: OpenYapDatabase,
) : DictionaryRepository {

    override suspend fun loadEntries(): List<DictionaryEntry> {
        return database.dictionaryEntryDao().getAll().map { it.toDomain() }
    }

    override suspend fun saveEntries(entries: List<DictionaryEntry>) {
        // Replace all: delete all then insert
        // Use a transaction via Room 3's withWriteTransaction
        val dao = database.dictionaryEntryDao()
        val existing = dao.getAll().map { it.id }.toSet()
        val newIds = entries.map { it.id }.toSet()

        // Delete entries not in the new list
        for (id in existing - newIds) {
            dao.deleteById(id)
        }
        // Upsert all new entries
        for (entry in entries) {
            dao.upsert(entry.toEntity())
        }
    }

    override suspend fun addOrUpdate(entry: DictionaryEntry) {
        database.dictionaryEntryDao().upsert(entry.toEntity())
    }

    override suspend fun remove(id: String) {
        database.dictionaryEntryDao().deleteById(id)
    }
}
