package ir.ali0003.downloader.browser.adblock

import android.net.Uri

object AdBlockEngine {

    private val AD_HOSTS = hashSetOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adsserving.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "trafficjunky.com",
        "adnxs.com",
        "rubiconproject.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "zergnet.com",
        "mgid.com",
        "adblade.com",
        "adcash.com",
        "adroll.com",
        "adsystem.com",
        "advertising.com",
        "admob.com",
        "inmobi.com",
        "monetag.com",
        "adsterra.com",
        "yllix.com",
        "hilltopads.com",
        "admaven.com",
        "juicyads.com",
        "chaturbate-ads.com",
        "tsyndicate.com",
        "onclickpredictiv.com",
        "onclkds.com",
        "clickadu.com"
    )

    private val BLOCKED_PATH_PATTERNS = listOf(
        "/ads/",
        "/banner/",
        "/popunder/",
        "/popup/",
        "/adserver/",
        "/advertisement/",
        "/tracking/pixel"
    )

    fun isAdUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }

        val host = uri.host?.lowercase() ?: return false

        // Check exact match or subdomains
        if (AD_HOSTS.contains(host)) return true
        for (adHost in AD_HOSTS) {
            if (host.endsWith(".$adHost")) return true
        }

        val path = uri.path?.lowercase() ?: ""
        for (pattern in BLOCKED_PATH_PATTERNS) {
            if (path.contains(pattern)) return true
        }

        return false
    }

    fun isDangerousOrUnsafeScheme(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("market://") ||
                lower.startsWith("intent://") ||
                lower.startsWith("sms:") ||
                lower.startsWith("tel:") ||
                lower.startsWith("alipay://") ||
                lower.startsWith("weixin://")
    }
}
