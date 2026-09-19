package com.fetchly.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object DownloadFlows {

    // Resolve a URL through the provider system and let the user pick a real,
    // available quality. Starts the download service on selection.
    fun start(activity: Activity, url: String) {
        if (!FetchlyApp.engineReady) {
            Toast.makeText(activity, R.string.engine_loading, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, R.string.resolving, Toast.LENGTH_SHORT).show()
        Thread {
            val media = Providers.resolve(activity, url)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (media == null) {
                    AlertDialog.Builder(activity)
                        .setMessage(R.string.no_media_detected)
                        .setPositiveButton(R.string.try_again) { _, _ -> start(activity, url) }
                        .setNeutralButton(R.string.open_in_browser) { _, _ ->
                            try {
                                activity.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            } catch (e: Exception) {
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    showQualityDialog(activity, url, media)
                }
            }
        }.start()
    }

    private fun showQualityDialog(activity: Activity, url: String, media: ResolvedMedia) {
        val items = mutableListOf<String>()
        for (h in media.heights) items.add(activity.getString(R.string.video_p, h))
        if (media.hasAudio) {
            items.add(activity.getString(R.string.audio_mp3))
            items.add(activity.getString(R.string.audio_original))
        }
        val subtitle = if (media.duration > 0) {
            media.title + "\n" + Utils.formatDuration(media.duration)
        } else {
            media.title
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.choose_quality)
            .setMessage(subtitle)
            .setItems(items.toTypedArray()) { _, which ->
                val mode: String
                val height: Int
                if (which < media.heights.size) {
                    mode = "video"
                    height = media.heights[which]
                } else if (which == media.heights.size) {
                    mode = "mp3"
                    height = 0
                } else {
                    mode = "audio"
                    height = 0
                }
                DownloadService.start(activity, url, mode, height)
                Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
