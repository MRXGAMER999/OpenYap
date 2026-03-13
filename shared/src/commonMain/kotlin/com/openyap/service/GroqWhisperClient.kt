package com.openyap.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GroqWhisperClient(private val client: HttpClient) : TranscriptionService {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"

        private val AVAILABLE_MODELS = listOf(
            ModelInfo("whisper-large-v3", "Whisper Large V3"),
            ModelInfo("whisper-large-v3-turbo", "Whisper Large V3 Turbo"),
        )

        /**
         * Maps the audio MIME type to a filename extension Groq will accept.
         * Groq accepts: flac, mp3, mp4, mpeg, mpga, m4a, ogg, opus, wav, webm
         */
        private fun mimeToFilename(mimeType: String): String = when {
            mimeType.contains("mp4", ignoreCase = true) -> "audio.m4a"
            mimeType.contains("wav", ignoreCase = true) -> "audio.wav"
            mimeType.contains("mp3", ignoreCase = true) -> "audio.mp3"
            mimeType.contains("ogg", ignoreCase = true) -> "audio.ogg"
            mimeType.contains("flac", ignoreCase = true) -> "audio.flac"
            mimeType.contains("webm", ignoreCase = true) -> "audio.webm"
            mimeType.contains("mpeg", ignoreCase = true) -> "audio.mpeg"
            mimeType.contains("opus", ignoreCase = true) -> "audio.opus"
            else -> "audio.wav"
        }

        /** Build a Content-Disposition: form-data with name and filename. */
        private fun fileDisposition(fieldName: String, filename: String): String =
            ContentDisposition("form-data")
                .withParameter(ContentDisposition.Parameters.Name, fieldName)
                .withParameter(ContentDisposition.Parameters.FileName, filename)
                .toString()

        /** Build a Content-Disposition: form-data with name only. */
        private fun fieldDisposition(fieldName: String): String =
            ContentDisposition("form-data")
                .withParameter(ContentDisposition.Parameters.Name, fieldName)
                .toString()
    }

    override suspend fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        systemPrompt: String, // ignored — Whisper is pure transcription
        apiKey: String,
        model: String,
    ): String {
        val filename = mimeToFilename(mimeType)

        val parts = listOf(
            PartData.FileItem(
                provider = { ByteReadChannel(audioBytes) },
                dispose = {},
                partHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, fileDisposition("file", filename))
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentLength, audioBytes.size.toString())
                },
            ),
            PartData.FormItem(
                value = model,
                dispose = {},
                partHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, fieldDisposition("model"))
                },
            ),
            PartData.FormItem(
                value = "json",
                dispose = {},
                partHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, fieldDisposition("response_format"))
                },
            ),
            PartData.FormItem(
                value = "en",
                dispose = {},
                partHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, fieldDisposition("language"))
                },
            ),
            PartData.FormItem(
                value = "0",
                dispose = {},
                partHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, fieldDisposition("temperature"))
                },
            ),
        )

        val response = client.post("$BASE_URL/audio/transcriptions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(MultiPartFormDataContent(parts))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.body<String>()
            throw GroqWhisperException("Groq API error (${response.status.value}): $errorBody")
        }

        val result = response.body<GroqTranscriptionResponse>()
        if (result.text.isBlank()) {
            throw GroqWhisperException("Groq Whisper returned an empty transcription.")
        }
        return result.text
    }

    override suspend fun listModels(apiKey: String): List<ModelInfo> = AVAILABLE_MODELS
}

class GroqWhisperException(message: String) : Exception(message)

@Serializable
internal data class GroqTranscriptionResponse(
    @SerialName("text") val text: String = "",
)
