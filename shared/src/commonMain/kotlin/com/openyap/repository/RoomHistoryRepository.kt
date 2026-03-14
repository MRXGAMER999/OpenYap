package com.openyap.repository

import com.openyap.database.OpenYapDatabase
import com.openyap.database.toDomain
import com.openyap.database.toEntity
import com.openyap.model.RecordingEntry

class RoomHistoryRepository(
    private val database: OpenYapDatabase,
) : HistoryRepository {

    override suspend fun loadEntries(): List<RecordingEntry> {
        return database.recordingEntryDao().getAll().map { it.toDomain() }
    }

    override suspend fun addEntry(entry: RecordingEntry) {
        database.recordingEntryDao().insert(entry.toEntity())
    }

    override suspend fun removeEntry(id: String) {
        database.recordingEntryDao().deleteById(id)
    }

    override suspend fun clearAll() {
        database.recordingEntryDao().deleteAll()
    }
}
