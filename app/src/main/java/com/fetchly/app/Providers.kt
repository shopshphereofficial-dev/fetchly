package com.fetchly.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.HttpURLConnection
import java.net.URL

data class ResolvedMedia(
    val title: String,
    val duration: Int,
    val heights: List<Int>,
    val hasAudio: Boolean
)

data class ResolveResult(
    val media: ResolvedMedia?,
    val imageUrl: String?,
    val error: String?
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

/**
 * Resolves downloadable images: a direct image link, or the og:image of a page
 * (used for photo posts on sites where the video engine finds no video).
 */
object ImageResolver {

    fun ogImage(context: Context, pageUrl: String): String? {
        // direct image link?
        if (Regex(".*\\.(jpe?g|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE).matches(pageUrl)) {
            return pageUrl
        }
        return try {
            val conn = URL(pageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            val cookie = CookieStore.cookieHeader(context, pageUrl)
            if (cookie != null) conn.setRequestProperty("Cookie", cookie)

            val reader = conn.inputStream.bufferedReader()
            val sb = StringBuilder()
            try {
                while (sb.length < 500_000) {
                    val line = reader.readLine() ?: break
                    sb.append(line)
                }
            } finally {
                reader.close()
            }
            val html = sb.toString()
            val m = Regex(
                "<meta[^>]+property=[\"']og:image(?::secure_url)?[\"'][^>]+content=[\"']([^\"']+)[\"']",
                RegexOption.IGNORE_CASE
            ).find(html)
                ?: Regex(
                    "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
                    RegexOption.IGNORE_CASE
                ).find(html)
            m?.groupValues?.get(1)
                ?.replace("&", "&")
                ?.takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            null
        }
    }
}

object Providers {
    // Modular provider registry. Add new providers to this list - the rest of
    // the app talks to them only through the MediaProvider interface.
    private val providers = listOf<MediaProvider>(YtDlpProvider())

    // Resolves video/audio via providers and, when no video was found, an
    // image (photo posts). Also returns the raw failure reason for display.
    fun resolveDetailed(context: Context, url: String): ResolveResult {
        var media: ResolvedMedia? = null
        var error: String? = null
        for (p in providers) {
            try {
                media = p.resolve(context, url)
                if (media != null) break
            } catch (e: Exception) {
                error = e.message
            }
        }

        // image option only makes sense when there is no video to grab
        var imageUrl: String? = null
        if (media == null || (media.heights.isEmpty() && !media.hasAudio)) {
            imageUrl = ImageResolver.ogImage(context, url)
        }

        if (media == null && imageUrl == null) {
            return ResolveResult(null, null, (error ?: "").ifBlank { "no media found" })
        }
        return ResolveResult(media, imageUrl, error)
    }
}
