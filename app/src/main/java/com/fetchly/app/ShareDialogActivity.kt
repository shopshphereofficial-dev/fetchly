package com.fetchly.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Small floating popup (not the full app) that opens when a link is shared
 * from another app. Shows the available download options grouped as
 * VIDEO / AUDIO / IMAGE and returns to the source app after choosing.
 */
class ShareDialogActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = text?.let { Regex("https?://\\S+").find(it)?.value }
        if (url == null) {
            finish()
            return
        }

        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 48)
            setBackgroundResource(R.drawable.bg_dialog)
        }
        val scroll = ScrollView(this)
        scroll.addView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        setContentView(scroll)

        if (!FetchlyApp.engineReady || FetchlyApp.engineUpdating) {
            showWait(root)
            return
        }

        showChecking(root)
        resolve(root, url)
    }

    private fun showWait(root: LinearLayout) {
        root.removeAllViews()
        val tv = TextView(this)
        tv.text = getString(
            if (FetchlyApp.engineUpdating) R.string.engine_updating_wait else R.string.engine_loading
        )
        tv.setTextColor(getColor(R.color.text))
        tv.textSize = 14f
        tv.setPadding(8, 4, 8, 12)
        root.addView(tv)
        root.addView(closeButton { finish() })
    }

    private fun showChecking(root: LinearLayout) {
        root.removeAllViews()
        val tv = TextView(this)
        tv.text = getString(R.string.resolving)
        tv.setTextColor(getColor(R.color.text_secondary))
        tv.textSize = 14f
        tv.setPadding(8, 4, 8, 4)
        root.addView(tv)
        val pb = android.widget.ProgressBar(this)
        root.addView(
            pb,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = 24 }
        )
    }

    private fun resolve(root: LinearLayout, url: String) {
        Thread {
            val result = Providers.resolveDetailed(this, url)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                root.removeAllViews()
                if (result.media == null && result.imageUrl == null) {
                    showError(root, url, result.error)
                } else {
                    val view = QualityPicker.buildView(this, result) { mode, height, directUrl ->
                        DownloadService.start(this, directUrl ?: url, mode, height)
                        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    root.addView(view)
                }
            }
        }.start()
    }

    private fun showError(root: LinearLayout, url: String, error: String?) {
        var msg = getString(R.string.no_media_detected)
        if (!error.isNullOrBlank()) {
            msg += "\n\n" + getString(R.string.reason_fmt, error.take(300))
        }
        if (error?.contains("sign in", ignoreCase = true) == true ||
            error?.contains("login", ignoreCase = true) == true
        ) {
            msg += "\n\n" + getString(R.string.login_hint)
        }
        val tv = TextView(this)
        tv.text = msg
        tv.setTextColor(getColor(R.color.text))
        tv.textSize = 14f
        tv.setPadding(8, 4, 8, 12)
        root.addView(tv)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END

        val retry = Button(this)
        retry.text = getString(R.string.try_again)
        retry.setTextColor(getColor(R.color.primary))
        retry.background = null
        retry.setOnClickListener {
            showChecking(root)
            resolve(root, url)
        }
        row.addView(retry)

        val close = Button(this)
        close.text = getString(R.string.close)
        close.setTextColor(getColor(R.color.danger))
        close.background = null
        close.setOnClickListener { finish() }
        row.addView(close)

        root.addView(row)
    }

    private fun closeButton(onClick: () -> Unit): Button {
        val close = Button(this)
        close.text = getString(R.string.close)
        close.setTextColor(getColor(R.color.primary))
        close.background = null
        close.setOnClickListener { onClick() }
        return close
    }
}
