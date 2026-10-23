
package com.company.phonedirectory

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AppUpdater(private val activity: Activity) {

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy {
        activity.getSharedPreferences("app_updater", Context.MODE_PRIVATE)
    }

    private var downloadId = -1L
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val id = intent.getLongExtra(
                DownloadManager.EXTRA_DOWNLOAD_ID, -1L
            )
            if (id != downloadId) return

            val manager = activity.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

            val uri = manager.getUriForDownloadedFile(id)

            if (uri == null) {
                Toast.makeText(
                    activity,
                    "Download failed. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            openInstaller(uri)
        }
    }

    fun checkForUpdate() {
        executor.execute {
            try {
                val connection = URL(
                    "https://api.github.com/repos/akaka19/TELE-NEW-APP/releases/latest"
                ).openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "Accept", "application/vnd.github+json"
                )
                connection.setRequestProperty(
                    "User-Agent", "HiPath-Directory-Android"
                )

                try {
                    if (connection.responseCode != 200) return@execute

                    val json = JSONObject(
                        connection.inputStream.bufferedReader().use {
                            it.readText()
                        }
                    )

                    if (json.optBoolean("draft", false) ||
                        json.optBoolean("prerelease", false)
                    ) {
                        return@execute
                    }

                    val latestTag = json.optString("tag_name")
                        .removePrefix("v")
                        .removePrefix("V")

                    val assets = json.optJSONArray("assets")
                        ?: return@execute

                    var apkUrl: String? = null

                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name") == "app-debug.apk") {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }

                    val url = apkUrl ?: return@execute

                    val currentVersion = activity.packageManager
                        .getPackageInfo(activity.packageName, 0)
                        .versionName ?: "1.0.0"

                    if (!isNewerVersion(latestTag, currentVersion)) {
                        return@execute
                    }

                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showUpdateDialog(latestTag, url)
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                // If offline or GitHub is unavailable, keep the app usable.
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map {
            it.toIntOrNull() ?: 0
        }
        val currentParts = current.split(".").map {
            it.toIntOrNull() ?: 0
        }

        val length = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until length) {
            val a = latestParts.getOrElse(i) { 0 }
            val b = currentParts.getOrElse(i) { 0 }

            if (a > b) return true
            if (a < b) return false
        }

        return false
    }

    private fun showUpdateDialog(version: String, apkUrl: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(
                "HiPath Directory $version is available.\n\n" +
                "Do you want to download and install the new version?"
            )
            .setPositiveButton("Update") { _, _ ->
                downloadUpdate(apkUrl)
            }
            .setNegativeButton("Later", null)
            .setCancelable(true)
            .show()
    }

    private fun downloadUpdate(apkUrl: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("HiPath Directory update")
                .setDescription("Downloading the latest version...")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    "HiPathDirectory-update.apk"
                )

            val manager = activity.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

            downloadId = manager.enqueue(request)

            prefs.edit()
                .putLong("download_id", downloadId)
                .apply()

            registerDownloadReceiver()

            Toast.makeText(
                activity,
                "Update download started.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(
                activity,
                "Could not start the download.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun registerDownloadReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(
            DownloadManager.ACTION_DOWNLOAD_COMPLETE
        )

        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(
                downloadReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(downloadReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun openInstaller(uri: Uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(settingsIntent)

                Toast.makeText(
                    activity,
                    "Allow installs for HiPath Directory, then open the downloaded APK.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    uri,
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(installIntent)
        } catch (_: Exception) {
            Toast.makeText(
                activity,
                "Could not open the installer. Open the downloaded APK manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun cleanup() {
        if (receiverRegistered) {
            try {
                activity.unregisterReceiver(downloadReceiver)
            } catch (_: Exception) {
                // Receiver may already be unregistered.
            }
            receiverRegistered = false
        }
    }
}
