package com.chrisgrou.mytube

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bridge exposed to the page as `window.MyTubeNative`. Kept intentionally small:
 * a read of the current filter preference, a way to open the native Settings
 * screen from the injected cog button, and a hook the page uses to tell native
 * code when a touch gesture starts inside one of its own nested scrollable
 * regions (a bottom sheet, a comments panel, ...) so SwipeRefreshLayout's pull
 * gesture doesn't fight with scrolling that content.
 */
class WebAppInterface(
    private val context: Context,
    private val onSetPullToRefreshAllowed: (Boolean) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun isHideImagePostsEnabled(): Boolean {
        return Prefs(context).hideImagePosts
    }

    @JavascriptInterface
    fun isAdBlockEnabled(): Boolean {
        return Prefs(context).blockAds
    }

    @JavascriptInterface
    fun openSettings() {
        val intent = Intent(context, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun setPullToRefreshAllowed(allowed: Boolean) {
        // JavascriptInterface methods run on a background thread; SwipeRefreshLayout
        // must only be touched from the UI thread.
        mainHandler.post { onSetPullToRefreshAllowed(allowed) }
    }
}
