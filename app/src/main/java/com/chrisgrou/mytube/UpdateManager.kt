package com.chrisgrou.mytube

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

sealed class UpdateCheckResult {
    data class Success(val releases: List<GitHubRelease>, val updateAvailable: GitHubRelease?) : UpdateCheckResult()
    data class Failure(val message: String) : UpdateCheckResult()
}

/**
 * Talks to the GitHub Releases API for chrisgrou/mytube. No auth needed since the
 * repo (and its releases) are public; GitHub does require a User-Agent header.
 */
object UpdateManager {

    private const val RELEASES_URL =
        "https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases"

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val json = httpGet(RELEASES_URL)
            val array = JSONArray(json)
            val releases = (0 until array.length()).map { parseRelease(array.getJSONObject(it)) }
            val latest = releases.firstOrNull()
            val updateAvailable = if (latest != null && isNewer(latest.tagName)) latest else null
            UpdateCheckResult.Success(releases, updateAvailable)
        } catch (e: Exception) {
            UpdateCheckResult.Failure(e.message ?: "unknown error")
        }
    }

    private fun parseRelease(o: JSONObject): GitHubRelease {
        var apkUrl: String? = null
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }
        return GitHubRelease(
            tagName = o.optString("tag_name"),
            name = o.optString("name").ifBlank { o.optString("tag_name") },
            body = o.optString("body"),
            publishedAt = o.optString("published_at"),
            htmlUrl = o.optString("html_url"),
            apkDownloadUrl = apkUrl
        )
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "MyTube-Android-App")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.inputStream.use { stream ->
            return stream.bufferedReader().readText()
        }
    }

    /** Compares dotted-numeric tags (with an optional leading 'v'), e.g. "v1.2.0" vs "1.0.0". */
    private fun isNewer(remoteTag: String): Boolean {
        val remote = remoteTag.removePrefix("v").removePrefix("V")
        val local = BuildConfig.VERSION_NAME
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        if (remoteParts.isEmpty() || localParts.isEmpty()) return remote != local
        val size = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until size) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    fun enqueueApkDownload(context: Context, release: GitHubRelease): Long? {
        val url = release.apkDownloadUrl ?: return null
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("MyTube ${release.tagName}")
            .setDescription("Λήψη ενημέρωσης")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "mytube-${release.tagName}.apk")
            .setMimeType("application/vnd.android.package-archive")
        return downloadManager.enqueue(request)
    }
}
