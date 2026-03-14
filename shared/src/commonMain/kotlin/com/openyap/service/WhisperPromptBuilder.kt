package com.openyap.service

import com.openyap.model.PrimaryUseCase

/**
 * Builds a vocabulary-guidance prompt for the Whisper transcription API.
 *
 * The Whisper `prompt` parameter (max 224 tokens / ~150 words) provides context
 * that helps the model recognize domain-specific vocabulary, proper nouns, and
 * technical terms. It is not an instruction — it sets transcription expectations.
 */
object WhisperPromptBuilder {

    fun build(useCase: PrimaryUseCase, context: String): String {
        if (useCase == PrimaryUseCase.GENERAL && context.isBlank()) return ""

        val preamble = when (useCase) {
            PrimaryUseCase.PROGRAMMING -> "Software development discussion."
            PrimaryUseCase.BUSINESS -> "Professional business communication."
            PrimaryUseCase.CREATIVE_WRITING -> "Creative writing and content drafting."
            PrimaryUseCase.GENERAL -> "General dictation."
        }

        val contextHint = if (context.isNotBlank()) {
            when (useCase) {
                PrimaryUseCase.PROGRAMMING ->
                    " Technologies and tools: $context. Technical terms include function names, class names, APIs, frameworks, and code-related vocabulary."

                PrimaryUseCase.BUSINESS ->
                    " Domain focus: $context. Vocabulary includes industry terms, metrics, deliverables, and professional language."

                PrimaryUseCase.CREATIVE_WRITING ->
                    " Topics: $context. Vocabulary includes narrative terms, character references, and genre-specific language."

                PrimaryUseCase.GENERAL ->
                    " Context: $context."
            }
        } else ""

        val prompt = "$preamble$contextHint"

        // Whisper prompt is limited to 224 tokens (~150 words). Truncate conservatively.
        val words = prompt.split("\\s+".toRegex())
        return if (words.size > 140) {
            words.take(140).joinToString(" ")
        } else {
            prompt
        }
    }
}
