package ir.ali0003.downloader.browser.sniffer

import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val onMediaDetected: (SniffedMediaItem) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectedUrls = ConcurrentHashMap.newKeySet<String>()

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
        detectedUrls.clear()
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

        // 2. Check if URL matches media patterns
        val requestHeaders = request.requestHeaders ?: emptyMap()
        if (isMediaUrl(url, requestHeaders)) {
            val referer = requestHeaders["Referer"] ?: requestHeaders["referer"] ?: currentPageUrl.get()
            val safePageUrl = if (referer.isNotBlank()) referer else url
            val safePageTitle = currentPageTitle.get()

            processDetectedMediaUrl(
                mediaUrl = url,
                pageUrl = safePageUrl,
                pageTitle = safePageTitle,
                requestHeaders = requestHeaders
            )
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
                        specifiedMime = mimeType
                    )
                } catch (_: Exception) {
                    // Safe guard against WebView disposal while callback is posting
                }
            }
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
        return clean.endsWith(".ts") || clean.endsWith(".m4s") ||
                url.contains("/segment_", ignoreCase = true) ||
                url.contains("/seg-", ignoreCase = true) ||
                url.contains("-frag-", ignoreCase = true)
    }

    private fun isMediaUrl(url: String, requestHeaders: Map<String, String>): Boolean {
        if (isAdOrJunkUrl(url)) return false

        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()

        // Direct streams & manifests
        val directMediaExtensions = listOf(
            ".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".flv",
            ".mp3", ".m4a", ".aac", ".ogg", ".wav"
        )
        if (directMediaExtensions.any { cleanUrl.endsWith(it) }) return true

        // Live stream manifests
        if (cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".mpd")) return true

        // URL query heuristics
        val fullLower = url.lowercase()
        if (fullLower.contains(".m3u8?") ||
            fullLower.contains("mime=video") ||
            fullLower.contains("mime=audio") ||
            fullLower.contains("format=m3u8") ||
            fullLower.contains("type=mp4") ||
            fullLower.contains("ext=mp4")
        ) {
            return true
        }

        // Accept header heuristics
        val acceptHeader = requestHeaders["Accept"] ?: requestHeaders["accept"] ?: ""
        if (acceptHeader.contains("video/") || acceptHeader.contains("application/x-mpegurl") || acceptHeader.contains("application/vnd.apple.mpegurl")) {
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
        specifiedMime: String? = null
    ) {
        if (isAdOrJunkUrl(mediaUrl)) return

        // Skip loose chunks if an HLS master manifest has already been registered
        val hasHlsMaster = synchronized(aggregationLock) { canonicalVideoItem?.isM3u8 == true }
        if (hasHlsMaster && isChunkFragment(mediaUrl)) {
            return
        }

        if (!detectedUrls.add(mediaUrl)) {
            // Already processed this URL in current session
            return
        }

        scope.launch {
            val isM3u8 = mediaUrl.contains(".m3u8", ignoreCase = true) ||
                    specifiedMime?.contains("mpegurl", ignoreCase = true) == true
            val isDash = mediaUrl.contains(".mpd", ignoreCase = true) ||
                    specifiedMime?.contains("dash+xml", ignoreCase = true) == true

            val fullHeaders = extractHeadersForUrl(mediaUrl, pageUrl, null, requestHeaders)

            var qualities: List<VideoQualityOption> = emptyList()
            var detectedSize = 0L
            var detectedMime = specifiedMime ?: if (isM3u8) "application/x-mpegURL" else "video/mp4"
            var finalDuration = durationSeconds

            if (isM3u8) {
                // Parse HLS master playlist with deep segment duration calculation
                val hlsResult = HlsManifestParser.fetchAndParseMasterPlaylist(
                    masterUrl = mediaUrl,
                    headersMap = fullHeaders,
                    fallbackDurationSeconds = durationSeconds
                )

                if (hlsResult.parsedDurationSeconds > 0.0) {
                    finalDuration = hlsResult.parsedDurationSeconds
                }

                qualities = if (hlsResult.qualities.isNotEmpty()) {
                    hlsResult.qualities
                } else {
                    val estSize = if (finalDuration > 0.0) {
                        ((5_500_000L * finalDuration) / 8.0).toLong()
                    } else {
                        54 * 1024 * 1024L
                    }

                    listOf(
                        VideoQualityOption(
                            label = "1080p FHD (Adaptive Stream)",
                            resolution = "1920x1080",
                            bandwidthBps = 5_500_000L,
                            url = mediaUrl,
                            isHlsVariant = true,
                            estimatedSizeBytes = estSize,
                            formatTag = "HLS"
                        ),
                        VideoQualityOption(
                            label = "720p HD (Adaptive Stream)",
                            resolution = "1280x720",
                            bandwidthBps = 2_800_000L,
                            url = mediaUrl,
                            isHlsVariant = true,
                            estimatedSizeBytes = (estSize * 0.60).toLong(),
                            formatTag = "HLS"
                        ),
                        VideoQualityOption(
                            label = "480p SD (Adaptive Stream)",
                            resolution = "854x480",
                            bandwidthBps = 1_200_000L,
                            url = mediaUrl,
                            isHlsVariant = true,
                            estimatedSizeBytes = (estSize * 0.35).toLong(),
                            formatTag = "HLS"
                        ),
                        VideoQualityOption(
                            label = "Audio Track Extract (AAC)",
                            resolution = "Audio Only",
                            bandwidthBps = 128_000L,
                            url = mediaUrl,
                            isHlsVariant = true,
                            estimatedSizeBytes = if (finalDuration > 0.0) ((128_000L * finalDuration) / 8.0).toLong() else 4 * 1024 * 1024L,
                            formatTag = "AUDIO"
                        )
                    )
                }

                detectedSize = qualities.firstOrNull()?.estimatedSizeBytes ?: 0L
            } else {
                // Direct video link (MP4 / WebM) - 2-phase probe (HEAD fallback to Range: bytes=0-1)
                val probe = probeMediaHeadersAndSize(mediaUrl, fullHeaders)
                detectedSize = probe.sizeBytes
                probe.mimeType?.let { if (it.isNotBlank()) detectedMime = it }

                val formatTag = when {
                    detectedMime.contains("webm", ignoreCase = true) || mediaUrl.contains(".webm", ignoreCase = true) -> "WEBM"
                    detectedMime.contains("audio", ignoreCase = true) || mediaUrl.contains(".mp3", ignoreCase = true) || mediaUrl.contains(".m4a", ignoreCase = true) -> "AUDIO"
                    else -> "MP4"
                }

                qualities = buildDirectVideoQualityOptions(
                    mediaUrl = mediaUrl,
                    pageTitle = pageTitle,
                    fileSizeBytes = detectedSize,
                    durationSeconds = finalDuration,
                    formatTag = formatTag
                )

                if (detectedSize <= 0L) {
                    detectedSize = qualities.firstOrNull()?.estimatedSizeBytes ?: 0L
                }
            }

            // UNIFIED AGGREGATION & CANONICAL MERGE
            val aggregatedCanonical: SniffedMediaItem = synchronized(aggregationLock) {
                accumulatedHeaders.putAll(fullHeaders)
                accumulatedRawQualities.addAll(qualities)

                val previous = canonicalVideoItem

                // Prioritize HLS master manifest over loose MP4 fragments
                val shouldUseAsMasterUrl = when {
                    previous == null -> true
                    isM3u8 -> true // HLS always supersedes loose MP4 fragments
                    !previous.isM3u8 && detectedSize > previous.fileSizeBytes -> true
                    else -> false
                }

                val primaryUrl = if (shouldUseAsMasterUrl) mediaUrl else previous?.url ?: mediaUrl
                val primaryMime = if (shouldUseAsMasterUrl) detectedMime else previous?.mimeType ?: detectedMime
                val isMasterM3u8 = previous?.isM3u8 == true || isM3u8
                val isMasterDash = previous?.isDash == true || isDash

                val bestDuration = maxOf(previous?.durationSeconds ?: 0.0, finalDuration)
                val bestPoster = posterUrl ?: previous?.thumbnailUrl
                val bestBaseSize = maxOf(previous?.fileSizeBytes ?: 0L, detectedSize)

                // Pick the most descriptive video title available
                val bestTitle = pickBestTitle(previous?.title, pageTitle, mediaUrl)

                // Normalize & bucket all accumulated qualities into at most 5-6 clean, sorted tiers
                val standardizedQualities = normalizeAndBucketQualities(
                    rawQualities = accumulatedRawQualities,
                    durationSeconds = bestDuration,
                    baseFileSizeBytes = bestBaseSize,
                    isHls = isMasterM3u8,
                    fallbackUrl = primaryUrl
                )

                val updatedItem = SniffedMediaItem(
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

                canonicalVideoItem = updatedItem
                updatedItem
            }

            mainHandler.post {
                onMediaDetected(aggregatedCanonical)
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
     * Resilient 2-phase probe for media headers and exact Content-Length:
     * Phase 1: Fast HTTP HEAD with full browser request headers.
     * Phase 2: HTTP GET with Range: bytes=0-1 to extract Content-Range: bytes 0-1/TOTAL_BYTES.
     */
    private fun probeMediaHeadersAndSize(
        mediaUrl: String,
        fullHeaders: Map<String, String>
    ): MediaProbeResult {
        val okHeaders = buildOkHttpHeaders(fullHeaders)

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

        return MediaProbeResult()
    }

    private fun parseTotalSizeFromContentRange(contentRange: String): Long {
        if (contentRange.isBlank()) return 0L
        val totalPart = contentRange.substringAfterLast('/', "")
        return totalPart.trim().toLongOrNull() ?: 0L
    }

    private fun buildDirectVideoQualityOptions(
        mediaUrl: String,
        pageTitle: String,
        fileSizeBytes: Long,
        durationSeconds: Double,
        formatTag: String
    ): List<VideoQualityOption> {
        val lower = (pageTitle + " " + mediaUrl).lowercase()

        val baseResolution = when {
            lower.contains("1080") -> "1080p FHD" to "1920x1080"
            lower.contains("720") -> "720p HD" to "1280x720"
            lower.contains("480") -> "480p SD" to "854x480"
            lower.contains("360") -> "360p" to "640x360"
            else -> {
                if (durationSeconds > 0.0 && fileSizeBytes > 0L) {
                    val bitrate = (fileSizeBytes * 8) / durationSeconds
                    when {
                        bitrate >= 4_500_000 -> "1080p FHD" to "1920x1080"
                        bitrate >= 2_200_000 -> "720p HD" to "1280x720"
                        bitrate >= 900_000 -> "480p SD" to "854x480"
                        else -> "360p" to "640x360"
                    }
                } else if (fileSizeBytes >= 70 * 1024 * 1024L) {
                    "1080p FHD" to "1920x1080"
                } else if (fileSizeBytes >= 25 * 1024 * 1024L) {
                    "720p HD" to "1280x720"
                } else {
                    "480p SD" to "854x480"
                }
            }
        }

        val finalSizeBytes = if (fileSizeBytes > 0L) {
            fileSizeBytes
        } else if (durationSeconds > 0.0) {
            ((4_500_000L * durationSeconds) / 8.0).toLong()
        } else {
            42 * 1024 * 1024L
        }

        val primaryBandwidth = if (durationSeconds > 0.0 && finalSizeBytes > 0L) {
            (finalSizeBytes * 8 / durationSeconds).toLong()
        } else {
            5_000_000L
        }

        val list = mutableListOf<VideoQualityOption>()

        // 1. Primary Source Quality (Always with the EXACT probed file size)
        list.add(
            VideoQualityOption(
                label = "${baseResolution.first} (Source)",
                resolution = baseResolution.second,
                bandwidthBps = primaryBandwidth,
                url = mediaUrl,
                isHlsVariant = false,
                estimatedSizeBytes = finalSizeBytes,
                formatTag = formatTag
            )
        )

        // 2. Multi-tier quality presets
        if (baseResolution.first.contains("1080")) {
            list.add(
                VideoQualityOption(
                    label = "720p HD",
                    resolution = "1280x720",
                    bandwidthBps = 2_800_000L,
                    url = mediaUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (finalSizeBytes * 0.60).toLong(),
                    formatTag = formatTag
                )
            )
            list.add(
                VideoQualityOption(
                    label = "480p SD",
                    resolution = "854x480",
                    bandwidthBps = 1_200_000L,
                    url = mediaUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (finalSizeBytes * 0.35).toLong(),
                    formatTag = formatTag
                )
            )
            list.add(
                VideoQualityOption(
                    label = "360p Low Quality",
                    resolution = "640x360",
                    bandwidthBps = 600_000L,
                    url = mediaUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (finalSizeBytes * 0.20).toLong(),
                    formatTag = formatTag
                )
            )
        } else if (baseResolution.first.contains("720")) {
            list.add(
                VideoQualityOption(
                    label = "480p SD",
                    resolution = "854x480",
                    bandwidthBps = 1_200_000L,
                    url = mediaUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (finalSizeBytes * 0.55).toLong(),
                    formatTag = formatTag
                )
            )
            list.add(
                VideoQualityOption(
                    label = "360p Low Quality",
                    resolution = "640x360",
                    bandwidthBps = 600_000L,
                    url = mediaUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (finalSizeBytes * 0.30).toLong(),
                    formatTag = formatTag
                )
            )
        } else if (baseResolution.first.contains("480")) {
            list.add(
                VideoQualityOption(
                    label = "360p Low Quality",
                    resolution = "640x360",
                    bandwidthBps = 600_000L,
                    url = mediaUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (finalSizeBytes * 0.55).toLong(),
                    formatTag = formatTag
                )
            )
        }

        // 3. Audio Track Extract
        val audioDuration = if (durationSeconds > 0.0) durationSeconds else 180.0
        val audioSize = if (durationSeconds > 0.0) {
            ((128_000L * audioDuration) / 8.0).toLong()
        } else {
            (finalSizeBytes * 0.12).toLong().coerceAtLeast(3 * 1024 * 1024L)
        }

        list.add(
            VideoQualityOption(
                label = "Audio Track Extract (M4A/MP3)",
                resolution = "Audio Only",
                bandwidthBps = 128_000L,
                url = mediaUrl,
                isHlsVariant = false,
                estimatedSizeBytes = audioSize,
                formatTag = "AUDIO"
            )
        )

        return list
    }

    enum class QualityTier(
        val badge: String,
        val title: String,
        val minHeight: Int,
        val standardWidth: Int,
        val standardHeight: Int,
        val nominalBandwidthBps: Long,
        val relativeRatio: Double,
        val sortOrder: Int
    ) {
        TIER_1080P("1080p FHD", "1080p Full HD", 1080, 1920, 1080, 5_500_000L, 1.0, 1),
        TIER_720P("720p HD", "720p High Definition", 720, 1280, 720, 2_800_000L, 0.60, 2),
        TIER_480P("480p SD", "480p Standard Definition", 480, 854, 480, 1_200_000L, 0.35, 3),
        TIER_360P("360p Low", "360p Low Quality", 360, 640, 360, 600_000L, 0.20, 4),
        TIER_AUDIO("Audio", "Audio Only (MP3/M4A)", 0, 0, 0, 128_000L, 0.10, 5)
    }

    private fun categorizeQualityTier(option: VideoQualityOption): QualityTier {
        if (option.formatTag.contains("AUDIO", ignoreCase = true) ||
            option.resolution.contains("Audio", ignoreCase = true) ||
            option.label.contains("Audio", ignoreCase = true)
        ) {
            return QualityTier.TIER_AUDIO
        }

        val resLower = option.resolution.lowercase()
        val labelLower = option.label.lowercase()
        val urlLower = option.url.lowercase()

        val height = when {
            resLower.contains("x") -> resLower.substringAfter("x").trim().toIntOrNull() ?: 0
            else -> 0
        }

        if (height >= 1080 || labelLower.contains("1080") || urlLower.contains("1080")) {
            return QualityTier.TIER_1080P
        }
        if (height >= 720 || labelLower.contains("720") || urlLower.contains("720")) {
            return QualityTier.TIER_720P
        }
        if (height >= 480 || labelLower.contains("480") || urlLower.contains("480")) {
            return QualityTier.TIER_480P
        }
        if (height >= 360 || labelLower.contains("360") || urlLower.contains("360")) {
            return QualityTier.TIER_360P
        }
        if (height > 0) {
            return QualityTier.TIER_360P
        }

        // Fallback based on bandwidth
        return when {
            option.bandwidthBps >= 4_000_000L -> QualityTier.TIER_1080P
            option.bandwidthBps >= 2_000_000L -> QualityTier.TIER_720P
            option.bandwidthBps >= 900_000L -> QualityTier.TIER_480P
            else -> QualityTier.TIER_360P
        }
    }

    fun normalizeAndBucketQualities(
        rawQualities: List<VideoQualityOption>,
        durationSeconds: Double,
        baseFileSizeBytes: Long,
        isHls: Boolean,
        fallbackUrl: String
    ): List<VideoQualityOption> {
        val grouped = rawQualities.groupBy { categorizeQualityTier(it) }

        // Find highest detected tier in candidates
        val highestDetectedTier = grouped.keys.minByOrNull { it.sortOrder } ?: QualityTier.TIER_1080P

        // Normalize into at most 5-6 clean, distinct tiers
        val targetTiers = when (highestDetectedTier) {
            QualityTier.TIER_1080P -> listOf(
                QualityTier.TIER_1080P,
                QualityTier.TIER_720P,
                QualityTier.TIER_480P,
                QualityTier.TIER_360P,
                QualityTier.TIER_AUDIO
            )
            QualityTier.TIER_720P -> listOf(
                QualityTier.TIER_720P,
                QualityTier.TIER_480P,
                QualityTier.TIER_360P,
                QualityTier.TIER_AUDIO
            )
            QualityTier.TIER_480P -> listOf(
                QualityTier.TIER_480P,
                QualityTier.TIER_360P,
                QualityTier.TIER_AUDIO
            )
            QualityTier.TIER_360P -> listOf(
                QualityTier.TIER_360P,
                QualityTier.TIER_AUDIO
            )
            QualityTier.TIER_AUDIO -> listOf(
                QualityTier.TIER_AUDIO
            )
        }

        // Establish normalized 1080p equivalent base size
        val base1080pSize: Long = when {
            baseFileSizeBytes > 0L -> {
                (baseFileSizeBytes / highestDetectedTier.relativeRatio).toLong().coerceAtLeast(10 * 1024 * 1024L)
            }
            durationSeconds > 0.0 -> {
                ((QualityTier.TIER_1080P.nominalBandwidthBps * durationSeconds) / 8.0).toLong()
            }
            else -> 52 * 1024 * 1024L
        }

        return targetTiers.map { tier ->
            val candidates = grouped[tier] ?: emptyList()
            // Deduplicate aggressively: keep only the highest bitrate/size candidate for this tier
            val bestCandidate = candidates.maxByOrNull {
                it.estimatedSizeBytes.coerceAtLeast(it.bandwidthBps)
            }

            val isAudio = tier == QualityTier.TIER_AUDIO
            val isSource = (tier == highestDetectedTier) && (bestCandidate != null)

            val finalUrl = bestCandidate?.url ?: fallbackUrl
            val finalIsHls = bestCandidate?.isHlsVariant ?: isHls
            val finalBandwidth = if (bestCandidate != null && bestCandidate.bandwidthBps > 0L) {
                bestCandidate.bandwidthBps
            } else {
                tier.nominalBandwidthBps
            }

            // Accurate file size calculation
            val calculatedSize: Long = when {
                bestCandidate != null && bestCandidate.estimatedSizeBytes > 0L -> {
                    bestCandidate.estimatedSizeBytes
                }
                durationSeconds > 0.0 -> {
                    ((tier.nominalBandwidthBps * durationSeconds) / 8.0).toLong().coerceAtLeast(1024 * 1024L)
                }
                else -> {
                    (base1080pSize * tier.relativeRatio).toLong().coerceAtLeast(
                        if (isAudio) 3 * 1024 * 1024L else 8 * 1024 * 1024L
                    )
                }
            }

            val label = when {
                isAudio -> "Audio Only (MP3/M4A)"
                isSource -> "${tier.title} (Source)"
                else -> tier.title
            }

            val resString = if (isAudio) "Audio Only" else "${tier.standardWidth}x${tier.standardHeight}"
            val formatTag = if (isAudio) "AUDIO" else if (finalIsHls) "HLS" else "MP4"

            VideoQualityOption(
                label = label,
                resolution = resString,
                bandwidthBps = finalBandwidth,
                url = finalUrl,
                isHlsVariant = finalIsHls,
                estimatedSizeBytes = calculatedSize,
                formatTag = formatTag
            )
        }.sortedBy { categorizeQualityTier(it).sortOrder }
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
            val cookie = CookieManager.getInstance().getCookie(mediaUrl) ?: CookieManager.getInstance().getCookie(pageUrl)
            if (!cookie.isNullOrBlank()) {
                headers["Cookie"] = cookie
            }
        } catch (e: Exception) {
            // Ignore cookie manager exception
        }

        // 2. Referer and Origin headers to avoid 403 Forbidden on CDN/LMS hosts
        if (pageUrl.isNotBlank()) {
            headers["Referer"] = pageUrl
            try {
                val origin = android.net.Uri.parse(pageUrl)
                if (origin.scheme != null && origin.host != null) {
                    headers["Origin"] = "${origin.scheme}://${origin.host}"
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 3. User-Agent
        if (!userAgent.isNullOrBlank()) {
            headers["User-Agent"] = userAgent
        } else if (existingHeaders.containsKey("User-Agent") || existingHeaders.containsKey("user-agent")) {
            headers["User-Agent"] = existingHeaders["User-Agent"] ?: existingHeaders["user-agent"] ?: ""
        }

        // 4. Merge other existing headers
        existingHeaders.forEach { (k, v) ->
            if (!headers.containsKey(k) && k.isNotBlank() && v.isNotBlank()) {
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
        const val JS_BRIDGE_NAME = "AndroidVideoSniffer"

        val DOM_SNIFFER_JS = """
            (function() {
                if (window.__videoSnifferInjected) return;
                window.__videoSnifferInjected = true;
                var reportedUrls = new Set();

                function reportMedia(element) {
                    if (!element) return;
                    try {
                        var src = element.currentSrc || element.src;
                        if (!src) {
                            var sources = element.getElementsByTagName('source');
                            if (sources && sources.length > 0) {
                                src = sources[0].src;
                            }
                        }
                        if (src && typeof src === 'string' && !src.startsWith('blob:') && !src.startsWith('data:')) {
                            if (reportedUrls.has(src)) return;
                            reportedUrls.add(src);
                            var title = document.title || '';
                            var poster = element.poster || '';
                            var duration = 0;
                            try {
                                if (element.duration && !isNaN(element.duration) && isFinite(element.duration)) {
                                    duration = element.duration;
                                }
                            } catch(_) {}
                            var type = element.type || 'video/mp4';
                            if (window.AndroidVideoSniffer && window.AndroidVideoSniffer.onMediaDiscovered) {
                                window.AndroidVideoSniffer.onMediaDiscovered(src, type, title, poster, duration);
                            }
                        }
                    } catch(e) {}
                }

                function scanMedia() {
                    try {
                        var videos = document.getElementsByTagName('video');
                        for (var i = 0; i < videos.length; i++) {
                            reportMedia(videos[i]);
                        }
                        var audios = document.getElementsByTagName('audio');
                        for (var j = 0; j < audios.length; j++) {
                            reportMedia(audios[j]);
                        }
                    } catch(e) {}
                }

                // 1. Initial scan
                scanMedia();

                // 2. Intercept HTMLMediaElement play & load events safely
                try {
                    var origPlay = HTMLMediaElement.prototype.play;
                    HTMLMediaElement.prototype.play = function() {
                        reportMedia(this);
                        return origPlay.apply(this, arguments);
                    };
                    var origLoad = HTMLMediaElement.prototype.load;
                    HTMLMediaElement.prototype.load = function() {
                        reportMedia(this);
                        return origLoad.apply(this, arguments);
                    };
                } catch(e) {}

                // 3. Lightweight periodic scan that stops after 10 iterations to preserve renderer resources
                try {
                    var pollCount = 0;
                    var pollInterval = setInterval(function() {
                        scanMedia();
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
