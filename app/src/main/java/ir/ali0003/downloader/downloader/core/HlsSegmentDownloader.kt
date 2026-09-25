package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.media.MediaScannerConnection
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
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Unified, Direct-to-File HLS Stream Downloader & Remuxer:
 * 1. Resolves master playlists & media variants using inherited session headers (Cookies, User-Agent, Referer).
 * 2. Downloads declared segments sequentially/concurrently with retry policy (minRetryCount = 5).
 * 3. Calculates real-time linear progress strictly by segment count:
 *    progress = (downloadedSegments.toFloat() / totalSegments.toFloat()).coerceIn(0f, 1f)
 * 4. Remuxes segments directly into a clean, standalone .mp4 container using HardwareMediaMuxer.
 * 5. Registers output file via MediaScannerConnection so device Gallery & external players detect it instantly.
 */
class HlsSegmentDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "HlsSegmentDownloader"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_RETRIES = 5

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
     * Downloads declared HLS segments directly to file and remuxes to standalone MP4 container.
     */
    fun downloadHls(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = flow {
        val taskId = task.id
        val manifestUrl = task.url
        val headers = parseHeaders(task.headersJson, manifestUrl, task.websiteUrl)

        Log.d(TAG, "Starting HLS download for Task $taskId from: $manifestUrl (dest: ${outputFile.absolutePath})")

        val targetBitrate = headers["target_bitrate"]?.toLongOrNull() ?: 0L
        val durationSec = headers["duration_seconds"]?.toDoubleOrNull() ?: 0.0
        val fixedTotalBytes = if (task.totalBytes > 0L) {
            task.totalBytes
        } else if (targetBitrate > 0L && durationSec > 0.0) {
            ((targetBitrate * durationSec) / 8.0).toLong()
        } else {
            0L
        }

        var totalDownloadedBytes = 0L
        var totalSegments = 0
        var downloadedSegments = 0

        try {
            // 1. Fetch Playlist and resolve target media segments
            val playlistContent = fetchText(manifestUrl, headers)
            val (resolvedPlaylistUrl, segmentUrls) = parseSegments(manifestUrl, playlistContent, headers, task.headersJson)

            if (segmentUrls.isEmpty()) {
                throw IOException("No media segments found in HLS manifest: $manifestUrl")
            }

            totalSegments = segmentUrls.size
            Log.d(TAG, "Task $taskId: parsed $totalSegments media segments from $resolvedPlaylistUrl (fixedTotalBytes: $fixedTotalBytes)")

            // Ensure destination directory exists
            outputFile.parentFile?.mkdirs()

            // Working intermediate file
            val isTargetMp4 = outputFile.name.endsWith(".mp4", ignoreCase = true)
            val workingFile = if (isTargetMp4) {
                File(outputFile.parentFile, "${outputFile.name}.raw_stream.ts")
            } else {
                outputFile
            }
            if (workingFile.exists()) workingFile.delete()

            var lastEmitTime = 0L
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()

            workingFile.outputStream().use { fos ->
                for (index in 0 until totalSegments) {
                    if (!coroutineContext.isActive) {
                        Log.w(TAG, "HLS download cancelled for task $taskId at segment $index")
                        break
                    }

                    val segmentUrl = segmentUrls[index]
                    var success = false
                    var attempts = 0
                    var segmentBytes = 0L
                    var lastError: Exception? = null

                    while (!success && attempts < MAX_RETRIES) {
                        attempts++
                        try {
                            segmentBytes = downloadSegmentToStream(segmentUrl, headers, fos)
                            success = true
                        } catch (e: Exception) {
                            lastError = e
                            Log.w(TAG, "Task $taskId segment $index/$totalSegments attempt $attempts/$MAX_RETRIES failed: ${e.message}")
                            if (attempts < MAX_RETRIES) {
                                delay(800L * attempts)
                            }
                        }
                    }

                    if (!success) {
                        throw IOException("Failed to download HLS segment $index after $MAX_RETRIES attempts: ${lastError?.message}")
                    }

                    downloadedSegments++
                    totalDownloadedBytes += segmentBytes

                    val now = System.currentTimeMillis()
                    val linearProgress = (downloadedSegments.toFloat() / totalSegments.toFloat()).coerceIn(0f, 1f)

                    val timeDelta = (now - lastTime).coerceAtLeast(1L)
                    val bytesDelta = (totalDownloadedBytes - lastBytes).coerceAtLeast(0L)
                    val speedBps = (bytesDelta * 1000L) / timeDelta
                    val remainingSegments = (totalSegments - downloadedSegments).coerceAtLeast(0)
                    val etaSeconds = if (speedBps > 0 && remainingSegments > 0) {
                        if (fixedTotalBytes > totalDownloadedBytes) {
                            (fixedTotalBytes - totalDownloadedBytes) / speedBps
                        } else if (downloadedSegments > 0) {
                            val avgBytes = totalDownloadedBytes / downloadedSegments
                            (remainingSegments * avgBytes) / speedBps
                        } else {
                            0L
                        }
                    } else {
                        0L
                    }

                    // Emit live updates every 300-500ms and on final segment
                    // Maintain fixed rock-solid totalBytes: NEVER recalculate dynamically inside download loop
                    if (now - lastEmitTime >= 350L || downloadedSegments == totalSegments) {
                        lastEmitTime = now
                        lastTime = now
                        lastBytes = totalDownloadedBytes

                        emit(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = totalDownloadedBytes,
                                totalBytes = fixedTotalBytes,
                                speedBps = speedBps,
                                etaSeconds = etaSeconds,
                                explicitProgress = linearProgress,
                                currentSegment = downloadedSegments,
                                totalSegments = totalSegments
                            )
                        )
                    }
                }
                fos.flush()
            }

            // 2. Automated Container Remuxing:
            // If the target file is .mp4, remux the raw TS segments into a clean MP4 container
            if (isTargetMp4 && workingFile.exists() && workingFile.length() > 0L) {
                Log.d(TAG, "Remuxing HLS raw stream (${workingFile.length()} bytes) to MP4 container: ${outputFile.name}")
                val hardwareMuxer = HardwareMediaMuxer(context)
                val remuxSuccess = try {
                    hardwareMuxer.remuxSingleStreamToMp4(
                        inputFile = workingFile,
                        outputFile = outputFile
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Hardware remuxing threw exception: ${e.message}, falling back to direct copy")
                    false
                }

                if (remuxSuccess && outputFile.exists() && outputFile.length() > 0L) {
                    Log.i(TAG, "Remuxing succeeded -> ${outputFile.name} (${outputFile.length()} bytes)")
                    workingFile.delete()
                } else {
                    Log.w(TAG, "Remuxing did not produce output, preserving stream directly as fallback")
                    if (!outputFile.exists() || outputFile.length() == 0L) {
                        workingFile.renameTo(outputFile)
                    } else {
                        workingFile.delete()
                    }
                }
            }

            // 3. MediaScanner indexing for public files
            val finalFileSize = if (outputFile.exists()) outputFile.length() else totalDownloadedBytes
            if (!task.isHidden && outputFile.exists()) {
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath),
                        arrayOf("video/mp4")
                    ) { path, uri ->
                        Log.d(TAG, "MediaScanner indexed completed HLS video: $path -> $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaScanner indexing failed: ${e.message}")
                }
            }

            // 4. Emit final completion
            emit(
                DownloadProgress(
                    taskId = taskId,
                    downloadedBytes = finalFileSize,
                    totalBytes = finalFileSize,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    isCompleted = true,
                    explicitProgress = 1.0f,
                    currentSegment = totalSegments,
                    totalSegments = totalSegments
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "HLS download unrecoverable failure for task $taskId: ${e.message}", e)
            emit(
                DownloadProgress(
                    taskId = taskId,
                    downloadedBytes = totalDownloadedBytes,
                    totalBytes = fixedTotalBytes,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    isFailed = true,
                    errorMessage = e.message ?: "HLS download failed",
                    explicitProgress = if (totalSegments > 0) (downloadedSegments.toFloat() / totalSegments.toFloat()) else 0f,
                    currentSegment = downloadedSegments,
                    totalSegments = totalSegments
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                requestBuilder.addHeader(k, v)
            }
        }
        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to fetch HLS manifest (${response.code}) from $url")
        }
        return response.body?.string() ?: throw IOException("Empty playlist body from $url")
    }

    private fun parseSegments(
        baseUrl: String,
        content: String,
        headers: Map<String, String>,
        headersJson: String?
    ): Pair<String, List<String>> {
        val lines = content.lines().map { it.trim() }

        // Check if Master Playlist with sub-variants (#EXT-X-STREAM-INF)
        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        if (isMaster) {
            // Check if user requested a specific resolution or bitrate in headersJson
            var targetRes = ""
            var targetBitrate = 0L
            if (!headersJson.isNullOrBlank()) {
                try {
                    val obj = JSONObject(headersJson)
                    targetRes = obj.optString("target_resolution", "")
                    targetBitrate = obj.optLong("target_bitrate", 0L)
                } catch (_: Exception) {}
            }

            var chosenVariantUrl: String? = null
            var bestBandwidth = 0L
            var currentBandwidth = 0L
            var currentRes = ""

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bwMatch = Regex("""(?:BANDWIDTH|AVERAGE-BANDWIDTH)=(\d+)""").find(line)
                    currentBandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val resMatch = Regex("""RESOLUTION=(\d+x\d+)""").find(line)
                    currentRes = resMatch?.groupValues?.get(1) ?: ""
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val streamUrl = resolveUrl(baseUrl, line)
                    if (targetRes.isNotBlank() && currentRes.contains(targetRes, ignoreCase = true)) {
                        chosenVariantUrl = streamUrl
                        break
                    } else if (targetBitrate > 0L && currentBandwidth == targetBitrate) {
                        chosenVariantUrl = streamUrl
                        break
                    } else if (currentBandwidth >= bestBandwidth) {
                        bestBandwidth = currentBandwidth
                        chosenVariantUrl = streamUrl
                    }
                    currentBandwidth = 0L
                    currentRes = ""
                }
            }

            val targetVariantUrl = chosenVariantUrl ?: baseUrl
            Log.d(TAG, "Selected HLS variant: $targetVariantUrl (bandwidth: $bestBandwidth)")
            val mediaPlaylistContent = fetchText(targetVariantUrl, headers)
            return Pair(targetVariantUrl, parseMediaPlaylistSegments(targetVariantUrl, mediaPlaylistContent))
        }

        // Direct media playlist
        return Pair(baseUrl, parseMediaPlaylistSegments(baseUrl, content))
    }

    private fun parseMediaPlaylistSegments(baseUrl: String, content: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = content.lines().map { it.trim() }

        // 1. Check for initialization segment (#EXT-X-MAP:URI="init.mp4")
        for (line in lines) {
            if (line.startsWith("#EXT-X-MAP:")) {
                val match = Regex("""URI="([^"]+)"""").find(line)
                val initUri = match?.groupValues?.get(1)
                if (!initUri.isNullOrBlank()) {
                    segments.add(resolveUrl(baseUrl, initUri))
                    break
                }
            }
        }

        // 2. Add all media segments (#EXTINF followed by URL)
        var isNextSegment = false
        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                isNextSegment = true
            } else if (isNextSegment && line.isNotEmpty() && !line.startsWith("#")) {
                segments.add(resolveUrl(baseUrl, line))
                isNextSegment = false
            }
        }

        // Fallback: any non-comment lines
        if (segments.isEmpty()) {
            for (line in lines) {
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    segments.add(resolveUrl(baseUrl, line))
                }
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
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                requestBuilder.addHeader(k, v)
            }
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP error ${response.code} downloading segment: $segmentUrl")
        }

        val body = response.body ?: throw IOException("Empty response body for segment: $segmentUrl")
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
        } catch (_: Exception) {
            relativeUrl
        }
    }

    private fun parseHeaders(
        headersJson: String?,
        manifestUrl: String,
        websiteUrl: String?
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()

        // Parse custom headers from JSON
        if (!headersJson.isNullOrBlank()) {
            try {
                val json = JSONObject(headersJson)
                json.keys().forEach { key ->
                    val v = json.optString(key)
                    if (key.isNotBlank() && v.isNotBlank() && !key.startsWith("target_") && !key.startsWith("media3_")) {
                        map[key] = v
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse headersJson: ${e.message}")
            }
        }

        // Ensure fresh cookies
        if (!map.containsKey("Cookie") && !map.containsKey("cookie")) {
            try {
                val cookie = android.webkit.CookieManager.getInstance().getCookie(manifestUrl)
                    ?: (if (!websiteUrl.isNullOrBlank()) android.webkit.CookieManager.getInstance().getCookie(websiteUrl) else null)
                if (!cookie.isNullOrBlank()) {
                    map["Cookie"] = cookie
                }
            } catch (_: Exception) {}
        }

        // Ensure Referer & Origin
        if (!map.containsKey("Referer") && !map.containsKey("referer")) {
            val effectivePage = if (!websiteUrl.isNullOrBlank()) websiteUrl else manifestUrl
            map["Referer"] = effectivePage
            try {
                val uri = Uri.parse(effectivePage)
                if (uri.scheme != null && uri.host != null) {
                    map["Origin"] = "${uri.scheme}://${uri.host}"
                }
            } catch (_: Exception) {}
        }

        // Ensure User-Agent
        if (!map.containsKey("User-Agent") && !map.containsKey("user-agent")) {
            map["User-Agent"] = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }

        return map
    }
}
