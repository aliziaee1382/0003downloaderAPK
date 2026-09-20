package ir.ali0003.downloader.downloader.muxer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
 * Lightweight, native audio-video multiplexer (Muxer) utilizing `androidx.media3.transformer`
 * to combine separate DASH / high-definition video streams and audio streams into a single
 * synchronized MP4 container without needing external FFmpeg binaries.
 *
 * Includes an automated fallback to Android system `MediaMuxer` for zero-re-encoding repacking.
 */
class DashStreamMuxer(
    private val context: Context
) {

    companion object {
        private const val TAG = "DashStreamMuxer"

        @Volatile
        private var INSTANCE: DashStreamMuxer? = null

        fun getInstance(context: Context): DashStreamMuxer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DashStreamMuxer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Combines the visual track from [videoFile] and audio track from [audioFile]
     * into [outputFile] (.mp4) using Media3 Transformer.
     *
     * @param videoFile Scratch file containing the downloaded video stream (MP4/WebM/M4S).
     * @param audioFile Scratch file containing the downloaded audio stream (M4A/MP3/M4S).
     * @param outputFile Destination synchronized MP4 container.
     * @param onProgress Callback emitting real-time muxing progress fraction [0.0f .. 1.0f].
     * @return True if muxing succeeded and output file exists with positive length.
     */
    suspend fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.Main) {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.e(TAG, "Video scratch file missing or empty: ${videoFile.absolutePath}")
            return@withContext false
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "Audio scratch file missing or empty: ${audioFile.absolutePath}")
            return@withContext false
        }

        // Ensure parent directory exists and clear existing output
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        Log.d(TAG, "Starting Media3 Transformer muxing: video=${videoFile.name} (${videoFile.length()} bytes), audio=${audioFile.name} (${audioFile.length()} bytes)")

        val completionDeferred = CompletableDeferred<Boolean>()

        // 1. Build EditedMediaItems separating visual and acoustic tracks
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(videoFile)))
            .setRemoveAudio(true)
            .build()
        val videoSequence = EditedMediaItemSequence(listOf(videoItem))

        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audioFile)))
            .setRemoveVideo(true)
            .build()
        val audioSequence = EditedMediaItemSequence(listOf(audioItem))

        // 2. Build Composition orchestrating parallel audio-video sequences
        val composition = Composition.Builder(listOf(videoSequence, audioSequence))
            .build()

        var activeTransformer: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.d(TAG, "Media3 Transformer completed successfully -> ${outputFile.name} (${outputFile.length()} bytes)")
                if (!completionDeferred.isCompleted) {
                    completionDeferred.complete(true)
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                Log.w(TAG, "Media3 Transformer error: ${exportException.message}", exportException)
                if (!completionDeferred.isCompleted) {
                    completionDeferred.complete(false)
                }
            }
        }

        val transformer = Transformer.Builder(context)
            .addListener(listener)
            .build()
        activeTransformer = transformer

        // Start asynchronous export
        try {
            transformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Media3 Transformer: ${e.message}", e)
            completionDeferred.complete(false)
        }

        // 3. Launch polling coroutine to track progress
        val progressJob = launch(Dispatchers.Main) {
            val progressHolder = ProgressHolder()
            while (isActive && !completionDeferred.isCompleted) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val progressFraction = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                    onProgress?.invoke(progressFraction)
                }
                delay(150)
            }
        }

        val success = try {
            val result = completionDeferred.await()
            progressJob.cancel()
            result && outputFile.exists() && outputFile.length() > 0L
        } catch (cancellation: CancellationException) {
            Log.i(TAG, "Muxing cancelled by coroutine scope")
            progressJob.cancel()
            activeTransformer.cancel()
            if (outputFile.exists()) outputFile.delete()
            cleanupScratchFiles(videoFile, audioFile)
            throw cancellation
        }

        if (success) {
            onProgress?.invoke(1.0f)
            cleanupScratchFiles(videoFile, audioFile)
            return@withContext true
        } else {
            Log.w(TAG, "Media3 Transformer failed or generated empty output. Attempting zero-re-encoding MediaMuxer fallback...")
            val fallbackResult = withContext(Dispatchers.IO) {
                fallbackMediaMuxer(videoFile, audioFile, outputFile, onProgress)
            }
            if (fallbackResult) {
                cleanupScratchFiles(videoFile, audioFile)
                return@withContext true
            } else {
                if (outputFile.exists()) outputFile.delete()
                return@withContext false
            }
        }
    }

    /**
     * Hardware-accelerated, zero-transcoding direct sample muxer using Android `MediaExtractor`
     * and `MediaMuxer`. Serves as an ultra-fast fallback when video and audio containers
     * match standard MP4/AAC/AVC/HEVC streams.
     */
    private fun fallbackMediaMuxer(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            if (outputFile.exists()) outputFile.delete()

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            // Find visual track
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

            // Find audio track
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
                Log.e(TAG, "MediaMuxer fallback: No video track found in ${videoFile.name}")
                return false
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.e(TAG, "MediaMuxer fallback: No audio track found in ${audioFile.name}")
                return false
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = 1024 * 1024 // 1MB buffer
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // 1. Copy Video Samples
            videoExtractor.selectTrack(videoTrackIndex)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }
            onProgress?.invoke(0.5f)

            // 2. Copy Audio Samples
            audioExtractor.selectTrack(audioTrackIndex)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            onProgress?.invoke(1.0f)
            Log.d(TAG, "MediaMuxer fallback succeeded -> ${outputFile.name} (${outputFile.length()} bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "MediaMuxer fallback failed: ${e.message}", e)
            return false
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Cleans up temporary intermediate scratch files after successful muxing.
     */
    private fun cleanupScratchFiles(videoFile: File, audioFile: File) {
        try {
            if (videoFile.exists()) {
                videoFile.delete()
                Log.d(TAG, "Cleaned up temporary video scratch: ${videoFile.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete video scratch: ${e.message}")
        }

        try {
            if (audioFile.exists()) {
                audioFile.delete()
                Log.d(TAG, "Cleaned up temporary audio scratch: ${audioFile.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete audio scratch: ${e.message}")
        }
    }
}
