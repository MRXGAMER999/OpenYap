package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
data class DictionaryEntry(
    val id: String,
    val original: String,
    val replacement: String,
    val isEnabled: Boolean = true,
    val frequency: Int = 1,
    val source: EntrySource = EntrySource.AUTO,
) {
    val isManual: Boolean get() = source == EntrySource.MANUAL
}

@Serializable
enum class EntrySource { MANUAL, AUTO, SUGGESTION }
