package com.fetchly.app

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

object CookieStore {

    // raw cookie header string for a URL (used by the image resolver)
    fun cookieHeader(context: Context, url: String): String? {
        return try {
            CookieManager.getInstance().flush()
            val host = Uri.parse(url).host ?: return null
            CookieManager.getInstance().getCookie("https://$host")
                ?: CookieManager.getInstance().getCookie("http://$host")
        } catch (e: Exception) {
            null
        }
    }

    // Exports WebView cookies (from logins made in the in-app browser) into a
    // Netscape cookie file that yt-dlp uses via --cookies. This is what makes
    // account-only / private content downloadable.
    fun write(context: Context, url: String): File? {
        return try {
            CookieManager.getInstance().flush()
            val host = Uri.parse(url).host ?: return null
            val domain = host.removePrefix("www.").removePrefix("m.")
            val cookieStr = CookieManager.getInstance().getCookie("https://$domain")
                ?: CookieManager.getInstance().getCookie("https://$host")
                ?: return null
            val sb = StringBuilder("# Netscape HTTP Cookie File\n")
            val expiry = System.currentTimeMillis() / 1000 + 60L * 60 * 24 * 365
            for (pair in cookieStr.split(";")) {
                val kv = pair.trim().split("=", limit = 2)
                if (kv.size != 2) continue
                sb.append(".").append(domain).append("\tTRUE\t/\tTRUE\t")
                    .append(expiry).append("\t").append(kv[0]).append("\t")
                    .append(kv[1]).append("\n")
            }
            if (sb.toString().lines().size <= 1) return null
            val f = File(context.cacheDir, "cookies.txt")
            f.writeText(sb.toString())
            f
        } catch (e: Exception) {
            null
        }
    }
}
