package com.chrisgrou.mytube

import android.app.Application
import android.os.Build

class MyTubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        recordVersionHistoryIfNeeded()
    }

    /**
     * The remote GitHub release list is fetched on demand in Settings, but we also
     * keep a small local log of which versions actually got installed on this
     * device and when, so the user has a history even offline.
     */
    private fun recordVersionHistoryIfNeeded() {
        val prefs = Prefs(this)
        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        }
        if (prefs.lastRecordedVersionCode != currentVersionCode) {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: BuildConfig.VERSION_NAME
            prefs.addHistoryEntry(versionName, currentVersionCode, System.currentTimeMillis())
            prefs.lastRecordedVersionCode = currentVersionCode
        }
    }
}
