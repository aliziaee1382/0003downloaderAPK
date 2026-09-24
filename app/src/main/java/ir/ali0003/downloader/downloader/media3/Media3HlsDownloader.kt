package ir.ali0003.downloader.downloader.media3

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

        Log.d(TAG, "Dispatching Media3 HLS download for task $taskId ($manifestUrl)")

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
                val totalBytes = if (download.contentLength > 0) download.contentLength else task.totalBytes
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
                        // Ensure placeholder output file exists so File/Vault checks succeed
                        try {
                            if (!outputFile.exists()) {
                                outputFile.parentFile?.mkdirs()
                                outputFile.writeText("MEDIA3_OFFLINE_CACHE_COMPLETED")
                            }
                        } catch (_: Exception) {}

                        trySend(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = downloadedBytes.coerceAtLeast(1024L),
                                totalBytes = downloadedBytes.coerceAtLeast(1024L),
                                speedBps = 0L,
                                etaSeconds = 0L,
                                isCompleted = true
                            )
                        )
                        close()
                    }
                    Download.STATE_FAILED -> {
                        Log.e(TAG, "Media3 HLS download failed for task $taskId: ${finalException?.message}")
                        trySend(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBps = 0L,
                                etaSeconds = 0L,
                                isFailed = true,
                                errorMessage = finalException?.message ?: "Media3 download failed"
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
                delay(600)
                try {
                    val download = downloadManager.downloadIndex.getDownload(downloadId)
                    if (download != null && download.state == Download.STATE_DOWNLOADING) {
                        val downloaded = download.bytesDownloaded
                        val total = if (download.contentLength > 0) download.contentLength else task.totalBytes
                        val now = System.currentTimeMillis()
                        val dt = (now - lastTime).coerceAtLeast(1)
                        val db = (downloaded - lastDownloadedBytes).coerceAtLeast(0)
                        val speed = (db * 1000L / dt)
                        lastTime = now
                        lastDownloadedBytes = downloaded

                        val eta = if (speed > 0 && total > downloaded) (total - downloaded) / speed else 0L

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
}
