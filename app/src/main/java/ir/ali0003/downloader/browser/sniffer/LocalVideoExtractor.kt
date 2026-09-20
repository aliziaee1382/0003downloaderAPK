package ir.ali0003.downloader.browser.sniffer

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class ExtractedVideoResult {
    data class Success(
        val originalUrl: String,
        val title: String,
        val durationSeconds: Double,
        val thumbnailUrl: String?,
        val rawFormats: List<VideoFormat>,
        val qualityOptions: List<VideoQualityOption>,
        val sniffedMediaItem: SniffedMediaItem
    ) : ExtractedVideoResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ExtractedVideoResult()
}

/**
 * High-performance Coroutine-based Local Video Extractor powered by native yt-dlp/youtubedl-android.
 * Extracts metadata, duration, thumbnails, and buckets formats into standard tiers.
 */
object LocalVideoExtractor {

    private const val TAG = "LocalVideoExtractor"
    private const val EXTRACTION_TIMEOUT_MS = 25_000L

    suspend fun extractMediaInfo(url: String): ExtractedVideoResult = withContext(Dispatchers.IO) {
        if (!shouldAttemptExtraction(url)) {
            return@withContext ExtractedVideoResult.Error("URL is not an individual media page")
        }

        try {
            val result = withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
                runExtraction(url)
            }
            result ?: ExtractedVideoResult.Error("Extraction timed out after ${EXTRACTION_TIMEOUT_MS / 1000} seconds")
        } catch (e: Exception) {
            val isUnsupported = e.message?.contains("Unsupported URL", ignoreCase = true) == true
            if (isUnsupported) {
                Log.d(TAG, "No native video extractor supported for: $url")
            } else {
                Log.w(TAG, "Extraction not available for $url: ${e.message}")
            }
            ExtractedVideoResult.Error(
                message = e.localizedMessage ?: "Failed to extract media information",
                cause = e
            )
        }
    }

    fun shouldAttemptExtraction(url: String): Boolean {
        if (url.isBlank() || !url.startsWith("http")) return false
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""

        // Skip search engines
        if (host.contains("google.") || host.contains("bing.") || host.contains("duckduckgo.") || host.contains("yahoo.")) {
            return false
        }

        // Skip root/home browsing pages
        if (path.isEmpty() || path == "/" || path == "/home" || path == "/home/" || path == "/index.html" || path == "/explore" || path == "/feed") {
            return false
        }

        return true
    }

    private fun runExtraction(url: String): ExtractedVideoResult {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--socket-timeout", "15")
            addOption("--no-update")
            addOption("--no-warnings")
        }

        val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
        val title = info.title?.takeIf { it.isNotBlank() } ?: "Extracted Video"
        val duration = info.duration.toDouble().coerceAtLeast(0.0)
        val thumbnail = info.thumbnail
        val rawFormats = info.formats ?: emptyList()

        val qualityOptions = bucketFormatsIntoTiers(
            formats = rawFormats,
            durationSeconds = duration,
            webpageUrl = url
        )

        val bestOption = qualityOptions.firstOrNull()
        val sniffedItem = SniffedMediaItem(
            url = bestOption?.url ?: url,
            pageUrl = url,
            title = title,
            mimeType = if (bestOption?.formatTag?.contains("AUDIO", ignoreCase = true) == true) "audio/mpeg" else "video/mp4",
            thumbnailUrl = thumbnail,
            durationSeconds = duration,
            fileSizeBytes = bestOption?.estimatedSizeBytes ?: 0L,
            qualities = qualityOptions
        )

        return ExtractedVideoResult.Success(
            originalUrl = url,
            title = title,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            rawFormats = rawFormats,
            qualityOptions = qualityOptions,
            sniffedMediaItem = sniffedItem
        )
    }

    /**
     * Calculate exact file size in bytes using format.fileSize or (tbr * duration * 1024) / 8.
     */
    fun calculateFormatSizeBytes(format: VideoFormat, durationSeconds: Double): Long {
        if (format.fileSize > 0L) {
            return format.fileSize
        }
        val tbr = format.tbr.toDouble()
        if (tbr > 0.0 && durationSeconds > 0.0) {
            return ((tbr * durationSeconds * 1024.0) / 8.0).toLong()
        }
        return 0L
    }

    /**
     * Automatically filters and buckets available video formats into standard tiers:
     * - 1080p FHD
     * - 720p HD
     * - 480p SD
     * - 360p
     * - Audio Only (M4A / MP3)
     */
    fun bucketFormatsIntoTiers(
        formats: List<VideoFormat>,
        durationSeconds: Double,
        webpageUrl: String
    ): List<VideoQualityOption> {
        val options = mutableListOf<VideoQualityOption>()

        // Find best audio format for audio size addition and audio-only tier
        val audioFormats = formats.filter { format ->
            format.vcodec == "none" || (format.height <= 0 && !format.acodec.isNullOrBlank() && format.acodec != "none")
        }
        val bestAudioFormat = audioFormats.maxByOrNull { calculateFormatSizeBytes(it, durationSeconds) }
        val bestAudioSizeBytes = bestAudioFormat?.let { calculateFormatSizeBytes(it, durationSeconds) } ?: 0L

        // Video formats with valid height
        val videoFormats = formats.filter { format ->
            format.height > 0 && format.vcodec != "none"
        }

        // Tier 1: 1080p FHD (height >= 1080)
        val fhdFormat = videoFormats
            .filter { it.height >= 1080 }
            .maxByOrNull { it.height * 10000 + (it.tbr.toInt()) }
        if (fhdFormat != null) {
            options.add(createTierOption(fhdFormat, "1080p Full HD", "1080p FHD", durationSeconds, bestAudioSizeBytes, webpageUrl))
        }

        // Tier 2: 720p HD (height in 720..1079)
        val hdFormat = videoFormats
            .filter { it.height in 720..1079 }
            .maxByOrNull { it.height * 10000 + (it.tbr.toInt()) }
        if (hdFormat != null) {
            options.add(createTierOption(hdFormat, "720p High Definition", "720p HD", durationSeconds, bestAudioSizeBytes, webpageUrl))
        }

        // Tier 3: 480p SD (height in 480..719)
        val sdFormat = videoFormats
            .filter { it.height in 480..719 }
            .maxByOrNull { it.height * 10000 + (it.tbr.toInt()) }
        if (sdFormat != null) {
            options.add(createTierOption(sdFormat, "480p Standard Definition", "480p SD", durationSeconds, bestAudioSizeBytes, webpageUrl))
        }

        // Tier 4: 360p (height in 360..479 or any lower video format)
        val lowFormat = videoFormats
            .filter { it.height in 360..479 }
            .maxByOrNull { it.height * 10000 + (it.tbr.toInt()) }
            ?: videoFormats.filter { it.height < 360 }.maxByOrNull { it.height }
        if (lowFormat != null) {
            options.add(createTierOption(lowFormat, "360p Mobile Quality", "360p", durationSeconds, bestAudioSizeBytes, webpageUrl))
        }

        // Fallback: If no standard tiers matched (e.g. non-standard format list or direct single format)
        if (options.isEmpty() && videoFormats.isNotEmpty()) {
            val bestFallback = videoFormats.maxByOrNull { it.height } ?: videoFormats.first()
            options.add(createTierOption(bestFallback, "${bestFallback.height}p Video", "${bestFallback.height}p", durationSeconds, bestAudioSizeBytes, webpageUrl))
        }

        // Tier 5: Audio Only (M4A / MP3)
        if (bestAudioFormat != null) {
            val audioSize = calculateFormatSizeBytes(bestAudioFormat, durationSeconds)
            val formatId = bestAudioFormat.formatId ?: "bestaudio/best"
            val ext = bestAudioFormat.ext ?: "m4a"
            val tag = if (ext.equals("mp3", ignoreCase = true)) "MP3 AUDIO" else "M4A AUDIO"

            options.add(
                VideoQualityOption(
                    label = "Audio Only (${ext.uppercase()})",
                    resolution = "Audio",
                    bandwidthBps = (bestAudioFormat.tbr.toDouble() * 1000.0).toLong(),
                    url = bestAudioFormat.url ?: webpageUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = if (audioSize > 0L) audioSize else (durationSeconds * 16_000).toLong().coerceAtLeast(3_500_000L),
                    formatTag = tag,
                    formatId = formatId,
                    isYoutubeDl = true
                )
            )
        } else if (durationSeconds > 0.0 || formats.isNotEmpty()) {
            // Generic Audio Option fallback
            options.add(
                VideoQualityOption(
                    label = "Audio Only (MP3)",
                    resolution = "Audio",
                    bandwidthBps = 128_000L,
                    url = webpageUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = (durationSeconds * 16_000).toLong().coerceAtLeast(3_500_000L),
                    formatTag = "MP3 AUDIO",
                    formatId = "bestaudio/best",
                    isYoutubeDl = true
                )
            )
        }

        // Fallback if completely empty
        if (options.isEmpty()) {
            options.add(
                VideoQualityOption(
                    label = "1080p Full HD (Best)",
                    resolution = "1920x1080",
                    bandwidthBps = 4_500_000L,
                    url = webpageUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = 45 * 1024 * 1024L,
                    formatTag = "MP4",
                    formatId = "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
                    isYoutubeDl = true
                )
            )
            options.add(
                VideoQualityOption(
                    label = "720p HD",
                    resolution = "1280x720",
                    bandwidthBps = 2_500_000L,
                    url = webpageUrl,
                    isHlsVariant = false,
                    estimatedSizeBytes = 22 * 1024 * 1024L,
                    formatTag = "MP4",
                    formatId = "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
                    isYoutubeDl = true
                )
            )
        }

        return options
    }

    private fun createTierOption(
        format: VideoFormat,
        label: String,
        badge: String,
        durationSeconds: Double,
        bestAudioSizeBytes: Long,
        webpageUrl: String
    ): VideoQualityOption {
        var calculatedSize = calculateFormatSizeBytes(format, durationSeconds)
        val isVideoOnly = format.vcodec != "none" && (format.acodec == null || format.acodec == "none")

        // If video-only stream, merge format id with bestaudio, and add audio size
        val formatSelector = if (isVideoOnly && !format.formatId.isNullOrBlank()) {
            "${format.formatId}+bestaudio/best"
        } else {
            format.formatId ?: "best"
        }

        if (isVideoOnly && bestAudioSizeBytes > 0L) {
            calculatedSize += bestAudioSizeBytes
        }

        val resolutionStr = if (format.width > 0 && format.height > 0) {
            "${format.width}x${format.height}"
        } else if (format.height > 0) {
            "${format.height}p"
        } else {
            badge
        }

        val bandwidth = (format.tbr.toDouble() * 1000.0).toLong()

        return VideoQualityOption(
            label = label,
            resolution = resolutionStr,
            bandwidthBps = bandwidth,
            url = format.url ?: webpageUrl,
            isHlsVariant = false,
            estimatedSizeBytes = calculatedSize,
            formatTag = (format.ext ?: "MP4").uppercase(),
            formatId = formatSelector,
            isYoutubeDl = true
        )
    }
}
