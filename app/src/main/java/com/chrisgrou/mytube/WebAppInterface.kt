package com.chrisgrou.mytube

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

/**
 * Bridge exposed to the page as `window.MyTubeNative`. Kept intentionally tiny:
 * a read of the current filter preference, and a way to open the native
 * Settings screen from the injected cog button.
 */
class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun isHideImagePostsEnabled(): Boolean {
        return Prefs(context).hideImagePosts
    }

    @JavascriptInterface
    fun openSettings() {
        val intent = Intent(context, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
