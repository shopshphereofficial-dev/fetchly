package com.fetchly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class MusicFragment : Fragment(R.layout.fragment_music) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { render(it) }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) view?.let { render(it) }
    }

    private fun render(view: View) {
        val ll = view.findViewById<LinearLayout>(R.id.llMusic)
        val audio = Db.get(requireContext()).completedAudio()
        ll.removeAllViews()
        if (audio.isEmpty()) {
            val tv = TextView(requireContext())
            tv.text = getString(R.string.no_music_yet)
            tv.setTextColor(requireContext().getColor(R.color.text_secondary))
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            tv.setPadding(8, 60, 8, 60)
            ll.addView(tv)
            return
        }
        for (rec in audio) {
            val row = LinearLayout(requireContext())
            row.orientation = LinearLayout.VERTICAL
            row.setBackgroundResource(R.drawable.bg_card)
            row.setPadding(24, 18, 24, 18)
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }

            val title = TextView(requireContext())
            title.text = "🎵  " + (rec.title ?: rec.url)
            title.setTextColor(requireContext().getColor(R.color.text))
            title.textSize = 15f
            title.maxLines = 2
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            row.addView(title)

            val meta = TextView(requireContext())
            meta.text = Utils.formatSize(rec.size)
            meta.setTextColor(requireContext().getColor(R.color.text_secondary))
            meta.textSize = 12f
            row.addView(meta)

            row.setOnClickListener {
                val index = audio.indexOfFirst { it.id == rec.id }
                AudioPlayerActivity.queue = audio
                AudioPlayerActivity.startIndex = if (index >= 0) index else 0
                startActivity(
                    Intent(requireContext(), AudioPlayerActivity::class.java)
                        .putExtra("uri", rec.uri ?: return@setOnClickListener)
                )
            }
            ll.addView(row)
        }
    }
}
