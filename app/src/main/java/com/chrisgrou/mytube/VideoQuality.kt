package com.chrisgrou.mytube

/**
 * Quality levels as YouTube's own player identifies them (the same strings the
 * official IFrame Player API uses for `setPlaybackQuality` — the mobile web
 * player shares that same underlying player core, see FeedScript.kt).
 */
enum class VideoQuality(val id: String, val label: String) {
    AUTO("auto", "Αυτόματη"),
    HD1080("hd1080", "1080p"),
    HD720("hd720", "720p"),
    LARGE480("large", "480p"),
    MEDIUM360("medium", "360p"),
    SMALL240("small", "240p"),
    TINY144("tiny", "144p");

    companion object {
        fun fromId(id: String): VideoQuality = entries.firstOrNull { it.id == id } ?: AUTO
    }
}
