package com.openyap.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GeminiClient(private val client: HttpClient) {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        private val DEFAULT_MODELS = listOf(
            GeminiModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash"),
            GeminiModelInfo("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite"),
            GeminiModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash"),
            GeminiModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro"),
        )
    }

    suspend fun listModels(apiKey: String): List<GeminiModelInfo> {
        return try {
            val response = client.get("$BASE_URL/models") {
                parameter("key", apiKey)
            }
            if (response.status != HttpStatusCode.OK) return DEFAULT_MODELS

            val body = response.body<ModelsListResponse>()
            val filtered = body.models
                .asSequence()
                .filter { it.name.startsWith("models/gemini") }
                .filterNot { "embedding" in it.name || "aqa" in it.name || "imagen" in it.name }
                .map { raw ->
                    val id = raw.name.removePrefix("models/")
                    GeminiModelInfo(id = id, displayName = raw.displayName)
                }
                .filterNot { it.id.endsWith("-latest") || "latest" in it.id }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<GeminiModelInfo> { it.id.contains("2.0") || it.id.contains("2.5") }
                    .thenByDescending { it.id.contains("flash") }
                    .thenBy { it.id })
                .toList()

            filtered.ifEmpty { DEFAULT_MODELS }
        } catch (_: Exception) {
            DEFAULT_MODELS
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun processAudio(
        audioBytes: ByteArray,
        systemPrompt: String,
        apiKey: String,
        model: String,
    ): String {
        val base64Audio = Base64.encode(audioBytes)

        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = systemPrompt),
                        GeminiPart(
                            inlineData = InlineData(
                                mimeType = "audio/wav",
                                data = base64Audio,
                            )
                        ),
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
            ),
        )

        val response = client.post("$BASE_URL/models/$model:generateContent") {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
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
}

class GeminiException(message: String) : Exception(message)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
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
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

data class GeminiModelInfo(
    val id: String,
    val displayName: String,
)

@Serializable
internal data class ModelsListResponse(
    val models: List<ModelsListEntry> = emptyList(),
)

@Serializable
internal data class ModelsListEntry(
    val name: String = "",
    @SerialName("displayName") val displayName: String = "",
)
