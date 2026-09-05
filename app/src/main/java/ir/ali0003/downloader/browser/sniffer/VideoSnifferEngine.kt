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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VideoSnifferEngine(
    private val onMediaDetected: (SniffedMediaItem) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectedUrls = ConcurrentHashMap.newKeySet<String>()

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
    }

    fun updateCurrentPageInfo(url: String?, title: String?, userAgent: String? = null) {
        if (!url.isNullOrBlank()) currentPageUrl.set(url)
        if (!title.isNullOrBlank()) currentPageTitle.set(title)
        if (!userAgent.isNullOrBlank()) currentUserAgent.set(userAgent)
    }

    /**
     * Intercepts network calls from WebView. Returns null to let WebView continue loading normally,
     * or WebResourceResponse for blocked ads.
     * Note: This method is called on a background thread (ThreadPoolForeg). No WebView instance methods may be called here.
     */
    fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        val url = request.url?.toString() ?: return null

        // 1. Check AdBlock
        if (AdBlockEngine.isAdUrl(url)) {
            // Return empty response to block the ad without error
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

    private fun isMediaUrl(url: String, requestHeaders: Map<String, String>): Boolean {
        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase()

        // Common video and audio file extensions
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

            if (isM3u8) {
                // Parse HLS master playlist
                qualities = HlsManifestParser.fetchAndParseMasterPlaylist(mediaUrl, fullHeaders)
                if (qualities.isEmpty()) {
                    // Fallback to default single stream option
                    qualities = listOf(
                        VideoQualityOption(
                            label = "Auto Adaptive Stream",
                            url = mediaUrl,
                            isHlsVariant = true,
                            formatTag = "HLS M3U8"
                        )
                    )
                }
            } else {
                // Direct video link (e.g., MP4/WebM) - Probe headers for Content-Length and MIME
                try {
                    val headRequest = Request.Builder()
                        .url(mediaUrl)
                        .head()
                        .headers(buildOkHttpHeaders(fullHeaders))
                        .build()

                    httpClient.newCall(headRequest).execute().use { resp ->
                        if (resp.isSuccessful) {
                            detectedSize = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                            resp.header("Content-Type")?.let { detectedMime = it }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore probe error and proceed
                }

                val resolutionLabel = when {
                    mediaUrl.contains("1080", ignoreCase = true) -> "1080p FHD"
                    mediaUrl.contains("720", ignoreCase = true) -> "720p HD"
                    mediaUrl.contains("480", ignoreCase = true) -> "480p SD"
                    else -> "Original Video Quality"
                }

                qualities = listOf(
                    VideoQualityOption(
                        label = resolutionLabel,
                        url = mediaUrl,
                        isHlsVariant = false,
                        estimatedSizeBytes = detectedSize,
                        formatTag = if (detectedMime.contains("webm")) "WEBM" else "MP4"
                    ),
                    VideoQualityOption(
                        label = "Audio Track Extract (M4A/MP3)",
                        url = mediaUrl,
                        isHlsVariant = false,
                        estimatedSizeBytes = if (detectedSize > 0) detectedSize / 5 else 0L,
                        formatTag = "AUDIO"
                    )
                )
            }

            val item = SniffedMediaItem(
                url = mediaUrl,
                pageUrl = pageUrl,
                title = if (pageTitle.isNotBlank()) pageTitle else SniffedMediaItem.extractFileNameFromUrl(mediaUrl),
                mimeType = detectedMime,
                isM3u8 = isM3u8,
                isDash = isDash,
                headers = fullHeaders,
                thumbnailUrl = posterUrl,
                durationSeconds = durationSeconds,
                fileSizeBytes = detectedSize,
                qualities = qualities
            )

            mainHandler.post {
                onMediaDetected(item)
            }
        }
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
                        if (src && !src.startsWith('blob:') && !src.startsWith('data:')) {
                            if (reportedUrls.has(src)) return;
                            reportedUrls.add(src);
                            var title = document.title || '';
                            var poster = element.poster || '';
                            var duration = element.duration || 0;
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

                // 2. Intercept HTMLMediaElement prototypes
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

                // 3. Debounced MutationObserver to avoid CPU thrashing and renderer crashes
                var scanTimeout = null;
                function scheduleScan() {
                    if (scanTimeout) return;
                    scanTimeout = setTimeout(function() {
                        scanTimeout = null;
                        scanMedia();
                    }, 800);
                }

                try {
                    var observer = new MutationObserver(function(mutations) {
                        scheduleScan();
                    });
                    var target = document.body || document.documentElement;
                    if (target) {
                        observer.observe(target, { childList: true, subtree: true });
                    } else {
                        document.addEventListener('DOMContentLoaded', function() {
                            var t = document.body || document.documentElement;
                            if (t) observer.observe(t, { childList: true, subtree: true });
                        });
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
