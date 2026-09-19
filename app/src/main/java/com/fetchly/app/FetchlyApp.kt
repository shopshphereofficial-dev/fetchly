package com.fetchly.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class FetchlyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        when (Prefs.getTheme(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                Aria2c.getInstance().init(this)
                engineReady = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
            updateEngineIfStale(this)
        }.start()
    }

    // keeps the yt-dlp engine fresh (once a day) so site changes do not break downloads
    private fun updateEngineIfStale(context: android.content.Context) {
        try {
            val prefs = context.getSharedPreferences("engine", MODE_PRIVATE)
            val last = prefs.getLong("lastUpdate", 0L)
            if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        var engineReady = false
    }
}
