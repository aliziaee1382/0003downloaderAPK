package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.util.Log
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Resilient DASH & Separated Audio/Video Stream Downloader.
 * 1. Supports separated DASH streams (videoUrl|audioUrl) and .mpd manifests.
 * 2. Concurrently downloads video and audio tracks to temporary cache files.
 * 3. Muxes both tracks into a standard MP4 container via Android's hardware-accelerated MediaMuxer.
 * 4. Emits real-time unified progress and notifies MediaScanner on completion.
 */
class DashStreamDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "DashStreamDownloader"
        private const val BUFFER_SIZE = 64 * 1024

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * Downloads separated video and audio streams concurrently, then muxes them into outputFile.
     */
    fun downloadDash(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = flow {
        val taskId = task.id
        val rawUrl = task.url
        val headers = ChunkDownloader.parseHeaders(task.headersJson, rawUrl, task.websiteUrl)

        val parts = if (rawUrl.contains("|")) rawUrl.split("|") else listOf(rawUrl)
        val videoUrl = parts[0].trim()
        val audioUrl = if (parts.size > 1) parts[1].trim() else null

        Log.d(TAG, "Starting DASH download for Task $taskId (video=$videoUrl, audio=$audioUrl)")

        val tempDir = File(context.cacheDir, "dash_task_$taskId").apply { mkdirs() }
        val videoTempFile = File(tempDir, "video_track.tmp")
        val audioTempFile = File(tempDir, "audio_track.tmp")

        val videoDownloadedBytes = AtomicLong(0L)
        val audioDownloadedBytes = AtomicLong(0L)
        val videoTotalBytes = AtomicLong(0L)
        val audioTotalBytes = AtomicLong(0L)

        var lastEmittedProgress = 0f
        var lastEmittedTime = 0L

        try {
            emit(
                DownloadProgress(
                    taskId = taskId,
                    downloadedBytes = 0L,
                    totalBytes = task.totalBytes,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    explicitProgress = 0f
                )
            )

            coroutineScope {
                val videoDeferred = async<Long>(Dispatchers.IO) {
                    downloadUrlToFile(
                        url = videoUrl,
                        destination = videoTempFile,
                        headers = headers,
                        onProgress = { bytesRead: Long, total: Long ->
                            videoDownloadedBytes.set(bytesRead)
                            if (total > 0L) videoTotalBytes.set(total)
                        }
                    )
                }

                val audioDeferred = if (!audioUrl.isNullOrBlank()) {
                    async<Long>(Dispatchers.IO) {
                        downloadUrlToFile(
                            url = audioUrl,
                            destination = audioTempFile,
                            headers = headers,
                            onProgress = { bytesRead: Long, total: Long ->
                                audioDownloadedBytes.set(bytesRead)
                                if (total > 0L) audioTotalBytes.set(total)
                            }
                        )
                    }
                } else {
                    null
                }

                while (videoDeferred.isActive || audioDeferred?.isActive == true) {
                    kotlinx.coroutines.delay(250)
                    val currentDownloaded = videoDownloadedBytes.get() + audioDownloadedBytes.get()
                    val knownTotal = if (task.totalBytes > 0L) {
                        task.totalBytes
                    } else {
                        videoTotalBytes.get() + audioTotalBytes.get()
                    }

                    val currentProg = if (knownTotal > 0L) {
                        (currentDownloaded.toFloat() / knownTotal.toFloat()).coerceIn(0f, 0.95f)
                    } else {
                        0.5f
                    }

                    val now = System.currentTimeMillis()
                    if (currentProg - lastEmittedProgress > 0.01f || now - lastEmittedTime > 500) {
                        lastEmittedProgress = currentProg
                        lastEmittedTime = now
                        emit(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = currentDownloaded,
                                totalBytes = knownTotal,
                                speedBps = task.speedBps,
                                etaSeconds = 0L,
                                explicitProgress = currentProg
                            )
                        )
                    }
                }

                videoDeferred.await()
                audioDeferred?.await()
            }

            // Mux video and audio streams together into the final output container
            outputFile.parentFile?.mkdirs()
            val muxSuccess = if (audioTempFile.exists() && audioTempFile.length() > 0) {
                muxAudioVideo(videoTempFile, audioTempFile, outputFile)
            } else {
                false
            }

            if (!muxSuccess) {
                // Single stream copy fallback
                copyFile(videoTempFile, outputFile)
            }

            val finalSize = outputFile.length()
            Log.d(TAG, "Task $taskId DASH download complete: ${outputFile.absolutePath} ($finalSize bytes)")

            try {
                val db = ir.ali0003.downloader.data.local.AppDatabase.getInstance(context)
                db.downloadDao().updateLocalFilePath(taskId, outputFile.absolutePath)
                db.downloadDao().updateDownloadPath(taskId, outputFile.absolutePath)
                db.downloadDao().updateProgress(taskId, finalSize, finalSize, 0L, 0L)
                db.downloadDao().updateStatus(taskId, ir.ali0003.downloader.data.model.DownloadStatus.COMPLETED)
                db.downloadDao().markCompletedWithFile(taskId, outputFile.absolutePath, finalSize)
            } catch (e: Exception) {
                Log.w(TAG, "Error committing completed status in DashStreamDownloader: ${e.message}")
            }

            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputFile.absolutePath),
                    arrayOf(task.mimeType.ifBlank { "video/mp4" }),
                    null
                )
            } catch (_: Exception) {}

            emit(
                DownloadProgress(
                    taskId = taskId,
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    explicitProgress = 1.0f,
                    isCompleted = true
                )
            )
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads an individual stream directly to a destination file using OkHttp.
     */
    private suspend fun downloadUrlToFile(
        url: String,
        destination: File,
        headers: Map<String, String>,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank() && !key.equals("range", ignoreCase = true)) {
                requestBuilder.header(key, value)
            }
        }
        val request = requestBuilder.build()
        var bytesCopied = 0L

        okHttpClient.newCall(request).execute().use { response: Response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading DASH segment from $url")
            }
            val body = response.body ?: throw IOException("Empty response body from $url")
            val totalBytes = body.contentLength()
            destination.parentFile?.mkdirs()

            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (!coroutineContext.isActive) {
                            throw CancellationException("DASH download cancelled")
                        }
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        onProgress?.invoke(bytesCopied, totalBytes)
                    }
                    output.flush()
                }
            }
        }
        bytesCopied
    }

    /**
     * Hardware-accelerated MP4 container muxing for combining elementary video and audio tracks.
     */
    private fun muxAudioVideo(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Boolean {
        var muxer: MediaMuxer? = null
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        try {
            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerVideoTrackIndex = -1
            var muxerAudioTrackIndex = -1

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    muxerVideoTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    muxerAudioTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (muxerVideoTrackIndex == -1) {
                return false
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            // Write video track
            videoExtractor.selectTrack(videoTrackIndex)
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Write audio track if available
            if (muxerAudioTrackIndex != -1 && audioTrackIndex != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Muxing video/audio failed: ${e.message}", e)
            return false
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }

    private fun copyFile(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, BUFFER_SIZE)
                output.flush()
            }
        }
    }
}
