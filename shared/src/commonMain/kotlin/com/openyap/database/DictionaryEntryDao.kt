package com.openyap.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface DictionaryEntryDao {

    @Query("SELECT * FROM dictionary_entries")
    suspend fun getAll(): List<DictionaryEntryEntity>

    @Upsert
    suspend fun upsert(entry: DictionaryEntryEntity)

    @Query("DELETE FROM dictionary_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM dictionary_entries")
    suspend fun deleteAll()
}
