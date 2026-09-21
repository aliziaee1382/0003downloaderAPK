package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * 100% on-device Hardware Media Muxer using AndroidX Media3 Transformer and native android.media.MediaMuxer.
 * Combines separated video and audio tracks into a single playback-ready .mp4 container
 * with zero native NDK binaries or heavy external dependencies.
 */
class HardwareMediaMuxer(
    private val context: Context
) {
    companion object {
        private const val TAG = "HardwareMediaMuxer"

        @Volatile
        private var INSTANCE: HardwareMediaMuxer? = null

        fun getInstance(context: Context): HardwareMediaMuxer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HardwareMediaMuxer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Combines the visual track from [videoFile] and audio track from [audioFile]
     * into [outputFile] (.mp4) using Android hardware acceleration.
     *
     * @param videoFile Scratch file containing the downloaded video stream (MP4/WebM/M4S).
     * @param audioFile Scratch file containing the downloaded audio stream (M4A/MP3/M4S).
     * @param outputFile Destination synchronized MP4 container.
     * @param onProgress Optional callback emitting real-time muxing progress fraction [0.0f .. 1.0f].
     * @return True if muxing succeeded and output file exists with positive length.
     */
    suspend fun muxAudioAndVideo(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.e(TAG, "Video scratch file missing or empty: ${videoFile.absolutePath}")
            return@withContext false
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "Audio scratch file missing or empty: ${audioFile.absolutePath}")
            return@withContext false
        }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        Log.d(TAG, "Starting Hardware Media Muxing: video=${videoFile.name} (${videoFile.length()} B), audio=${audioFile.name} (${audioFile.length()} B)")

        // 1. Fast path: Direct hardware sample remuxing using Android's native MediaMuxer (zero re-encoding)
        val fastSuccess = tryDirectHardwareMux(videoFile, audioFile, outputFile, onProgress)
        if (fastSuccess && outputFile.exists() && outputFile.length() > 0L) {
            Log.i(TAG, "Hardware MediaMuxer direct remux succeeded -> ${outputFile.name} (${outputFile.length()} bytes)")
            onProgress?.invoke(1.0f)
            return@withContext true
        }

        // 2. Fallback path: Media3 Transformer for complex format adaptation
        Log.i(TAG, "Attempting Media3 Transformer muxing pipeline...")
        val media3Success = withContext(Dispatchers.Main) {
            runMedia3Transformer(videoFile, audioFile, outputFile, onProgress)
        }

        if (media3Success && outputFile.exists() && outputFile.length() > 0L) {
            Log.i(TAG, "Media3 Transformer muxing succeeded -> ${outputFile.name} (${outputFile.length()} bytes)")
            onProgress?.invoke(1.0f)
            true
        } else {
            Log.e(TAG, "Both hardware MediaMuxer and Media3 Transformer pipelines failed")
            if (outputFile.exists()) outputFile.delete()
            false
        }
    }

    /**
     * Direct hardware sample remuxing using Android's native MediaMuxer & MediaExtractor.
     * Extracts compressed bitstreams from video & audio containers and writes to MP4 without re-encoding.
     */
    private fun tryDirectHardwareMux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            if (outputFile.exists()) outputFile.delete()

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            // Locate video track
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            // Locate audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                Log.w(TAG, "Hardware mux: No video track found in ${videoFile.name}")
                return false
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.w(TAG, "Hardware mux: No audio track found in ${audioFile.name}")
                return false
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = 1024 * 1024 // 1 MB buffer
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Select video track & pump samples
            videoExtractor.selectTrack(videoTrackIndex)
            val totalEstimatedVideoBytes = videoFile.length().coerceAtLeast(1L)
            var bytesPumpedVideo = 0L

            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                bytesPumpedVideo += sampleSize
                onProgress?.invoke((bytesPumpedVideo.toFloat() / totalEstimatedVideoBytes * 0.5f).coerceIn(0f, 0.5f))
                videoExtractor.advance()
            }

            // Select audio track & pump samples
            audioExtractor.selectTrack(audioTrackIndex)
            val totalEstimatedAudioBytes = audioFile.length().coerceAtLeast(1L)
            var bytesPumpedAudio = 0L

            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags

                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                bytesPumpedAudio += sampleSize
                onProgress?.invoke((0.5f + (bytesPumpedAudio.toFloat() / totalEstimatedAudioBytes * 0.5f)).coerceIn(0.5f, 0.99f))
                audioExtractor.advance()
            }

            return true
        } catch (e: Exception) {
            Log.w(TAG, "Hardware MediaMuxer direct remux encountered: ${e.message}")
            return false
        } finally {
            try {
                videoExtractor?.release()
                audioExtractor?.release()
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Media3 Transformer execution running on the Main thread.
     */
    private suspend fun runMedia3Transformer(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        val completionDeferred = CompletableDeferred<Boolean>()

        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(videoFile)))
            .setRemoveAudio(true)
            .build()
        val videoSequence = EditedMediaItemSequence(listOf(videoItem))

        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audioFile)))
            .setRemoveVideo(true)
            .build()
        val audioSequence = EditedMediaItemSequence(listOf(audioItem))

        val composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (!completionDeferred.isCompleted) {
                    completionDeferred.complete(true)
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                Log.w(TAG, "Media3 Transformer error: ${exportException.message}")
                if (!completionDeferred.isCompleted) {
                    completionDeferred.complete(false)
                }
            }
        }

        val transformer = Transformer.Builder(context)
            .addListener(listener)
            .build()

        try {
            transformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Media3 Transformer", e)
            return false
        }

        // Monitor progress
        var progressActive = true
        val progressJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            val progressHolder = ProgressHolder()
            while (progressActive && !completionDeferred.isCompleted) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val progressFraction = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                    onProgress?.invoke(progressFraction)
                }
                delay(150)
            }
        }

        return try {
            val result = completionDeferred.await()
            progressActive = false
            progressJob.cancel()
            result && outputFile.exists() && outputFile.length() > 0L
        } catch (c: CancellationException) {
            progressActive = false
            progressJob.cancel()
            transformer.cancel()
            throw c
        }
    }
}
