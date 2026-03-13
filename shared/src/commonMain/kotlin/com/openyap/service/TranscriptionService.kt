package com.openyap.service

/**
 * Common model info type used by both Gemini and Groq providers.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
)

/**
 * Abstraction over transcription providers (Gemini, Groq Whisper, etc.).
 */
interface TranscriptionService {
    /**
     * Transcribe audio bytes into text.
     *
     * @param audioBytes raw audio file bytes
     * @param mimeType MIME type of the audio (e.g. "audio/aac", "audio/wav")
     * @param systemPrompt system prompt for rewriting — ignored by providers that do raw transcription only
     * @param apiKey provider API key
     * @param model model identifier (e.g. "gemini-2.5-flash", "whisper-large-v3")
     */
    suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
    ): String

    /**
     * List available models for this provider.
     */
    suspend fun listModels(apiKey: String): List<ModelInfo>
}
