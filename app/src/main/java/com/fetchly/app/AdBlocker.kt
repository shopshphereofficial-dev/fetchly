package com.fetchly.app

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Blocks known ad/tracker hosts inside the in-app browser, so pages load
 * without banner/popup ads. Note: ads that are baked into the video stream
 * itself (e.g. some YouTube in-stream ads) cannot be blocked this way.
 */
object AdBlocker {

    private val BLOCKED_HOSTS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "adservice.google.com",
        "adnxs.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "teads.tv",
        "media.net",
        "mgid.com",
        "revcontent.com",
        "zedo.com",
        "adform.net",
        "sharethrough.com",
        "yieldmo.com",
        "popads.net",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "inmobi.com",
        "chartboost.com",
        "vungle.com",
        "ads-twitter.com",
        "an.yandex.ru",
        "adfox.ru"
    )

    fun isAd(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = try {
            Uri.parse(url).host ?: return false
        } catch (e: Exception) {
            return false
        }
        val h = host.lowercase()
        return BLOCKED_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
