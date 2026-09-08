package com.chrisgrou.mytube

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Request-level ad/tracker blocking, the same basic idea a content blocker like
 * Brave uses: refuse the request outright instead of hiding the result afterwards.
 *
 * Deliberately conservative. Anything that would break playback or login stays
 * off the list — in particular `*.googlevideo.com` (the actual video streams),
 * `i.ytimg.com`/`yt3.ggpht.com` (thumbnails, avatars) and every `accounts.google.com`
 * flow. That also means this layer alone cannot remove in-stream (pre-roll/mid-roll)
 * video ads: YouTube serves those from the same googlevideo.com hosts as real video,
 * with the ad metadata riding along inside the normal /youtubei/v1/player response.
 * Those are handled JS-side in FeedScript.kt instead.
 */
object AdBlocker {

    /** Matched against the request host, as an exact match or a dot-suffix. */
    private val BLOCKED_HOSTS = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "analytics.google.com",
        "2mdn.net",
        "ads.youtube.com",
        "ade.googlesyndication.com",
        "pagead2.googlesyndication.com",
    )

    /** Host prefixes — `adservice.google.<anything>` exists per country TLD. */
    private val BLOCKED_HOST_PREFIXES = listOf(
        "adservice.google.",
        "pagead.",
    )

    /**
     * Ad-specific paths on hosts we otherwise must keep working (youtube.com).
     * Kept narrow on purpose: blocking the general /youtubei/ API would break the app.
     */
    private val BLOCKED_PATHS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_info",
        "/generate_ad_break",
    )

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false

        val host = hostOf(lower) ?: return false
        for (blocked in BLOCKED_HOSTS) {
            if (host == blocked || host.endsWith(".$blocked")) return true
        }
        for (prefix in BLOCKED_HOST_PREFIXES) {
            if (host.startsWith(prefix)) return true
        }
        for (path in BLOCKED_PATHS) {
            if (lower.contains(path)) return true
        }
        return false
    }

    /**
     * A new empty response per call — a WebResourceResponse's stream is consumed
     * once, so a shared instance would only block the first request.
     */
    fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "No Content",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

    private fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val start = schemeEnd + 3
        var end = url.length
        for (i in start until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                end = i
                break
            }
        }
        val authority = url.substring(start, end)
        return authority.substringAfter('@').substringBefore(':').ifEmpty { null }
    }
}
