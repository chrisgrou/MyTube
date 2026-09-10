package com.chrisgrou.mytube

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlin.math.roundToInt

private const val HOME_URL = "https://m.youtube.com/"
/** Path of YouTube's own Settings page (confirmed from a captured page snapshot). */
private const val SETTINGS_PAGE_PATH = "/select_site"
private const val DESKTOP_LIKE_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: PlayerGestureLayout? = null
    private lateinit var buttonSettings: ImageButton

    /**
     * Stream volume is coarse (typically 0..15), so a small drag would round to
     * no change at all and feel stuck. Volume is accumulated as a float across a
     * gesture and only rounded when handed to AudioManager.
     */
    private var pendingVolume: Float? = null

    /**
     * Shape of the most recently played video, reported from the page. Decides
     * which way fullscreen rotates; null until a video reports its dimensions,
     * in which case landscape is assumed (the common case).
     */
    private var lastVideoIsPortrait: Boolean? = null

    /**
     * Read by shouldInterceptRequest, which the WebView calls on a background
     * thread for every single request — far too hot to hit SharedPreferences in,
     * so the preference is mirrored here and refreshed in onResume (i.e. after
     * returning from Settings).
     */
    @Volatile
    private var adBlockEnabled: Boolean = true

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way — see requestNotificationPermissionIfNeeded */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpEdgeToEdge()

        val rootContainer = findViewById<View>(R.id.rootContainer)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        buttonSettings = findViewById(R.id.buttonSettings)
        buttonSettings.visibility = View.GONE
        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        swipeRefresh.setColorSchemeResources(R.color.youtube_red)
        swipeRefresh.setOnRefreshListener { webView.reload() }

        requestNotificationPermissionIfNeeded()

        // Edge-to-edge draws the WebView behind the status/navigation bars for a
        // borderless look, but without this the page's own top content renders
        // underneath the status bar and becomes unreachable. Padding the WebView
        // itself doesn't work — WebView doesn't honor its own padding for laying
        // out rendered page content — so pad the parent container instead, which
        // genuinely shrinks the WebView's bounds.
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(rootContainer)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = DESKTOP_LIKE_MOBILE_UA
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(
            WebAppInterface(
                context = this,
                onSetPullToRefreshAllowed = { allowed -> swipeRefresh.isEnabled = allowed },
                onVideoPlayingChanged = { playing ->
                    keepScreenOn(playing)
                    setPlaybackServiceRunning(playing)
                },
                onVideoAspectChanged = { isPortrait -> lastVideoIsPortrait = isPortrait },
            ),
            "MyTubeNative"
        )

        // The primary injection path: runs before any of the page's own scripts,
        // for every navigation including SPA soft-navigations — far more reliable
        // than waiting for onPageFinished and calling evaluateJavascript reactively
        // (which onPageFinished below still does too, as a fallback for older
        // WebView versions where this feature isn't supported).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, FeedScript.SCRIPT, setOf("*"))
        }

        adBlockEnabled = Prefs(this).blockAds

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (adBlockEnabled && AdBlocker.shouldBlock(request.url.toString())) {
                    return AdBlocker.blockedResponse()
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (e: ActivityNotFoundException) {
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(FeedScript.SCRIPT, null)
                swipeRefresh.isRefreshing = false
                swipeRefresh.isEnabled = true
                updateSettingsButtonVisibility(url)
            }

            // YouTube navigates to its own Settings page (and elsewhere) mostly via
            // pushState rather than a full page load, so onPageFinished alone
            // misses it — this fires on both kinds of navigation.
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                updateSettingsButtonVisibility(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                enterFullscreen(view)
            }

            override fun onHideCustomView() {
                leaveFullscreenIfActive()
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.message().contains("MyTube")) {
                    Log.d("MyTubeWebView", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                }
                return true
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            youTubeUrlFrom(intent)?.let { webView.loadUrl(it) }
        } else {
            webView.loadUrl(youTubeUrlFrom(intent) ?: HOME_URL)
        }
    }

    /**
     * Handles a YouTube link tapped in another app while MyTube is already
     * running (launchMode singleTask reuses this instance instead of creating a
     * second one, delivering the new link here rather than to a fresh onCreate).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        youTubeUrlFrom(intent)?.let { webView.loadUrl(it) }
    }

    /** The tapped URL, for a VIEW intent that opened the app via the link intent-filter. */
    private fun youTubeUrlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return if (uri.scheme == "http" || uri.scheme == "https") uri.toString() else null
    }

    /**
     * Only shown on YouTube's own Settings page — elsewhere it would just be
     * another floating button competing with the feed. Driven purely by the
     * WebView's current URL, so it doesn't depend on anything about the page's
     * DOM (unlike three earlier, all failed, attempts to inject a row into that
     * page — see FeedScript.kt's git history).
     */
    private fun updateSettingsButtonVisibility(url: String?) {
        val path = url?.let { Uri.parse(it).path }
        buttonSettings.visibility =
            if (path == SETTINGS_PAGE_PATH) View.VISIBLE else View.GONE
    }

    private fun setUpEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    /**
     * Fullscreen playback: the player view goes edge to edge with the status and
     * navigation bars hidden (a swipe brings them back temporarily), the screen
     * rotates to landscape the way the YouTube app does, and vertical swipes on
     * the left/right half control brightness/volume.
     */
    private fun enterFullscreen(playerView: View) {
        val container = PlayerGestureLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                playerView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        val indicator = TextView(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 28, 48, 28)
            visibility = View.GONE
        }
        container.addView(
            indicator,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        container.onVerticalDrag = { onLeftHalf, delta ->
            handleFullscreenDrag(onLeftHalf, delta, container.height, indicator)
        }
        container.onDragEnd = {
            pendingVolume = null
            indicator.visibility = View.GONE
        }

        fullscreenContainer = container

        (window.decorView as FrameLayout).addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webView.visibility = View.GONE

        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        // Rotate immediately from the last reported shape so there's no visible
        // flip, then confirm against the DOM in case the cached value came from
        // a different video (an autoplaying feed preview, say) and correct it.
        applyFullscreenOrientation(lastVideoIsPortrait)
        webView.evaluateJavascript(
            "window.__mytubeVideoIsPortrait && window.__mytubeVideoIsPortrait()"
        ) { result ->
            if (customView == null) return@evaluateJavascript
            when (result) {
                "true" -> applyFullscreenOrientation(true)
                "false" -> applyFullscreenOrientation(false)
            }
        }
    }

    /** Portrait videos (and Shorts) stay portrait; everything else goes landscape. */
    private fun applyFullscreenOrientation(isPortrait: Boolean?) {
        requestedOrientation = if (isPortrait == true) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * Tears fullscreen down and tells the page about it. Returns false if there
     * was no fullscreen view, so the back key can fall through to normal
     * navigation. Also used by onHideCustomView, guarded so the page calling it
     * back after onCustomViewHidden() is a no-op.
     */
    private fun leaveFullscreenIfActive(): Boolean {
        if (customView == null) return false
        exitFullscreen()
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        return true
    }

    private fun exitFullscreen() {
        val decor = window.decorView as FrameLayout
        fullscreenContainer?.let { decor.removeView(it) }
        fullscreenContainer = null
        pendingVolume = null
        webView.visibility = View.VISIBLE

        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // Hand brightness back to the system rather than pinning whatever the
        // user swiped to during that one video.
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun handleFullscreenDrag(
        onLeftHalf: Boolean,
        deltaPixels: Float,
        containerHeight: Int,
        indicator: TextView,
    ) {
        if (containerHeight <= 0) return
        // Dragging across ~70% of the screen covers the full range; up increases.
        val fraction = -deltaPixels / (containerHeight * 0.7f)

        if (onLeftHalf) {
            val attributes = window.attributes
            val current = if (attributes.screenBrightness >= 0f) {
                attributes.screenBrightness
            } else {
                systemBrightness()
            }
            val next = (current + fraction).coerceIn(0.01f, 1f)
            window.attributes = attributes.apply { screenBrightness = next }
            showIndicator(indicator, getString(R.string.brightness_indicator, (next * 100).roundToInt()))
        } else {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = pendingVolume
                ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            val next = (current + fraction * max).coerceIn(0f, max.toFloat())
            pendingVolume = next
            // Flag 0: no system volume UI, we show our own indicator instead.
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next.roundToInt(), 0)
            showIndicator(indicator, getString(R.string.volume_indicator, (next / max * 100).roundToInt()))
        }
    }

    private fun showIndicator(indicator: TextView, text: String) {
        indicator.text = text
        indicator.visibility = View.VISIBLE
    }

    /** The device's current brightness (0..1), used as the starting point for a drag. */
    private fun systemBrightness(): Float = try {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Settings.SettingNotFoundException) {
        0.5f
    }

    private fun keepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * PlaybackService's only job is to keep this process alive (and so the
     * WebView's audio playing) once the app is backgrounded or the screen locks;
     * see its own doc comment for why a foreground service is what that takes.
     */
    private fun setPlaybackServiceRunning(running: Boolean) {
        val intent = Intent(this, PlaybackService::class.java)
        if (running) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            stopService(intent)
        }
    }

    /**
     * Android 13+ requires this permission to actually display a foreground
     * service's notification — without it the service still runs (so audio still
     * keeps playing), it's just invisible. Asked once, up front, like most media
     * apps do; declining doesn't block anything else in the app.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        adBlockEnabled = Prefs(this).blockAds
        // Picks up a filter toggle change made in Settings without a full reload.
        webView.evaluateJavascript(
            "window.__mytubeApplyFilter && window.__mytubeApplyFilter();",
            null
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Back out of fullscreen first, rather than out of the app.
            if (leaveFullscreenIfActive()) return true
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        setPlaybackServiceRunning(false)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
