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
import kotlinx.coroutines.CancellationException
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

        /** Safety settings that disable all content filtering for all harm categories. */
        private val ALL_DISABLED_SAFETY_SETTINGS = listOf(
            SafetySetting(category = "HARM_CATEGORY_HARASSMENT", threshold = "BLOCK_NONE"),
            SafetySetting(category = "HARM_CATEGORY_HATE_SPEECH", threshold = "BLOCK_NONE"),
            SafetySetting(category = "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold = "BLOCK_NONE"),
            SafetySetting(category = "HARM_CATEGORY_DANGEROUS_CONTENT", threshold = "BLOCK_NONE"),
            SafetySetting(category = "HARM_CATEGORY_CIVIC_INTEGRITY", threshold = "BLOCK_NONE"),
        )
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
        language: String,
    ): String = processAudio(
        audioBytes = audioBytes,
        mimeType = mimeType,
        systemPrompt = systemPrompt,
        apiKey = apiKey,
        model = model,
        temperature = 0.3f,
    )

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
        temperature: Float,
    ): String = processAudio(
        audioBytes = audioBytes,
        mimeType = mimeType,
        systemPrompt = systemPrompt,
        apiKey = apiKey,
        model = model,
        temperature = temperature,
    )

    suspend fun rewriteText(
        text: String,
        systemPrompt: String,
        apiKey: String,
        model: String,
        temperature: Float,
    ): String {
        val thinkingConfig = if (supportsThinking(model)) {
            ThinkingConfig(thinkingBudget = 512)
        } else {
            null
        }
        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = "Transcript:\n$text"
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
                temperature = temperature,
                responseMimeType = "text/plain",
                thinkingConfig = thinkingConfig,
            ),
            safetySettings = ALL_DISABLED_SAFETY_SETTINGS,
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
            ?.lastOrNull { it.text != null }
            ?.text

        if (rewrittenText.isNullOrBlank()) {
            val blockReason = geminiResponse.promptFeedback?.blockReason
            if (blockReason != null) throw GeminiException("Gemini blocked the request: $blockReason")
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
        temperature: Float,
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
                temperature = temperature,
                responseMimeType = "text/plain",
            ),
            safetySettings = ALL_DISABLED_SAFETY_SETTINGS,
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
            val blockReason = geminiResponse.promptFeedback?.blockReason
            if (blockReason != null) throw GeminiException("Gemini blocked the request: $blockReason")
            throw GeminiException("Gemini returned an empty response.")
        }
        return text
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }
        throw lastException ?: GeminiException("Request failed after retries.")
    }

    private fun supportsThinking(model: String): Boolean {
        if (!model.startsWith("gemini-2.5", ignoreCase = true)) return false

        val normalizedModel = model.lowercase()
        return "audio" !in normalizedModel
    }
}

class GeminiException(message: String) : Exception(message)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("system_instruction") val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null,
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
    @SerialName("thinkingConfig") val thinkingConfig: ThinkingConfig? = null,
)

@Serializable
data class ThinkingConfig(
    @SerialName("thinkingBudget") val thinkingBudget: Int? = null,
)

@Serializable
data class SafetySetting(
    val category: String,
    val threshold: String,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null,
)

@Serializable
data class GeminiPromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
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
