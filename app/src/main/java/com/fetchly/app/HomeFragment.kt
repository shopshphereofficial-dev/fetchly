package com.fetchly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var lastUrl: String? = null

    fun setUrl(url: String) {
        lastUrl = url
        view?.findViewById<EditText>(R.id.etUrl)?.setText(url)
        if (view != null) {
            DownloadFlows.start(requireActivity(), url)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // a URL shared before this fragment's view existed
        lastUrl?.takeIf { view.findViewById<EditText>(R.id.etUrl).text.isNullOrBlank() }?.let { url ->
            view.findViewById<EditText>(R.id.etUrl).setText(url)
            activity?.let { DownloadFlows.start(it, url) }
        }

        view.findViewById<Button>(R.id.btnDownload).setOnClickListener {
            val input = view.findViewById<EditText>(R.id.etUrl).text.toString().trim()
            when {
                input.isEmpty() ->
                    Toast.makeText(requireContext(), R.string.url_hint, Toast.LENGTH_SHORT).show()
                input.startsWith("http") ->
                    DownloadFlows.start(requireActivity(), input)
                else ->
                    (activity as? MainActivity)?.openBrowserSearch(input)
            }
        }

        view.findViewById<Button>(R.id.qaBrowser).setOnClickListener {
            (activity as? MainActivity)?.showTab(3)
        }
        view.findViewById<Button>(R.id.qaDownloads).setOnClickListener {
            (activity as? MainActivity)?.showTab(1)
        }
        view.findViewById<Button>(R.id.qaMusic).setOnClickListener {
            (activity as? MainActivity)?.showTab(2)
        }

        renderRecent(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { renderRecent(it) }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) view?.let { renderRecent(it) }
    }

    private fun renderRecent(view: View) {
        val ll = view.findViewById<LinearLayout>(R.id.llRecent)
        ll.removeAllViews()
        val recent = Db.get(requireContext()).recentCompleted(5)
        if (recent.isEmpty()) {
            val tv = TextView(requireContext())
            tv.text = getString(R.string.no_downloads_yet)
            tv.setTextColor(requireContext().getColor(R.color.text_secondary))
            tv.textSize = 14f
            tv.setPadding(8, 16, 8, 16)
            ll.addView(tv)
            return
        }
        for (rec in recent) {
            val row = LinearLayout(requireContext())
            row.orientation = LinearLayout.VERTICAL
            row.setBackgroundResource(R.drawable.bg_card)
            row.setPadding(24, 18, 24, 18)
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }

            val title = TextView(requireContext())
            title.text = rec.title ?: rec.url
            title.setTextColor(requireContext().getColor(R.color.text))
            title.textSize = 15f
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            row.addView(title)

            val meta = TextView(requireContext())
            meta.text = Utils.formatSize(rec.size)
            meta.setTextColor(requireContext().getColor(R.color.text_secondary))
            meta.textSize = 12f
            row.addView(meta)

            row.setOnClickListener {
                val uri = rec.uri ?: return@setOnClickListener
                try {
                    val open = Intent(Intent.ACTION_VIEW)
                    open.setDataAndType(Uri.parse(uri), rec.mime ?: "*/*")
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(open)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.open_failed, Toast.LENGTH_SHORT).show()
                }
            }
            ll.addView(row)
        }
    }
}
