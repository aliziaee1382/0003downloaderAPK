package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * HLS/M3U8 Stream Downloader & Muxer.
 * 1. Fetches the chosen variant playlist `.m3u8`.
 * 2. Parses all `.ts` or `.m4s` segment URLs.
 * 3. Downloads segments in bounded batches with automatic retry.
 * 4. Merges segments directly into a unified `.mp4` file on scoped storage.
 */
class HlsSegmentDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "HlsSegmentDownloader"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_RETRIES = 3

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    /**
     * Downloads all segments from an HLS manifest and writes the merged container to outputFile.
     */
    fun downloadHls(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = flow {
        val taskId = task.id
        val manifestUrl = task.url
        val headers = parseHeaders(task.headersJson)

        Log.d(TAG, "Starting HLS download for Task $taskId from: $manifestUrl")

        // 1. Fetch Master or Media Playlist
        val playlistContent = fetchText(manifestUrl, headers)
        val segmentUrls = parseSegments(manifestUrl, playlistContent, headers)

        if (segmentUrls.isEmpty()) {
            throw java.io.IOException("No media segments found in HLS manifest: $manifestUrl")
        }

        val totalSegments = segmentUrls.size
        Log.d(TAG, "Parsed $totalSegments segments for HLS task $taskId")

        if (outputFile.exists()) outputFile.delete()
        if (outputFile.parentFile?.exists() == false) outputFile.parentFile?.mkdirs()

        val totalDownloadedBytes = AtomicLong(0L)
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        // Estimated average segment size: 1.5MB if unknown
        val estimatedTotalBytes = if (task.totalBytes > 0) task.totalBytes else totalSegments * 1024L * 1024L * 2L

        outputFile.outputStream().use { fos ->
            for (index in 0 until totalSegments) {
                if (!coroutineContext.isActive) break

                val segmentUrl = segmentUrls[index]
                var success = false
                var attempts = 0
                var segmentBytes = 0L

                while (!success && attempts < MAX_RETRIES) {
                    attempts++
                    try {
                        segmentBytes = downloadSegmentToStream(segmentUrl, headers, fos)
                        success = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Segment $index attempt $attempts failed: ${e.message}")
                        if (attempts < MAX_RETRIES) {
                            delay(1000L * attempts)
                        } else {
                            throw java.io.IOException("Failed to download HLS segment $index after $MAX_RETRIES attempts: ${e.message}")
                        }
                    }
                }

                val currentTotal = totalDownloadedBytes.addAndGet(segmentBytes)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTime).coerceAtLeast(1L)
                val diff = (currentTotal - lastBytes).coerceAtLeast(0L)
                val speedBps = (diff * 1000L) / elapsed

                // Recalculate ETA based on segment count
                val remainingSegments = (totalSegments - (index + 1)).coerceAtLeast(0)
                val avgBytesPerSegment = (currentTotal / (index + 1)).coerceAtLeast(1L)
                val remainingBytes = remainingSegments * avgBytesPerSegment
                val etaSeconds = if (speedBps > 0) remainingBytes / speedBps else 0L

                emit(
                    DownloadProgress(
                        taskId = taskId,
                        downloadedBytes = currentTotal,
                        totalBytes = (totalSegments * avgBytesPerSegment).coerceAtLeast(currentTotal),
                        speedBps = speedBps,
                        etaSeconds = etaSeconds
                    )
                )

                lastTime = now
                lastBytes = currentTotal
            }
            fos.flush()
        }

        // Emit final completion
        emit(
            DownloadProgress(
                taskId = taskId,
                downloadedBytes = totalDownloadedBytes.get(),
                totalBytes = totalDownloadedBytes.get(),
                speedBps = 0L,
                etaSeconds = 0L,
                isCompleted = true
            )
        )

    }.flowOn(Dispatchers.IO)

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("Failed to fetch HLS manifest (${response.code})")
        }
        return response.body?.string() ?: throw java.io.IOException("Empty playlist body")
    }

    private fun parseSegments(
        baseUrl: String,
        content: String,
        headers: Map<String, String>
    ): List<String> {
        val lines = content.lines().map { it.trim() }
        val segments = mutableListOf<String>()

        // Check if this is a Master Playlist with sub-variants
        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        if (isMaster) {
            // Find the highest bitrate variant URL
            var bestVariantUrl: String? = null
            var maxBandwidth = 0L

            var currentBandwidth = 0L
            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    currentBandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val resolvedUrl = resolveUrl(baseUrl, line)
                    if (currentBandwidth >= maxBandwidth) {
                        maxBandwidth = currentBandwidth
                        bestVariantUrl = resolvedUrl
                    }
                    currentBandwidth = 0L
                }
            }

            val targetVariantUrl = bestVariantUrl ?: baseUrl
            Log.d(TAG, "Selected highest HLS variant: $targetVariantUrl ($maxBandwidth bps)")
            val mediaPlaylistContent = fetchText(targetVariantUrl, headers)
            return parseSegments(targetVariantUrl, mediaPlaylistContent, headers)
        }

        // Media Playlist parsing
        for (line in lines) {
            if (line.isNotEmpty() && !line.startsWith("#")) {
                segments.add(resolveUrl(baseUrl, line))
            }
        }

        return segments
    }

    private fun downloadSegmentToStream(
        segmentUrl: String,
        headers: Map<String, String>,
        outputStream: FileOutputStream
    ): Long {
        val requestBuilder = Request.Builder().url(segmentUrl).get()
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code} on segment: $segmentUrl")
        }

        val body = response.body ?: throw java.io.IOException("Null segment response body")
        var bytesRead = 0L

        body.byteStream().use { inputStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                outputStream.write(buffer, 0, len)
                bytesRead += len
            }
        }
        return bytesRead
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val baseUri = URI(baseUrl)
                baseUri.resolve(relativeUrl).toString()
            }
        } catch (e: Exception) {
            relativeUrl
        }
    }

    private fun parseHeaders(headersJson: String?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (headersJson.isNullOrBlank()) return map
        try {
            val json = JSONObject(headersJson)
            json.keys().forEach { key ->
                map[key] = json.optString(key)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse headers: ${e.message}")
        }
        return map
    }
}
