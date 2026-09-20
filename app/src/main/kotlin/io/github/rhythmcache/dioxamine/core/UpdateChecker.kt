package io.github.rhythmcache.dioxamine.core

import io.github.rhythmcache.dioxamine.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val isNewer: Boolean
)

internal fun parseVersionParts(raw: String): List<Int> {
    val clean = raw.trim().removePrefix("v").removePrefix("V")
    val numbersPart = clean.substringBefore('-')
    return numbersPart.split('.').mapNotNull { it.toIntOrNull() }
}

fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
    val latest = parseVersionParts(latestTag)
    val current = parseVersionParts(currentVersion)

    val maxParts = maxOf(latest.size, current.size)
    for (i in 0 until maxParts) {
        val l = latest.getOrElse(i) { 0 }
        val c = current.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }

    return false
}

object UpdateChecker {
    suspend fun fetchLatestRelease(
        apiUrl: String = BuildConfig.GITHUB_RELEASES_API_URL,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Dioxamine-Android-App")
                instanceFollowRedirects = true
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("HTTP $responseCode: $err")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "")
            val releaseNotes = json.optString("body", "")

            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (downloadUrl.isEmpty()) {
                downloadUrl = htmlUrl
            }

            val cleanVersion = tagName.trim().removePrefix("v").removePrefix("V")
            val isNewer = isNewerVersion(tagName, currentVersion)

            AppReleaseInfo(
                tagName = tagName,
                versionName = cleanVersion,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                htmlUrl = htmlUrl,
                isNewer = isNewer
            )
        }
    }
}
