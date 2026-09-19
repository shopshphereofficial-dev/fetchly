package com.fetchly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView

class MainActivity : BaseActivity() {

    private val homeFragment = HomeFragment()
    private val downloadsFragment = DownloadsFragment()
    private val musicFragment = MusicFragment()
    private val browserFragment = BrowserFragment()
    private val settingsFragment = SettingsFragment()

    private val fragments = listOf(
        homeFragment, downloadsFragment, musicFragment, browserFragment, settingsFragment
    )
    private val navButtons = mutableListOf<TextView>()
    private var current = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val ids = listOf(
            R.id.navHome, R.id.navDownloads, R.id.navMusic, R.id.navBrowser, R.id.navSettings
        )
        for ((index, id) in ids.withIndex()) {
            val tv = findViewById<TextView>(id)
            navButtons.add(tv)
            tv.setOnClickListener { showTab(index) }
        }

        showTab(0)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // uses show/hide so tab state (e.g. the browser page) survives switching
    fun showTab(index: Int) {
        if (index !in fragments.indices) return
        if (index == current) return
        val tx = supportFragmentManager.beginTransaction()
        for ((i, f) in fragments.withIndex()) {
            if (i == index) {
                if (f.isAdded) tx.show(f) else tx.add(R.id.container, f)
            } else if (f.isAdded) {
                tx.hide(f)
            }
        }
        tx.commit()
        current = index
        for ((i, btn) in navButtons.withIndex()) {
            btn.setTextColor(
                if (i == index) getColor(R.color.primary) else getColor(R.color.text_secondary)
            )
        }
    }

    fun openBrowserSearch(query: String) {
        browserFragment.openSearch(query)
        showTab(3)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = Regex("https?://\\S+").find(text)?.value ?: return
            showTab(0)
            homeFragment.setUrl(url)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentFragment = fragments.getOrNull(current)
        if (currentFragment is BrowserFragment && currentFragment.handleBack()) return
        super.onBackPressed()
    }
}
