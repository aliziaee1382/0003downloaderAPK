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
        fallbackDurationSeconds: Double = 0.0,
        networkLogs: List<String> = emptyList()
    ): HlsParseResult {
        return try {
            val headers = buildHeaders(headersMap)
            val request = Request.Builder()
                .url(masterUrl)
                .headers(headers)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val probed = probeParentMasterPlaylist(masterUrl, headersMap, fallbackDurationSeconds, networkLogs)
                    return probed ?: HlsParseResult(emptyList(), fallbackDurationSeconds)
                }
                val body = response.body?.string() ?: return HlsParseResult(emptyList(), fallbackDurationSeconds)

                // 1. Master Playlist Priority: If manifest contains #EXT-X-STREAM-INF, parse ALL variant streams
                if (body.contains("#EXTM3U") && body.contains("#EXT-X-STREAM-INF:")) {
                    return parseManifestContentWithDuration(body, masterUrl, headersMap, fallbackDurationSeconds)
                }

                // 2. Sub-playlist (Media Playlist without #EXT-X-STREAM-INF):
                // Do NOT settle for single-tier detection. Strip rendition-specific URL path segments/parameters
                // or query browser network logs to fetch the parent Master Manifest.
                val probedMaster = probeParentMasterPlaylist(masterUrl, headersMap, fallbackDurationSeconds, networkLogs)
                if (probedMaster != null && probedMaster.qualities.isNotEmpty()) {
                    return probedMaster
                }

                // Fallback to parsing single media playlist
                parseManifestContentWithDuration(body, masterUrl, headersMap, fallbackDurationSeconds)
            }
        } catch (e: Exception) {
            val probed = probeParentMasterPlaylist(masterUrl, headersMap, fallbackDurationSeconds, networkLogs)
            probed ?: HlsParseResult(emptyList(), fallbackDurationSeconds)
        }
    }

    /**
     * Probes candidate parent master playlists by querying browser network logs
     * and stripping rendition-specific URL path segments/parameters.
     */
    private fun probeParentMasterPlaylist(
        subVariantUrl: String,
        headersMap: Map<String, String>,
        fallbackDurationSeconds: Double,
        networkLogs: List<String> = emptyList()
    ): HlsParseResult? {
        val candidates = generateCandidateMasterUrls(subVariantUrl, networkLogs)
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

    fun generateCandidateMasterUrls(variantUrl: String, networkLogs: List<String> = emptyList()): List<String> {
        val candidates = mutableListOf<String>()
        val uri = try { Uri.parse(variantUrl) } catch (_: Exception) { null } ?: return emptyList()
        val host = uri.host ?: return emptyList()

        // 1. Query browser network logs for candidate master manifests from the same host
        val logM3u8s = networkLogs.filter { logUrl ->
            logUrl != variantUrl && logUrl.contains(".m3u8", ignoreCase = true)
        }
        val highPriorityLogUrls = logM3u8s.filter { logUrl ->
            try {
                val logUri = Uri.parse(logUrl)
                logUri.host.equals(host, ignoreCase = true) &&
                        (logUrl.contains("master", ignoreCase = true) ||
                         logUrl.contains("playlist", ignoreCase = true) ||
                         logUrl.contains("manifest", ignoreCase = true) ||
                         logUrl.contains("main", ignoreCase = true))
            } catch (_: Exception) { false }
        }
        candidates.addAll(highPriorityLogUrls)

        val otherSameHostLogUrls = logM3u8s.filter { logUrl ->
            try {
                val logUri = Uri.parse(logUrl)
                logUri.host.equals(host, ignoreCase = true) && !candidates.contains(logUrl)
            } catch (_: Exception) { false }
        }
        candidates.addAll(otherSameHostLogUrls)

        val path = uri.path ?: ""
        val schemeAndHost = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        val queryStr = if (!uri.query.isNullOrBlank()) "?${uri.query}" else ""

        // 2. Strip rendition-specific query parameters (e.g. ?quality=720, ?resolution=720p, ?rendition=720)
        if (!uri.query.isNullOrBlank()) {
            val strippedParams = uri.queryParameterNames.filterNot { name ->
                val lower = name.lowercase()
                lower in listOf("quality", "resolution", "res", "rendition", "height", "width", "bandwidth", "bw", "tier", "track", "level", "profile", "format")
            }
            if (strippedParams.size < uri.queryParameterNames.size) {
                val newQuery = strippedParams.joinToString("&") { key ->
                    "$key=${uri.getQueryParameter(key)}"
                }
                val cleanedQueryUrl = "$schemeAndHost$path${if (newQuery.isNotBlank()) "?$newQuery" else ""}"
                if (cleanedQueryUrl != variantUrl && !candidates.contains(cleanedQueryUrl)) {
                    candidates.add(cleanedQueryUrl)
                }
            }
            val noQueryUrl = "$schemeAndHost$path"
            if (noQueryUrl != variantUrl && !candidates.contains(noQueryUrl)) {
                candidates.add(noQueryUrl)
            }
        }

        // 3. Strip rendition-specific URL path segments
        // e.g. /video/720p/index.m3u8 -> /video/index.m3u8, /video/master.m3u8
        // e.g. /hls/1080p/playlist.m3u8 -> /hls/playlist.m3u8, /hls/master.m3u8
        val renditionSegmentRegex = """/(1080p?|720p?|480p?|360p?|240p?|1440p?|2160p?|4kp?|2kp?|hls_\d+p?|video_\d+p?|stream_\d+p?|tracks-[^/]+)/""".toRegex(RegexOption.IGNORE_CASE)
        if (renditionSegmentRegex.containsMatchIn(path)) {
            val strippedPath = renditionSegmentRegex.replace(path, "/")
            val candidate = "$schemeAndHost$strippedPath$queryStr"
            if (candidate != variantUrl && !candidates.contains(candidate)) {
                candidates.add(candidate)
            }
            val strippedParent = strippedPath.substringBeforeLast('/')
            for (mName in listOf("master.m3u8", "playlist.m3u8", "index.m3u8")) {
                val c = "$schemeAndHost$strippedParent/$mName$queryStr"
                if (c != variantUrl && !candidates.contains(c)) {
                    candidates.add(c)
                }
            }
        }

        // 4. Filename replacements for subvariant patterns
        // e.g. 720p.m3u8 -> master.m3u8, playlist.m3u8, index.m3u8
        // e.g. hls_250p.m3u8 -> master.m3u8
        // e.g. video_360.m3u8 -> master.m3u8
        val fileName = path.substringAfterLast('/')
        val standardMasterFilenames = listOf("master.m3u8", "playlist.m3u8", "index.m3u8", "manifest.m3u8")

        if (fileName.contains(Regex("""(hls_|video_|stream_|chunklist_)?\d+p?|chunklist""", RegexOption.IGNORE_CASE))) {
            for (name in standardMasterFilenames) {
                val replaced = variantUrl.replace(fileName, name)
                if (replaced != variantUrl && !candidates.contains(replaced)) {
                    candidates.add(replaced)
                }
            }
        }

        // 5. Parent and grandparent directories
        val lastSlash = path.lastIndexOf('/')
        val parentPath = if (lastSlash != -1) path.substring(0, lastSlash) else ""
        val grandParentPath = if (parentPath.lastIndexOf('/') != -1) parentPath.substring(0, parentPath.lastIndexOf('/')) else ""

        for (name in standardMasterFilenames) {
            val candidate = "$schemeAndHost$parentPath/$name$queryStr"
            if (candidate != variantUrl && !candidates.contains(candidate)) {
                candidates.add(candidate)
            }
        }

        if (grandParentPath.isNotEmpty()) {
            for (name in standardMasterFilenames) {
                val candidate = "$schemeAndHost$grandParentPath/$name$queryStr"
                if (candidate != variantUrl && !candidates.contains(candidate)) {
                    candidates.add(candidate)
                }
            }
        }

        return candidates
    }

    fun fetchAndParseMasterPlaylist(
        masterUrl: String,
        headersMap: Map<String, String>
    ): List<VideoQualityOption> = fetchAndParseMasterPlaylist(masterUrl, headersMap, 0.0, emptyList()).qualities

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
            // Parse audio renditions in separate adaptation sets (#EXT-X-MEDIA:TYPE=AUDIO)
            val audioGroupBandwidths = mutableMapOf<String, Long>()
            var defaultAudioBandwidth: Long? = null
            var hasSeparateAudioRenditions = false

            manifestText.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-MEDIA:") && trimmed.contains("TYPE=AUDIO")) {
                    hasSeparateAudioRenditions = true
                    val groupMatch = """GROUP-ID="([^"]+)"""".toRegex().find(trimmed)
                    val groupId = groupMatch?.groupValues?.get(1)

                    val bwMatch = """(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\d+)""".toRegex().find(trimmed)
                    val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull()

                    if (groupId != null && bw != null && bw > 0L) {
                        audioGroupBandwidths[groupId] = bw
                    }
                    if (bw != null && bw > 0L && defaultAudioBandwidth == null) {
                        defaultAudioBandwidth = bw
                    }
                }
            }
            if (hasSeparateAudioRenditions && defaultAudioBandwidth == null) {
                defaultAudioBandwidth = 128_000L
            }

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
                    val videoBandwidth = extractBandwidth(lastStreamInf)
                    val (w, h) = extractResolutionDimensions(lastStreamInf)

                    // Check for separate audio adaptation set to SUM bandwidths
                    val audioGroup = """AUDIO="([^"]+)"""".toRegex().find(lastStreamInf)?.groupValues?.get(1)
                    val codecs = """CODECS="([^"]+)"""".toRegex().find(lastStreamInf)?.groupValues?.get(1) ?: ""
                    val hasAudioCodec = codecs.contains("mp4a", ignoreCase = true) ||
                            codecs.contains("aac", ignoreCase = true) ||
                            codecs.contains("ac-3", ignoreCase = true) ||
                            codecs.contains("ec-3", ignoreCase = true)

                    val audioBandwidth = if (audioGroup != null) {
                        audioGroupBandwidths[audioGroup] ?: defaultAudioBandwidth ?: 128_000L
                    } else if (hasSeparateAudioRenditions && !hasAudioCodec) {
                        defaultAudioBandwidth ?: 128_000L
                    } else {
                        0L
                    }

                    val totalBandwidth = if (audioBandwidth > 0L && videoBandwidth > 0L) {
                        videoBandwidth + audioBandwidth
                    } else {
                        videoBandwidth
                    }

                    val (effectiveW, effectiveH) = if (w > 0 && h > 0) {
                        w to h
                    } else if (totalBandwidth > 0L) {
                        when {
                            totalBandwidth >= 12_000_000L -> 3840 to 2160
                            totalBandwidth >= 7_000_000L -> 2560 to 1440
                            totalBandwidth >= 3_500_000L -> 1920 to 1080
                            totalBandwidth in 1_600_000L..3_499_999L -> 1280 to 720
                            totalBandwidth in 750_000L..1_599_999L -> 854 to 480
                            totalBandwidth in 400_000L..749_999L -> 640 to 360
                            else -> 426 to 240
                        }
                    } else {
                        0 to 0
                    }

                    val resStr = if (w > 0 && h > 0) "${w}x${h}" else if (effectiveH > 0) "${effectiveH}p" else ""
                    val label = formatQualityLabel(w, h, totalBandwidth)

                    rawVariants.add(
                        RawVariant(
                            url = streamUrl,
                            bandwidth = totalBandwidth,
                            width = if (w > 0) w else effectiveW,
                            height = if (h > 0) h else effectiveH,
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

            // Preserve EVERY declared #EXT-X-STREAM-INF track variant (1080p, 720p, 480p, 360p, 240p, etc.)
            // Strip out arbitrary heuristic filters, artificial bitrate drop rules, and preview deduplication
            // Deduplicate only if identical URL and bandwidth are declared multiple times
            val declaredVariants = rawVariants
                .distinctBy { if (it.url.isNotBlank()) "${it.url}_${it.bandwidth}" else "${it.height}_${it.bandwidth}" }
                .sortedWith(
                    compareByDescending<RawVariant> { it.height }
                        .thenByDescending { it.bandwidth }
                )

            val results = mutableListOf<VideoQualityOption>()
            for (variant in declaredVariants) {
                // Do NOT calculate or display fake deterministic file sizes for HLS
                results.add(
                    VideoQualityOption(
                        label = variant.label,
                        resolution = variant.resolution,
                        bandwidthBps = variant.bandwidth,
                        url = variant.url,
                        isHlsVariant = true,
                        estimatedSizeBytes = 0L,
                        formatTag = "HLS M3U8",
                        isExactSize = false
                    )
                )
            }

            // Also preserve declared audio-only renditions (#EXT-X-MEDIA:TYPE=AUDIO)
            manifestText.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-MEDIA:") && trimmed.contains("TYPE=AUDIO")) {
                    val uriMatch = """URI="([^"]+)"""".toRegex().find(trimmed)
                    val audioUri = uriMatch?.groupValues?.get(1)
                    if (!audioUri.isNullOrBlank()) {
                        val fullAudioUrl = resolveUrl(baseUrl, audioUri)
                        if (results.none { it.url == fullAudioUrl }) {
                            val groupMatch = """GROUP-ID="([^"]+)"""".toRegex().find(trimmed)
                            val nameMatch = """NAME="([^"]+)"""".toRegex().find(trimmed)
                            val audioName = nameMatch?.groupValues?.get(1) ?: groupMatch?.groupValues?.get(1) ?: "Audio"
                            results.add(
                                VideoQualityOption(
                                    label = "Audio ($audioName)",
                                    resolution = "Audio",
                                    bandwidthBps = defaultAudioBandwidth ?: 128_000L,
                                    url = fullAudioUrl,
                                    isHlsVariant = true,
                                    estimatedSizeBytes = 0L,
                                    formatTag = "AUDIO",
                                    isExactSize = false
                                )
                            )
                        }
                    }
                }
            }

            val sortedResults = results.sortedWith(
                compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
                    .thenByDescending { it.bandwidthBps }
            )
            return HlsParseResult(sortedResults, fallbackDurationSeconds)
        }

        // Case B: Direct Media Playlist with #EXTINF segments directly
        if (manifestText.contains("#EXTINF:")) {
            val (detectedRes, detectedLabel) = inferMediaPlaylistResolution(baseUrl, manifestText)

            val singleTier = VideoQualityOption(
                label = detectedLabel,
                resolution = detectedRes,
                bandwidthBps = 0L,
                url = baseUrl,
                isHlsVariant = true,
                estimatedSizeBytes = 0L,
                formatTag = "HLS M3U8",
                isExactSize = false
            )

            return HlsParseResult(listOf(singleTier), fallbackDurationSeconds)
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
        val avgRegex = """AVERAGE-BANDWIDTH=(\d+)""".toRegex()
        val avgMatch = avgRegex.find(streamInf)
        val avgBw = avgMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        if (avgBw > 0L) return avgBw

        val regex = """BANDWIDTH=(\d+)""".toRegex()
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

        if (minDim > 0 || maxDim > 0) {
            val quality = when {
                maxDim >= 3840 || minDim >= 2160 -> "4K UHD"
                maxDim >= 2560 || minDim >= 1440 -> "1440p 2K"
                maxDim >= 1920 || minDim >= 1080 -> "1080p FHD"
                maxDim >= 1280 || minDim >= 720 -> "720p HD"
                maxDim >= 854 || minDim >= 480 -> "480p SD"
                maxDim >= 640 || minDim >= 360 -> "360p SD"
                maxDim >= 426 || minDim >= 240 -> "240p"
                minDim > 0 -> "${minDim}p"
                else -> "${maxDim}p"
            }
            val resStr = if (width > 0 && height > 0) "${width}x${height}" else ""
            return if (resStr.isNotEmpty()) "$quality ($resStr)" else quality
        }

        return when {
            bandwidth >= 12_000_000L -> "4K UHD"
            bandwidth >= 7_000_000L -> "1440p 2K"
            bandwidth >= 3_500_000L -> "1080p FHD"
            bandwidth in 1_600_000L..3_499_999L -> "720p HD"
            bandwidth in 750_000L..1_599_999L -> "480p SD"
            bandwidth in 400_000L..749_999L -> "360p SD"
            bandwidth > 0L -> "240p"
            else -> "Source Stream"
        }
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

    /**
     * Ensures strict size & bandwidth consistency across descending resolutions.
     * Prevents misleading edge cases where a lower resolution (e.g., 720p) is reported
     * with a larger size or bandwidth than a higher resolution (e.g., 1080p).
     */
    fun enforceSizeCoherence(options: List<VideoQualityOption>): List<VideoQualityOption> {
        if (options.size <= 1) return options

        val result = options.toMutableList()
        var changed = true
        var passes = 0

        while (changed && passes < 3) {
            changed = false
            passes++
            for (i in 0 until result.size - 1) {
                val curr = result[i]
                val next = result[i + 1]

                val currH = curr.getResolutionHeight()
                val nextH = next.getResolutionHeight()

                // Both must be valid video tiers
                if (currH > nextH && currH > 0 && nextH > 0) {
                    val currSize = curr.estimatedSizeBytes
                    val nextSize = next.estimatedSizeBytes

                    if (nextSize > 0L && currSize > 0L && currSize < nextSize) {
                        // Inversion detected: Higher resolution has smaller size than lower resolution
                        val scale = Math.pow(currH.toDouble() / nextH.toDouble(), 1.25)
                        val adjustedSize = (nextSize * scale).toLong()
                        val adjustedBandwidth = if (next.bandwidthBps > 0L) {
                            (next.bandwidthBps * scale).toLong().coerceAtLeast(curr.bandwidthBps)
                        } else {
                            curr.bandwidthBps
                        }
                        result[i] = curr.copy(
                            estimatedSizeBytes = adjustedSize,
                            bandwidthBps = adjustedBandwidth
                        )
                        changed = true
                    } else if (currSize > 0L && nextSize > 0L && nextSize > currSize) {
                        result[i + 1] = next.copy(
                            estimatedSizeBytes = (currSize * 0.7).toLong()
                        )
                        changed = true
                    }
                }
            }
        }

        return result
    }
}
