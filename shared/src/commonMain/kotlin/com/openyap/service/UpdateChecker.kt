package com.openyap.service

import com.openyap.Platform
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTag: String,
    val hasUpdate: Boolean,
    val releaseNotes: String? = null,
    val releasePageUrl: String? = null,
    val downloadUrl: String? = null,
)

class UpdateChecker(
    private val client: HttpClient,
    private val owner: String = "your-org",
    private val repo: String = "OpenYap",
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): UpdateCheckResult? {
        return try {
            val currentVersion = normalizeVersion(Platform.APP_VERSION)

            val response = client.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "openyap-updater")
            }

            if (response.status != HttpStatusCode.OK) return null

            val body = response.body<String>()
            val release = json.decodeFromString(GitHubRelease.serializer(), body)

            val tagName = release.tagName?.trim() ?: return null
            val latestVersion = normalizeVersion(tagName)

            val msiUrl = release.assets?.firstOrNull {
                it.name?.lowercase()?.endsWith(".msi") == true
            }?.browserDownloadUrl

            UpdateCheckResult(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                releaseTag = tagName,
                hasUpdate = compareSemver(latestVersion, currentVersion) > 0,
                releaseNotes = release.body,
                releasePageUrl = release.htmlUrl,
                downloadUrl = msiUrl,
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun normalizeVersion(version: String): String {
            var normalized = version.trim()
            if (normalized.startsWith("v", ignoreCase = true)) {
                normalized = normalized.substring(1)
            }
            val plusIndex = normalized.indexOf('+')
            if (plusIndex > 0) normalized = normalized.substring(0, plusIndex)
            val dashIndex = normalized.indexOf('-')
            if (dashIndex > 0) normalized = normalized.substring(0, dashIndex)
            return normalized
        }

        fun compareSemver(a: String, b: String): Int {
            val aParts = a.split(".")
            val bParts = b.split(".")
            val maxLen = maxOf(aParts.size, bParts.size)
            for (i in 0 until maxLen) {
                val aVal = aParts.getOrNull(i)?.toIntOrNull() ?: 0
                val bVal = bParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (aVal != bVal) return aVal.compareTo(bVal)
            }
            return 0
        }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAsset>? = null,
) {
    @Serializable
    data class GitHubAsset(
        val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )
}
