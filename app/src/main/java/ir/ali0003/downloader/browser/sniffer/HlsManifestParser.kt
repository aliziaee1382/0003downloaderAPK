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

                // If body is a Media Playlist (#EXTINF directly, no #EXT-X-STREAM-INF), probe candidate parent master playlists
                if (body.contains("#EXTM3U") && !body.contains("#EXT-X-STREAM-INF:") && body.contains("#EXTINF:")) {
                    val probedMaster = probeParentMasterPlaylist(masterUrl, headersMap, fallbackDurationSeconds)
                    if (probedMaster != null && probedMaster.qualities.isNotEmpty()) {
                        return probedMaster
                    }
                }

                parseManifestContentWithDuration(body, masterUrl, headersMap, fallbackDurationSeconds)
            }
        } catch (e: Exception) {
            HlsParseResult(emptyList(), fallbackDurationSeconds)
        }
    }

    /**
     * Probes candidate parent master playlists (e.g. master.m3u8, index.m3u8, playlist.m3u8)
     * when a sub-variant media playlist was intercepted.
     */
    private fun probeParentMasterPlaylist(
        subVariantUrl: String,
        headersMap: Map<String, String>,
        fallbackDurationSeconds: Double
    ): HlsParseResult? {
        val candidates = generateCandidateMasterUrls(subVariantUrl)
        val headers = buildHeaders(headersMap)

        for (candidateUrl in candidates) {
            try {
                val request = Request.Builder()
                    .url(candidateUrl)
                    .headers(headers)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null && body.contains("#EXTM3U") && body.contains("#EXT-X-STREAM-INF:")) {
                            val parsed = parseManifestContentWithDuration(body, candidateUrl, headersMap, fallbackDurationSeconds)
                            if (parsed.qualities.isNotEmpty()) {
                                return parsed
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue checking other candidates
            }
        }
        return null
    }

    private fun generateCandidateMasterUrls(variantUrl: String): List<String> {
        val candidates = mutableListOf<String>()
        try {
            val uri = Uri.parse(variantUrl)
            val path = uri.path ?: return emptyList()
            val lastSlash = path.lastIndexOf('/')
            val parentPath = if (lastSlash != -1) path.substring(0, lastSlash) else ""
            val grandParentPath = if (parentPath.lastIndexOf('/') != -1) parentPath.substring(0, parentPath.lastIndexOf('/')) else ""

            val schemeAndHost = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
            val queryStr = if (!uri.query.isNullOrBlank()) "?${uri.query}" else ""

            val standardMasterFilenames = listOf("master.m3u8", "index.m3u8", "playlist.m3u8")

            // In parent directory
            for (name in standardMasterFilenames) {
                val candidate = "$schemeAndHost$parentPath/$name$queryStr"
                if (candidate != variantUrl && !candidates.contains(candidate)) {
                    candidates.add(candidate)
                }
            }

            // In grand-parent directory if nested (e.g. /hls/720p/index.m3u8 -> /hls/master.m3u8)
            if (grandParentPath.isNotEmpty()) {
                for (name in standardMasterFilenames) {
                    val candidate = "$schemeAndHost$grandParentPath/$name$queryStr"
                    if (candidate != variantUrl && !candidates.contains(candidate)) {
                        candidates.add(candidate)
                    }
                }
            }

            // Regex replacements for subvariant patterns:
            // e.g. hls_250p.m3u8 -> master.m3u8 or index.m3u8
            // 720p.m3u8 -> master.m3u8
            // video_360.m3u8 -> master.m3u8
            val fileName = path.substringAfterLast('/')
            if (fileName.contains(Regex("""(hls_)?\d+p?""", RegexOption.IGNORE_CASE))) {
                for (name in standardMasterFilenames) {
                    val replaced = variantUrl.replace(fileName, name)
                    if (replaced != variantUrl && !candidates.contains(replaced)) {
                        candidates.add(replaced)
                    }
                }
            }
        } catch (_: Exception) {}
        return candidates
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
                    val resStr = if (w > 0 && h > 0) "${w}x${h}" else ""
                    val label = formatQualityLabel(w, h, bandwidth)

                    rawVariants.add(
                        RawVariant(
                            url = streamUrl,
                            bandwidth = bandwidth,
                            width = w,
                            height = h,
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

            // Retain genuine server-provided variants:
            // 1. Filter out preview/teaser variants if standard variants exist
            // 2. Hide unlabeled intermediate playlist chunks if named variants exist
            // 3. Deduplicate variants sharing the same resolution or clean URL, keeping highest bandwidth
            val hasNamedVariants = rawVariants.any { it.resolution.isNotEmpty() }

            val deduplicatedVariants = rawVariants
                .filterNot { variant ->
                    val isPreview = variant.url.contains("preview", ignoreCase = true) ||
                            variant.url.contains("thumb", ignoreCase = true) ||
                            variant.label.contains("preview", ignoreCase = true)
                    isPreview && rawVariants.any { !it.url.contains("preview", ignoreCase = true) }
                }
                .filterNot { variant ->
                    hasNamedVariants && (variant.resolution.isEmpty() || variant.label.equals("Variant Stream", ignoreCase = true))
                }
                .groupBy { variant ->
                    val cleanUrl = variant.url.substringBefore('?').substringBefore('#')
                    if (variant.resolution.isNotEmpty()) variant.resolution else cleanUrl
                }
                .mapNotNull { (_, variantsInGroup) ->
                    variantsInGroup.maxByOrNull { it.bandwidth }
                }
                .distinctBy { variant ->
                    val cleanUrl = variant.url.substringBefore('?').substringBefore('#')
                    if (variant.resolution.isNotEmpty()) variant.resolution else cleanUrl
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
                } else {
                    0L
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

            val sortedResults = results.sortedByDescending { it.bandwidthBps }
            return HlsParseResult(sortedResults, finalDuration)
        }

        // Case B: Direct Media Playlist with #EXTINF segments directly
        if (manifestText.contains("#EXTINF:")) {
            val segmentDuration = parseSegmentDurationsFromText(manifestText)
            val finalDuration = when {
                segmentDuration > 0.0 -> segmentDuration
                fallbackDurationSeconds > 0.0 -> fallbackDurationSeconds
                else -> 0.0
            }

            // Calculate exact total size of actual segments
            val segmentCount = countSegments(manifestText)
            val byteRangeTotal = parseByteRangeTotalSize(manifestText)

            val calculatedTotalSize = when {
                byteRangeTotal > 0L -> byteRangeTotal
                segmentCount > 0 -> {
                    val firstSegUrl = extractFirstSegmentUrl(manifestText, baseUrl)
                    val firstSegSize = if (firstSegUrl.isNotBlank()) probeSegmentSize(firstSegUrl, headersMap) else 0L
                    if (firstSegSize > 0L) firstSegSize * segmentCount else 0L
                }
                else -> 0L
            }

            // Probe or infer actual video resolution from URL / manifest text
            val (detectedRes, detectedLabel) = inferMediaPlaylistResolution(baseUrl, manifestText)

            val calculatedBandwidth = if (finalDuration > 0.0 && calculatedTotalSize > 0L) {
                (calculatedTotalSize * 8 / finalDuration).toLong()
            } else {
                0L
            }

            val singleTier = VideoQualityOption(
                label = detectedLabel,
                resolution = detectedRes,
                bandwidthBps = calculatedBandwidth,
                url = baseUrl,
                isHlsVariant = true,
                estimatedSizeBytes = calculatedTotalSize,
                formatTag = "HLS M3U8"
            )

            return HlsParseResult(listOf(singleTier), finalDuration)
        }

        return HlsParseResult(emptyList(), fallbackDurationSeconds)
    }

    private fun countSegments(manifestText: String): Int {
        var count = 0
        manifestText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                count++
            }
        }
        return count
    }

    private fun parseByteRangeTotalSize(manifestText: String): Long {
        var total = 0L
        val regex = """#EXT-X-BYTERANGE:(\d+)""".toRegex()
        manifestText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-BYTERANGE:")) {
                regex.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()?.let {
                    total += it
                }
            }
        }
        return total
    }

    private fun extractFirstSegmentUrl(manifestText: String, baseUrl: String): String {
        val lines = manifestText.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:") && i + 1 < lines.size) {
                for (j in (i + 1) until lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        return resolveUrl(baseUrl, nextLine)
                    }
                }
            }
        }
        return ""
    }

    private fun probeSegmentSize(segmentUrl: String, headersMap: Map<String, String>): Long {
        return try {
            val headers = buildHeaders(headersMap)
            val request = Request.Builder()
                .url(segmentUrl)
                .head()
                .headers(headers)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.header("Content-Length")?.toLongOrNull() ?: 0L
                } else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun inferMediaPlaylistResolution(baseUrl: String, manifestText: String): Pair<String, String> {
        val combined = (baseUrl + " " + manifestText.take(500)).lowercase()

        // Check dimension patterns: e.g. 1920x1080, 1280x720, 640x360, 426x240
        val dimRegex = """(\d{3,4})x(\d{3,4})""".toRegex()
        val dimMatch = dimRegex.find(combined)
        if (dimMatch != null) {
            val w = dimMatch.groupValues[1].toIntOrNull() ?: 0
            val h = dimMatch.groupValues[2].toIntOrNull() ?: 0
            if (w > 0 && h > 0) {
                val label = formatQualityLabel(w, h, 0L)
                return Pair("${w}x${h}", label)
            }
        }

        // Check height patterns: e.g. 2160p, 1440p, 1080p, 720p, 480p, 360p, 250p, 240p
        val pRegex = """(?:hls_|video_|_|/)(\d{3,4})p\b""".toRegex()
        val pMatch = pRegex.find(combined)
        if (pMatch != null) {
            val h = pMatch.groupValues[1].toIntOrNull() ?: 0
            if (h > 0) {
                val label = formatQualityLabel(0, h, 0L)
                return Pair("${h}p", label)
            }
        }

        return Pair("", "Direct Stream")
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
            maxDim >= 3840 || minDim >= 2160 -> "4K UHD"
            maxDim >= 2560 || minDim >= 1440 -> "1440p 2K"
            maxDim >= 1920 || minDim >= 1080 -> "1080p FHD"
            maxDim >= 1280 || minDim >= 720 -> "720p HD"
            maxDim >= 854 || minDim >= 480 -> "480p SD"
            maxDim >= 640 || minDim >= 360 -> "360p"
            minDim > 0 -> "${minDim}p"
            bandwidth >= 12_000_000L -> "4K UHD"
            bandwidth >= 7_000_000L -> "1440p 2K"
            bandwidth >= 4_500_000L -> "1080p FHD"
            bandwidth >= 2_200_000L -> "720p HD"
            bandwidth >= 900_000L -> "480p SD"
            bandwidth >= 400_000L -> "360p"
            bandwidth > 0L -> "${bandwidth / 1000} kbps"
            else -> "Variant Stream"
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
