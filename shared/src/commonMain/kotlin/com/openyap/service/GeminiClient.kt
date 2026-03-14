package com.openyap.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class GeminiClient(private val client: HttpClient) : TranscriptionService {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_RETRIES = 2
        private val RETRY_DELAYS_MS = longArrayOf(500, 1500)

        private val DEFAULT_MODELS = listOf(
            ModelInfo("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview"),
            ModelInfo("gemini-3.1-flash-preview", "Gemini 3.1 Flash Preview"),
            ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"),
            ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro"),
        )

        /**
         * Regex matching versioned model suffixes like `-001`, `-002`, `-8b-001` etc.
         * We keep only the unversioned alias (e.g. `gemini-1.5-flash`) and drop
         * pinned versions (e.g. `gemini-1.5-flash-001`).
         */
        private val VERSIONED_SUFFIX = Regex("-\\d{3}$")
    }

    override suspend fun listModels(apiKey: String): List<ModelInfo> {
        return try {
            val response = client.get("$BASE_URL/models") {
                parameter("key", apiKey)
            }
            if (response.status != HttpStatusCode.OK) return DEFAULT_MODELS

            val body = response.body<ModelsListResponse>()
            val filtered = body.models
                .asSequence()
                // Only keep Gemini models that support content generation (excludes deprecated & embedding-only)
                .filter { it.name.startsWith("models/gemini") }
                .filter { "generateContent" in it.supportedGenerationMethods }
                .filterNot { "embedding" in it.name || "aqa" in it.name || "imagen" in it.name }
                .map { raw ->
                    val id = raw.name.removePrefix("models/")
                    ModelInfo(id = id, displayName = raw.displayName)
                }
                .filterNot { it.id.endsWith("-latest") || "latest" in it.id }
                // Drop pinned version variants (e.g. gemini-1.5-flash-001) — keep only the unversioned alias
                .filterNot { VERSIONED_SUFFIX.containsMatchIn(it.id) }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<ModelInfo> {
                        it.id.contains("3.") // prioritise 3.x first
                    }
                        .thenByDescending { it.id.contains("2.5") }
                        .thenByDescending { it.id.contains("2.0") }
                        .thenByDescending { it.id.contains("flash") }
                        .thenBy { it.id }
                )
                .toList()

            filtered.ifEmpty { DEFAULT_MODELS }
        } catch (_: Exception) {
            DEFAULT_MODELS
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
        whisperPrompt: String,
    ): String = processAudio(audioBytes, mimeType, systemPrompt, apiKey, model)

    suspend fun rewriteText(
        text: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
    ): String {
        val correctionPrompt = buildString {
            appendLine("You are doing a second-pass correction of a speech transcript that was already transcribed by a speech-to-text model.")
            appendLine("Your job is to preserve the user's intended message while making only conservative, context-aware corrections.")
            appendLine()
            appendLine("CORRECTION RULES:")
            appendLine("- Preserve the original meaning, wording, language, and intent as closely as possible.")
            appendLine("- Fix obvious punctuation, capitalization, spacing, and minor grammar issues.")
            appendLine("- Correct likely misheard or accent-related words ONLY when the surrounding context makes the intended word highly likely.")
            appendLine("- Keep domain-specific, product, company, and technical terms unless you are highly confident they were misrecognized.")
            appendLine("- If you are uncertain, keep the original word or phrase.")
            appendLine("- Do not paraphrase, summarize, expand, add detail, or change tone unless clearly required by the system instructions.")
            appendLine("- Do not invent names, facts, or context that are not strongly implied by the transcript.")
            appendLine()
            appendLine("Return only the final corrected text to paste.")
            appendLine()
            append("Transcript:\n")
            append(text)
        }

        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = correctionPrompt
                        ),
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(text = systemPrompt),
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0f,
                responseMimeType = "text/plain",
            ),
        )

        val response = executeWithRetry {
            client.post("$BASE_URL/models/$model:generateContent") {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }

        if (response.status != HttpStatusCode.OK) {
            val body = response.body<String>()
            throw GeminiException("API error (${response.status.value}): $body")
        }

        val geminiResponse = response.body<GeminiResponse>()
        val rewrittenText = geminiResponse.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull { it.text != null }
            ?.text

        if (rewrittenText.isNullOrBlank()) {
            throw GeminiException("Gemini returned an empty response.")
        }
        return rewrittenText
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun processAudio(
        audioBytes: ByteArray,
        mimeType: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
    ): String {
        val base64Audio = Base64.encode(audioBytes)

        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inlineData = InlineData(
                                mimeType = mimeType,
                                data = base64Audio,
                            )
                        ),
                        GeminiPart(text = "Transcribe this audio."),
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(text = systemPrompt),
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0f,
                responseMimeType = "text/plain",
            ),
        )

        val response = executeWithRetry {
            client.post("$BASE_URL/models/$model:generateContent") {
                parameter("key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }

        if (response.status != HttpStatusCode.OK) {
            val body = response.body<String>()
            throw GeminiException("API error (${response.status.value}): $body")
        }

        val geminiResponse = response.body<GeminiResponse>()

        val text = geminiResponse.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull { it.text != null }
            ?.text

        if (text.isNullOrBlank()) {
            throw GeminiException("Gemini returned an empty response.")
        }
        return text
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                // Don't retry on the last attempt
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }
        throw lastException ?: GeminiException("Request failed after retries.")
    }
}

class GeminiException(message: String) : Exception(message)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("system_instruction") val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    @SerialName("response_mime_type") val responseMimeType: String? = null,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Deprecated("Use ModelInfo instead", replaceWith = ReplaceWith("ModelInfo"))
typealias GeminiModelInfo = ModelInfo

@Serializable
internal data class ModelsListResponse(
    val models: List<ModelsListEntry> = emptyList(),
)

@Serializable
internal data class ModelsListEntry(
    val name: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("supportedGenerationMethods") val supportedGenerationMethods: List<String> = emptyList(),
)
