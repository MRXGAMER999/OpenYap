package com.openyap.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.IOException

class GroqWhisperClient(private val client: HttpClient) : TranscriptionService {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"
        private const val MAX_RETRIES = 2
        private val RETRY_DELAYS_MS = longArrayOf(500, 1500)

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
        systemPrompt: String,
        apiKey: String,
        model: String,
        whisperPrompt: String,
        language: String,
    ): String {
        val filename = mimeToFilename(mimeType)

        val parts = buildList {
            add(
                PartData.FileItem(
                    provider = { ByteReadChannel(audioBytes) },
                    dispose = {},
                    partHeaders = Headers.build {
                        append(HttpHeaders.ContentDisposition, fileDisposition("file", filename))
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentLength, audioBytes.size.toString())
                    },
                )
            )
            add(
                PartData.FormItem(
                    value = model,
                    dispose = {},
                    partHeaders = Headers.build {
                        append(HttpHeaders.ContentDisposition, fieldDisposition("model"))
                    },
                )
            )
            add(
                PartData.FormItem(
                    value = "text",
                    dispose = {},
                    partHeaders = Headers.build {
                        append(HttpHeaders.ContentDisposition, fieldDisposition("response_format"))
                    },
                )
            )
            if (language.isNotBlank()) {
                add(
                    PartData.FormItem(
                        value = language,
                        dispose = {},
                        partHeaders = Headers.build {
                            append(HttpHeaders.ContentDisposition, fieldDisposition("language"))
                        },
                    )
                )
            }
            add(
                PartData.FormItem(
                    value = "0.0",
                    dispose = {},
                    partHeaders = Headers.build {
                        append(HttpHeaders.ContentDisposition, fieldDisposition("temperature"))
                    },
                )
            )
            if (whisperPrompt.isNotBlank()) {
                add(
                    PartData.FormItem(
                        value = whisperPrompt,
                        dispose = {},
                        partHeaders = Headers.build {
                            append(HttpHeaders.ContentDisposition, fieldDisposition("prompt"))
                        },
                    )
                )
            }
        }

        val response = executeWithRetry {
            val response = client.post("$BASE_URL/audio/transcriptions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(MultiPartFormDataContent(parts))
            }

            if (response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500) {
                throw TemporaryGroqWhisperException("Temporary Groq Whisper API error (${response.status.value}).")
            }

            response
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.body<String>()
            throw GroqWhisperException("Groq API error (${response.status.value}): $errorBody")
        }

        val result = response.body<String>().trim()
        if (result.isBlank()) {
            throw GroqWhisperException("Groq Whisper returned an empty transcription.")
        }
        return result
    }

    override suspend fun listModels(apiKey: String): List<ModelInfo> = AVAILABLE_MODELS

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            } catch (e: ConnectTimeoutException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            } catch (e: TemporaryGroqWhisperException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }
        throw lastException ?: GroqWhisperException("Request failed after retries.")
    }
}

class GroqWhisperException(message: String) : Exception(message)

private class TemporaryGroqWhisperException(message: String) : Exception(message)

