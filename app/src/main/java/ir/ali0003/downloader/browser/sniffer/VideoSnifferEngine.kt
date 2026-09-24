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
    private val context: Context? = null,
    private val onMediaDetected: (SniffedMediaItem) -> Unit
) {
    private val appContext: Context?
        get() = context?.applicationContext

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
        specifiedMime: String? = null,
        qualityLabelHint: String? = null,
        resolutionHint: String? = null
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
                // Primary: AndroidX Media3 DownloadHelper for genuine track groups and bitrates
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
                    // Fallback to HlsManifestParser
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
                        listOf(
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

                detectedSize = qualities.firstOrNull()?.estimatedSizeBytes ?: 0L
            } else {
                // Direct video link (MP4 / WebM) - 2-phase probe (HEAD fallback to Range: bytes=0-1)
                val probe = probeMediaHeadersAndSize(mediaUrl, fullHeaders)
                detectedSize = probe.sizeBytes
                probe.mimeType?.let { if (it.isNotBlank()) detectedMime = it }

                val isAudio = detectedMime.contains("audio", ignoreCase = true) ||
                        mediaUrl.contains(".mp3", ignoreCase = true) ||
                        mediaUrl.contains(".m4a", ignoreCase = true)

                // Filter Out Micro-Clips & Thumbnail Previews:
                // Discard background preview MP4s matching common patterns under 1 MB
                val isPreview = isThumbnailOrPreviewUrl(mediaUrl)
                if (!isAudio && isPreview && (detectedSize in 0 until (1024 * 1024L) || (finalDuration in 0.001..9.999))) {
                    return@launch
                }

                // If a full media item already exists on page (>= 1.5 MB or duration >= 10s), discard incoming micro-clips
                val hasExistingFullVideo = synchronized(aggregationLock) {
                    val prev = canonicalVideoItem
                    prev != null && !prev.mimeType.contains("audio", ignoreCase = true) &&
                            (prev.fileSizeBytes >= 1536 * 1024L || prev.durationSeconds >= 10.0)
                }

                if (!isAudio && hasExistingFullVideo && (detectedSize in 1 until (1536 * 1024L) || (finalDuration in 0.001..9.999))) {
                    return@launch
                }

                val formatTag = when {
                    detectedMime.contains("webm", ignoreCase = true) || mediaUrl.contains(".webm", ignoreCase = true) -> "WEBM"
                    isAudio -> "AUDIO"
                    else -> "MP4"
                }

                val singleOption = createGenuineDirectQualityOption(
                    mediaUrl = mediaUrl,
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
                val previous = canonicalVideoItem
                val isAudioItem = detectedMime.contains("audio", ignoreCase = true) ||
                        mediaUrl.contains(".mp3", ignoreCase = true) ||
                        mediaUrl.contains(".m4a", ignoreCase = true)

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
                    detectedSize > previous.fileSizeBytes -> true
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

                // Normalize & bucket all accumulated qualities into clean, genuine, sorted tiers
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
                formatTag = "AUDIO"
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
            formatTag = formatTag
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
                    estimatedSizeBytes = baseFileSizeBytes,
                    formatTag = if (isHls) "HLS" else "MP4"
                )
            )
        }

        // 1. Strict Deduplication by clean URL
        val urlDeduplicated = rawQualities
            .groupBy { it.url.substringBefore('?').substringBefore('#') }
            .mapNotNull { (_, optionsForUrl) ->
                optionsForUrl.maxWithOrNull(
                    compareBy<VideoQualityOption> { if (!it.isHlsVariant) 1 else 0 }
                        .thenBy { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                        .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
                )
            }

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
                val isPreview = isThumbnailOrPreviewUrl(opt.url) || isThumbnailOrPreviewUrl(opt.label)
                isPreview && (opt.estimatedSizeBytes in 0 until (1024 * 1024L))
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
                    !it.label.contains("Direct Stream", ignoreCase = true)
        }

        val cleanStreamList = postClipFilter.filterNot { opt ->
            val isAudio = opt.formatTag.contains("AUDIO", ignoreCase = true) || opt.resolution.contains("Audio", ignoreCase = true)
            if (isAudio) return@filterNot false

            if (hasProgressiveMp4 && opt.isHlsVariant && (opt.label.contains("Direct Stream", ignoreCase = true) || opt.resolution.isBlank())) {
                return@filterNot true
            }

            if (hasNamedVideoOptions && (opt.label.contains("Direct Stream", ignoreCase = true) || opt.resolution.isBlank() || opt.label.equals("Variant Stream", ignoreCase = true))) {
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

        // 5. Group streams by their distinct resolution height (e.g., 1080, 720, 480, 360)
        // and keep the highest-bandwidth stream for each tier.
        val distinctVideoTiers = videoOptions
            .groupBy { it.getResolutionHeight() }
            .mapNotNull { (_, optionsInTier) ->
                optionsInTier.maxWithOrNull(
                    compareBy<VideoQualityOption> { if (!it.isHlsVariant) 1 else 0 }
                        .thenBy { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                        .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
                )
            }

        // 6. Present a clean, descending list from highest resolution to lowest resolution
        val sortedCandidates = distinctVideoTiers.sortedWith(
            compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
                .thenByDescending { it.bandwidthBps }
                .thenByDescending { it.estimatedSizeBytes }
        )

        // Enforce strict size & bandwidth consistency across descending resolutions
        val coherentCandidates = HlsManifestParser.enforceSizeCoherence(sortedCandidates)

        // Never allow identical resolutions with the same file size to render twice
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
            uniqueVideoOptions.add(opt)
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
                        return origPlay.apply(this, arguments);
                    };
                    var origLoad = HTMLMediaElement.prototype.load;
                    HTMLMediaElement.prototype.load = function() {
                        scanElement(this);
                        return origLoad.apply(this, arguments);
                    };
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
