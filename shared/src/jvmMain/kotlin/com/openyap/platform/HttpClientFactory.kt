package com.openyap.platform

import com.openyap.service.GeminiClient
import com.openyap.service.GroqLLMClient
import com.openyap.service.GroqWhisperClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    private fun createHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    fun createGeminiClient(): GeminiClient {
        return GeminiClient(createHttpClient())
    }

    fun createGroqWhisperClient(): GroqWhisperClient {
        return GroqWhisperClient(createHttpClient())
    }

    fun createGroqLLMClient(): GroqLLMClient {
        return GroqLLMClient(createHttpClient())
    }
}
