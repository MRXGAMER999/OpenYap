package com.openyap.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GroqLLMClient(private val client: HttpClient) {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"
        private const val MAX_RETRIES = 2
        private val RETRY_DELAYS_MS = longArrayOf(500, 1500)

        private val DEFAULT_MODELS = listOf(
            ModelInfo("moonshotai/kimi-k2-instruct-0905", "Kimi K2 Instruct 0905"),
            ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile"),
            ModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant"),
            ModelInfo("qwen/qwen3-32b", "Qwen 3 32B"),
            ModelInfo("openai/gpt-oss-120b", "GPT-OSS 120B"),
            ModelInfo("openai/gpt-oss-20b", "GPT-OSS 20B"),
        )

        private val EXCLUDED_MODEL_PREFIXES = listOf(
            "whisper",
            "canopylabs/orpheus",
            "meta-llama/llama-prompt-guard",
        )

        /** Exact model IDs that are deprecated and should be hidden from the list. */
        private val EXCLUDED_MODEL_IDS = setOf(
            "moonshotai/kimi-k2-instruct",
        )
    }

    suspend fun listModels(apiKey: String): List<ModelInfo> {
        return try {
            val response = client.get("$BASE_URL/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                throw GroqLLMException("Authentication failed (${response.status.value}): check your API key")
            }
            if (response.status != HttpStatusCode.OK) return DEFAULT_MODELS

            val body = response.body<GroqModelsListResponse>()
            val filtered = body.data
                .asSequence()
                .filterNot { model ->
                    EXCLUDED_MODEL_PREFIXES.any { prefix -> model.id.startsWith(prefix, ignoreCase = true) }
                }
                .filterNot { it.id.contains("compound") }
                .filterNot { it.id in EXCLUDED_MODEL_IDS }
                .map { ModelInfo(id = it.id, displayName = formatModelName(it.id, it.ownedBy)) }
                .sortedWith(
                    compareByDescending<ModelInfo> { it.id.contains("kimi-k2") }
                        .thenByDescending { it.id.contains("gpt-oss-120b") }
                        .thenByDescending { it.id.contains("70b") }
                        .thenByDescending { it.id.contains("32b") }
                        .thenBy { it.id }
                )
                .toList()

            filtered.ifEmpty { DEFAULT_MODELS }
        } catch (e: GroqLLMException) {
            throw e
        } catch (_: Exception) {
            DEFAULT_MODELS
        }
    }

    /**
     * Rewrite/correct a raw transcript using a Groq-hosted text model.
     *
     * The system prompt is placed first (static) and the dynamic transcript
     * is appended as the user message, maximizing prefix cache hits across
     * repeated calls with the same system configuration.
     */
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
            appendLine("- NEVER censor, mask, or replace any words with asterisks or symbols. Preserve all words exactly as they appear, including profanity, slang, and explicit language. If the transcript contains masked or redacted tokens (asterisks), preserve them exactly as they appear and do not attempt to reconstruct or guess the redacted words.")
            appendLine()
            appendLine("Return only the final corrected text to paste.")
        }

        val fullSystemPrompt = if (systemPrompt.isNotBlank()) {
            "$systemPrompt\n\n$correctionPrompt"
        } else {
            correctionPrompt
        }

        val requestBody = GroqChatRequest(
            model = model,
            messages = listOf(
                GroqChatMessage(role = "system", content = fullSystemPrompt),
                GroqChatMessage(role = "user", content = "Transcript:\n$text"),
            ),
            temperature = 0.2f,
        )

        val response = executeWithRetry {
            val response = client.post("$BASE_URL/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500) {
                throw TemporaryGroqLLMException("Temporary Groq LLM API error (${response.status.value}).")
            }

            response
        }

        if (response.status != HttpStatusCode.OK) {
            val body = response.body<String>()
            throw GroqLLMException("Groq LLM API error (${response.status.value}): $body")
        }

        val chatResponse = response.body<GroqChatResponse>()
        val rewrittenText = chatResponse.choices
            .firstOrNull()
            ?.message
            ?.content

        if (rewrittenText.isNullOrBlank()) {
            throw GroqLLMException("Groq LLM returned an empty response.")
        }
        return rewrittenText.trim()
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }
        throw lastException ?: GroqLLMException("Request failed after retries.")
    }

    private fun formatModelName(id: String, ownedBy: String): String {
        val name = id
            .substringAfterLast("/")
            .replace("-instruct", " Instruct")
            .replace("-instant", " Instant")
            .replace("-versatile", " Versatile")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                when {
                    word.all { it.isDigit() || it == 'b' || it == 'B' } && word.contains("b", ignoreCase = true) -> word.uppercase()
                    word.length <= 3 -> word.uppercase()
                    else -> word.replaceFirstChar { it.uppercase() }
                }
            }
        return "$name ($ownedBy)"
    }
}

class GroqLLMException(message: String) : Exception(message)

private class TemporaryGroqLLMException(message: String) : Exception(message)

@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqChatMessage>,
    val temperature: Float = 0.2f,
)

@Serializable
data class GroqChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class GroqChatResponse(
    val id: String = "",
    val choices: List<GroqChatChoice> = emptyList(),
    val usage: GroqUsage? = null,
)

@Serializable
data class GroqChatChoice(
    val index: Int = 0,
    val message: GroqChatMessage = GroqChatMessage(role = "", content = ""),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class GroqUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
internal data class GroqModelsListResponse(
    val data: List<GroqModelEntry> = emptyList(),
)

@Serializable
internal data class GroqModelEntry(
    val id: String = "",
    @SerialName("owned_by") val ownedBy: String = "",
)
