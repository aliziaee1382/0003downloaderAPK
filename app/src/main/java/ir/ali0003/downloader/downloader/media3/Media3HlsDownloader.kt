package ir.ali0003.downloader.downloader.media3

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import ir.ali0003.downloader.browser.sniffer.HlsManifestParser
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Executes HLS downloads via official Media3 DownloadManager/DownloadService.
 * Observes download progress cleanly and emits [DownloadProgress] models mapped
 * directly to the application's architecture.
 */
@OptIn(UnstableApi::class)
class Media3HlsDownloader(
    private val context: Context
) {

    companion object {
        private const val TAG = "Media3HlsDownloader"
    }

    private val downloadManager: DownloadManager =
        Media3DownloadManagerProvider.getDownloadManager(context)

    fun downloadHls(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = callbackFlow {
        val taskId = task.id
        val manifestUrl = task.url
        val downloadId = "task_$taskId"

        val headers = parseHeaders(task.headersJson, manifestUrl)
        Media3DownloadManagerProvider.registerRequestHeaders(manifestUrl, headers)

        // Determine totalBytes ONCE before download begins:
        // fixedTotalBytes = (averageBitrateBps * durationSeconds) / 8L
        // If bitrate/duration is unavailable, keep totalBytes = 0L
        val targetBitrate = headers["target_bitrate"]?.toLongOrNull() ?: 0L
        val durationSec = headers["duration_seconds"]?.toDoubleOrNull() ?: 0.0
        val fixedTotalBytes = if (task.totalBytes > 0L) {
            task.totalBytes
        } else if (targetBitrate > 0L && durationSec > 0.0) {
            ((targetBitrate * durationSec) / 8.0).toLong()
        } else {
            0L
        }

        Log.d(TAG, "Dispatching Media3 HLS download for task $taskId ($manifestUrl, fixedTotalBytes: $fixedTotalBytes)")

        // 1. Build and dispatch Media3 DownloadRequest
        launch {
            try {
                val targetResolution = headers["target_resolution"] ?: ""
                val targetBitrate = headers["target_bitrate"]?.toLongOrNull() ?: 0L
                val renditionKey = headers["media3_rendition_key"]

                val selectedQuality = if (targetResolution.isNotBlank() || targetBitrate > 0L || !renditionKey.isNullOrBlank()) {
                    ir.ali0003.downloader.browser.model.VideoQualityOption(
                        label = targetResolution.ifBlank { "Selected Quality" },
                        resolution = targetResolution,
                        bandwidthBps = targetBitrate,
                        url = manifestUrl,
                        isHlsVariant = true,
                        renditionKey = renditionKey
                    )
                } else {
                    null
                }

                val request = Media3HlsHelper.createDownloadRequest(
                    context = context,
                    manifestUrl = manifestUrl,
                    headers = headers,
                    selectedQuality = selectedQuality,
                    customDownloadId = downloadId
                )

                // Dispatch to AppDownloadService
                AppDownloadService.sendAddDownload(
                    context = context,
                    downloadRequest = request,
                    stopReason = Download.STOP_REASON_NONE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating Media3 HLS download: ${e.message}", e)
                trySend(
                    DownloadProgress(
                        taskId = taskId,
                        downloadedBytes = 0L,
                        totalBytes = task.totalBytes,
                        speedBps = 0L,
                        etaSeconds = 0L,
                        isFailed = true,
                        errorMessage = e.message
                    )
                )
                close(e)
            }
        }

        var lastDownloadedBytes = 0L
        var lastTime = System.currentTimeMillis()

        // 2. Register listener on DownloadManager
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                manager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                if (download.request.id != downloadId) return

                val state = download.state
                val downloadedBytes = download.bytesDownloaded
                val totalBytes = if (fixedTotalBytes > 0L) fixedTotalBytes else if (download.contentLength > 0L) download.contentLength else 0L
                val percent = download.percentDownloaded

                val now = System.currentTimeMillis()
                val timeDelta = (now - lastTime).coerceAtLeast(1)
                val bytesDelta = (downloadedBytes - lastDownloadedBytes).coerceAtLeast(0)
                val speedBps = if (timeDelta >= 500) {
                    (bytesDelta * 1000L / timeDelta).also {
                        lastTime = now
                        lastDownloadedBytes = downloadedBytes
                    }
                } else {
                    0L
                }

                val eta = if (speedBps > 0 && totalBytes > downloadedBytes) {
                    (totalBytes - downloadedBytes) / speedBps
                } else {
                    0L
                }

                when (state) {
                    Download.STATE_COMPLETED -> {
                        Log.d(TAG, "Media3 HLS download completed for task $taskId")
                        launch(Dispatchers.IO) {
                            // If user chose public storage (!task.isHidden), merge cached segments into a clean standalone .mp4 container
                            if (!task.isHidden) {
                                tryExportHlsCacheToContainer(context, manifestUrl, outputFile)
                            } else {
                                // Ensure placeholder output file exists so Vault checks succeed
                                try {
                                    if (!outputFile.exists()) {
                                        outputFile.parentFile?.mkdirs()
                                        outputFile.writeText("MEDIA3_OFFLINE_CACHE_COMPLETED")
                                    }
                                } catch (_: Exception) {}
                            }

                            val actualSize = if (outputFile.exists() && outputFile.length() > 1024L) {
                                outputFile.length()
                            } else {
                                downloadedBytes.coerceAtLeast(1024L)
                            }

                            trySend(
                                DownloadProgress(
                                    taskId = taskId,
                                    downloadedBytes = actualSize,
                                    totalBytes = actualSize,
                                    speedBps = 0L,
                                    etaSeconds = 0L,
                                    isCompleted = true
                                )
                            )
                            close()
                        }
                    }
                    Download.STATE_FAILED -> {
                        val errorMsg = finalException?.message ?: "Media3 HLS download failed"
                        Log.e(TAG, "Media3 HLS download failed for task $taskId: $errorMsg", finalException)
                        launch(Dispatchers.IO) {
                            try {
                                val db = ir.ali0003.downloader.data.local.AppDatabase.getInstance(context)
                                db.downloadDao().markFailed(taskId, errorMsg)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error updating failed status for task $taskId: ${e.message}")
                            }
                        }
                        trySend(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBps = 0L,
                                etaSeconds = 0L,
                                isFailed = true,
                                errorMessage = errorMsg
                            )
                        )
                        close(finalException)
                    }
                    Download.STATE_DOWNLOADING -> {
                        trySend(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBps = speedBps,
                                etaSeconds = eta
                            )
                        )
                    }
                    Download.STATE_QUEUED, Download.STATE_RESTARTING -> {
                        trySend(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBps = 0L,
                                etaSeconds = 0L
                            )
                        )
                    }
                }
            }
        }

        downloadManager.addListener(listener)

        // Periodic polling fallback to guarantee smooth UI progress even if onDownloadChanged is throttled
        val pollerJob = launch {
            while (isActive) {
                delay(500)
                try {
                    val download = downloadManager.downloadIndex.getDownload(downloadId)
                    if (download != null && download.state == Download.STATE_DOWNLOADING) {
                        val downloaded = download.bytesDownloaded
                        val total = if (fixedTotalBytes > 0L) fixedTotalBytes else if (download.contentLength > 0L) download.contentLength else 0L
                        val now = System.currentTimeMillis()
                        val dt = (now - lastTime).coerceAtLeast(1)
                        val db = (downloaded - lastDownloadedBytes).coerceAtLeast(0)
                        val speed = (db * 1000L / dt)
                        lastTime = now
                        lastDownloadedBytes = downloaded

                        val eta = if (speed > 0 && total > downloaded) (total - downloaded) / speed else 0L

                        try {
                            val db = ir.ali0003.downloader.data.local.AppDatabase.getInstance(context)
                            db.downloadDao().updateProgress(taskId, downloaded, total, speed, eta)
                        } catch (_: Exception) {}

                        trySend(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                speedBps = speed,
                                etaSeconds = eta
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            pollerJob.cancel()
            downloadManager.removeListener(listener)
        }
    }

    fun pauseHls(taskId: Long) {
        val downloadId = "task_$taskId"
        try {
            downloadManager.setStopReason(downloadId, Download.STOP_REASON_NONE)
            AppDownloadService.sendPauseDownloads(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing task $taskId: ${e.message}")
        }
    }

    fun resumeHls(taskId: Long) {
        val downloadId = "task_$taskId"
        try {
            downloadManager.setStopReason(downloadId, Download.STOP_REASON_NONE)
            AppDownloadService.sendResumeDownloads(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming task $taskId: ${e.message}")
        }
    }

    fun cancelHls(taskId: Long) {
        val downloadId = "task_$taskId"
        try {
            AppDownloadService.sendRemoveDownload(context, downloadId)
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling task $taskId: ${e.message}")
        }
    }

    private fun parseHeaders(headersJson: String, url: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(headersJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.optString(key)
            }
        } catch (_: Exception) {}

        if (!map.containsKey("User-Agent")) {
            map["User-Agent"] = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }
        if (!map.containsKey("Referer")) {
            try {
                val uri = android.net.Uri.parse(url)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: ""
                if (host.isNotBlank()) map["Referer"] = "$scheme://$host/"
            } catch (_: Exception) {}
        }
        return map
    }

    private suspend fun tryExportHlsCacheToContainer(
        context: Context,
        manifestUrl: String,
        outputFile: File
    ) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val scratchDir = File(context.cacheDir, "hls_export_${System.currentTimeMillis()}")
        if (!scratchDir.exists()) scratchDir.mkdirs()

        try {
            val cacheDataSource = Media3DownloadManagerProvider.getCacheDataSourceFactory(context)
                .createDataSource()

            val manifestUri = Uri.parse(manifestUrl)
            val dataSpec = androidx.media3.datasource.DataSpec(manifestUri)
            val manifestBytes = readAllFromDataSource(cacheDataSource, dataSpec) ?: return@withContext
            val manifestText = String(manifestBytes, Charsets.UTF_8)
            var mediaPlaylistUrl = manifestUrl
            var mediaPlaylistText = manifestText
            var separateAudioPlaylistUrl: String? = null

            // If it's a master playlist, find the selected or first video variant and separate audio if present
            if (manifestText.contains("#EXT-X-STREAM-INF:")) {
                val lines = manifestText.lines()
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") && separateAudioPlaylistUrl == null) {
                        val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                        if (uriMatch != null) {
                            separateAudioPlaylistUrl = HlsManifestParser.resolveUrl(manifestUrl, uriMatch.groupValues[1])
                        }
                    } else if (line.startsWith("#EXT-X-STREAM-INF:") && i + 1 < lines.size) {
                        val variantLine = lines[i + 1].trim()
                        if (variantLine.isNotEmpty() && !variantLine.startsWith("#") && mediaPlaylistUrl == manifestUrl) {
                            mediaPlaylistUrl = HlsManifestParser.resolveUrl(manifestUrl, variantLine)
                            val variantSpec = androidx.media3.datasource.DataSpec(Uri.parse(mediaPlaylistUrl))
                            val variantBytes = readAllFromDataSource(cacheDataSource, variantSpec)
                            if (variantBytes != null && variantBytes.isNotEmpty()) {
                                mediaPlaylistText = String(variantBytes, Charsets.UTF_8)
                            }
                        }
                    }
                }
            }

            // Extract video segment URLs
            val videoSegmentUrls = extractSegmentUrls(mediaPlaylistText, mediaPlaylistUrl)
            if (videoSegmentUrls.isEmpty()) {
                Log.w(TAG, "No segments found in media playlist: $mediaPlaylistUrl")
                return@withContext
            }

            // Extract separate audio segment URLs if present
            val audioSegmentUrls = if (!separateAudioPlaylistUrl.isNullOrBlank()) {
                val audioSpec = androidx.media3.datasource.DataSpec(Uri.parse(separateAudioPlaylistUrl))
                val audioBytes = readAllFromDataSource(cacheDataSource, audioSpec)
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    val audioText = String(audioBytes, Charsets.UTF_8)
                    extractSegmentUrls(audioText, separateAudioPlaylistUrl)
                } else emptyList()
            } else emptyList()

            Log.d(TAG, "Exporting HLS: ${videoSegmentUrls.size} video segments, ${audioSegmentUrls.size} audio segments to ${outputFile.name}")

            val hardwareMuxer = ir.ali0003.downloader.downloader.core.HardwareMediaMuxer.getInstance(context)

            if (audioSegmentUrls.isNotEmpty()) {
                // Separate video and audio tracks -> download scratch files and mux
                val rawVideo = File(scratchDir, "raw_video.ts")
                val rawAudio = File(scratchDir, "raw_audio.ts")

                writeSegmentsToFile(cacheDataSource, videoSegmentUrls, rawVideo)
                writeSegmentsToFile(cacheDataSource, audioSegmentUrls, rawAudio)

                val muxSuccess = hardwareMuxer.muxAudioAndVideo(rawVideo, rawAudio, outputFile)
                if (!muxSuccess || !outputFile.exists() || outputFile.length() == 0L) {
                    Log.w(TAG, "Audio+Video mux failed, falling back to direct remux of video stream")
                    hardwareMuxer.remuxSingleStreamToMp4(rawVideo, outputFile)
                }
            } else {
                // Multiplexed single stream (e.g. standard MPEG-TS)
                val rawCombined = File(scratchDir, "raw_combined.ts")
                writeSegmentsToFile(cacheDataSource, videoSegmentUrls, rawCombined)

                val remuxSuccess = hardwareMuxer.remuxSingleStreamToMp4(rawCombined, outputFile)
                if (!remuxSuccess || !outputFile.exists() || outputFile.length() == 0L) {
                    Log.w(TAG, "Single stream hardware remux failed, keeping raw combined file if valid")
                    if (rawCombined.exists() && rawCombined.length() > 0L) {
                        rawCombined.copyTo(outputFile, overwrite = true)
                    }
                }
            }

            if (outputFile.exists() && outputFile.length() > 1024L) {
                Log.i(TAG, "Successfully remuxed HLS to MP4 container: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                // Notify Android MediaStore so Gallery, VLC, and MX Player index it immediately
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath),
                        arrayOf("video/mp4")
                    ) { path, uri ->
                        Log.d(TAG, "MediaScanner indexed remuxed HLS file: $path -> $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaScannerConnection error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in tryExportHlsCacheToContainer: ${e.message}", e)
        } finally {
            scratchDir.deleteRecursively()
        }
    }

    private fun extractSegmentUrls(playlistText: String, playlistBaseUrl: String): List<String> {
        val segmentUrls = mutableListOf<String>()
        val lines = playlistText.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:") && i + 1 < lines.size) {
                for (j in (i + 1) until lines.size) {
                    val segLine = lines[j].trim()
                    if (segLine.isNotEmpty() && !segLine.startsWith("#")) {
                        segmentUrls.add(HlsManifestParser.resolveUrl(playlistBaseUrl, segLine))
                        break
                    }
                }
            }
        }
        return segmentUrls
    }

    private fun writeSegmentsToFile(
        cacheDataSource: androidx.media3.datasource.DataSource,
        segmentUrls: List<String>,
        targetFile: File
    ) {
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { fos ->
            val buffer = ByteArray(64 * 1024)
            for (segUrl in segmentUrls) {
                val segUri = Uri.parse(segUrl)
                val segSpec = androidx.media3.datasource.DataSpec(segUri)
                try {
                    cacheDataSource.open(segSpec)
                    var bytesRead: Int
                    while (cacheDataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Segment read error for $segUrl: ${e.message}")
                } finally {
                    try { cacheDataSource.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun readAllFromDataSource(
        dataSource: androidx.media3.datasource.DataSource,
        dataSpec: androidx.media3.datasource.DataSpec
    ): ByteArray? {
        return try {
            dataSource.open(dataSpec)
            val baos = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            while (dataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                baos.write(buffer, 0, bytesRead)
            }
            baos.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            try { dataSource.close() } catch (_: Exception) {}
        }
    }
}
