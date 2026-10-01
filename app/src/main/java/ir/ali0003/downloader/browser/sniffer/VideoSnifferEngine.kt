package ir.ali0003.downloader.browser.sniffer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import ir.ali0003.downloader.browser.adblock.AdBlockEngine
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VideoSnifferEngine(
    private val context: Context? = null,
    private val onMediaDetected: (SniffedMediaItem) -> Unit
) {
    var onActiveMediaDetected: ((SniffedMediaItem) -> Unit)? = null
    var onActivePlayStarted: ((url: String, title: String?) -> Unit)? = null
    private val hasActivePlayingVideo = java.util.concurrent.atomic.AtomicBoolean(false)
    private val activePlayWindowEnd = java.util.concurrent.atomic.AtomicLong(0L)

    fun activatePlayTimeWindow(durationMs: Long = 500L) {
        hasActivePlayingVideo.set(true)
        activePlayWindowEnd.set(android.os.SystemClock.elapsedRealtime() + durationMs)
    }

    fun isInActivePlayWindow(): Boolean {
        return android.os.SystemClock.elapsedRealtime() <= activePlayWindowEnd.get()
    }

    private val appContext: Context?
        get() = context?.applicationContext

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectedUrls = ConcurrentHashMap.newKeySet<String>()
    private val recentNetworkUrls = java.util.concurrent.ConcurrentLinkedDeque<String>()

    fun getRecentNetworkLogs(): List<String> = recentNetworkUrls.toList()

    // Canonical Aggregator State
    private val aggregationLock = Any()
    private var canonicalVideoItem: SniffedMediaItem? = null
    private val accumulatedRawQualities = mutableListOf<VideoQualityOption>()
    private val accumulatedHeaders = ConcurrentHashMap<String, String>()

    private val currentPageUrl = java.util.concurrent.atomic.AtomicReference<String>("")
    private val currentPageTitle = java.util.concurrent.atomic.AtomicReference<String>("")
    private val currentUserAgent = java.util.concurrent.atomic.AtomicReference<String>("")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun resetSession() {
        hasActivePlayingVideo.set(false)
        activePlayWindowEnd.set(0L)
        detectedUrls.clear()
        recentNetworkUrls.clear()
        synchronized(aggregationLock) {
            canonicalVideoItem = null
            accumulatedRawQualities.clear()
            accumulatedHeaders.clear()
        }
    }

    /**
     * Retrieves the single canonical aggregated video item for the current page.
     */
    fun getPrimaryVideoItem(): SniffedMediaItem? {
        return synchronized(aggregationLock) { canonicalVideoItem }
    }

    /**
     * Proactively inspects an external or manually pasted URL to probe media streams and qualities.
     */
    fun inspectUrl(
        mediaUrl: String,
        pageUrl: String = "",
        pageTitle: String = "",
        requestHeaders: Map<String, String> = emptyMap()
    ) {
        processDetectedMediaUrl(
            mediaUrl = mediaUrl,
            pageUrl = if (pageUrl.isNotBlank()) pageUrl else mediaUrl,
            pageTitle = if (pageTitle.isNotBlank()) pageTitle else "External Stream",
            requestHeaders = requestHeaders
        )
    }

    fun updateCurrentPageInfo(url: String?, title: String?, userAgent: String? = null) {
        if (!url.isNullOrBlank()) currentPageUrl.set(url)
        if (!title.isNullOrBlank()) currentPageTitle.set(title)
        if (!userAgent.isNullOrBlank()) currentUserAgent.set(userAgent)

        synchronized(aggregationLock) {
            canonicalVideoItem?.let { current ->
                val updatedTitle = if (!title.isNullOrBlank() &&
                    (current.title.isBlank() || current.title == "External Stream" || current.title.startsWith("video_"))
                ) {
                    title
                } else {
                    current.title
                }
                canonicalVideoItem = current.copy(
                    pageUrl = url ?: current.pageUrl,
                    title = updatedTitle
                )
            }
        }
    }

    /**
     * Intercepts network calls from WebView. Returns null to let WebView continue loading normally,
     * or WebResourceResponse for blocked ads.
     */
    fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        val url = request.url?.toString() ?: return null

        // 1. Check AdBlock
        if (AdBlockEngine.isAdUrl(url) || isAdOrJunkUrl(url)) {
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        // Record non-ad requests in rolling network log for parent manifest traversal
        if (recentNetworkUrls.size >= 250) {
            recentNetworkUrls.pollFirst()
        }
        recentNetworkUrls.addLast(url)

        // 2. Check if URL matches media patterns
        val requestHeaders = request.requestHeaders ?: emptyMap()
        if (isMediaUrl(url, requestHeaders)) {
            val referer = requestHeaders["Referer"] ?: requestHeaders["referer"] ?: currentPageUrl.get()
            val safePageUrl = if (referer.isNotBlank()) referer else url
            val safePageTitle = currentPageTitle.get()

            val inPlayWindow = isInActivePlayWindow()
            val isPlayingActive = hasActivePlayingVideo.get()

            // Time-window Binding (500ms):
            // Identify the manifest file (.m3u8 or .mpd) or video link requested within this active play window as the primary stream.
            // Other background/header/feed requests before or without a play event are completely ignored.
            if (inPlayWindow) {
                processDetectedMediaUrl(
                    mediaUrl = url,
                    pageUrl = safePageUrl,
                    pageTitle = safePageTitle,
                    requestHeaders = requestHeaders,
                    isActivePlayEvent = true
                )
            } else if (isPlayingActive) {
                // If an active stream is already playing, only process additional sub-playlists/renditions of the same stream
                val isSame = synchronized(aggregationLock) {
                    val currentMaster = canonicalVideoItem ?: return@synchronized false
                    val isM3u8 = url.contains(".m3u8", ignoreCase = true)
                    isSameVideoUrl(currentMaster.url, url) ||
                            (currentMaster.isM3u8 && isM3u8 && isSameHlsStream(currentMaster.url, url))
                }
                if (isSame) {
                    processDetectedMediaUrl(
                        mediaUrl = url,
                        pageUrl = safePageUrl,
                        pageTitle = safePageTitle,
                        requestHeaders = requestHeaders,
                        isActivePlayEvent = false
                    )
                }
            }
        }

        return null
    }

    /**
     * Fallback URL interceptor for older WebViews or direct calls
     */
    fun shouldInterceptRequestUrl(
        view: WebView?,
        url: String?
    ): WebResourceResponse? {
        if (url.isNullOrBlank()) return null
        if (AdBlockEngine.isAdUrl(url) || isAdOrJunkUrl(url)) {
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        if (recentNetworkUrls.size >= 250) {
            recentNetworkUrls.pollFirst()
        }
        recentNetworkUrls.addLast(url)

        if (isMediaUrl(url, emptyMap())) {
            val safePageUrl = currentPageUrl.get().ifBlank { url }
            val safePageTitle = currentPageTitle.get()

            val inPlayWindow = isInActivePlayWindow()
            val isPlayingActive = hasActivePlayingVideo.get()

            if (inPlayWindow) {
                processDetectedMediaUrl(
                    mediaUrl = url,
                    pageUrl = safePageUrl,
                    pageTitle = safePageTitle,
                    requestHeaders = emptyMap(),
                    isActivePlayEvent = true
                )
            } else if (isPlayingActive) {
                val isSame = synchronized(aggregationLock) {
                    val currentMaster = canonicalVideoItem ?: return@synchronized false
                    val isM3u8 = url.contains(".m3u8", ignoreCase = true)
                    isSameVideoUrl(currentMaster.url, url) ||
                            (currentMaster.isM3u8 && isM3u8 && isSameHlsStream(currentMaster.url, url))
                }
                if (isSame) {
                    processDetectedMediaUrl(
                        mediaUrl = url,
                        pageUrl = safePageUrl,
                        pageTitle = safePageTitle,
                        requestHeaders = emptyMap(),
                        isActivePlayEvent = false
                    )
                }
            }
        }
        return null
    }

    /**
     * JavaScript Bridge to receive HTML5 video/audio elements and mutations directly from DOM
     */
    inner class VideoSnifferBridge(private val webView: WebView) {

        @JavascriptInterface
        fun onMediaDiscovered(
            mediaUrl: String?,
            mimeType: String?,
            title: String?,
            poster: String?,
            duration: Double
        ) {
            onMediaDiscoveredWithQuality(
                mediaUrl = mediaUrl,
                mimeType = mimeType,
                title = title,
                poster = poster,
                duration = duration,
                qualityLabel = null,
                resolution = null
            )
        }

        @JavascriptInterface
        fun onMediaDiscoveredWithQuality(
            mediaUrl: String?,
            mimeType: String?,
            title: String?,
            poster: String?,
            duration: Double,
            qualityLabel: String?,
            resolution: String?
        ) {
            if (mediaUrl.isNullOrBlank()) return
            if (mediaUrl.startsWith("blob:") || mediaUrl.startsWith("data:")) return

            mainHandler.post {
                try {
                    val pageUrl = webView.url ?: ""
                    val currentTitle = if (!title.isNullOrBlank()) title else webView.title ?: ""
                    val userAgent = try { webView.settings.userAgentString } catch (_: Exception) { "" }
                    val headers = extractHeadersForUrl(mediaUrl, pageUrl, userAgent)

                    processDetectedMediaUrl(
                        mediaUrl = mediaUrl,
                        pageUrl = pageUrl,
                        pageTitle = currentTitle,
                        requestHeaders = headers,
                        posterUrl = poster,
                        durationSeconds = duration,
                        specifiedMime = mimeType,
                        qualityLabelHint = qualityLabel,
                        resolutionHint = resolution
                    )
                } catch (_: Exception) {
                    // Safe guard against WebView disposal while callback is posting
                }
            }
        }

        @JavascriptInterface
        fun processMediaDefinitions(json: String?) {
            processMediaDefinitionsWithDuration(json, 0.0, null)
        }

        @JavascriptInterface
        fun processMediaDefinitionsWithDuration(json: String?, duration: Double, title: String?) {
            if (json.isNullOrBlank()) return
            mainHandler.post {
                try {
                    val pageUrl = webView.url ?: currentPageUrl.get()
                    val currentTitle = if (!title.isNullOrBlank()) title else webView.title ?: currentPageTitle.get()
                    val userAgent = try { webView.settings.userAgentString } catch (_: Exception) { "" }
                    parseAndProcessMediaDefinitions(
                        json = json,
                        pageUrl = pageUrl,
                        pageTitle = currentTitle,
                        userAgent = userAgent,
                        externalDuration = duration
                    )
                } catch (e: Exception) {
                    android.util.Log.e("VideoSnifferEngine", "Error handling processMediaDefinitions: ${e.message}", e)
                }
            }
        }

        @JavascriptInterface
        fun onMediaDefinitionsFound(json: String?) {
            processMediaDefinitions(json)
        }

        @JavascriptInterface
        fun onActivePlayTriggered(src: String?) {
            onActivePlayTriggered(src, null)
        }

        @JavascriptInterface
        fun onActivePlayTriggered(src: String?, title: String?) {
            mainHandler.post {
                activatePlayTimeWindow(500L)
                val cleanSrc = src?.trim() ?: ""
                val resolvedSrc = if (cleanSrc.isNotBlank() && !cleanSrc.startsWith("blob:") && !cleanSrc.startsWith("data:") && !cleanSrc.startsWith("javascript:")) {
                    cleanSrc
                } else {
                    ""
                }
                onActivePlayStarted?.invoke(resolvedSrc, title)
            }
        }

        @JavascriptInterface
        fun onActiveVideoDetected(src: String?) {
            onActiveVideoDetected(src, null)
        }

        @JavascriptInterface
        fun onActiveVideoDetected(src: String?, title: String?) {
            if (src.isNullOrBlank()) return
            val cleanSrc = src.trim()
            if (cleanSrc.startsWith("blob:") || cleanSrc.startsWith("data:") || cleanSrc.startsWith("javascript:")) return
            // 1. Strictly ignore partial segments and subtitle fragments
            if (isChunkFragment(cleanSrc)) return

            mainHandler.post {
                try {
                    // Activate 500ms time-window binding immediately
                    activatePlayTimeWindow(500L)

                    val pageUrl = webView.url ?: currentPageUrl.get()
                    val currentTitle = if (!title.isNullOrBlank()) title else webView.title ?: currentPageTitle.get()
                    val userAgent = try { webView.settings.userAgentString } catch (_: Exception) { "" }
                    val headers = extractHeadersForUrl(cleanSrc, pageUrl, userAgent)

                    val resolvedSrc = if (!cleanSrc.startsWith("http://") && !cleanSrc.startsWith("https://")) {
                        try {
                            java.net.URI(pageUrl).resolve(cleanSrc).toString()
                        } catch (_: Exception) { cleanSrc }
                    } else {
                        cleanSrc
                    }

                    // 1. Mark active video playing and verify if this is a new video
                    hasActivePlayingVideo.set(true)
                    val isSame = synchronized(aggregationLock) {
                        canonicalVideoItem?.let { prev ->
                            isSameVideoUrl(prev.url, resolvedSrc) ||
                            (prev.isM3u8 && resolvedSrc.contains(".m3u8", ignoreCase = true) && isSameHlsStream(prev.url, resolvedSrc))
                        } ?: false
                    }

                    if (!isSame) {
                        // Purge previous video's aggregation state so the new video's manifest is parsed independently
                        synchronized(aggregationLock) {
                            canonicalVideoItem = null
                            accumulatedRawQualities.clear()
                            accumulatedHeaders.clear()
                        }
                    }

                    // Immediately signal active play event so ViewModel clears old videos and resets counter
                    onActivePlayStarted?.invoke(resolvedSrc, currentTitle)

                    // For stream manifests (HLS/DASH), notify UI immediately since it's a full adaptive stream
                    val isStreamManifest = cleanSrc.contains(".m3u8", ignoreCase = true) || cleanSrc.contains(".mpd", ignoreCase = true)
                    if (isStreamManifest) {
                        val immediateItem = createImmediateMediaItem(
                            mediaUrl = resolvedSrc,
                            pageUrl = pageUrl,
                            pageTitle = currentTitle,
                            headers = headers
                        )
                        onActiveMediaDetected?.invoke(immediateItem) ?: onMediaDetected(immediateItem)
                    }

                    // 2. Perform dedicated background manifest/stream analysis for genuine quality tiers
                    processDetectedMediaUrl(
                        mediaUrl = resolvedSrc,
                        pageUrl = pageUrl,
                        pageTitle = currentTitle,
                        requestHeaders = headers,
                        isActivePlayEvent = true
                    )
                } catch (_: Exception) {
                    // Safe guard against WebView disposal while callback is posting
                }
            }
        }
    }

    private fun createImmediateMediaItem(
        mediaUrl: String,
        pageUrl: String,
        pageTitle: String,
        headers: Map<String, String>
    ): SniffedMediaItem {
        val isM3u8 = mediaUrl.contains(".m3u8", ignoreCase = true)
        val isDash = mediaUrl.contains(".mpd", ignoreCase = true)
        val mime = when {
            isM3u8 -> "application/x-mpegURL"
            isDash -> "application/dash+xml"
            mediaUrl.contains(".webm", ignoreCase = true) -> "video/webm"
            mediaUrl.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            mediaUrl.contains(".m4a", ignoreCase = true) -> "audio/mp4"
            else -> "video/mp4"
        }
        val defaultQuality = VideoQualityOption(
            label = if (isM3u8) "HLS Adaptive" else "Original Quality",
            resolution = "",
            bandwidthBps = 0L,
            url = mediaUrl,
            isHlsVariant = isM3u8,
            estimatedSizeBytes = 0L,
            formatTag = if (isM3u8) "HLS" else "MP4"
        )
        return SniffedMediaItem(
            id = UUID.randomUUID().toString(),
            url = mediaUrl,
            pageUrl = pageUrl,
            title = pickBestTitle(null, pageTitle, mediaUrl),
            mimeType = mime,
            isM3u8 = isM3u8,
            isDash = isDash,
            headers = headers,
            thumbnailUrl = null,
            durationSeconds = 0.0,
            fileSizeBytes = 0L,
            qualities = listOf(defaultQuality)
        )
    }

    /**
     * Parses streaming media definitions (such as Pornhub flashvars.mediaDefinitions)
     * and populates all declared quality tiers (1080p, 720p, 480p, 240p) in HLS and MP4 formats.
     */
    fun parseAndProcessMediaDefinitions(
        json: String,
        pageUrl: String,
        pageTitle: String,
        userAgent: String,
        externalDuration: Double = 0.0
    ) {
        if (json.isBlank()) return
        try {
            val jsonArray = org.json.JSONArray(json)
            val extractedQualities = mutableListOf<VideoQualityOption>()
            var primaryHlsUrl: String? = null
            var primaryMp4Url: String? = null
            var defaultVideoUrl: String? = null

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val format = obj.optString("format", "").trim().lowercase()
                val isDefault = obj.optBoolean("defaultQuality", false)
                val rawVideoUrl = (obj.optString("videoUrl").takeIf { it.isNotBlank() && it != "null" }
                    ?: obj.optString("url").takeIf { it.isNotBlank() && it != "null" }
                    ?: "").trim()

                // Sanitize 1: Filter out zero-length, non-http, or internal dummy URLs
                if (rawVideoUrl.isBlank() || rawVideoUrl.length < 10) continue
                if (rawVideoUrl.startsWith("blob:") || rawVideoUrl.startsWith("data:") || rawVideoUrl.startsWith("javascript:")) continue

                // Sanitize 2: Filter out advertising preroll video definitions
                val isAdOrPreroll = format == "ad" || format == "preroll" || format == "midroll" || format == "postroll" ||
                        obj.optBoolean("isAd", false) || obj.optBoolean("isPreroll", false) ||
                        isAdOrJunkUrl(rawVideoUrl)
                if (isAdOrPreroll) continue

                val videoUrl = rawVideoUrl.replace("\\/", "/")

                if (defaultVideoUrl == null || isDefault) {
                    defaultVideoUrl = videoUrl
                }

                val isHls = format == "hls" || videoUrl.contains(".m3u8", ignoreCase = true)
                if (isHls && primaryHlsUrl == null) {
                    primaryHlsUrl = videoUrl
                } else if (!isHls && primaryMp4Url == null) {
                    primaryMp4Url = videoUrl
                }

                // Extract quality field: can be string ("1080", "720p"), int (1080), or array (["1080", "720", ...])
                val rawQualityList = mutableListOf<String>()
                val qualityOpt = obj.opt("quality")
                when (qualityOpt) {
                    is org.json.JSONArray -> {
                        for (qIdx in 0 until qualityOpt.length()) {
                            val qVal = qualityOpt.opt(qIdx)?.toString()?.trim()
                            if (!qVal.isNullOrBlank() && qVal != "null") rawQualityList.add(qVal)
                        }
                    }
                    is org.json.JSONObject -> {
                        val label = qualityOpt.optString("label")
                        if (label.isNotBlank()) rawQualityList.add(label)
                    }
                    null -> {
                        val height = obj.optInt("height", 0)
                        if (height > 0) rawQualityList.add(height.toString())
                    }
                    else -> {
                        val qStr = qualityOpt.toString().trim()
                        if (qStr.isNotBlank() && qStr != "null") {
                            rawQualityList.add(qStr)
                        }
                    }
                }

                if (rawQualityList.isEmpty()) {
                    val inferred = when {
                        videoUrl.contains("1080", ignoreCase = true) -> "1080"
                        videoUrl.contains("720", ignoreCase = true) -> "720"
                        videoUrl.contains("480", ignoreCase = true) -> "480"
                        videoUrl.contains("360", ignoreCase = true) -> "360"
                        videoUrl.contains("240", ignoreCase = true) -> "240"
                        else -> if (isHls) "Adaptive HLS" else "720"
                    }
                    rawQualityList.add(inferred)
                }

                for (q in rawQualityList) {
                    val digits = q.replace("[^0-9]".toRegex(), "")
                    val heightInt = digits.toIntOrNull() ?: 0
                    val resolution = when (heightInt) {
                        2160 -> "3840x2160"
                        1440 -> "2560x1440"
                        1080 -> "1920x1080"
                        720 -> "1280x720"
                        480 -> "854x480"
                        360 -> "640x360"
                        240 -> "426x240"
                        else -> if (heightInt > 0) "${(heightInt * 16) / 9}x$heightInt" else ""
                    }
                    // Accurately map resolution numbers to standard tier labels
                    val label = when (heightInt) {
                        2160 -> "4K UHD"
                        1440 -> "1440p 2K"
                        1080 -> "1080p HD"
                        720 -> "720p HD"
                        480 -> "480p SD"
                        360 -> "360p SD"
                        240 -> "240p"
                        else -> if (heightInt > 0) "${heightInt}p" else q
                    }
                    val bandwidthBps = when (heightInt) {
                        2160 -> 15_000_000L
                        1440 -> 8_000_000L
                        1080 -> 5_000_000L
                        720 -> 2_500_000L
                        480 -> 1_200_000L
                        360 -> 800_000L
                        240 -> 400_000L
                        else -> obj.optLong("bitrate", obj.optLong("bandwidth", 0L))
                    }
                    val formatTag = if (isHls) "HLS" else if (format.contains("webm", ignoreCase = true)) "WEBM" else "MP4"
                    val targetDuration = maxOf(canonicalVideoItem?.durationSeconds ?: 0.0, externalDuration)
                    val estBytes = if (targetDuration > 0.0 && bandwidthBps > 0L) {
                        ((bandwidthBps * targetDuration) / 8.0).toLong()
                    } else {
                        0L
                    }

                    extractedQualities.add(
                        VideoQualityOption(
                            label = label,
                            resolution = resolution,
                            bandwidthBps = bandwidthBps,
                            url = videoUrl,
                            isHlsVariant = isHls,
                            estimatedSizeBytes = estBytes,
                            formatTag = formatTag
                        )
                    )
                }
            }

            if (extractedQualities.isEmpty()) return

            val targetUrl = primaryMp4Url ?: primaryHlsUrl ?: defaultVideoUrl ?: return
            val isTargetHls = targetUrl.contains(".m3u8", ignoreCase = true) || (primaryMp4Url == null && primaryHlsUrl != null)
            val fullHeaders = extractHeadersForUrl(targetUrl, pageUrl, userAgent)

            val aggregated = synchronized(aggregationLock) {
                var previous = canonicalVideoItem
                val isSame = previous != null && (isSameVideoUrl(previous.url, targetUrl) || (previous.isM3u8 && isTargetHls && isSameHlsStream(previous.url, targetUrl)))
                if (previous != null && !isSame) {
                    accumulatedRawQualities.clear()
                    accumulatedHeaders.clear()
                    canonicalVideoItem = null
                    previous = null
                }

                accumulatedHeaders.putAll(fullHeaders)
                // Deduplicate incoming options against accumulated to prevent duplicate tiers on repeated taps
                for (extracted in extractedQualities) {
                    accumulatedRawQualities.removeAll { existing ->
                        existing.url == extracted.url ||
                                (existing.getResolutionHeight() == extracted.getResolutionHeight() &&
                                        existing.isHlsVariant == extracted.isHlsVariant &&
                                        existing.getResolutionHeight() > 0)
                    }
                    accumulatedRawQualities.add(extracted)
                }

                val bestTitle = pickBestTitle(previous?.title, pageTitle, targetUrl)
                val bestPoster = previous?.thumbnailUrl
                val bestDuration = maxOf(previous?.durationSeconds ?: 0.0, externalDuration)
                val bestBaseSize = previous?.fileSizeBytes ?: 0L

                val standardizedQualities = normalizeAndBucketQualities(
                    rawQualities = accumulatedRawQualities,
                    durationSeconds = bestDuration,
                    baseFileSizeBytes = bestBaseSize,
                    isHls = isTargetHls || previous?.isM3u8 == true,
                    fallbackUrl = targetUrl
                )

                val updatedItem = SniffedMediaItem(
                    id = previous?.id ?: java.util.UUID.randomUUID().toString(),
                    url = previous?.url ?: targetUrl,
                    pageUrl = if (pageUrl.isNotBlank()) pageUrl else currentPageUrl.get(),
                    title = bestTitle,
                    mimeType = if (isTargetHls) "application/x-mpegURL" else "video/mp4",
                    isM3u8 = isTargetHls || previous?.isM3u8 == true,
                    headers = accumulatedHeaders.toMap(),
                    thumbnailUrl = bestPoster,
                    durationSeconds = bestDuration,
                    fileSizeBytes = bestBaseSize,
                    qualities = standardizedQualities
                )
                canonicalVideoItem = updatedItem
                updatedItem
            }

            mainHandler.post {
                onActiveMediaDetected?.invoke(aggregated) ?: onMediaDetected(aggregated)
            }

            // If HLS manifest was found, also probe master playlist details in background
            primaryHlsUrl?.let { hlsUrl ->
                scope.launch {
                    processDetectedMediaUrl(
                        mediaUrl = hlsUrl,
                        pageUrl = pageUrl,
                        pageTitle = pageTitle,
                        requestHeaders = fullHeaders
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoSnifferEngine", "Failed to parse mediaDefinitions: ${e.message}", e)
        }
    }

    private fun isAdOrJunkUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("doubleclick") || lower.contains("/ads/") ||
                lower.contains("googlesyndication") || lower.contains("adnxs") ||
                lower.contains("analytics") || lower.contains("telemetry") ||
                lower.contains("tracking") || lower.contains("beacon") ||
                lower.contains("pixel") || lower.contains("adservice") ||
                lower.contains("banner") || lower.contains("adsystem")
    }

    private fun isChunkFragment(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        val lower = url.lowercase()

        // 1. Partial streaming chunk extensions
        if (clean.endsWith(".ts") || clean.endsWith(".m4s") || clean.endsWith(".m4f") ||
            clean.endsWith(".cmfa") || clean.endsWith(".cmfv") || clean.endsWith(".init") ||
            clean.endsWith(".key") || clean.endsWith(".vtt") || clean.endsWith(".webvtt") ||
            clean.endsWith(".srt")
        ) {
            return true
        }

        // 2. Query or fragment parameters indicating chunk segments
        if (lower.contains(".ts?") || lower.contains(".m4s?") || lower.contains(".vtt?")) {
            return true
        }

        // 3. Segment path signatures
        return lower.contains("/segment_", ignoreCase = true) ||
                lower.contains("/seg-", ignoreCase = true) ||
                lower.contains("-frag-", ignoreCase = true) ||
                lower.contains("/chunk-", ignoreCase = true) ||
                lower.contains("chunk_", ignoreCase = true) ||
                lower.contains("/fragments/", ignoreCase = true) ||
                lower.contains("live_segment", ignoreCase = true) ||
                lower.contains("media-segment", ignoreCase = true) ||
                lower.contains("/hls-live/", ignoreCase = true) ||
                (lower.contains("range=", ignoreCase = true) && lower.contains("bytestart="))
    }

    private fun isThumbnailOrPreviewUrl(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        val full = url.lowercase()
        return clean.contains("preview") ||
                clean.contains("thumb") ||
                clean.contains("teaser") ||
                clean.contains("trailer_preview") ||
                clean.contains("story_preview") ||
                clean.contains("hover_preview") ||
                clean.contains("short_loop") ||
                clean.contains("micro_clip") ||
                full.contains("preview=true") ||
                full.contains("type=preview")
    }

    private fun isSameVideoUrl(url1: String, url2: String): Boolean {
        val norm1 = url1.substringBefore('?').substringBefore('#').trim().lowercase()
        val norm2 = url2.substringBefore('?').substringBefore('#').trim().lowercase()
        return norm1 == norm2
    }

    private fun isSameHlsStream(url1: String, url2: String): Boolean {
        val norm1 = url1.substringBefore('?').substringBefore('#').trim().lowercase()
        val norm2 = url2.substringBefore('?').substringBefore('#').trim().lowercase()
        if (norm1 == norm2) return true
        val dir1 = norm1.substringBeforeLast('/')
        val dir2 = norm2.substringBeforeLast('/')
        return dir1.isNotBlank() && dir1 == dir2
    }

    private fun isMediaUrl(url: String, requestHeaders: Map<String, String>): Boolean {
        if (isAdOrJunkUrl(url)) return false
        // Strictly filter out partial HLS/DASH segment chunks and subtitles
        if (isChunkFragment(url)) return false

        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()

        // Direct streams & manifests
        val directMediaExtensions = listOf(
            ".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".flv",
            ".mp3", ".m4a", ".aac", ".ogg", ".wav", ".3gp"
        )
        if (directMediaExtensions.any { cleanUrl.endsWith(it) }) return true

        // Live stream manifests
        if (cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".mpd")) return true

        // URL query heuristics & embedded media markers
        val fullLower = url.lowercase()
        if (fullLower.contains(".m3u8") ||
            fullLower.contains(".mpd") ||
            fullLower.contains(".mp4") ||
            fullLower.contains(".webm") ||
            fullLower.contains("mime=video") ||
            fullLower.contains("mime=audio") ||
            fullLower.contains("format=m3u8") ||
            fullLower.contains("format=mp4") ||
            fullLower.contains("type=mp4") ||
            fullLower.contains("ext=mp4") ||
            fullLower.contains("ext=m3u8") ||
            fullLower.contains("videoplayback") ||
            fullLower.contains("/manifest/hls_variant/") ||
            fullLower.contains("/manifest/dash/")
        ) {
            return true
        }

        // Accept & Content-Type header heuristics
        val acceptHeader = requestHeaders["Accept"] ?: requestHeaders["accept"] ?: ""
        val contentTypeHeader = requestHeaders["Content-Type"] ?: requestHeaders["content-type"] ?: ""
        val secFetchDest = requestHeaders["Sec-Fetch-Dest"] ?: requestHeaders["sec-fetch-dest"] ?: ""
        val combinedHeaders = "$acceptHeader $contentTypeHeader $secFetchDest".lowercase()
        if (combinedHeaders.contains("video/") ||
            combinedHeaders.contains("video") ||
            combinedHeaders.contains("audio/") ||
            combinedHeaders.contains("application/x-mpegurl") ||
            combinedHeaders.contains("application/vnd.apple.mpegurl") ||
            combinedHeaders.contains("application/dash+xml")
        ) {
            return true
        }

        return false
    }

    private fun processDetectedMediaUrl(
        mediaUrl: String,
        pageUrl: String,
        pageTitle: String,
        requestHeaders: Map<String, String>,
        posterUrl: String? = null,
        durationSeconds: Double = 0.0,
        specifiedMime: String? = null,
        qualityLabelHint: String? = null,
        resolutionHint: String? = null,
        isActivePlayEvent: Boolean = false
    ) {
        if (isAdOrJunkUrl(mediaUrl)) return

        // 1. Unconditionally reject partial segments and subtitle fragments
        if (isChunkFragment(mediaUrl)) {
            return
        }

        val isNew = detectedUrls.add(mediaUrl)
        if (!isNew && !isActivePlayEvent) {
            // Already processed this URL in current session and not an active play event
            return
        }

        scope.launch {
            val isM3u8 = mediaUrl.contains(".m3u8", ignoreCase = true) ||
                    specifiedMime?.contains("mpegurl", ignoreCase = true) == true
            val isDash = mediaUrl.contains(".mpd", ignoreCase = true) ||
                    specifiedMime?.contains("dash+xml", ignoreCase = true) == true

            // When an active video is playing, strictly reject background videos, scroll previews, or ads from accumulating
            if (!isActivePlayEvent && hasActivePlayingVideo.get()) {
                val currentMaster = synchronized(aggregationLock) { canonicalVideoItem }
                if (currentMaster != null) {
                    val isSame = isSameVideoUrl(currentMaster.url, mediaUrl) ||
                            (currentMaster.isM3u8 && isM3u8 && isSameHlsStream(currentMaster.url, mediaUrl))
                    if (!isSame) {
                        return@launch
                    }
                }
            }

            val fullHeaders = extractHeadersForUrl(mediaUrl, pageUrl, null, requestHeaders)

            var qualities: List<VideoQualityOption> = emptyList()
            var detectedSize = 0L
            var detectedMime = specifiedMime ?: if (isM3u8) "application/x-mpegURL" else "video/mp4"
            var finalDuration = durationSeconds

            if (isM3u8) {
                // 1. Master Playlist Priority & Parent Traversal:
                // When an .m3u8 URL is intercepted, inspect its contents:
                // - If valid Master Playlist (#EXT-X-STREAM-INF), parse ALL variant streams (1080p, 720p, 480p, 240p).
                // - If single-rendition sub-playlist without #EXT-X-STREAM-INF, do NOT settle for single-tier detection.
                //   Strip rendition-specific URL path segments/parameters or query browser network logs to fetch the parent Master Manifest.
                val hlsResult = HlsManifestParser.fetchAndParseMasterPlaylist(
                    masterUrl = mediaUrl,
                    headersMap = fullHeaders,
                    fallbackDurationSeconds = durationSeconds,
                    networkLogs = getRecentNetworkLogs()
                )

                if (hlsResult.parsedDurationSeconds > 0.0) {
                    finalDuration = hlsResult.parsedDurationSeconds
                }

                if (hlsResult.qualities.isNotEmpty()) {
                    qualities = hlsResult.qualities
                } else {
                    // Fallback: AndroidX Media3 DownloadHelper if HlsManifestParser yielded no tracks
                    val media3Result = if (appContext != null) {
                        ir.ali0003.downloader.downloader.media3.Media3HlsHelper.extractHlsTracks(
                            context = appContext!!,
                            manifestUrl = mediaUrl,
                            headers = fullHeaders,
                            fallbackDurationSeconds = durationSeconds
                        )
                    } else {
                        null
                    }

                    if (media3Result != null && media3Result.qualities.isNotEmpty()) {
                        qualities = media3Result.qualities
                        if (media3Result.durationSeconds > 0.0) {
                            finalDuration = media3Result.durationSeconds
                        }
                    } else {
                        qualities = listOf(
                            VideoQualityOption(
                                label = "Direct Stream",
                                resolution = "",
                                bandwidthBps = 0L,
                                url = mediaUrl,
                                isHlsVariant = true,
                                estimatedSizeBytes = 0L,
                                formatTag = "HLS M3U8"
                            )
                        )
                    }
                }

                // Never treat manifest text file size (a few KB) as video file size
                val genuineHlsSize = qualities.firstOrNull { it.estimatedSizeBytes >= 1024 * 1024L }?.estimatedSizeBytes ?: 0L
                detectedSize = if (genuineHlsSize >= 1024 * 1024L) genuineHlsSize else 0L
            } else {
                // Direct video link (MP4 / WebM) - 2-phase probe (HEAD fallback to Range: bytes=0-1)
                var resolvedMediaUrl = mediaUrl
                val probe = probeMediaHeadersAndSize(mediaUrl, fullHeaders)
                detectedSize = probe.sizeBytes
                probe.mimeType?.let { if (it.isNotBlank()) detectedMime = it }

                // Check if server returned application/json (API endpoint returning media URL)
                if (detectedMime.contains("application/json", ignoreCase = true) ||
                    detectedMime.contains("text/javascript", ignoreCase = true) ||
                    mediaUrl.contains(".json", ignoreCase = true)
                ) {
                    val extractedMediaUrl = tryFetchAndExtractMediaUrl(mediaUrl, fullHeaders)
                    if (!extractedMediaUrl.isNullOrBlank() && extractedMediaUrl != mediaUrl) {
                        Log.e(TAG, "VideoSnifferEngine: Extracted genuine video URL from JSON API: $extractedMediaUrl (API endpoint: $mediaUrl)")
                        resolvedMediaUrl = extractedMediaUrl
                        // Re-probe genuine media URL
                        val realProbe = probeMediaHeadersAndSize(resolvedMediaUrl, fullHeaders)
                        detectedSize = realProbe.sizeBytes
                        if (!realProbe.mimeType.isNullOrBlank()) {
                            detectedMime = realProbe.mimeType!!
                        } else {
                            detectedMime = if (resolvedMediaUrl.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/mp4"
                        }
                    } else {
                        // Crucial: If response is application/json and NO media URL could be extracted,
                        // do NOT put the raw API endpoint URL in VideoQualityOption!
                        Log.w(TAG, "VideoSnifferEngine: Discarding non-media JSON API endpoint without playable stream: $mediaUrl")
                        return@launch
                    }
                }

                val isAudio = detectedMime.contains("audio", ignoreCase = true) ||
                        resolvedMediaUrl.contains(".mp3", ignoreCase = true) ||
                        resolvedMediaUrl.contains(".m4a", ignoreCase = true)

                // Reject text/html server error responses or non-media payloads
                if (detectedMime.startsWith("text/", ignoreCase = true) && !detectedMime.contains("vtt")) {
                    return@launch
                }

                // 3. Minimum size validation for direct video files:
                // Direct files under 1 MB are tracking pixels, previews, stickers, ads, or server errors.
                if (!isAudio && detectedSize in 1 until (1024 * 1024L)) {
                    return@launch
                }

                // Filter Out Micro-Clips & Thumbnail Previews (unless triggered by an active play event):
                // Discard background preview MP4s matching common patterns under 1 MB
                val isPreview = isThumbnailOrPreviewUrl(resolvedMediaUrl)
                if (!isAudio && isPreview && (detectedSize in 0 until (1024 * 1024L) || (finalDuration in 0.001..9.999))) {
                    return@launch
                }

                // If a full media item already exists on page (>= 1.5 MB or duration >= 10s), discard incoming background micro-clips
                val hasExistingFullVideo = synchronized(aggregationLock) {
                    val prev = canonicalVideoItem
                    prev != null && !prev.mimeType.contains("audio", ignoreCase = true) &&
                            (prev.fileSizeBytes >= 1536 * 1024L || prev.durationSeconds >= 10.0)
                }

                if (!isAudio && hasExistingFullVideo && (detectedSize in 1 until (1536 * 1024L) || (finalDuration in 0.001..9.999))) {
                    return@launch
                }

                val formatTag = when {
                    detectedMime.contains("webm", ignoreCase = true) || resolvedMediaUrl.contains(".webm", ignoreCase = true) -> "WEBM"
                    isAudio -> "AUDIO"
                    else -> "MP4"
                }

                val singleOption = createGenuineDirectQualityOption(
                    mediaUrl = resolvedMediaUrl,
                    pageTitle = pageTitle,
                    fileSizeBytes = detectedSize,
                    durationSeconds = finalDuration,
                    formatTag = formatTag,
                    qualityLabelHint = qualityLabelHint,
                    resolutionHint = resolutionHint
                )

                qualities = listOf(singleOption)
            }

            // UNIFIED AGGREGATION & CANONICAL MERGE
            val aggregatedCanonical: SniffedMediaItem = synchronized(aggregationLock) {
                var previous = canonicalVideoItem
                val isAudioItem = detectedMime.contains("audio", ignoreCase = true) ||
                        mediaUrl.contains(".mp3", ignoreCase = true) ||
                        mediaUrl.contains(".m4a", ignoreCase = true)

                val effectiveTargetUrl = if (qualities.isNotEmpty()) qualities.first().url else mediaUrl

                val isSameVideo = previous != null && (
                    isSameVideoUrl(previous.url, effectiveTargetUrl) ||
                    (previous.isM3u8 && isM3u8 && isSameHlsStream(previous.url, effectiveTargetUrl))
                )

                if (isActivePlayEvent && !isSameVideo) {
                    // Replace on Active Play:
                    // Purge previous video's qualities, headers, and reference entirely so only this video's real qualities exist
                    accumulatedRawQualities.clear()
                    accumulatedHeaders.clear()
                    canonicalVideoItem = null
                    previous = null
                }

                val isIncomingSubstantial = detectedSize >= 1536 * 1024L || finalDuration >= 10.0 || isAudioItem
                val wasPreviousMicroClip = previous != null &&
                        !previous.mimeType.contains("audio", ignoreCase = true) &&
                        previous.fileSizeBytes in 1 until (1536 * 1024L) &&
                        (previous.durationSeconds == 0.0 || previous.durationSeconds < 10.0)

                // If a genuine full video arrives and previous was merely a micro-clip, purge previous micro-clips
                if (wasPreviousMicroClip && isIncomingSubstantial && !isAudioItem) {
                    accumulatedRawQualities.clear()
                }

                accumulatedHeaders.putAll(fullHeaders)
                accumulatedRawQualities.addAll(qualities)

                // Preference for Clean Progressive MP4 over Raw HLS Sub-playlists
                val hasIncomingProgressiveMp4 = !isM3u8 && !isDash && !isAudioItem && detectedSize >= 1536 * 1024L
                val previousHasOnlyVagueHls = previous?.isM3u8 == true &&
                        previous.qualities.all { it.label.contains("Direct Stream", ignoreCase = true) || it.resolution.isBlank() }

                val shouldUseAsMasterUrl = when {
                    previous == null -> true
                    hasIncomingProgressiveMp4 && previousHasOnlyVagueHls -> true
                    hasIncomingProgressiveMp4 && previous.fileSizeBytes < detectedSize -> true
                    isM3u8 && qualities.any { it.resolution.isNotBlank() } && !previous.isM3u8 -> true
                    detectedSize > (previous.fileSizeBytes) -> true
                    else -> false
                }

                val primaryUrl = if (shouldUseAsMasterUrl) effectiveTargetUrl else previous?.url ?: effectiveTargetUrl
                val primaryMime = if (shouldUseAsMasterUrl) detectedMime else previous?.mimeType ?: detectedMime
                val isMasterM3u8 = previous?.isM3u8 == true || isM3u8
                val isMasterDash = previous?.isDash == true || isDash

                val bestDuration = maxOf(previous?.durationSeconds ?: 0.0, finalDuration)
                val bestPoster = posterUrl ?: previous?.thumbnailUrl
                val bestBaseSize = maxOf(previous?.fileSizeBytes ?: 0L, detectedSize)

                // Pick the most descriptive video title available
                val bestTitle = pickBestTitle(previous?.title, pageTitle, effectiveTargetUrl)

                // Normalize & bucket all accumulated qualities into clean, genuine, sorted tiers
                val standardizedQualities = normalizeAndBucketQualities(
                    rawQualities = accumulatedRawQualities,
                    durationSeconds = bestDuration,
                    baseFileSizeBytes = bestBaseSize,
                    isHls = isMasterM3u8,
                    fallbackUrl = primaryUrl
                )

                val distinctItem = SniffedMediaItem(
                    id = UUID.randomUUID().toString(),
                    url = effectiveTargetUrl,
                    pageUrl = if (pageUrl.isNotBlank()) pageUrl else currentPageUrl.get(),
                    title = pickBestTitle(null, pageTitle, effectiveTargetUrl),
                    mimeType = detectedMime,
                    isM3u8 = isM3u8,
                    isDash = isDash,
                    headers = fullHeaders,
                    thumbnailUrl = posterUrl,
                    durationSeconds = finalDuration,
                    fileSizeBytes = detectedSize,
                    qualities = qualities
                )

                val updatedItem = if (isSameVideo || previous == null) {
                    SniffedMediaItem(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        url = primaryUrl,
                        pageUrl = if (pageUrl.isNotBlank()) pageUrl else currentPageUrl.get(),
                        title = bestTitle,
                        mimeType = primaryMime,
                        isM3u8 = isMasterM3u8,
                        isDash = isMasterDash,
                        headers = accumulatedHeaders.toMap(),
                        thumbnailUrl = bestPoster,
                        durationSeconds = bestDuration,
                        fileSizeBytes = bestBaseSize,
                        qualities = standardizedQualities
                    )
                } else {
                    distinctItem
                }

                if (isSameVideo || previous == null || isActivePlayEvent) {
                    canonicalVideoItem = updatedItem
                }
                updatedItem
            }

            mainHandler.post {
                if (isActivePlayEvent) {
                    onActiveMediaDetected?.invoke(aggregatedCanonical) ?: onMediaDetected(aggregatedCanonical)
                } else {
                    onMediaDetected(aggregatedCanonical)
                }
            }
        }
    }

    private fun pickBestTitle(previousTitle: String?, newTitle: String?, url: String): String {
        val p = previousTitle?.trim() ?: ""
        val n = newTitle?.trim() ?: ""

        val isGeneric = { s: String ->
            s.isBlank() || s.equals("External Stream", ignoreCase = true) ||
                    s.equals("Web Video Portal", ignoreCase = true) ||
                    s.startsWith("video_", ignoreCase = true) ||
                    s.startsWith("http", ignoreCase = true)
        }

        return when {
            !isGeneric(p) -> p
            !isGeneric(n) -> n
            n.isNotBlank() -> n
            p.isNotBlank() -> p
            else -> SniffedMediaItem.extractFileNameFromUrl(url)
        }
    }

    private data class MediaProbeResult(
        val sizeBytes: Long = 0L,
        val mimeType: String? = null,
        val acceptsByteRanges: Boolean = false
    )

    /**
     * Attempts to query a suspected API URL that returns JSON, extracting a genuine playable media URL.
     */
    private fun tryFetchAndExtractMediaUrl(
        apiUrl: String,
        headers: Map<String, String>
    ): String? {
        try {
            val okHeaders = buildOkHttpHeaders(headers)
            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .headers(okHeaders)
                .header("Accept", "application/json, text/plain, */*")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    return ir.ali0003.downloader.downloader.core.ChunkDownloader.extractVideoUrlFromJson(body)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch JSON from API URL $apiUrl: ${e.message}")
        }
        return null
    }

    /**
     * Resilient 2-phase probe for media headers and exact Content-Length:
     * Phase 1: Fast HTTP HEAD with full browser request headers.
     * Phase 2: HTTP GET with Range: bytes=0-1 to extract Content-Range: bytes 0-1/TOTAL_BYTES.
     */
    private fun probeMediaHeadersAndSize(
        mediaUrl: String,
        fullHeaders: Map<String, String>
    ): MediaProbeResult {
        val okHeaders = buildOkHttpHeaders(fullHeaders)
        var capturedMime: String? = null

        // Phase 1: Fast HTTP HEAD
        try {
            val headRequest = Request.Builder()
                .url(mediaUrl)
                .head()
                .headers(okHeaders)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .build()

            httpClient.newCall(headRequest).execute().use { resp ->
                val cl = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                val ct = resp.header("Content-Type")
                if (!ct.isNullOrBlank()) capturedMime = ct
                val acceptRanges = resp.header("Accept-Ranges")
                val isByteRange = acceptRanges?.contains("bytes", ignoreCase = true) == true

                if (resp.isSuccessful && cl > 0L) {
                    return MediaProbeResult(
                        sizeBytes = cl,
                        mimeType = ct,
                        acceptsByteRanges = isByteRange
                    )
                }
            }
        } catch (_: Exception) {
            // HEAD failed, fall back to Range GET
        }

        // Phase 2: Range GET probe (Range: bytes=0-1)
        try {
            val rangeRequest = Request.Builder()
                .url(mediaUrl)
                .get()
                .headers(okHeaders)
                .header("Range", "bytes=0-1")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .build()

            httpClient.newCall(rangeRequest).execute().use { resp ->
                val ct = resp.header("Content-Type")
                if (!ct.isNullOrBlank()) capturedMime = ct
                if (resp.code == 206) {
                    val contentRange = resp.header("Content-Range") ?: ""
                    val totalBytes = parseTotalSizeFromContentRange(contentRange)
                    if (totalBytes > 0L) {
                        return MediaProbeResult(
                            sizeBytes = totalBytes,
                            mimeType = ct,
                            acceptsByteRanges = true
                        )
                    }
                } else if (resp.isSuccessful) {
                    val cl = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    if (cl > 0L) {
                        return MediaProbeResult(
                            sizeBytes = cl,
                            mimeType = ct,
                            acceptsByteRanges = false
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Range probe failed
        }

        return MediaProbeResult(
            sizeBytes = 0L,
            mimeType = capturedMime,
            acceptsByteRanges = false
        )
    }

    private fun parseTotalSizeFromContentRange(contentRange: String): Long {
        if (contentRange.isBlank()) return 0L
        val totalPart = contentRange.substringAfterLast('/', "")
        return totalPart.trim().toLongOrNull() ?: 0L
    }

    private fun createGenuineDirectQualityOption(
        mediaUrl: String,
        pageTitle: String,
        fileSizeBytes: Long,
        durationSeconds: Double,
        formatTag: String,
        qualityLabelHint: String? = null,
        resolutionHint: String? = null
    ): VideoQualityOption {
        val isAudio = formatTag.contains("AUDIO", ignoreCase = true) ||
                mediaUrl.contains(".mp3", ignoreCase = true) ||
                mediaUrl.contains(".m4a", ignoreCase = true)

        if (isAudio) {
            val audioBandwidth = if (durationSeconds > 0.0 && fileSizeBytes > 0L) {
                (fileSizeBytes * 8L) / durationSeconds.toLong().coerceAtLeast(1L)
            } else 128000L

            return VideoQualityOption(
                label = "Audio Track",
                resolution = "Audio",
                bandwidthBps = audioBandwidth,
                url = mediaUrl,
                isHlsVariant = false,
                estimatedSizeBytes = fileSizeBytes,
                formatTag = "AUDIO",
                isExactSize = (fileSizeBytes > 0L)
            )
        }

        // 1. Check genuine quality hints from HTML5 tags (e.g. data-quality, label, res) or explicit hints
        val explicitHint = resolutionHint?.trim()?.takeIf { it.isNotBlank() }
            ?: qualityLabelHint?.trim()?.takeIf { it.isNotBlank() }

        var parsedLabel = ""
        var parsedRes = ""

        if (explicitHint != null) {
            val dimRegex = """(\d{3,4})x(\d{3,4})""".toRegex()
            val dimMatch = dimRegex.find(explicitHint)
            if (dimMatch != null) {
                val w = dimMatch.groupValues[1].toIntOrNull() ?: 0
                val h = dimMatch.groupValues[2].toIntOrNull() ?: 0
                if (h > 0) {
                    parsedRes = "${w}x${h}"
                    parsedLabel = when {
                        h >= 2160 -> "4K UHD"
                        h >= 1440 -> "1440p 2K"
                        h >= 1080 -> "1080p FHD"
                        h >= 720 -> "720p HD"
                        h >= 480 -> "480p SD"
                        h >= 360 -> "360p SD"
                        else -> "${h}p"
                    }
                }
            } else {
                val pRegex = """(\d{3,4})p?""".toRegex()
                val pMatch = pRegex.find(explicitHint)
                if (pMatch != null) {
                    val h = pMatch.groupValues[1].toIntOrNull() ?: 0
                    if (h > 0) {
                        parsedRes = "${h}p"
                        parsedLabel = when {
                            h >= 2160 -> "4K UHD"
                            h >= 1440 -> "1440p 2K"
                            h >= 1080 -> "1080p FHD"
                            h >= 720 -> "720p HD"
                            h >= 480 -> "480p SD"
                            h >= 360 -> "360p SD"
                            else -> "${h}p"
                        }
                    }
                }
            }
        }

        // Also check URL patterns if explicit hints were absent
        if (parsedLabel.isEmpty()) {
            val urlClean = mediaUrl.substringBefore('?').lowercase()
            val urlDimMatch = """(\d{3,4})x(\d{3,4})""".toRegex().find(urlClean)
            if (urlDimMatch != null) {
                val w = urlDimMatch.groupValues[1].toIntOrNull() ?: 0
                val h = urlDimMatch.groupValues[2].toIntOrNull() ?: 0
                if (h > 0) {
                    parsedRes = "${w}x${h}"
                    parsedLabel = when {
                        h >= 2160 -> "4K UHD"
                        h >= 1440 -> "1440p 2K"
                        h >= 1080 -> "1080p FHD"
                        h >= 720 -> "720p HD"
                        h >= 480 -> "480p SD"
                        h >= 360 -> "360p SD"
                        else -> "${h}p"
                    }
                }
            } else {
                val urlP = """(?:_|-|/|v|quality=)(\d{3,4})p?\b""".toRegex().find(urlClean)
                if (urlP != null) {
                    val h = urlP.groupValues[1].toIntOrNull() ?: 0
                    if (h in listOf(240, 360, 480, 720, 1080, 1440, 2160)) {
                        parsedRes = "${h}p"
                        parsedLabel = when {
                            h >= 2160 -> "4K UHD"
                            h >= 1440 -> "1440p 2K"
                            h >= 1080 -> "1080p FHD"
                            h >= 720 -> "720p HD"
                            h >= 480 -> "480p SD"
                            h >= 360 -> "360p SD"
                            else -> "${h}p"
                        }
                    }
                }
            }
        }

        // 2. Bitrate-Derived Classification:
        // Compute actual bitrate using: (contentLengthBytes * 8L) / durationSeconds.toLong()
        val durationSec = durationSeconds.toLong()
        val bitrateBps = if (durationSec > 0L && fileSizeBytes > 0L) {
            (fileSizeBytes * 8L) / durationSec
        } else {
            0L
        }

        // Map bitrateBps to standard resolution tiers matching the bandwidth scale:
        if (parsedLabel.isEmpty()) {
            if (bitrateBps > 0L) {
                val (tierLabel, resDim) = when {
                    bitrateBps >= 12_000_000L -> "4K UHD" to "3840x2160"
                    bitrateBps >= 7_000_000L -> "1440p 2K" to "2560x1440"
                    bitrateBps >= 3_500_000L -> "1080p FHD" to "1920x1080"
                    bitrateBps >= 1_600_000L -> "720p HD" to "1280x720"
                    bitrateBps >= 750_000L -> "480p SD" to "854x480"
                    else -> "360p SD" to "640x360"
                }
                parsedLabel = tierLabel
                parsedRes = resDim
            } else {
                // If duration is unavailable: display as "Source Stream" with exact probed Content-Length
                parsedLabel = "Source Stream"
                parsedRes = ""
            }
        }

        val displayLabel = if (parsedRes.isNotBlank() && !parsedLabel.contains(parsedRes) && parsedRes.contains("x")) {
            "$parsedLabel ($parsedRes)"
        } else {
            parsedLabel
        }

        return VideoQualityOption(
            label = displayLabel,
            resolution = if (parsedRes.isNotBlank()) parsedRes else parsedLabel,
            bandwidthBps = bitrateBps,
            url = mediaUrl,
            isHlsVariant = false,
            estimatedSizeBytes = fileSizeBytes,
            formatTag = formatTag,
            isExactSize = (fileSizeBytes > 0L)
        )
    }

    fun normalizeAndBucketQualities(
        rawQualities: List<VideoQualityOption>,
        durationSeconds: Double,
        baseFileSizeBytes: Long,
        isHls: Boolean,
        fallbackUrl: String
    ): List<VideoQualityOption> {
        if (rawQualities.isEmpty()) {
            return listOf(
                VideoQualityOption(
                    label = if (isHls) "Direct Stream" else "Source Stream",
                    resolution = "",
                    bandwidthBps = 0L,
                    url = fallbackUrl,
                    isHlsVariant = isHls,
                    estimatedSizeBytes = if (isHls && baseFileSizeBytes < 1024 * 1024L) 0L else baseFileSizeBytes,
                    formatTag = if (isHls) "HLS" else "MP4"
                )
            )
        }

        // 1. Strict Deduplication:
        // For HLS variants: preserve EVERY declared variant (distinct by resolution, bandwidth, clean badge, and URL/renditionKey)
        // For progressive MP4: deduplicate by clean URL
        val hlsVariants = rawQualities.filter { it.isHlsVariant }.distinctBy {
            "${it.resolution}_${it.bandwidthBps}_${it.cleanResolutionBadge}_${it.renditionKey ?: it.url}"
        }
        val nonHlsVariants = rawQualities.filterNot { it.isHlsVariant }
            .groupBy { it.url.substringBefore('?').substringBefore('#') }
            .mapNotNull { (_, optionsForUrl) ->
                optionsForUrl.maxWithOrNull(
                    compareBy<VideoQualityOption> { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                        .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
                )
            }
        val urlDeduplicated = hlsVariants + nonHlsVariants

        // 2. Filter Out Micro-Clips & Thumbnail Previews (Never filter out HLS variants based on megabytes)
        val hasFullVideo = urlDeduplicated.any { opt ->
            val isAudio = opt.formatTag.contains("AUDIO", ignoreCase = true) || opt.resolution.contains("Audio", ignoreCase = true)
            !isAudio && (opt.isHlsVariant || opt.estimatedSizeBytes >= 1536 * 1024L || (durationSeconds >= 10.0 && opt.estimatedSizeBytes > 0L)) && !isThumbnailOrPreviewUrl(opt.url)
        }

        val postClipFilter = if (hasFullVideo) {
            urlDeduplicated.filterNot { opt ->
                val isAudio = opt.formatTag.contains("AUDIO", ignoreCase = true) || opt.resolution.contains("Audio", ignoreCase = true)
                if (isAudio) return@filterNot false
                // Keep all declared server HLS variants
                if (opt.isHlsVariant) {
                    return@filterNot isThumbnailOrPreviewUrl(opt.url) || isThumbnailOrPreviewUrl(opt.label)
                }
                val isPreview = isThumbnailOrPreviewUrl(opt.url) || isThumbnailOrPreviewUrl(opt.label)
                val isUnderSize = opt.estimatedSizeBytes in 1 until (1536 * 1024L)
                val isShort = durationSeconds in 0.001..9.999
                isPreview || isUnderSize || isShort
            }
        } else {
            urlDeduplicated.filterNot { opt ->
                val isAudio = opt.formatTag.contains("AUDIO", ignoreCase = true) || opt.resolution.contains("Audio", ignoreCase = true)
                if (isAudio) return@filterNot false
                if (opt.isHlsVariant) return@filterNot false
                val isPreview = isThumbnailOrPreviewUrl(opt.url) || isThumbnailOrPreviewUrl(opt.label)
                val isUnder1MB = opt.estimatedSizeBytes in 1 until (1024 * 1024L)
                isPreview || isUnder1MB
            }
        }

        // 3. Clean stream list: suppress vague Direct Stream if named video options exist
        val progressiveMp4s = postClipFilter.filter {
            !it.isHlsVariant && !it.formatTag.contains("AUDIO", ignoreCase = true) &&
                    (it.resolution.isNotBlank() || !it.label.contains("Direct Stream", ignoreCase = true))
        }
        val hasProgressiveMp4 = progressiveMp4s.isNotEmpty()
        val hasNamedVideoOptions = postClipFilter.any {
            !it.formatTag.contains("AUDIO", ignoreCase = true) &&
                    it.resolution.isNotBlank() &&
                    !it.label.contains("Direct Stream", ignoreCase = true) &&
                    !it.label.contains("Source Stream", ignoreCase = true)
        }

        val cleanStreamList = postClipFilter.filterNot { opt ->
            val isAudio = opt.formatTag.contains("AUDIO", ignoreCase = true) || opt.resolution.contains("Audio", ignoreCase = true)
            if (isAudio) return@filterNot false

            if (hasProgressiveMp4 && opt.isHlsVariant && (opt.label.contains("Direct Stream", ignoreCase = true) || opt.label.contains("Source Stream", ignoreCase = true) || opt.resolution.isBlank())) {
                return@filterNot true
            }

            if (hasNamedVideoOptions && (opt.label.contains("Direct Stream", ignoreCase = true) || opt.label.contains("Source Stream", ignoreCase = true) || opt.resolution.isBlank() || opt.label.equals("Variant Stream", ignoreCase = true))) {
                return@filterNot true
            }

            false
        }

        // 4. Separate Audio and Video
        val audioOptions = cleanStreamList.filter {
            it.formatTag.contains("AUDIO", ignoreCase = true) || it.resolution.contains("Audio", ignoreCase = true)
        }
        val videoOptions = cleanStreamList.filterNot {
            it.formatTag.contains("AUDIO", ignoreCase = true) || it.resolution.contains("Audio", ignoreCase = true)
        }

        // 5. Video Tiers Grouping & Deduplication:
        // Group by distinct resolution height.
        // For each resolution tier: prioritize direct MP4 video files when available.
        val deduplicatedVideoTiers = mutableListOf<VideoQualityOption>()
        val groupedByHeight = videoOptions.groupBy { it.getResolutionHeight() }

        for ((height, optionsInHeight) in groupedByHeight) {
            val mp4Candidate = optionsInHeight.filter { !it.isHlsVariant && !it.formatTag.contains("HLS", ignoreCase = true) && !it.url.contains(".m3u8", ignoreCase = true) }
                .maxWithOrNull(
                    compareBy<VideoQualityOption> { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                        .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
                )
            val hlsCandidate = optionsInHeight.filter { it.isHlsVariant || it.formatTag.contains("HLS", ignoreCase = true) || it.url.contains(".m3u8", ignoreCase = true) }
                .maxWithOrNull(
                    compareBy<VideoQualityOption> { it.bandwidthBps }
                        .thenBy { it.estimatedSizeBytes }
                )

            val chosen = when {
                mp4Candidate != null -> {
                    if (mp4Candidate.estimatedSizeBytes <= 0L) {
                        val est = if (durationSeconds > 0.0 && mp4Candidate.bandwidthBps > 0L) {
                            ((mp4Candidate.bandwidthBps * durationSeconds) / 8.0).toLong()
                        } else if (hlsCandidate?.estimatedSizeBytes ?: 0L > 0L) {
                            hlsCandidate!!.estimatedSizeBytes
                        } else 0L
                        if (est > 0L) mp4Candidate.copy(estimatedSizeBytes = est) else mp4Candidate
                    } else mp4Candidate
                }
                hlsCandidate != null -> {
                    if (hlsCandidate.estimatedSizeBytes <= 0L) {
                        val est = if (durationSeconds > 0.0 && hlsCandidate.bandwidthBps > 0L) {
                            ((hlsCandidate.bandwidthBps * durationSeconds) / 8.0).toLong()
                        } else 0L
                        if (est > 0L) hlsCandidate.copy(estimatedSizeBytes = est) else hlsCandidate
                    } else hlsCandidate
                }
                else -> optionsInHeight.firstOrNull()
            }
            if (chosen != null) {
                deduplicatedVideoTiers.add(chosen)
            }
        }

        // 6. Present a clean, descending list from highest resolution to lowest resolution
        val sortedCandidates = deduplicatedVideoTiers.sortedWith(
            compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
                .thenByDescending { it.bandwidthBps }
                .thenByDescending { it.estimatedSizeBytes }
        )

        // Enforce strict size & bandwidth consistency across descending resolutions
        val coherentCandidates = HlsManifestParser.enforceSizeCoherence(sortedCandidates)

        // Final guard against duplicate resolution heights or identical sizes
        val uniqueVideoOptions = mutableListOf<VideoQualityOption>()
        val seenHeights = mutableSetOf<Int>()
        val seenExactSizes = mutableSetOf<Long>()

        for (opt in coherentCandidates) {
            val h = opt.getResolutionHeight()
            if (h > 0 && seenHeights.contains(h)) {
                continue
            }
            if (opt.estimatedSizeBytes > 0L && seenExactSizes.contains(opt.estimatedSizeBytes)) {
                continue
            }
            if (h > 0) {
                seenHeights.add(h)
            }
            if (opt.estimatedSizeBytes > 0L) {
                seenExactSizes.add(opt.estimatedSizeBytes)
            }
            val sanitizedOpt = if (opt.isHlsVariant && opt.estimatedSizeBytes in 1 until (1024 * 1024L)) {
                opt.copy(estimatedSizeBytes = 0L)
            } else {
                opt
            }
            uniqueVideoOptions.add(sanitizedOpt)
        }

        // 7. Audio track at the bottom
        val bestAudioOption = audioOptions.maxByOrNull { it.estimatedSizeBytes.coerceAtLeast(it.bandwidthBps) }

        val combinedResult = if (bestAudioOption != null) {
            uniqueVideoOptions + bestAudioOption
        } else {
            uniqueVideoOptions
        }

        return if (combinedResult.isNotEmpty()) {
            combinedResult
        } else {
            listOf(
                VideoQualityOption(
                    label = if (isHls) "Direct Stream" else "Source Stream",
                    resolution = "",
                    bandwidthBps = 0L,
                    url = fallbackUrl,
                    isHlsVariant = isHls,
                    estimatedSizeBytes = baseFileSizeBytes,
                    formatTag = if (isHls) "HLS" else "MP4"
                )
            )
        }
    }

    private fun getResolutionTierKey(opt: VideoQualityOption): String {
        val h = opt.getResolutionHeight()
        return if (h > 0) "${h}p" else opt.cleanResolutionBadge.ifBlank { opt.resolution.ifBlank { opt.label } }
    }

    private fun extractHeightForSorting(option: VideoQualityOption): Int {
        return option.getResolutionHeight()
    }

    private fun extractHeadersForUrl(
        mediaUrl: String,
        pageUrl: String,
        userAgent: String?,
        existingHeaders: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        // 1. Sync Cookies from CookieManager
        try {
            val cookie = CookieManager.getInstance().getCookie(mediaUrl)
                ?: (if (pageUrl.isNotBlank()) CookieManager.getInstance().getCookie(pageUrl) else null)
                ?: (if (currentPageUrl.get().isNotBlank()) CookieManager.getInstance().getCookie(currentPageUrl.get()) else null)
            if (!cookie.isNullOrBlank()) {
                headers["Cookie"] = cookie
            }
        } catch (_: Exception) {
            // Ignore cookie manager exception
        }

        // 2. Referer and Origin headers to avoid 403 Forbidden on CDN/LMS hosts
        val effectivePageUrl = when {
            pageUrl.isNotBlank() -> pageUrl
            currentPageUrl.get().isNotBlank() -> currentPageUrl.get()
            else -> ""
        }
        if (effectivePageUrl.isNotBlank()) {
            headers["Referer"] = effectivePageUrl
            try {
                val origin = android.net.Uri.parse(effectivePageUrl)
                if (origin.scheme != null && origin.host != null) {
                    headers["Origin"] = "${origin.scheme}://${origin.host}"
                }
            } catch (_: Exception) {
                // Ignore
            }
        }

        // 3. User-Agent
        val effectiveUa = when {
            !userAgent.isNullOrBlank() -> userAgent
            !currentUserAgent.get().isNullOrBlank() -> currentUserAgent.get()
            existingHeaders.containsKey("User-Agent") -> existingHeaders["User-Agent"] ?: ""
            existingHeaders.containsKey("user-agent") -> existingHeaders["user-agent"] ?: ""
            else -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }
        if (effectiveUa.isNotBlank()) {
            headers["User-Agent"] = effectiveUa
        }

        // 4. Merge other existing headers (purging range and transport-specific headers)
        existingHeaders.forEach { (k, v) ->
            val lk = k.lowercase()
            if (!headers.containsKey(k) && k.isNotBlank() && v.isNotBlank() &&
                lk != "range" && lk != "if-range" && lk != "content-length" && lk != "host"
            ) {
                headers[k] = v
            }
        }

        return headers
    }

    private fun buildOkHttpHeaders(headersMap: Map<String, String>): Headers {
        val builder = Headers.Builder()
        headersMap.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                try {
                    builder.add(k, v)
                } catch (e: Exception) {
                    // Ignore malformed header values
                }
            }
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "VideoSnifferEngine"
        const val JS_BRIDGE_NAME = "AndroidVideoSniffer"

        const val BLOB_HOOK_SNIFFER_JS = """
            (function() {
                if (window.__blobHookInjected) return;
                window.__blobHookInjected = true;

                window.__recentMediaUrls = window.__recentMediaUrls || [];
                window.__blobToSourceMap = window.__blobToSourceMap || {};

                function normalizeUrl(u) {
                    if (!u || typeof u !== 'string') return '';
                    try {
                        var a = document.createElement('a');
                        a.href = u;
                        return a.href;
                    } catch(e) {
                        return u;
                    }
                }

                function recordMediaUrl(url) {
                    if (!url || typeof url !== 'string') return;
                    var lower = url.toLowerCase();
                    if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('.mpd') !== -1 ||
                        lower.indexOf('.mp4') !== -1 || lower.indexOf('.webm') !== -1 ||
                        lower.indexOf('manifest') !== -1 || lower.indexOf('playlist') !== -1 ||
                        lower.indexOf('master') !== -1) {
                        var full = normalizeUrl(url);
                        window.__recentMediaUrls.unshift({ url: full, time: Date.now() });
                        if (window.__recentMediaUrls.length > 50) {
                            window.__recentMediaUrls.pop();
                        }
                    }
                }

                // 1. Hook window.fetch to capture stream manifests & direct media URLs
                try {
                    if (window.fetch) {
                        var origFetch = window.fetch;
                        window.fetch = function(input, init) {
                            try {
                                var u = (typeof input === 'string') ? input : (input && input.url);
                                if (u) recordMediaUrl(u);
                            } catch(e) {}
                            return origFetch.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                // 2. Hook XMLHttpRequest.prototype.open
                try {
                    if (window.XMLHttpRequest && XMLHttpRequest.prototype && XMLHttpRequest.prototype.open) {
                        var origOpen = XMLHttpRequest.prototype.open;
                        XMLHttpRequest.prototype.open = function(method, url) {
                            try {
                                if (url) recordMediaUrl(url);
                            } catch(e) {}
                            return origOpen.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                // 3. Hook URL.createObjectURL to map blob URLs to original stream manifests
                try {
                    if (window.URL && typeof window.URL.createObjectURL === 'function') {
                        var origCreateObjectURL = window.URL.createObjectURL;
                        window.URL.createObjectURL = function(obj) {
                            var blobUrl = origCreateObjectURL.call(window.URL, obj);
                            try {
                                var now = Date.now();
                                var match = (window.__recentMediaUrls && window.__recentMediaUrls.length > 0)
                                    ? window.__recentMediaUrls.find(function(item) { return (now - item.time) < 15000; })
                                    : null;

                                var sourceUrl = match ? match.url : (obj && (obj.__sourceUrl || obj.src || obj.url));
                                if (sourceUrl) {
                                    window.__blobToSourceMap[blobUrl] = sourceUrl;
                                }
                            } catch(e) {}
                            return blobUrl;
                        };
                    }
                } catch(e) {}

                // 4. Hook MediaSource
                try {
                    if (window.MediaSource) {
                        var origAddSourceBuffer = MediaSource.prototype.addSourceBuffer;
                        MediaSource.prototype.addSourceBuffer = function() {
                            try {
                                if (window.__recentMediaUrls && window.__recentMediaUrls.length > 0) {
                                    this.__sourceUrl = window.__recentMediaUrls[0].url;
                                }
                            } catch(e) {}
                            return origAddSourceBuffer.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                // 5. Hook HTMLVideoElement.prototype.src setter
                try {
                    var videoProto = window.HTMLVideoElement ? window.HTMLVideoElement.prototype : (window.HTMLMediaElement ? window.HTMLMediaElement.prototype : null);
                    if (videoProto) {
                        var desc = Object.getOwnPropertyDescriptor(videoProto, 'src') ||
                                   (window.HTMLMediaElement ? Object.getOwnPropertyDescriptor(window.HTMLMediaElement.prototype, 'src') : null);
                        if (desc && desc.set) {
                            var origSet = desc.set;
                            desc.set = function(val) {
                                try {
                                    if (val && typeof val === 'string' && val.indexOf('blob:') === 0) {
                                        var mapped = window.__blobToSourceMap[val];
                                        if (mapped) {
                                            this.__originalSourceUrl = mapped;
                                        }
                                    } else if (val && typeof val === 'string') {
                                        recordMediaUrl(val);
                                    }
                                } catch(e) {}
                                return origSet.call(this, val);
                            };
                            Object.defineProperty(videoProto, 'src', desc);
                        }
                    }
                } catch(e) {}
            })();
        """

        const val PLAY_EVENT_SNIFFER_JS = """
            (function() {
                if (window.__videoPlaySnifferInjected) return;
                window.__videoPlaySnifferInjected = true;

                function getBridge() {
                    return window.AndroidBridge || window.AndroidVideoSniffer || null;
                }

                document.addEventListener('play', function(e) {
                    if (e.target && e.target.tagName === 'VIDEO') {
                        var video = e.target;
                        var bridge = getBridge();
                        if (!bridge) return;

                        var title = document.title || '';
                        try {
                            var elemTitle = video.getAttribute('title') || video.getAttribute('aria-label');
                            if (elemTitle) title = elemTitle;
                        } catch(_) {}

                        var currentSrcVal = video.currentSrc || video.src || '';

                        // 1. Immediately trigger active play window (500ms binding on Android)
                        try {
                            if (typeof bridge.onActivePlayTriggered === 'function') {
                                bridge.onActivePlayTriggered(currentSrcVal, title);
                            }
                        } catch(_) {}

                        var src = currentSrcVal;
                        if (!src || src.indexOf('blob:') === 0) {
                            var s = video.querySelector('source');
                            if (s) {
                                var sSrc = s.src || s.getAttribute('src');
                                if (sSrc && sSrc.indexOf('blob:') !== 0) {
                                    src = sSrc;
                                }
                            }
                        }

                        // 2. Resolve blob: using URL.createObjectURL hook map or recent stream manifests
                        if (src && src.indexOf('blob:') === 0) {
                            var mapped = (window.__blobToSourceMap && window.__blobToSourceMap[src]) || video.__originalSourceUrl;
                            if (!mapped && window.__recentMediaUrls && window.__recentMediaUrls.length > 0) {
                                var now = Date.now();
                                var match = window.__recentMediaUrls.find(function(item) {
                                    return (now - item.time) < 15000;
                                });
                                if (match) mapped = match.url;
                            }
                            if (mapped) {
                                src = mapped;
                            }
                        }

                        // 3. Dispatch genuine stream source to Android client
                        if (src && src.indexOf('blob:') !== 0) {
                            try {
                                if (typeof bridge.onActiveVideoDetected === 'function') {
                                    bridge.onActiveVideoDetected(src, title);
                                }
                            } catch(_) {}
                        }
                    }
                }, true);
            })();
        """

        const val MEDIA_DEFINITIONS_EXTRACTOR_JS = """javascript:(function(){
            try {
                function triggerBridge(defs) {
                    if (!defs) return;
                    var jsonStr = typeof defs === 'string' ? defs : JSON.stringify(defs);
                    if (window.AndroidBridge && typeof window.AndroidBridge.processMediaDefinitions === 'function') {
                        window.AndroidBridge.processMediaDefinitions(jsonStr);
                    } else if (window.AndroidVideoSniffer && typeof window.AndroidVideoSniffer.processMediaDefinitions === 'function') {
                        window.AndroidVideoSniffer.processMediaDefinitions(jsonStr);
                    }
                }
                var fKey = Object.keys(window).find(function(k){ return k.indexOf('flashvars') !== -1; });
                if (fKey && window[fKey] && window[fKey].mediaDefinitions) {
                    triggerBridge(window[fKey].mediaDefinitions);
                    return;
                }
                if (window.mediaDefinitions) {
                    triggerBridge(window.mediaDefinitions);
                    return;
                }
                if (window.player_mp4_seek && Array.isArray(window.player_mp4_seek)) {
                    triggerBridge(window.player_mp4_seek);
                    return;
                }
                var scripts = document.getElementsByTagName('script');
                for (var i = 0; i < scripts.length; i++) {
                    var txt = scripts[i].textContent || '';
                    if (txt.indexOf('mediaDefinitions') !== -1) {
                        var m = txt.match(/mediaDefinitions\s*[:=]\s*(\[[^\]]+\])/);
                        if (m && m[1]) {
                            try {
                                var parsed = JSON.parse(m[1]);
                                triggerBridge(parsed);
                                return;
                            } catch (_) {}
                        }
                    }
                }
            } catch(e){}
        })();"""

        const val PORNHUB_FLASHVARS_EXTRACTOR_JS = MEDIA_DEFINITIONS_EXTRACTOR_JS

        val DOM_SNIFFER_JS = """
            (function() {
                if (window.__videoSnifferInjected) return;
                window.__videoSnifferInjected = true;
                var reportedUrls = new Set();

                function report(src, mime, title, poster, duration, qualityLabel, resolution) {
                    if (!src || typeof src !== 'string') return;
                    src = src.trim();
                    if (!src || src.startsWith('blob:') || src.startsWith('data:') || src.startsWith('javascript:')) return;
                    try {
                        var a = document.createElement('a');
                        a.href = src;
                        src = a.href;
                    } catch(e) {}

                    if (reportedUrls.has(src)) return;
                    reportedUrls.add(src);

                    var pageTitle = title || document.title || '';
                    var p = poster || '';
                    var dur = 0;
                    try {
                        if (duration && !isNaN(duration) && isFinite(duration) && duration > 0) {
                            dur = duration;
                        }
                    } catch(_) {}

                    var type = mime || 'video/mp4';
                    var qLabel = qualityLabel || '';
                    var res = resolution || '';

                    if (window.AndroidVideoSniffer) {
                        if (window.AndroidVideoSniffer.onMediaDiscoveredWithQuality) {
                            window.AndroidVideoSniffer.onMediaDiscoveredWithQuality(src, type, pageTitle, p, dur, qLabel, res);
                        } else if (window.AndroidVideoSniffer.onMediaDiscovered) {
                            window.AndroidVideoSniffer.onMediaDiscovered(src, type, pageTitle, p, dur);
                        }
                    }
                }

                function scanElement(elem) {
                    if (!elem) return;
                    try {
                        var dur = 0;
                        try { dur = elem.duration || 0; } catch(_) {}
                        var poster = elem.poster || '';
                        var title = elem.getAttribute('title') || document.title || '';

                        var src = elem.currentSrc || elem.src;
                        if (src) {
                            report(src, elem.type || 'video/mp4', title, poster, dur, '', '');
                        }

                        ['data-src', 'data-video', 'data-url'].forEach(function(attr) {
                            var dataSrc = elem.getAttribute(attr);
                            if (dataSrc) report(dataSrc, 'video/mp4', title, poster, dur, '', '');
                        });

                        var sources = elem.getElementsByTagName('source');
                        for (var i = 0; i < sources.length; i++) {
                            var s = sources[i];
                            var sSrc = s.src || s.getAttribute('src') || s.getAttribute('data-src');
                            if (sSrc) {
                                var sType = s.type || s.getAttribute('type') || 'video/mp4';
                                var label = s.getAttribute('label') || s.getAttribute('title') || s.getAttribute('data-quality') || '';
                                var res = s.getAttribute('res') || s.getAttribute('size') || s.getAttribute('data-res') || '';
                                report(sSrc, sType, title, poster, dur, label, res);
                            }
                        }
                    } catch(e) {}
                }

                function scanPlayerConfigs() {
                    try {
                        var fKey = Object.keys(window).find(function(k){ return k.indexOf('flashvars') !== -1; });
                        if (fKey && window[fKey] && window[fKey].mediaDefinitions) {
                            var vDur = 0;
                            try {
                                vDur = parseFloat(window[fKey].video_duration || window[fKey].duration || (document.querySelector('video') && document.querySelector('video').duration) || 0);
                            } catch(_) {}
                            var vTitle = window[fKey].video_title || '';
                            var bridge = window.AndroidBridge || window.AndroidVideoSniffer;
                            if (bridge && typeof bridge.processMediaDefinitionsWithDuration === 'function') {
                                bridge.processMediaDefinitionsWithDuration(JSON.stringify(window[fKey].mediaDefinitions), vDur, vTitle);
                            } else if (bridge && typeof bridge.processMediaDefinitions === 'function') {
                                bridge.processMediaDefinitions(JSON.stringify(window[fKey].mediaDefinitions));
                            } else if (bridge && typeof bridge.onMediaDefinitionsFound === 'function') {
                                bridge.onMediaDefinitionsFound(JSON.stringify(window[fKey].mediaDefinitions));
                            }
                        }
                    } catch(e) {}

                    try {
                        if (window.html5player) {
                            var hp = window.html5player;
                            if (typeof hp.getVideoUrlHigh === 'function') report(hp.getVideoUrlHigh(), 'video/mp4', '', '', 0, 'High', '720p');
                            if (typeof hp.getVideoUrlLow === 'function') report(hp.getVideoUrlLow(), 'video/mp4', '', '', 0, 'Low', '360p');
                            if (typeof hp.getVideoHLS === 'function') report(hp.getVideoHLS(), 'application/x-mpegurl', '', '', 0, 'HLS Adaptive', '');
                            if (hp.video_url) report(hp.video_url, 'video/mp4', '', '', 0, '', '');
                            if (hp.video_url_high) report(hp.video_url_high, 'video/mp4', '', '', 0, 'High', '720p');
                            if (hp.video_url_low) report(hp.video_url_low, 'video/mp4', '', '', 0, 'Low', '360p');
                            if (hp.hls_url) report(hp.hls_url, 'application/x-mpegurl', '', '', 0, 'HLS', '');
                        }

                        if (typeof window.jwplayer === 'function') {
                            try {
                                var jw = window.jwplayer();
                                if (jw && typeof jw.getPlaylist === 'function') {
                                    var pl = jw.getPlaylist();
                                    if (pl && pl.length) {
                                        for (var p = 0; p < pl.length; p++) {
                                            var item = pl[p];
                                            var sources = item.sources || [];
                                            for (var s = 0; s < sources.length; s++) {
                                                var srcItem = sources[s];
                                                if (srcItem.file) {
                                                    report(srcItem.file, srcItem.type || 'video/mp4', item.title || '', item.image || '', 0, srcItem.label || '', '');
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch(_) {}
                        }

                        if (typeof window.videojs === 'function' && window.videojs.players) {
                            try {
                                var players = window.videojs.players;
                                for (var key in players) {
                                    if (players.hasOwnProperty(key)) {
                                        var vp = players[key];
                                        if (vp && typeof vp.currentSources === 'function') {
                                            var cSources = vp.currentSources();
                                            if (Array.isArray(cSources)) {
                                                for (var cs = 0; cs < cSources.length; cs++) {
                                                    if (cSources[cs].src) report(cSources[cs].src, cSources[cs].type || 'video/mp4', '', '', 0, '', '');
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch(_) {}
                        }

                        var scripts = document.getElementsByTagName('script');
                        for (var sc = 0; sc < scripts.length; sc++) {
                            var content = scripts[sc].textContent || '';
                            if (!content || content.length < 20 || content.length > 300000) continue;

                            var highMatch = content.match(/setVideoUrlHigh\s*\(\s*['"]([^'"]+)['"]/);
                            if (highMatch && highMatch[1]) report(highMatch[1], 'video/mp4', '', '', 0, 'High', '720p');

                            var lowMatch = content.match(/setVideoUrlLow\s*\(\s*['"]([^'"]+)['"]/);
                            if (lowMatch && lowMatch[1]) report(lowMatch[1], 'video/mp4', '', '', 0, 'Low', '360p');

                            var hlsMatch = content.match(/setVideoHLS\s*\(\s*['"]([^'"]+)['"]/);
                            if (hlsMatch && hlsMatch[1]) report(hlsMatch[1], 'application/x-mpegurl', '', '', 0, 'HLS Adaptive', '');

                            var fileRegex = /['"]file['"]\s*:\s*['"](https?:\\?\/\\?[^'"]+\.(?:mp4|webm|m3u8)[^'"]*)['"]/g;
                            var fm;
                            while ((fm = fileRegex.exec(content)) !== null) {
                                var fUrl = fm[1].replace(/\\\//g, '/');
                                report(fUrl, fUrl.indexOf('.m3u8') !== -1 ? 'application/x-mpegurl' : 'video/mp4', '', '', 0, '', '');
                            }
                        }
                    } catch(e) {}
                }

                function scanAll() {
                    try {
                        var videos = document.getElementsByTagName('video');
                        for (var i = 0; i < videos.length; i++) scanElement(videos[i]);
                        var audios = document.getElementsByTagName('audio');
                        for (var j = 0; j < audios.length; j++) scanElement(audios[j]);
                        scanPlayerConfigs();
                    } catch(e) {}
                }

                // 1. Initial scan
                scanAll();

                // 2. Intercept HTMLMediaElement play & load events safely
                try {
                    var origPlay = HTMLMediaElement.prototype.play;
                    HTMLMediaElement.prototype.play = function() {
                        scanElement(this);
                        scanPlayerConfigs();
                        return origPlay.apply(this, arguments);
                    };
                    var origLoad = HTMLMediaElement.prototype.load;
                    HTMLMediaElement.prototype.load = function() {
                        scanElement(this);
                        scanPlayerConfigs();
                        return origLoad.apply(this, arguments);
                    };
                    document.addEventListener('click', function() { scanPlayerConfigs(); }, true);
                    document.addEventListener('touchend', function() { scanPlayerConfigs(); }, true);
                    document.addEventListener('pointerup', function() { scanPlayerConfigs(); }, true);
                    document.addEventListener('play', function() { scanPlayerConfigs(); }, true);
                    document.addEventListener('playing', function() { scanPlayerConfigs(); }, true);
                    window.addEventListener('play', function() { scanPlayerConfigs(); }, true);
                } catch(e) {}

                // 3. Lightweight periodic scan that stops after 10 iterations to preserve renderer resources
                try {
                    var pollCount = 0;
                    var pollInterval = setInterval(function() {
                        scanAll();
                        pollCount++;
                        if (pollCount >= 10) {
                            clearInterval(pollInterval);
                        }
                    }, 1200);
                } catch(e) {}
            })();
        """.trimIndent()

        fun isDirectMediaUrl(url: String): Boolean {
            val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()
            val directMediaExtensions = listOf(
                ".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".flv",
                ".mp3", ".m4a", ".aac", ".ogg", ".wav", ".m3u8", ".mpd", ".ts"
            )
            if (directMediaExtensions.any { cleanUrl.endsWith(it) }) return true

            val fullLower = url.lowercase()
            return fullLower.contains(".m3u8?") ||
                    fullLower.contains("mime=video") ||
                    fullLower.contains("mime=audio") ||
                    fullLower.contains("format=m3u8") ||
                    fullLower.contains("type=mp4") ||
                    fullLower.contains("ext=mp4")
        }
    }
}
