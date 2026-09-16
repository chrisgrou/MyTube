package com.chrisgrou.mytube

import android.app.Application
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyTubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        recordVersionHistoryIfNeeded()
        installCrashHandler()
        surfaceLastCrashIfAny()
    }

    // TEMPORARY: diagnosing the app disappearing during a phone call. If it's
    // an actual uncaught exception (not the system just killing a
    // backgrounded process for memory, which never reaches app code at all),
    // this catches it and persists the stack trace to SharedPreferences
    // (survives process death, unlike the in-memory DebugLog) before letting
    // the platform's default handler proceed as normal (still crashes/reports
    // the usual way — this only adds a durable record for us to read). See
    // Prefs.lastCrashInfo and surfaceLastCrashIfAny() below. Remove once the
    // cause of the disappearing-during-a-call report is found.
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                Prefs(this).lastCrashInfo = "$timestamp on thread ${thread.name}:\n$sw"
            } catch (e: Exception) {
                // Swallow — must not throw from a crash handler.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun surfaceLastCrashIfAny() {
        val prefs = Prefs(this)
        val crash = prefs.lastCrashInfo ?: return
        DebugLog.add("MyTube[crash] app previously crashed:\n$crash")
        prefs.lastCrashInfo = null
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
