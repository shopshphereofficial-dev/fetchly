package com.fetchly.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.yausername.youtubedl_android.YoutubeDL

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnTheme).setOnClickListener { pickTheme() }
        view.findViewById<Button>(R.id.btnLanguage).setOnClickListener { pickLanguage() }
        view.findViewById<Button>(R.id.btnWifi).setOnClickListener { toggleWifi() }
        view.findViewById<Button>(R.id.btnEngine).setOnClickListener { updateEngine() }
        view.findViewById<Button>(R.id.btnClearBookmarks).setOnClickListener {
            Db.get(requireContext()).clearBookmarks()
            Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
            refresh(view)
        }
        view.findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            Db.get(requireContext()).clearBrowserHistory()
            Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnClearSearch).setOnClickListener {
            Db.get(requireContext()).clearSearchHistory()
            Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
        }

        refresh(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { refresh(it) }
    }

    private fun refresh(view: View) {
        val wifi = Prefs.getWifiOnly(requireContext())
        view.findViewById<Button>(R.id.btnWifi).text =
            getString(R.string.pref_wifi_only) + (if (wifi) " • ON" else " • OFF")
        val theme = Prefs.getTheme(requireContext())
        val themeLabel = when (theme) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        view.findViewById<Button>(R.id.btnTheme).text = getString(R.string.pref_theme) + " • " + themeLabel
        val lang = Prefs.getLanguage(requireContext())
        val langLabel = when (lang) {
            "en" -> getString(R.string.lang_en)
            "hi" -> getString(R.string.lang_hi)
            "ur" -> getString(R.string.lang_ur)
            else -> getString(R.string.lang_system)
        }
        view.findViewById<Button>(R.id.btnLanguage).text = getString(R.string.pref_language) + " • " + langLabel
        val used = Utils.formatSize(Db.get(requireContext()).totalCompletedSize())
        view.findViewById<TextView>(R.id.tvStorage).text = getString(R.string.storage_used, used)
    }

    private fun pickTheme() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_theme)
            .setItems(options) { _, which ->
                val value = when (which) {
                    0 -> "light"
                    1 -> "dark"
                    else -> "system"
                }
                Prefs.setTheme(requireContext(), value)
                when (value) {
                    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                view?.let { refresh(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickLanguage() {
        val options = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_en),
            getString(R.string.lang_hi),
            getString(R.string.lang_ur)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_language)
            .setItems(options) { _, which ->
                val value = when (which) {
                    1 -> "en"
                    2 -> "hi"
                    3 -> "ur"
                    else -> "system"
                }
                Prefs.setLanguage(requireContext(), value)
                activity?.recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleWifi() {
        val newValue = !Prefs.getWifiOnly(requireContext())
        Prefs.setWifiOnly(requireContext(), newValue)
        view?.let { refresh(it) }
    }

    private fun updateEngine() {
        Toast.makeText(requireContext(), R.string.engine_updating, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(requireContext(), YoutubeDL.UpdateChannel.STABLE)
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.engine_updated, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.engine_update_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
