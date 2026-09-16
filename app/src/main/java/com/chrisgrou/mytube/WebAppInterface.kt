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
 * gesture doesn't fight with scrolling that content, plus a report of whether a
 * video is currently playing so the screen can be kept awake.
 */
class WebAppInterface(
    private val context: Context,
    private val onSetPullToRefreshAllowed: (Boolean) -> Unit,
    private val onVideoPlayingChanged: (Boolean) -> Unit,
    private val onVideoAspectChanged: (isPortrait: Boolean) -> Unit,
    private val onTapFullscreenButton: (cssX: Double, cssY: Double) -> Unit,
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
    fun getPreferredVideoQuality(): String {
        return Prefs(context).videoQuality
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

    @JavascriptInterface
    fun setVideoPlaying(playing: Boolean) {
        mainHandler.post { onVideoPlayingChanged(playing) }
    }

    @JavascriptInterface
    fun setVideoAspect(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        mainHandler.post { onVideoAspectChanged(height > width) }
    }

    // TEMPORARY: backs the seek-pause diagnostic logging in FeedScript.kt —
    // see DebugLog's doc comment. Remove both once the real cause is found.
    @JavascriptInterface
    fun logDebug(message: String) {
        DebugLog.add(message)
    }

    // A JS .click() on YouTube's fullscreen button invokes its handler but
    // doesn't carry real browser "user activation" — confirmed by a captured
    // debug log where a correct, visible, enabled button was clicked yet
    // fullscreen never engaged. So instead this asks native to dispatch a
    // genuine synthetic touch (a real MotionEvent through the WebView's
    // actual input pipeline) at the button's on-screen position, which does
    // count as a real gesture. See MainActivity.tapFullscreenButton.
    @JavascriptInterface
    fun tapFullscreenButton(cssX: Double, cssY: Double) {
        mainHandler.post { onTapFullscreenButton(cssX, cssY) }
    }
}
