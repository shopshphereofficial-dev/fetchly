package com.fetchly.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

data class ResolvedMedia(
    val title: String,
    val duration: Int,
    val heights: List<Int>,
    val hasAudio: Boolean
)

interface MediaProvider {
    fun id(): String

    // Returns null when the URL has no downloadable media or is not supported.
    @Throws(Exception::class)
    fun resolve(context: Context, url: String): ResolvedMedia?
}

/**
 * Universal provider backed by yt-dlp. It resolves real available qualities
 * from the media's actual format list, so only qualities that truly exist are
 * ever shown to the user.
 */
class YtDlpProvider : MediaProvider {
    override fun id() = "yt-dlp"

    override fun resolve(context: Context, url: String): ResolvedMedia? {
        val cookies = CookieStore.write(context, url)
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--no-update")
            if (cookies != null) addOption("--cookies", cookies.absolutePath)
        }
        val info = YoutubeDL.getInstance().getInfo(request)

        val heights = sortedSetOf<Int>()
        var hasAudio = false
        val formats = info.formats
        if (formats != null) {
            for (fmt in formats) {
                val m = fmt as? Map<*, *> ?: continue
                val vcodec = m["vcodec"] as? String
                val acodec = m["acodec"] as? String
                val h = (m["height"] as? Number)?.toInt() ?: 0
                if (h > 0 && vcodec != null && vcodec != "none") heights.add(h)
                if (acodec != null && acodec != "none") hasAudio = true
            }
        }
        if (heights.isEmpty() && !hasAudio) return null

        return ResolvedMedia(
            title = info.title ?: url,
            duration = info.duration,
            heights = heights.toList().sortedDescending(),
            hasAudio = hasAudio
        )
    }
}

object Providers {
    // Modular provider registry. Add new providers to this list - the rest of
    // the app talks to them only through the MediaProvider interface.
    private val providers = listOf<MediaProvider>(YtDlpProvider())

    fun resolve(context: Context, url: String): ResolvedMedia? {
        for (p in providers) {
            try {
                val r = p.resolve(context, url)
                if (r != null) return r
            } catch (e: Exception) {
                // this provider could not handle the url - try the next one
            }
        }
        return null
    }
}
