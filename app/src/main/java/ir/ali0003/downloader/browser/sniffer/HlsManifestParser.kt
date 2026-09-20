package ir.ali0003.downloader.browser.sniffer

import android.net.Uri
import ir.ali0003.downloader.browser.model.VideoQualityOption
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class HlsParseResult(
    val qualities: List<VideoQualityOption>,
    val parsedDurationSeconds: Double
)

private data class RawVariant(
    val url: String,
    val bandwidth: Long,
    val width: Int,
    val height: Int,
    val resolution: String,
    val label: String
)

object HlsManifestParser {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchAndParseMasterPlaylist(
        masterUrl: String,
        headersMap: Map<String, String> = emptyMap(),
        fallbackDurationSeconds: Double = 0.0
    ): HlsParseResult {
        return try {
            val headers = buildHeaders(headersMap)
            val request = Request.Builder()
                .url(masterUrl)
                .headers(headers)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return HlsParseResult(emptyList(), fallbackDurationSeconds)
                }
                val body = response.body?.string() ?: return HlsParseResult(emptyList(), fallbackDurationSeconds)
                parseManifestContentWithDuration(body, masterUrl, headersMap, fallbackDurationSeconds)
            }
        } catch (e: Exception) {
            HlsParseResult(emptyList(), fallbackDurationSeconds)
        }
    }

    fun fetchAndParseMasterPlaylist(
        masterUrl: String,
        headersMap: Map<String, String>
    ): List<VideoQualityOption> = fetchAndParseMasterPlaylist(masterUrl, headersMap, 0.0).qualities

    fun parseManifestContent(manifestText: String, baseUrl: String): List<VideoQualityOption> {
        return parseManifestContentWithDuration(manifestText, baseUrl, emptyMap(), 0.0).qualities
    }

    fun parseManifestContentWithDuration(
        manifestText: String,
        baseUrl: String,
        headersMap: Map<String, String>,
        fallbackDurationSeconds: Double
    ): HlsParseResult {
        if (!manifestText.contains("#EXTM3U")) {
            return HlsParseResult(emptyList(), fallbackDurationSeconds)
        }

        // Case A: Master Playlist containing variant streams (#EXT-X-STREAM-INF)
        if (manifestText.contains("#EXT-X-STREAM-INF:")) {
            val rawVariants = mutableListOf<RawVariant>()
            val reader = BufferedReader(StringReader(manifestText))
            var line: String?
            var lastStreamInf: String? = null

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue

                if (currentLine.startsWith("#EXT-X-STREAM-INF:")) {
                    lastStreamInf = currentLine
                } else if (!currentLine.startsWith("#") && lastStreamInf != null) {
                    val streamUrl = resolveUrl(baseUrl, currentLine)
                    val bandwidth = extractBandwidth(lastStreamInf)
                    val (w, h) = extractResolutionDimensions(lastStreamInf)
                    val maxDim = max(w, h)
                    val minDim = min(w, h)
                    val isFhdOrHigher = minDim >= 1080 || maxDim >= 1920
                    val normalizedW = if (isFhdOrHigher) 1920 else w
                    val normalizedH = if (isFhdOrHigher) 1080 else h
                    val resStr = if (normalizedW > 0 && normalizedH > 0) "${normalizedW}x${normalizedH}" else ""
                    val label = formatQualityLabel(normalizedW, normalizedH, bandwidth)

                    rawVariants.add(
                        RawVariant(
                            url = streamUrl,
                            bandwidth = bandwidth,
                            width = normalizedW,
                            height = normalizedH,
                            resolution = resStr,
                            label = label
                        )
                    )
                    lastStreamInf = null
                }
            }

            if (rawVariants.isEmpty()) {
                return HlsParseResult(emptyList(), fallbackDurationSeconds)
            }

            // Deduplicate: Group by standard resolution tier (1080p, 720p, 480p, 360p)
            // and keep only the single highest bitrate variant per tier.
            val deduplicatedVariants = rawVariants
                .groupBy { variant ->
                    val minD = min(variant.width, variant.height)
                    val maxD = max(variant.width, variant.height)
                    when {
                        minD >= 1080 || maxD >= 1920 || variant.bandwidth >= 4_000_000L -> "1080p"
                        minD >= 720 || maxD >= 1280 || variant.bandwidth >= 2_000_000L -> "720p"
                        minD >= 480 || maxD >= 854 || variant.bandwidth >= 900_000L -> "480p"
                        else -> "360p"
                    }
                }
                .mapNotNull { (_, variantsInTier) ->
                    variantsInTier.maxByOrNull { it.bandwidth }
                }
                .sortedByDescending { it.bandwidth }

            // Read actual #EXTINF segment durations from the media playlist of the first variant
            var actualDuration = 0.0
            val probeVariant = deduplicatedVariants.firstOrNull()
            if (probeVariant != null) {
                actualDuration = fetchMediaPlaylistDuration(probeVariant.url, headersMap)
            }

            val finalDuration = when {
                actualDuration > 0.0 -> actualDuration
                fallbackDurationSeconds > 0.0 -> fallbackDurationSeconds
                else -> 0.0
            }

            val results = mutableListOf<VideoQualityOption>()
            for (variant in deduplicatedVariants) {
                val estimatedSize = if (finalDuration > 0.0 && variant.bandwidth > 0L) {
                    ((variant.bandwidth * finalDuration) / 8.0).toLong()
                } else if (variant.bandwidth > 0L) {
                    // Fallback to standard 3-minute clip (180s)
                    (variant.bandwidth * 180L) / 8L
                } else {
                    35 * 1024 * 1024L
                }

                results.add(
                    VideoQualityOption(
                        label = variant.label,
                        resolution = variant.resolution,
                        bandwidthBps = variant.bandwidth,
                        url = variant.url,
                        isHlsVariant = true,
                        estimatedSizeBytes = estimatedSize,
                        formatTag = "HLS M3U8"
                    )
                )
            }

            // Audio track extract
            if (finalDuration > 0.0 || deduplicatedVariants.isNotEmpty()) {
                val audioDuration = if (finalDuration > 0.0) finalDuration else 180.0
                val audioBandwidth = 128_000L
                val audioSize = ((audioBandwidth * audioDuration) / 8.0).toLong()
                results.add(
                    VideoQualityOption(
                        label = "Audio Track Extract (AAC)",
                        resolution = "Audio Only",
                        bandwidthBps = audioBandwidth,
                        url = deduplicatedVariants.lastOrNull()?.url ?: baseUrl,
                        isHlsVariant = true,
                        estimatedSizeBytes = audioSize,
                        formatTag = "AUDIO"
                    )
                )
            }

            val sortedResults = results.sortedByDescending { it.bandwidthBps }
            return HlsParseResult(sortedResults, finalDuration)
        }

        // Case B: Direct Media Playlist with #EXTINF segments directly
        if (manifestText.contains("#EXTINF:")) {
            val segmentDuration = parseSegmentDurationsFromText(manifestText)
            val finalDuration = when {
                segmentDuration > 0.0 -> segmentDuration
                fallbackDurationSeconds > 0.0 -> fallbackDurationSeconds
                else -> 180.0
            }

            val qualityList = listOf(
                VideoQualityOption(
                    label = "1080p FHD (Adaptive Stream)",
                    resolution = "1920x1080",
                    bandwidthBps = 5_000_000L,
                    url = baseUrl,
                    isHlsVariant = true,
                    estimatedSizeBytes = ((5_000_000L * finalDuration) / 8.0).toLong(),
                    formatTag = "HLS M3U8"
                ),
                VideoQualityOption(
                    label = "720p HD (Adaptive Stream)",
                    resolution = "1280x720",
                    bandwidthBps = 2_800_000L,
                    url = baseUrl,
                    isHlsVariant = true,
                    estimatedSizeBytes = ((2_800_000L * finalDuration) / 8.0).toLong(),
                    formatTag = "HLS M3U8"
                ),
                VideoQualityOption(
                    label = "480p SD (Adaptive Stream)",
                    resolution = "854x480",
                    bandwidthBps = 1_200_000L,
                    url = baseUrl,
                    isHlsVariant = true,
                    estimatedSizeBytes = ((1_200_000L * finalDuration) / 8.0).toLong(),
                    formatTag = "HLS M3U8"
                ),
                VideoQualityOption(
                    label = "360p Low (Adaptive Stream)",
                    resolution = "640x360",
                    bandwidthBps = 600_000L,
                    url = baseUrl,
                    isHlsVariant = true,
                    estimatedSizeBytes = ((600_000L * finalDuration) / 8.0).toLong(),
                    formatTag = "HLS M3U8"
                ),
                VideoQualityOption(
                    label = "Audio Track Extract (AAC)",
                    resolution = "Audio Only",
                    bandwidthBps = 128_000L,
                    url = baseUrl,
                    isHlsVariant = true,
                    estimatedSizeBytes = ((128_000L * finalDuration) / 8.0).toLong(),
                    formatTag = "AUDIO"
                )
            )
            return HlsParseResult(qualityList, finalDuration)
        }

        return HlsParseResult(emptyList(), fallbackDurationSeconds)
    }

    private fun fetchMediaPlaylistDuration(
        playlistUrl: String,
        headersMap: Map<String, String>
    ): Double {
        return try {
            val headers = buildHeaders(headersMap)
            val request = Request.Builder()
                .url(playlistUrl)
                .headers(headers)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return 0.0
                val body = response.body?.string() ?: return 0.0
                parseSegmentDurationsFromText(body)
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseSegmentDurationsFromText(manifestText: String): Double {
        var durationSum = 0.0
        val regex = """#EXTINF:([\d.]+)""".toRegex()
        manifestText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                regex.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                    durationSum += it
                }
            }
        }
        return durationSum
    }

    private fun extractBandwidth(streamInf: String): Long {
        val regex = "BANDWIDTH=(\\d+)".toRegex()
        val match = regex.find(streamInf)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun extractResolutionDimensions(streamInf: String): Pair<Int, Int> {
        val regex = "RESOLUTION=(\\d+)x(\\d+)".toRegex()
        val match = regex.find(streamInf) ?: return Pair(0, 0)
        val w = match.groupValues[1].toIntOrNull() ?: 0
        val h = match.groupValues[2].toIntOrNull() ?: 0
        return Pair(w, h)
    }

    private fun formatQualityLabel(width: Int, height: Int, bandwidth: Long): String {
        val maxDim = max(width, height)
        val minDim = min(width, height)

        val quality = when {
            maxDim >= 1920 || minDim >= 1080 -> "1080p FHD"
            maxDim >= 1280 || minDim >= 720 -> "720p HD"
            maxDim >= 854 || minDim >= 480 -> "480p SD"
            maxDim >= 640 || minDim >= 360 -> "360p"
            minDim > 0 -> "${minDim}p"
            bandwidth >= 4_500_000 -> "1080p FHD"
            bandwidth >= 2_200_000 -> "720p HD"
            bandwidth >= 900_000 -> "480p SD"
            bandwidth >= 400_000 -> "360p"
            else -> "Adaptive Stream"
        }

        val resStr = if (width > 0 && height > 0) "${width}x${height}" else ""
        return if (resStr.isNotEmpty()) "$quality ($resStr)" else quality
    }

    private fun buildHeaders(headersMap: Map<String, String>): Headers {
        val headersBuilder = Headers.Builder()
        headersMap.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                try {
                    headersBuilder.add(k, v)
                } catch (_: Exception) {}
            }
        }
        return headersBuilder.build()
    }

    fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }

        return try {
            val baseUri = Uri.parse(baseUrl)
            if (relativeOrAbsolute.startsWith("/")) {
                "${baseUri.scheme}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$relativeOrAbsolute"
            } else {
                val lastSlash = baseUrl.lastIndexOf('/')
                val baseDir = if (lastSlash != -1) baseUrl.substring(0, lastSlash + 1) else "$baseUrl/"
                "$baseDir$relativeOrAbsolute"
            }
        } catch (e: Exception) {
            relativeOrAbsolute
        }
    }
}
