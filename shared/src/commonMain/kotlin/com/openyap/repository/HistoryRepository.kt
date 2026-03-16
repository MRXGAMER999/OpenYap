package com.openyap.repository

import com.openyap.model.RecordingEntry

interface HistoryRepository {
    suspend fun loadEntries(): List<RecordingEntry>
    suspend fun loadRecentEntriesForApp(targetApp: String, limit: Int): List<RecordingEntry>
    suspend fun addEntry(entry: RecordingEntry)
    suspend fun removeEntry(id: String)
    suspend fun clearAll()
}
