package com.chrisgrou.mytube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin wrapper around SharedPreferences for the app's settings and the
 * locally-recorded update history (distinct from the remote GitHub release list).
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("mytube_prefs", Context.MODE_PRIVATE)

    var hideImagePosts: Boolean
        get() = sp.getBoolean(KEY_HIDE_IMAGE_POSTS, true)
        set(value) = sp.edit().putBoolean(KEY_HIDE_IMAGE_POSTS, value).apply()

    var blockAds: Boolean
        get() = sp.getBoolean(KEY_BLOCK_ADS, true)
        set(value) = sp.edit().putBoolean(KEY_BLOCK_ADS, value).apply()

    /** Version code the app was at last time we recorded a history entry. */
    var lastRecordedVersionCode: Int
        get() = sp.getInt(KEY_LAST_VERSION_CODE, -1)
        set(value) = sp.edit().putInt(KEY_LAST_VERSION_CODE, value).apply()

    fun addHistoryEntry(versionName: String, versionCode: Int, timestampMillis: Long) {
        val history = historyEntries().toMutableList()
        history.add(0, HistoryEntry(versionName, versionCode, timestampMillis))
        val arr = JSONArray()
        history.forEach {
            arr.put(JSONObject().apply {
                put("versionName", it.versionName)
                put("versionCode", it.versionCode)
                put("timestamp", it.timestampMillis)
            })
        }
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun historyEntries(): List<HistoryEntry> {
        val raw = sp.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(o.getString("versionName"), o.getInt("versionCode"), o.getLong("timestamp"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    data class HistoryEntry(val versionName: String, val versionCode: Int, val timestampMillis: Long)

    companion object {
        private const val KEY_HIDE_IMAGE_POSTS = "hide_image_posts"
        private const val KEY_BLOCK_ADS = "block_ads"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"
        private const val KEY_HISTORY = "update_history"
    }
}
