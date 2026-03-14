package com.openyap.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "dictionary_entries")
data class DictionaryEntryEntity(
    @PrimaryKey val id: String,
    val original: String,
    val replacement: String,
    val isEnabled: Boolean = true,
    val frequency: Int = 1,
    val source: String = "AUTO",
)
