package com.openyap.repository

import com.openyap.model.DictionaryEntry

interface DictionaryRepository {
    suspend fun loadEntries(): List<DictionaryEntry>
    suspend fun saveEntries(entries: List<DictionaryEntry>)
    suspend fun addOrUpdate(entry: DictionaryEntry)
    suspend fun remove(id: String)
}
