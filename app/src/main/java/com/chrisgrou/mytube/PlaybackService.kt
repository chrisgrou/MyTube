package com.chrisgrou.mytube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * A minimal foreground service whose only job is to keep the app process (and so
 * the WebView, and so YouTube's own audio/video element) alive once the user
 * locks the screen or switches away — without it, Android stops or eventually
 * kills a backgrounded app, which cuts the audio. Started when a video starts
 * playing (MainActivity's onVideoPlayingChanged, via the injected script's
 * play/pause events) and stopped when it stops.
 *
 * Deliberately does NOT request its own audio focus. It used to (for other apps
 * to duck/pause, like any other media app would trigger), but that meant two
 * separate AudioFocusRequest holders within the same process — this service's,
 * and the one Chromium/WebView already registers on its own for the actual
 * playing <video> element. Every genuine play resumed this service (after a
 * pause stopped it and abandoned its focus), so every resume re-requested
 * focus, which interrupted the WebView's own already-held request — Chromium
 * reacted to that as a focus loss and auto-paused the video within
 * milliseconds. Ducking/pausing for other apps' audio already happens via the
 * WebView's own focus handling, so this service doesn't need to duplicate it.
 * No transport controls yet — those need a MediaSession wired to the page's
 * actual play/pause/seek, which is a separate, larger piece of work than "keep
 * the audio alive" is on its own.
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14 (API 34) requires the foreground service type on the call
        // itself, matching the one declared in the manifest, not just the latter.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        createNotificationChannelIfNeeded()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playback_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
    }
}
