package com.openyap.service

import com.openyap.model.DictionaryEntry
import com.openyap.model.EntrySource
import com.openyap.model.UserProfile

object PhraseExpansionEngine {

    fun expandText(
        text: String,
        profile: UserProfile,
        dictionaryEntries: List<DictionaryEntry>,
        enabled: Boolean,
    ): String {
        if (!enabled || text.isBlank()) return text

        val replacements = mutableMapOf<String, ReplacementRule>()

        fun upsert(phrase: String, replacement: String, priority: Int) {
            val normalizedPhrase = normalizeLookupKey(phrase)
            val normalizedReplacement = replacement.trim()
            if (normalizedPhrase.isEmpty() || normalizedReplacement.isEmpty()) return
            val existing = replacements[normalizedPhrase]
            if (existing == null || priority > existing.priority) {
                replacements[normalizedPhrase] = ReplacementRule(normalizedReplacement, priority)
            }
        }

        for ((key, value) in profile.aliasMap) {
            upsert(key, value, 100)
        }

        for (entry in dictionaryEntries) {
            val phrase = entry.original.trim()
            val replacement = entry.replacement.trim()
            if (!entry.isEnabled || phrase.isEmpty() || replacement.isEmpty()) continue
            if (phrase == replacement) continue
            val priority = if (entry.source == EntrySource.MANUAL) 300 else 200
            upsert(phrase, replacement, priority)
        }

        if (replacements.isEmpty()) return text

        val sortedKeys = replacements.keys.sortedByDescending { it.length }
        val pattern = sortedKeys.joinToString("|") { phrasePatternFromKey(it) }
        if (pattern.isEmpty()) return text

        val regex = Regex("(?<!\\w)($pattern)(?!\\w)", RegexOption.IGNORE_CASE)

        return regex.replace(text) { matchResult ->
            val matched = normalizeLookupKey(matchResult.value)
            replacements[matched]?.replacement ?: matchResult.value
        }
    }

    private fun normalizeLookupKey(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun phrasePatternFromKey(key: String): String =
        key.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString("\\s+") { Regex.escape(it) }

    private data class ReplacementRule(val replacement: String, val priority: Int)
}
