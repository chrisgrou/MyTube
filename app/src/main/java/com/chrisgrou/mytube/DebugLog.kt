package com.chrisgrou.mytube

/**
 * Small in-memory ring buffer for the temporary seek-pause diagnostic
 * logging (see FeedScript.kt's logVideoEvent). The user has no way to pull
 * Logcat off their device, so instead of relying on `adb`, the same lines
 * land here and SettingsActivity offers a "copy to clipboard" button — the
 * user can paste the result directly back into chat. Remove alongside the
 * rest of the seek-pause diagnostic once the real cause is found.
 */
object DebugLog {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) {
            lines.removeFirst()
        }
    }

    @Synchronized
    fun getAll(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
