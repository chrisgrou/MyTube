package com.chrisgrou.mytube

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chrisgrou.mytube.update.UpdateChecker
import com.chrisgrou.mytube.update.UpdateInfo
import com.chrisgrou.mytube.update.UpdateInstaller
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var switchFilter: SwitchMaterial
    private lateinit var switchBlockAds: SwitchMaterial
    private lateinit var buttonVideoQuality: Button
    private lateinit var textCurrentVersion: TextView
    private lateinit var buttonCheckUpdates: Button
    private lateinit var textUpdateStatus: TextView
    private lateinit var buttonDownloadInstall: Button
    private lateinit var progressUpdate: ProgressBar
    private lateinit var containerHistory: LinearLayout
    private lateinit var buttonCopyDebugLog: Button

    private var pendingUpdate: UpdateInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        switchFilter = findViewById(R.id.switchFilterImagePosts)
        switchBlockAds = findViewById(R.id.switchBlockAds)
        buttonVideoQuality = findViewById(R.id.buttonVideoQuality)
        textCurrentVersion = findViewById(R.id.textCurrentVersion)
        buttonCheckUpdates = findViewById(R.id.buttonCheckUpdates)
        textUpdateStatus = findViewById(R.id.textUpdateStatus)
        buttonDownloadInstall = findViewById(R.id.buttonDownloadInstall)
        progressUpdate = findViewById(R.id.progressUpdate)
        containerHistory = findViewById(R.id.containerHistory)
        buttonCopyDebugLog = findViewById(R.id.buttonCopyDebugLog)

        switchFilter.isChecked = prefs.hideImagePosts
        switchFilter.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideImagePosts = isChecked
        }

        switchBlockAds.isChecked = prefs.blockAds
        switchBlockAds.setOnCheckedChangeListener { _, isChecked ->
            prefs.blockAds = isChecked
        }

        updateVideoQualityButtonLabel()
        buttonVideoQuality.setOnClickListener { showVideoQualityPicker() }

        textCurrentVersion.text = getString(
            R.string.current_version,
            "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        )

        buttonCheckUpdates.setOnClickListener { checkForUpdates() }
        buttonDownloadInstall.setOnClickListener { onDownloadInstallClicked() }

        renderHistory()

        buttonCopyDebugLog.setOnClickListener { copyDebugLogToClipboard() }
    }

    // TEMPORARY: pairs with FeedScript.kt's seek-pause diagnostic logging and
    // DebugLog. The user has no way to pull Logcat off their device, so this
    // puts the captured lines on the clipboard to paste back into chat.
    // Remove once the real cause is found.
    private fun copyDebugLogToClipboard() {
        val log = DebugLog.getAll()
        if (log.isBlank()) {
            Toast.makeText(this, R.string.debug_log_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("MyTube debug log", log))
        val lineCount = log.count { it == '\n' } + 1
        Toast.makeText(this, getString(R.string.debug_log_copied, lineCount), Toast.LENGTH_SHORT).show()
    }

    private fun checkForUpdates() {
        buttonCheckUpdates.isEnabled = false
        buttonDownloadInstall.visibility = View.GONE
        pendingUpdate = null
        textUpdateStatus.text = getString(R.string.checking_updates)

        lifecycleScope.launch {
            val result = runCatching {
                UpdateChecker.checkForUpdate(BuildConfig.GITHUB_REPO, BuildConfig.VERSION_CODE)
            }

            result.onSuccess { update ->
                if (update != null) {
                    pendingUpdate = update
                    val notes = update.releaseNotes?.let { "\n\n$it" } ?: ""
                    textUpdateStatus.text = getString(R.string.update_available, update.versionCode.toString()) + notes
                    buttonDownloadInstall.visibility = View.VISIBLE
                } else {
                    textUpdateStatus.text = getString(R.string.up_to_date)
                }
            }.onFailure { error ->
                textUpdateStatus.text = getString(R.string.update_check_failed, error.message)
            }
            buttonCheckUpdates.isEnabled = true
        }
    }

    private fun onDownloadInstallClicked() {
        val update = pendingUpdate ?: return
        if (!UpdateInstaller.canRequestInstall(this)) {
            textUpdateStatus.text = getString(R.string.install_permission_needed)
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        buttonDownloadInstall.isEnabled = false
        progressUpdate.visibility = View.VISIBLE
        progressUpdate.isIndeterminate = true
        textUpdateStatus.text = getString(R.string.downloading_update)

        lifecycleScope.launch {
            val uri = runCatching {
                UpdateInstaller.downloadApk(this@SettingsActivity, update.apkUrl) { progress ->
                    progressUpdate.isIndeterminate = false
                    progressUpdate.progress = (progress * 100).toInt()
                }
            }.getOrElse {
                progressUpdate.visibility = View.GONE
                buttonDownloadInstall.isEnabled = true
                textUpdateStatus.text = it.message ?: "Αποτυχία λήψης"
                return@launch
            }
            progressUpdate.visibility = View.GONE
            buttonDownloadInstall.isEnabled = true
            UpdateInstaller.launchInstall(this@SettingsActivity, uri)
        }
    }

    private fun updateVideoQualityButtonLabel() {
        val current = VideoQuality.fromId(prefs.videoQuality)
        buttonVideoQuality.text = getString(R.string.pref_quality_button, current.label)
    }

    private fun showVideoQualityPicker() {
        val options = VideoQuality.entries.toTypedArray()
        val currentIndex = options.indexOf(VideoQuality.fromId(prefs.videoQuality))
        AlertDialog.Builder(this)
            .setTitle(R.string.pref_quality_title)
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), currentIndex) { dialog, which ->
                prefs.videoQuality = options[which].id
                updateVideoQualityButtonLabel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun makeInfoText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 4)
        }
    }
}
