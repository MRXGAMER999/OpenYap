package com.openyap.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface RecordingEntryDao {

    @Query("SELECT * FROM recording_entries ORDER BY recordedAtMillis DESC")
    suspend fun getAll(): List<RecordingEntryEntity>

    @Query("SELECT * FROM recording_entries WHERE targetApp = :targetApp AND isFallback = 0 ORDER BY recordedAtMillis DESC LIMIT :limit")
    suspend fun getRecentEntriesForApp(targetApp: String, limit: Int): List<RecordingEntryEntity>

    @Insert
    suspend fun insert(entry: RecordingEntryEntity)

    @Query("DELETE FROM recording_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recording_entries")
    suspend fun deleteAll()
}
