package com.fetchly.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class BrowserFragment : Fragment(R.layout.fragment_browser) {

    var pendingSearch: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val webView = view.findViewById<WebView>(R.id.webView)
        val etAddress = view.findViewById<EditText>(R.id.etAddress)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                etAddress.setText(url)
                try {
                    Db.get(requireContext()).addBrowserHistory(view.title ?: url, url)
                } catch (e: Exception) {
                }
            }
        }

        fun loadOrSearch(input: String) {
            val q = input.trim()
            if (q.isEmpty()) return
            if (q.startsWith("http")) {
                webView.loadUrl(q)
            } else {
                try {
                    Db.get(requireContext()).addSearchHistory(q)
                } catch (e: Exception) {
                }
                webView.loadUrl("https://www.google.com/search?q=" + Uri.encode(q))
            }
        }

        view.findViewById<Button>(R.id.btnGo).setOnClickListener {
            loadOrSearch(etAddress.text.toString())
        }
        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        view.findViewById<Button>(R.id.btnFwd).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        view.findViewById<Button>(R.id.btnRefresh).setOnClickListener { webView.reload() }
        view.findViewById<Button>(R.id.btnHome).setOnClickListener { webView.loadUrl("https://www.google.com") }

        view.findViewById<Button>(R.id.btnBookmark).setOnClickListener {
            val url = webView.url
            if (url == null || !url.startsWith("http")) return@setOnClickListener
            val added = Db.get(requireContext()).addBookmark(webView.title ?: url, url)
            if (added) Toast.makeText(requireContext(), R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnBookmarks).setOnClickListener { showBookmarks(webView) }
        view.findViewById<Button>(R.id.btnHistory).setOnClickListener { showHistory(webView) }

        view.findViewById<Button>(R.id.btnGrab).setOnClickListener {
            val url = webView.url
            if (url == null || !url.startsWith("http")) {
                Toast.makeText(requireContext(), R.string.no_media_detected, Toast.LENGTH_SHORT).show()
            } else {
                DownloadFlows.start(requireActivity(), url)
            }
        }

        pendingSearch?.let { search ->
            pendingSearch = null
            etAddress.setText(search)
            loadOrSearch(search)
        } ?: webView.loadUrl("https://www.google.com")
    }

    private fun showBookmarks(webView: WebView) {
        val bookmarks = Db.get(requireContext()).bookmarks()
        if (bookmarks.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        val names = bookmarks.map { it.title.ifBlank { it.url } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.btn_bookmarks)
            .setItems(names) { _, which ->
                webView.loadUrl(bookmarks[which].url)
            }
            .setNeutralButton(R.string.btn_delete) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clear_bookmarks)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Db.get(requireContext()).clearBookmarks()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHistory(webView: WebView) {
        val history = Db.get(requireContext()).browserHistory()
        if (history.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_history, Toast.LENGTH_SHORT).show()
            return
        }
        val names = history.map { it.title.ifBlank { it.url } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.btn_history)
            .setItems(names) { _, which ->
                webView.loadUrl(history[which].url)
            }
            .setNeutralButton(R.string.cleared) { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clear_history_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Db.get(requireContext()).clearBrowserHistory()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // public entry point: load a search term into the browser tab
    fun openSearch(query: String) {
        if (view != null) {
            view?.findViewById<EditText>(R.id.etAddress)?.setText(query)
            val wv = view?.findViewById<WebView>(R.id.webView) ?: return
            if (query.startsWith("http")) {
                wv.loadUrl(query)
            } else {
                try {
                    Db.get(requireContext()).addSearchHistory(query)
                } catch (e: Exception) {
                }
                wv.loadUrl("https://www.google.com/search?q=" + Uri.encode(query))
            }
        } else {
            pendingSearch = query
        }
    }

    // returns true when the webview consumed the back press
    fun handleBack(): Boolean {
        val webView = view?.findViewById<WebView>(R.id.webView) ?: return false
        return if (webView.canGoBack()) {
            webView.goBack()
            true
        } else {
            false
        }
    }
}
