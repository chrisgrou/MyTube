package com.chrisgrou.mytube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
 * Deliberately minimal for now: just the always-required ongoing notification
 * (tapping it reopens the app) and requesting audio focus so other apps duck or
 * pause the way they would for any other media app. No transport controls yet —
 * those need a MediaSession wired to the page's actual play/pause/seek, which is
 * a separate, larger piece of work than "keep the audio alive" is on its own.
 */
class PlaybackService : Service() {

    private var audioFocusRequest: AudioFocusRequest? = null

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
        requestAudioFocus()
        return START_STICKY
    }

    override fun onDestroy() {
        abandonAudioFocus()
        super.onDestroy()
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

    private fun requestAudioFocus() {
        // Idempotent: onStartCommand can in principle run more than once for
        // the same "playback session" (e.g. the system restarting the
        // service). Re-requesting AUDIOFOCUS_GAIN when we already hold it
        // creates a second, distinct AudioFocusRequest — Android then treats
        // that as a new focus holder interrupting the previous one, which the
        // WebView's own already-playing video reacts to by auto-pausing
        // itself. See MainActivity.setPlaybackServiceRunning's doc comment.
        if (audioFocusRequest != null) return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
    }
}
