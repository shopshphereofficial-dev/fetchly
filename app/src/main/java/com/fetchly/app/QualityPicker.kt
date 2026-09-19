package com.fetchly.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Builds the grouped download-options view used by both the Home quality
 * dialog and the share popup: VIDEO / AUDIO / IMAGE sections, each with only
 * the options that are really available.
 */
object QualityPicker {

    // onPick(mode, height, directUrl) - directUrl is set for image downloads
    fun buildView(
        context: Context,
        result: ResolveResult,
        onPick: (mode: String, height: Int, directUrl: String?) -> Unit
    ): View {
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(4, 8, 4, 8)

        val media = result.media

        if (media != null && (media.heights.isNotEmpty() || media.hasAudio)) {
            val title = TextView(context)
            title.text = if (media.duration > 0) {
                media.title + "\n" + Utils.formatDuration(media.duration)
            } else {
                media.title
            }
            title.setTextColor(context.getColor(R.color.text))
            title.textSize = 15f
            title.maxLines = 2
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            title.setPadding(8, 4, 8, 12)
            root.addView(title)
        }

        if (media != null && media.heights.isNotEmpty()) {
            root.addView(header(context, R.string.sec_video))
            for (h in media.heights) {
                root.addView(option(context, "${h}p") { onPick("video", h, null) })
            }
        }

        if (media != null && media.hasAudio) {
            root.addView(header(context, R.string.sec_audio))
            root.addView(option(context, context.getString(R.string.audio_mp3)) { onPick("mp3", 0, null) })
            root.addView(option(context, context.getString(R.string.audio_original)) { onPick("audio", 0, null) })
        }

        if (result.imageUrl != null) {
            root.addView(header(context, R.string.sec_image))
            root.addView(option(context, context.getString(R.string.btn_save_image)) {
                onPick("image", 0, result.imageUrl)
            })
        }

        return root
    }

    private fun header(context: Context, labelRes: Int): TextView {
        val tv = TextView(context)
        tv.text = context.getString(labelRes)
        tv.setTextColor(context.getColor(R.color.primary))
        tv.textSize = 12f
        tv.textStyle = android.graphics.Typeface.BOLD
        tv.setPadding(8, 18, 8, 4)
        return tv
    }

    private fun option(context: Context, label: String, onClick: () -> Unit): TextView {
        val tv = TextView(context)
        tv.text = label
        tv.setTextColor(context.getColor(R.color.text))
        tv.textSize = 14f
        tv.gravity = Gravity.CENTER
        tv.setBackgroundResource(R.drawable.bg_btn_ghost)
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 108
        ).apply { topMargin = 10 }
        tv.setOnClickListener { onClick() }
        return tv
    }
}
