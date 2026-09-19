package com.fetchly.app

import android.content.Context

object Prefs {
    private const val FILE = "settings"

    fun getTheme(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("theme", "system") ?: "system"
    }

    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme", theme).apply()
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("language", "system") ?: "system"
    }

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("language", language).apply()
    }

    fun getWifiOnly(context: Context): Boolean {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("wifi_only", false)
    }

    fun setWifiOnly(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("wifi_only", value).apply()
    }
}
