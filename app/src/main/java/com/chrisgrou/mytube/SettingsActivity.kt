package com.chrisgrou.mytube

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var switchFilter: SwitchMaterial
    private lateinit var textCurrentVersion: TextView
    private lateinit var buttonCheckUpdates: Button
    private lateinit var textUpdateStatus: TextView
    private lateinit var buttonDownloadInstall: Button
    private lateinit var progressUpdate: ProgressBar
    private lateinit var containerHistory: LinearLayout
    private lateinit var containerReleases: LinearLayout

    private var pendingRelease: GitHubRelease? = null
    private var pendingDownloadId: Long? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == -1L || id != pendingDownloadId) return
            progressUpdate.visibility = View.GONE
            installDownloadedApk(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = Prefs(this)
        switchFilter = findViewById(R.id.switchFilterImagePosts)
        textCurrentVersion = findViewById(R.id.textCurrentVersion)
        buttonCheckUpdates = findViewById(R.id.buttonCheckUpdates)
        textUpdateStatus = findViewById(R.id.textUpdateStatus)
        buttonDownloadInstall = findViewById(R.id.buttonDownloadInstall)
        progressUpdate = findViewById(R.id.progressUpdate)
        containerHistory = findViewById(R.id.containerHistory)
        containerReleases = findViewById(R.id.containerReleases)

        switchFilter.isChecked = prefs.hideImagePosts
        switchFilter.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideImagePosts = isChecked
        }

        textCurrentVersion.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)

        buttonCheckUpdates.setOnClickListener { checkForUpdates() }
        buttonDownloadInstall.setOnClickListener { onDownloadInstallClicked() }

        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        renderHistory()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: IllegalArgumentException) {
            // was never registered / already unregistered — ignore
        }
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun checkForUpdates() {
        buttonCheckUpdates.isEnabled = false
        buttonDownloadInstall.visibility = View.GONE
        textUpdateStatus.text = getString(R.string.checking_updates)

        lifecycleScope.launch {
            when (val result = UpdateManager.checkForUpdate()) {
                is UpdateCheckResult.Success -> {
                    renderReleases(result.releases)
                    val update = result.updateAvailable
                    if (update != null) {
                        pendingRelease = update
                        textUpdateStatus.text = getString(R.string.update_available, update.tagName)
                        buttonDownloadInstall.visibility =
                            if (update.apkDownloadUrl != null) View.VISIBLE else View.GONE
                    } else {
                        textUpdateStatus.text = getString(R.string.up_to_date)
                    }
                }
                is UpdateCheckResult.Failure -> {
                    textUpdateStatus.text = getString(R.string.update_check_failed)
                }
            }
            buttonCheckUpdates.isEnabled = true
        }
    }

    private fun onDownloadInstallClicked() {
        val release = pendingRelease ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            textUpdateStatus.text = getString(R.string.install_permission_needed)
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        progressUpdate.visibility = View.VISIBLE
        progressUpdate.isIndeterminate = true
        textUpdateStatus.text = getString(R.string.downloading_update)
        pendingDownloadId = UpdateManager.enqueueApkDownload(this, release)
    }

    private fun installDownloadedApk(downloadId: Long) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        // getUriForDownloadedFile already returns a content:// uri via the system
        // Downloads provider, which is safe to hand straight to the installer.
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderHistory() {
        containerHistory.removeAllViews()
        val entries = prefs.historyEntries()
        if (entries.isEmpty()) {
            containerHistory.addView(makeInfoText(getString(R.string.history_empty)))
            return
        }
        val formatter: DateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        entries.forEach { entry ->
            val line = "${entry.versionName} (build ${entry.versionCode}) — ${formatter.format(Date(entry.timestampMillis))}"
            containerHistory.addView(makeInfoText(line))
        }
    }

    private fun renderReleases(releases: List<GitHubRelease>) {
        containerReleases.removeAllViews()
        releases.forEach { release ->
            val title = "${release.name} (${release.tagName})"
            val body = release.body.trim().ifBlank { "—" }
            containerReleases.addView(makeInfoText(title, bold = true))
            containerReleases.addView(makeInfoText(body))
            containerReleases.addView(makeInfoText(" "))
        }
    }

    private fun makeInfoText(text: String, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 4)
            if (bold) {
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }
    }
}
