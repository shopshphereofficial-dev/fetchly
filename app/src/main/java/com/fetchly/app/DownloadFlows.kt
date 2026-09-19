package com.fetchly.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object DownloadFlows {

    @Volatile
    private var resolving = false

    // Resolve a URL through the provider system and let the user pick a real,
    // available quality. Starts the download service on selection.
    fun start(activity: Activity, url: String) {
        if (!FetchlyApp.engineReady) {
            Toast.makeText(activity, R.string.engine_loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (FetchlyApp.engineUpdating) {
            Toast.makeText(activity, R.string.engine_updating_wait, Toast.LENGTH_LONG).show()
            return
        }
        if (resolving) return
        resolving = true
        Toast.makeText(activity, R.string.resolving, Toast.LENGTH_SHORT).show()
        Thread {
            val result = Providers.resolveDetailed(activity, url)
            activity.runOnUiThread {
                resolving = false
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (result.media == null && result.imageUrl == null) {
                    showErrorDialog(activity, url, result.error)
                } else {
                    val view = QualityPicker.buildView(activity, result) { mode, height, directUrl ->
                        DownloadService.start(activity, directUrl ?: url, mode, height)
                        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show()
                    }
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.choose_quality)
                        .setView(view)
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }.start()
    }

    private fun showErrorDialog(activity: Activity, url: String, error: String?) {
        var msg = activity.getString(R.string.no_media_detected)
        if (!error.isNullOrBlank()) {
            msg += "\n\n" + activity.getString(R.string.reason_fmt, error.take(300))
        }
        if (error?.contains("sign in", ignoreCase = true) == true ||
            error?.contains("login", ignoreCase = true) == true
        ) {
            msg += "\n\n" + activity.getString(R.string.login_hint)
        }
        AlertDialog.Builder(activity)
            .setMessage(msg)
            .setPositiveButton(R.string.try_again) { _, _ -> start(activity, url) }
            .setNeutralButton(R.string.open_in_browser) { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
