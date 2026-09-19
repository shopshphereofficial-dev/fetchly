package com.fetchly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class DownloadsFragment : Fragment(R.layout.fragment_downloads) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastFingerprint = ""
    private val poller = object : Runnable {
        override fun run() {
            view?.let { render(it) }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poller)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(poller)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) handler.removeCallbacks(poller) else handler.post(poller)
    }

    private fun render(view: View) {
        val ll = view.findViewById<LinearLayout>(R.id.llItems)
        val records = Db.get(requireContext()).allDownloads()
        // skip re-rendering when nothing actually changed - keeps scrolling smooth
        val fingerprint = records.joinToString("|") { "${it.id}:${it.status}:${it.progress}:${it.speed}" }
        if (fingerprint == lastFingerprint) return
        lastFingerprint = fingerprint
        ll.removeAllViews()
        if (records.isEmpty()) {
            val tv = TextView(requireContext())
            tv.text = getString(R.string.no_downloads_yet)
            tv.setTextColor(requireContext().getColor(R.color.text_secondary))
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            tv.setPadding(8, 60, 8, 60)
            ll.addView(tv)
            return
        }
        for (rec in records) {
            ll.addView(buildRow(rec))
        }
    }

    private fun statusLabel(status: String): String {
        val res = when (status) {
            Db.STATUS_QUEUED -> R.string.status_queued
            Db.STATUS_PREPARING -> R.string.status_preparing
            Db.STATUS_DOWNLOADING -> R.string.status_downloading
            Db.STATUS_COMPLETED -> R.string.status_completed
            Db.STATUS_FAILED -> R.string.status_failed
            Db.STATUS_CANCELLED -> R.string.status_cancelled
            else -> R.string.status_queued
        }
        return getString(res)
    }

    private fun buildRow(rec: Db.DownloadRecord): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.VERTICAL
        row.setBackgroundResource(R.drawable.bg_card)
        row.setPadding(24, 18, 16, 14)
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 }

        val title = TextView(ctx)
        title.text = rec.title ?: rec.url
        title.setTextColor(ctx.getColor(R.color.text))
        title.textSize = 15f
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(title)

        val meta = TextView(ctx)
        var metaText = statusLabel(rec.status)
        if (rec.status == Db.STATUS_DOWNLOADING && rec.progress > 0) {
            metaText = "$metaText ${rec.progress}%"
        }
        if (!rec.speed.isNullOrBlank()) metaText += " • ${rec.speed}"
        if (rec.status == Db.STATUS_COMPLETED && rec.size > 0) {
            metaText += " • ${Utils.formatSize(rec.size)}"
        }
        meta.text = metaText
        meta.setTextColor(ctx.getColor(R.color.text_secondary))
        meta.textSize = 12f
        row.addView(meta)

        if (rec.status == Db.STATUS_DOWNLOADING) {
            val pb = android.widget.ProgressBar(
                ctx, null, android.R.attr.progressBarStyleHorizontal
            )
            pb.max = 100
            pb.progress = rec.progress
            row.addView(
                pb,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 14).apply {
                    topMargin = 10
                }
            )
        }

        val buttons = LinearLayout(ctx)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.END
        buttons.setPadding(0, 10, 0, 0)
        row.addView(buttons)

        fun addButton(labelRes: Int, colorRes: Int, onClick: () -> Unit) {
            val b = TextView(ctx)
            b.text = getString(labelRes)
            b.setTextColor(ctx.getColor(colorRes))
            b.textSize = 13f
            b.setPadding(28, 16, 28, 16)
            b.setOnClickListener { onClick() }
            buttons.addView(b)
        }

        when (rec.status) {
            Db.STATUS_QUEUED, Db.STATUS_PREPARING, Db.STATUS_DOWNLOADING -> {
                addButton(R.string.btn_cancel, R.color.danger) {
                    DownloadService.cancel(ctx, rec.id)
                }
            }
            Db.STATUS_FAILED, Db.STATUS_CANCELLED -> {
                addButton(R.string.btn_retry, R.color.primary) {
                    DownloadService.retry(ctx, rec.id)
                }
                addButton(R.string.btn_delete, R.color.danger) {
                    confirmDelete(rec)
                }
            }
            Db.STATUS_COMPLETED -> {
                addButton(R.string.btn_open, R.color.primary) { openRecord(rec) }
                addButton(R.string.btn_share, R.color.accent) { shareRecord(rec) }
                addButton(R.string.btn_delete, R.color.danger) { confirmDelete(rec) }
            }
        }

        return row
    }

    private fun openRecord(rec: Db.DownloadRecord) {
        val uri = rec.uri ?: return
        val isAudio = (rec.mime ?: "").startsWith("audio")
        if (isAudio) {
            val audio = Db.get(requireContext()).completedAudio()
            val index = audio.indexOfFirst { it.id == rec.id }
            AudioPlayerActivity.queue = audio
            AudioPlayerActivity.startIndex = if (index >= 0) index else 0
            startActivity(
                Intent(requireContext(), AudioPlayerActivity::class.java)
                    .putExtra("uri", uri)
            )
        } else {
            startActivity(
                Intent(requireContext(), VideoPlayerActivity::class.java)
                    .putExtra("uri", uri)
                    .putExtra("title", rec.title ?: "")
            )
        }
    }

    private fun shareRecord(rec: Db.DownloadRecord) {
        val uri = rec.uri ?: return
        try {
            val send = Intent(Intent.ACTION_SEND)
            send.type = rec.mime ?: "*/*"
            send.putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.btn_share)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(rec: Db.DownloadRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                try {
                    if (rec.uri != null) {
                        requireContext().contentResolver.delete(Uri.parse(rec.uri), null, null)
                    }
                } catch (e: Exception) {
                    // file may already be gone
                }
                if (rec.path != null) {
                    try {
                        java.io.File(rec.path).deleteRecursively()
                    } catch (e: Exception) {
                    }
                }
                Db.get(requireContext()).deleteDownload(rec.id)
                view?.let { render(it) }
                Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
