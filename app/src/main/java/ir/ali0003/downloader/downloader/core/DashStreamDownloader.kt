package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.util.Log
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance DASH and Separated Audio-Video Stream Downloader.
 *
 * Downloads separated video and audio payloads concurrently into temporary scratch files
 * (temp_vid.mp4 and temp_aud.m4a), and automatically triggers [HardwareMediaMuxer]
 * to multiplex them into a single synchronized MP4 container.
 */
class DashStreamDownloader(
    private val context: Context,
    private val chunkDownloader: ChunkDownloader = ChunkDownloader(context)
) {

    companion object {
        private const val TAG = "DashStreamDownloader"

        /**
         * Checks if a [DownloadTaskEntity] represents a DASH stream or a separated video/audio pair.
         */
        fun isDashOrSeparatedPair(task: DownloadTaskEntity): Boolean {
            if (task.url.contains("|")) return true
            if (task.url.contains(".mpd", ignoreCase = true)) return true
            if (task.url.contains("dash_video=", ignoreCase = true) && task.url.contains("dash_audio=", ignoreCase = true)) return true
            val headers = task.headersJson
            return headers.contains("dash_audio") || headers.contains("audio_url") || headers.contains("audioUrl")
        }

        /**
         * Extracts the pair of (videoUrl, audioUrl) from the task.
         */
        fun extractStreamUrls(task: DownloadTaskEntity): Pair<String, String>? {
            // Case 1: Pipe delimited "videoUrl|audioUrl"
            if (task.url.contains("|")) {
                val parts = task.url.split("|")
                if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return Pair(parts[0].trim(), parts[1].trim())
                }
            }

            // Case 2: Query param encoded "dash_video=...&dash_audio=..."
            if (task.url.contains("dash_video=") && task.url.contains("dash_audio=")) {
                try {
                    val uri = android.net.Uri.parse(task.url)
                    val v = uri.getQueryParameter("dash_video")
                    val a = uri.getQueryParameter("dash_audio")
                    if (!v.isNullOrBlank() && !a.isNullOrBlank()) {
                        return Pair(v, a)
                    }
                } catch (_: Exception) {}
            }

            // Case 3: headersJson contains audio_url or dash_audio_url
            try {
                val json = JSONObject(task.headersJson)
                val audio = json.optString("dash_audio_url").ifBlank {
                    json.optString("audio_url").ifBlank {
                        json.optString("audioUrl")
                    }
                }
                if (audio.isNotBlank()) {
                    return Pair(task.url.trim(), audio.trim())
                }
            } catch (_: Exception) {}

            return null
        }
    }

    fun isDashOrSeparatedPair(task: DownloadTaskEntity): Boolean =
        Companion.isDashOrSeparatedPair(task)

    /**
     * Downloads video and audio streams concurrently, then muxes them into [outputFile].
     * Emits real-time [DownloadProgress] throughout the download and muxing phases.
     */
    fun downloadAndMux(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = flow {
        val taskId = task.id
        val urls = extractStreamUrls(task)
            ?: throw IOException("Unable to resolve separated DASH video and audio stream URLs for task $taskId")

        val videoUrl = urls.first
        val audioUrl = urls.second
        val headers = chunkDownloader.parseHeaders(task.headersJson)

        Log.d(TAG, "Starting concurrent DASH download for task $taskId: Video=$videoUrl, Audio=$audioUrl")

        // Scratch temp directory for intermediate video & audio payload files
        val scratchDir = File(context.cacheDir, "dash_mux_$taskId")
        if (!scratchDir.exists()) scratchDir.mkdirs()

        val tempVidFile = File(scratchDir, "temp_vid.mp4")
        val tempAudFile = File(scratchDir, "temp_aud.m4a")

        val totalDownloadedAtomic = AtomicLong(0L)
        val estimatedTotalBytes = if (task.totalBytes > 0L) task.totalBytes else 40L * 1024L * 1024L
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        try {
            // 1. Download video and audio payloads concurrently
            coroutineScope {
                val vidDeferred = async(Dispatchers.IO) {
                    chunkDownloader.downloadUrlToFile(
                        url = videoUrl,
                        headers = headers,
                        outputFile = tempVidFile,
                        onBytesRead = { bytes ->
                            totalDownloadedAtomic.addAndGet(bytes)
                        }
                    )
                }

                val audDeferred = async(Dispatchers.IO) {
                    chunkDownloader.downloadUrlToFile(
                        url = audioUrl,
                        headers = headers,
                        outputFile = tempAudFile,
                        onBytesRead = { bytes ->
                            totalDownloadedAtomic.addAndGet(bytes)
                        }
                    )
                }

                // Progress polling during download phase
                while (vidDeferred.isActive || audDeferred.isActive) {
                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 350) {
                        val currentDownloaded = totalDownloadedAtomic.get()
                        val elapsed = (now - lastTime).coerceAtLeast(1L)
                        val speedBps = ((currentDownloaded - lastBytes) * 1000L) / elapsed
                        val remaining = (estimatedTotalBytes - currentDownloaded).coerceAtLeast(0L)
                        val eta = if (speedBps > 0 && estimatedTotalBytes > 0) remaining / speedBps else 0L

                        emit(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = currentDownloaded,
                                totalBytes = estimatedTotalBytes,
                                speedBps = speedBps,
                                etaSeconds = eta,
                                isCompleted = false
                            )
                        )
                        lastTime = now
                        lastBytes = currentDownloaded
                    }
                    delay(150)
                }

                vidDeferred.await()
                audDeferred.await()
            }

            val downloadedTotal = totalDownloadedAtomic.get()
            Log.d(TAG, "DASH stream downloads finished ($downloadedTotal bytes). Triggering native HardwareMediaMuxer...")

            // Emit download completion status before starting muxer
            emit(
                DownloadProgress(
                    taskId = taskId,
                    downloadedBytes = downloadedTotal,
                    totalBytes = downloadedTotal,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    isCompleted = false
                )
            )

            // 2. Multiplex visual & acoustic tracks with HardwareMediaMuxer
            val muxer = HardwareMediaMuxer.getInstance(context)
            val muxSuccess = muxer.muxAudioAndVideo(
                videoFile = tempVidFile,
                audioFile = tempAudFile,
                outputFile = outputFile,
                onProgress = { muxProgress ->
                    // Keep progress updated during muxing
                }
            )

            if (!muxSuccess || !outputFile.exists() || outputFile.length() == 0L) {
                throw IOException("HardwareMediaMuxer failed to generate synchronized MP4 container at ${outputFile.absolutePath}")
            }

            Log.d(TAG, "DASH muxing completed successfully: ${outputFile.name} (${outputFile.length()} bytes)")

            // 3. Emit final completed state now that the merged MP4 is safely saved
            emit(
                DownloadProgress(
                    taskId = taskId,
                    downloadedBytes = outputFile.length(),
                    totalBytes = outputFile.length(),
                    speedBps = 0L,
                    etaSeconds = 0L,
                    isCompleted = true
                )
            )

        } catch (cancellation: CancellationException) {
            Log.i(TAG, "DASH download/muxing cancelled for task $taskId")
            if (outputFile.exists()) outputFile.delete()
            throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "DASH download/muxing error for task $taskId: ${e.message}", e)
            if (outputFile.exists()) outputFile.delete()
            throw e
        } finally {
            // Clean up temporary scratch directory
            scratchDir.deleteRecursively()
        }
    }.flowOn(Dispatchers.IO)
}
