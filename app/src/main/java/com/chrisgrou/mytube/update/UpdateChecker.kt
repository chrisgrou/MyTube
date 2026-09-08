package com.chrisgrou.mytube.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val versionCode: Int, val apkUrl: String, val releaseUrl: String, val releaseNotes: String?)

/**
 * Checks the repo's "latest" GitHub Release (a fixed tag CI replaces on every push,
 * see .github/workflows/build.yml) for a build newer than the one currently
 * installed. The release's APK asset is named "mytube-<versionCode>.apk", so
 * the version number is read straight from the asset filename rather than needing a
 * separate API call.
 */
object UpdateChecker {

    private val client = OkHttpClient()

    /**
     * Returns the newer [UpdateInfo], or null if [currentVersionCode] is already
     * current. Throws (rather than returning null) on any failure to reach GitHub
     * or parse the release — e.g. a 404 because the repo is still private — so
     * the caller can tell "check failed" apart from "already up to date" instead
     * of both looking identical to the user.
     */
    suspend fun checkForUpdate(repo: String, currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/tags/latest")
            .header("Accept", "application/vnd.github+json")
            // GitHub's API rejects requests with no User-Agent with a 403 — this
            // isn't a permissions/visibility issue, it's just a required header.
            .header("User-Agent", "MyTube-Android-App")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} από το GitHub")
            val json = JSONObject(response.body?.string().orEmpty())
            val assets = json.optJSONArray("assets")
            if (assets == null || assets.length() == 0) error("Το release 'latest' δεν έχει APK asset")
            val asset = assets.getJSONObject(0)
            val remoteVersionCode = Regex("(\\d+)").find(asset.optString("name")).let { it?.value?.toIntOrNull() }
                ?: return@withContext null
            if (remoteVersionCode <= currentVersionCode) return@withContext null
            UpdateInfo(
                versionCode = remoteVersionCode,
                apkUrl = asset.optString("browser_download_url"),
                releaseUrl = json.optString("html_url"),
                releaseNotes = json.optString("body").trim().ifBlank { null },
            )
        }
    }
}
