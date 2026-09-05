package ir.ali0003.downloader.browser.sniffer

import android.net.Uri
import ir.ali0003.downloader.browser.model.VideoQualityOption
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

object HlsManifestParser {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchAndParseMasterPlaylist(
        masterUrl: String,
        headersMap: Map<String, String> = emptyMap()
    ): List<VideoQualityOption> {
        return try {
            val headersBuilder = Headers.Builder()
            headersMap.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    headersBuilder.add(k, v)
                }
            }

            val request = Request.Builder()
                .url(masterUrl)
                .headers(headersBuilder.build())
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseManifestContent(body, masterUrl)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseManifestContent(manifestText: String, baseUrl: String): List<VideoQualityOption> {
        if (!manifestText.contains("#EXTM3U")) return emptyList()

        val results = mutableListOf<VideoQualityOption>()
        val reader = BufferedReader(StringReader(manifestText))
        var line: String?
        var lastStreamInf: String? = null

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line?.trim() ?: continue
            if (currentLine.isEmpty()) continue

            if (currentLine.startsWith("#EXT-X-STREAM-INF:")) {
                lastStreamInf = currentLine
            } else if (!currentLine.startsWith("#") && lastStreamInf != null) {
                // This line is the URI for the stream
                val streamUrl = resolveUrl(baseUrl, currentLine)
                val bandwidth = extractBandwidth(lastStreamInf)
                val resolution = extractResolution(lastStreamInf)
                val label = formatQualityLabel(resolution, bandwidth)

                // Estimate size assuming 10 minute video if unknown (10 * 60 * bandwidth / 8)
                val estimatedSize = if (bandwidth > 0) (bandwidth * 600L) / 8L else 0L

                results.add(
                    VideoQualityOption(
                        label = label,
                        resolution = resolution,
                        bandwidthBps = bandwidth,
                        url = streamUrl,
                        isHlsVariant = true,
                        estimatedSizeBytes = estimatedSize,
                        formatTag = "HLS M3U8"
                    )
                )
                lastStreamInf = null
            }
        }

        // Sort descending by bandwidth/resolution
        return results.sortedByDescending { it.bandwidthBps }
    }

    private fun extractBandwidth(streamInf: String): Long {
        val regex = "BANDWIDTH=(\\d+)".toRegex()
        val match = regex.find(streamInf)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun extractResolution(streamInf: String): String {
        val regex = "RESOLUTION=(\\d+x\\d+)".toRegex()
        val match = regex.find(streamInf)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun formatQualityLabel(resolution: String, bandwidth: Long): String {
        val height = if (resolution.contains("x")) {
            resolution.substringAfter("x").toIntOrNull() ?: 0
        } else {
            0
        }

        val qualityPrefix = when {
            height >= 2160 -> "4K Ultra HD"
            height >= 1440 -> "2K Quad HD"
            height >= 1080 -> "1080p Full HD"
            height >= 720 -> "720p HD"
            height >= 480 -> "480p SD"
            height >= 360 -> "360p Low"
            height > 0 -> "${height}p"
            bandwidth >= 4_000_000 -> "High Quality (1080p)"
            bandwidth >= 2_000_000 -> "Standard HD (720p)"
            bandwidth >= 800_000 -> "Medium Quality (480p)"
            bandwidth > 0 -> "Low Quality"
            else -> "Default Stream"
        }

        return if (resolution.isNotEmpty()) "$qualityPrefix ($resolution)" else qualityPrefix
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
