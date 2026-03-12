package com.openyap.service

import com.openyap.model.DictionaryEntry
import com.openyap.model.EntrySource
import com.openyap.repository.DictionaryRepository

class DictionaryEngine(private val repository: DictionaryRepository) {

    companion object {
        private const val AUTO_CORRECTION_SUGGEST_THRESHOLD = 0.7
        private const val AUTO_CORRECTION_APPLY_THRESHOLD = 0.9

        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for",
            "from", "i", "if", "in", "is", "it", "me", "myself", "of", "on",
            "or", "so", "the", "to", "we", "with", "you", "your",
        )
    }

    suspend fun ingestObservedText(text: String) {
        val normalizedText = normalizeSentence(text)
        if (normalizedText.isEmpty()) return

        val candidates = extractCandidates(normalizedText)
        if (candidates.isEmpty()) return

        val entries = repository.loadEntries().toMutableList()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds().toString()
        val seenInThisSession = mutableSetOf<String>()

        for (candidate in candidates) {
            val normalized = normalize(candidate)
            if (normalized.isEmpty()) continue

            val index = entries.indexOfFirst { normalize(it.original) == normalized }
            if (index >= 0) {
                val prev = entries[index]
                val nextFrequency = prev.frequency + 1
                val confidence = computeConfidence(normalized, nextFrequency)
                entries[index] = prev.copy(frequency = nextFrequency)
            } else {
                val confidence = computeConfidence(normalized, 1)
                val correction = buildAutoCorrection(normalized, confidence, entries)
                val isAutoApplied = correction != null && correction.confidence >= AUTO_CORRECTION_APPLY_THRESHOLD

                entries.add(
                    DictionaryEntry(
                        id = "${now}_${entries.size}",
                        original = normalized,
                        replacement = correction?.replacement ?: "",
                        isEnabled = isAutoApplied || correction == null,
                        frequency = 1,
                        source = EntrySource.AUTO,
                    )
                )
            }
        }

        repository.saveEntries(entries)
    }

    suspend fun addManualEntry(original: String, replacement: String, enabled: Boolean = true) {
        val cleaned = original.trim()
        if (cleaned.isEmpty()) return

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds().toString()
        repository.addOrUpdate(
            DictionaryEntry(
                id = now,
                original = cleaned,
                replacement = replacement.trim(),
                isEnabled = enabled,
                frequency = 0,
                source = EntrySource.MANUAL,
            )
        )
    }

    private fun extractCandidates(text: String): List<String> {
        val words = Regex("[a-z0-9@._'-]+").findAll(text.lowercase())
            .map { it.value }
            .toList()
        if (words.isEmpty()) return emptyList()

        val candidates = mutableSetOf<String>()
        for (word in words) {
            if (isValidWord(word)) candidates.add(word)
        }
        for (n in 2..4) {
            for (i in 0..words.size - n) {
                val phrase = words.subList(i, i + n).joinToString(" ")
                if (isValidPhrase(phrase)) candidates.add(phrase)
            }
        }
        return candidates.toList()
    }

    private fun isValidWord(word: String): Boolean {
        if (word.length < 3) return false
        if (word in STOP_WORDS) return false
        if (Regex("^\\d+$").matches(word)) return false
        return true
    }

    private fun isValidPhrase(phrase: String): Boolean {
        val words = phrase.split(" ")
        if (words.size < 2 || words.size > 4) return false
        if (words.all { it in STOP_WORDS }) return false
        if (phrase.length < 6) return false
        return true
    }

    private fun computeConfidence(candidate: String, usageCount: Int): Double {
        var score = 0.0
        score += (usageCount * 0.08).coerceAtMost(0.4)
        if (candidate.startsWith("my ")) score += 0.2
        if ("email" in candidate || "linkedin" in candidate || "phone" in candidate || "github" in candidate) {
            score += 0.15
        }
        if (candidate in STOP_WORDS) score -= 0.2
        if (candidate.length > 28) score -= 0.1
        return score.coerceIn(0.0, 1.0)
    }

    private fun buildAutoCorrection(
        observed: String,
        confidence: Double,
        entries: List<DictionaryEntry>,
    ): AutoCorrection? {
        if (observed.length < 4) return null
        var best: DictionaryEntry? = null
        var bestDistance = 999

        for (entry in entries) {
            if (normalize(entry.original) == observed) continue
            if (entry.frequency < 3) continue
            val dist = levenshtein(observed, normalize(entry.original))
            if (dist < bestDistance && dist <= 2) {
                bestDistance = dist
                best = entry
            }
        }

        if (best == null) return null
        val correctionConfidence = (confidence + 0.25).coerceAtMost(1.0)
        if (correctionConfidence < AUTO_CORRECTION_SUGGEST_THRESHOLD) return null

        return AutoCorrection(best.original, correctionConfidence)
    }

    private data class AutoCorrection(val replacement: String, val confidence: Double)

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun normalizeSentence(value: String): String =
        value.lowercase()
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = i - 1
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                val substitution = if (a[i - 1] == b[j - 1]) 0 else 1
                costs[j] = minOf(costs[j] + 1, costs[j - 1] + 1, previous + substitution)
                previous = temp
            }
        }
        return costs[b.length]
    }
}
