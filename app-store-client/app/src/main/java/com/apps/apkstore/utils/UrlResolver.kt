package com.apps.apkstore.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlResolver @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Resolves share links to direct download URLs.
     * Supports: pCloud, (future: Google Drive, Dropbox)
     * If URL is already direct, returns it unchanged.
     */
    suspend fun resolve(url: String): String {
        return when {
            isPcloudLink(url) -> resolvePcloud(url)
            else -> url
        }
    }

    fun isPcloudLink(url: String): Boolean {
        return url.contains("pcloud.link/publink/show") ||
               url.contains("pcloud.com/publink") ||
               url.contains("u.pcloud.link")
    }

    private suspend fun resolvePcloud(shareUrl: String): String {
        return try {
            val request = Request.Builder()
                .url(shareUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return shareUrl

            // Extract publinkData JSON from HTML
            val regex = Regex("""var\s+publinkData\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html)
            if (match != null) {
                val json = match.groupValues[1]
                // Extract downloadlink value
                val downloadRegex = Regex(""""downloadlink"\s*:\s*"([^"]+)"""")
                val downloadMatch = downloadRegex.find(json)
                if (downloadMatch != null) {
                    val directUrl = downloadMatch.groupValues[1]
                        .replace("\\/", "/")
                        .replace("\\u002F", "/")
                    return directUrl
                }
            }

            // Fallback: try to find any direct download URL pattern
            val fallbackRegex = Regex(""""(https?://[^"]*?\.apk[^"]*?)"""")
            val fallbackMatch = fallbackRegex.find(html)
            if (fallbackMatch != null) {
                return fallbackMatch.groupValues[1]
                    .replace("\\/", "/")
                    .replace("\\u002F", "/")
            }

            shareUrl
        } catch (e: Exception) {
            shareUrl
        }
    }
}
