package ir.ali0003.downloader.downloader.media3

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import ir.ali0003.downloader.browser.model.VideoQualityOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale

/**
 * AndroidX Media3 DownloadHelper wrapper for HLS stream inspection, genuine track selection,
 * and DownloadRequest generation.
 */
@OptIn(UnstableApi::class)
object Media3HlsHelper {

    private const val TAG = "Media3HlsHelper"
    private const val TIMEOUT_MS = 10_000L

    data class HlsMedia3ExtractionResult(
        val qualities: List<VideoQualityOption>,
        val durationSeconds: Double,
        val downloadHelper: DownloadHelper? = null
    )

    /**
     * Prepares a DownloadHelper asynchronously to inspect genuine track groups, resolutions,
     * and bitrates from the HLS manifest.
     */
    suspend fun extractHlsTracks(
        context: Context,
        manifestUrl: String,
        headers: Map<String, String> = emptyMap(),
        fallbackDurationSeconds: Double = 0.0
    ): HlsMedia3ExtractionResult = withContext(Dispatchers.IO) {
        val mediaUri = Uri.parse(manifestUrl)
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        Media3DownloadManagerProvider.registerRequestHeaders(manifestUrl, headers)
        val dataSourceFactory = Media3DownloadManagerProvider.getHttpDataSourceFactory(context, headers)

        val deferred = CompletableDeferred<HlsMedia3ExtractionResult>()
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            var downloadHelper: DownloadHelper? = null
            try {
                downloadHelper = DownloadHelper.forMediaItem(
                    mediaItem,
                    DefaultTrackSelector.Parameters.Builder(context).build(),
                    null,
                    dataSourceFactory
                )

                downloadHelper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper) {
                        try {
                            val options = mutableListOf<VideoQualityOption>()
                            val periodCount = helper.periodCount

                            // Detect highest audio bitrate from separate audio tracks if present
                            var detectedAudioBitrate = 0L
                            var hasAudioTracks = false
                            for (pIdx in 0 until periodCount) {
                                val pTracks = helper.getTracks(pIdx)
                                for (gInfo in pTracks.groups) {
                                    if (gInfo.type == C.TRACK_TYPE_AUDIO) {
                                        hasAudioTracks = true
                                        val aGroup = gInfo.mediaTrackGroup
                                        for (aIdx in 0 until aGroup.length) {
                                            val aFormat = aGroup.getFormat(aIdx)
                                            val aBw = if (aFormat.averageBitrate != androidx.media3.common.Format.NO_VALUE && aFormat.averageBitrate > 0) {
                                                aFormat.averageBitrate.toLong()
                                            } else if (aFormat.bitrate != androidx.media3.common.Format.NO_VALUE && aFormat.bitrate > 0) {
                                                aFormat.bitrate.toLong()
                                            } else 128_000L
                                            if (aBw > detectedAudioBitrate) {
                                                detectedAudioBitrate = aBw
                                            }
                                        }
                                    }
                                }
                            }
                            if (hasAudioTracks && detectedAudioBitrate == 0L) {
                                detectedAudioBitrate = 128_000L
                            }

                            for (periodIndex in 0 until periodCount) {
                                val tracks: Tracks = helper.getTracks(periodIndex)
                                for (trackGroupInfo in tracks.groups) {
                                    val group: TrackGroup = trackGroupInfo.mediaTrackGroup
                                    val trackType = trackGroupInfo.type

                                    if (trackType == C.TRACK_TYPE_VIDEO) {
                                        for (trackIndex in 0 until group.length) {
                                            val format = group.getFormat(trackIndex)
                                            val width = format.width
                                            val height = format.height
                                            // Check AVERAGE-BANDWIDTH first before falling back to BANDWIDTH (peak)
                                            val videoBitrate = if (format.averageBitrate != androidx.media3.common.Format.NO_VALUE && format.averageBitrate > 0) {
                                                format.averageBitrate.toLong()
                                            } else if (format.bitrate != androidx.media3.common.Format.NO_VALUE && format.bitrate > 0) {
                                                format.bitrate.toLong()
                                            } else if (format.peakBitrate != androidx.media3.common.Format.NO_VALUE && format.peakBitrate > 0) {
                                                format.peakBitrate.toLong()
                                            } else {
                                                0L
                                            }

                                            // If both audio and video renditions exist in separate adaptation sets, SUM the bandwidths
                                            val totalBitrate = if (hasAudioTracks && videoBitrate > 0L) {
                                                videoBitrate + detectedAudioBitrate
                                            } else {
                                                videoBitrate
                                            }

                                            val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""
                                            val cleanLabel = when {
                                                height >= 2160 -> "4K UHD"
                                                height >= 1440 -> "1440p 2K"
                                                height >= 1080 -> "1080p FHD"
                                                height >= 720 -> "720p HD"
                                                height >= 480 -> "480p SD"
                                                height >= 360 -> "360p SD"
                                                height > 0 -> "${height}p"
                                                else -> "Stream"
                                            }

                                            // Estimated size calculation: Bitrate (bps) * duration (s) / 8
                                            val effectiveDuration = if (fallbackDurationSeconds > 0) fallbackDurationSeconds else 60.0
                                            val estimatedSize = if (totalBitrate > 0L) {
                                                (totalBitrate * effectiveDuration / 8.0).toLong()
                                            } else {
                                                // Fallback proportional size heuristic by height
                                                when {
                                                    height >= 1080 -> (effectiveDuration * 450_000).toLong()
                                                    height >= 720 -> (effectiveDuration * 250_000).toLong()
                                                    height >= 480 -> (effectiveDuration * 120_000).toLong()
                                                    else -> (effectiveDuration * 60_000).toLong()
                                                }
                                            }

                                            val renditionKey = "p${periodIndex}_video_${width}x${height}_b${totalBitrate}"

                                            options.add(
                                                VideoQualityOption(
                                                    label = cleanLabel,
                                                    resolution = resolution,
                                                    bandwidthBps = totalBitrate,
                                                    url = manifestUrl,
                                                    isHlsVariant = true,
                                                    estimatedSizeBytes = estimatedSize,
                                                    formatTag = "HLS M3U8",
                                                    formatId = format.id,
                                                    renditionKey = renditionKey,
                                                    isExactSize = false
                                                )
                                            )
                                        }
                                    } else if (trackType == C.TRACK_TYPE_AUDIO) {
                                        // Detect isolated audio tracks if available
                                        for (trackIndex in 0 until group.length) {
                                            val format = group.getFormat(trackIndex)
                                            val bitrate = if (format.bitrate > 0) format.bitrate.toLong() else 128_000L
                                            val effectiveDuration = if (fallbackDurationSeconds > 0) fallbackDurationSeconds else 60.0
                                            val estSize = (bitrate * effectiveDuration / 8.0).toLong()

                                            options.add(
                                                VideoQualityOption(
                                                    label = "Audio Only (${format.sampleMimeType?.substringAfterLast('/') ?: "AAC"})",
                                                    resolution = "Audio",
                                                    bandwidthBps = bitrate,
                                                    url = manifestUrl,
                                                    isHlsVariant = true,
                                                    estimatedSizeBytes = estSize,
                                                    formatTag = "AUDIO",
                                                    formatId = format.id,
                                                    renditionKey = "p${periodIndex}_audio_${format.id ?: trackIndex}"
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Sort descending by resolution height, then bitrate
                            val sorted = options.distinctBy { it.renditionKey ?: (it.resolution + it.bandwidthBps) }
                                .sortedWith(
                                    compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
                                        .thenByDescending { it.bandwidthBps }
                                )

                            helper.release()
                            deferred.complete(HlsMedia3ExtractionResult(sorted, fallbackDurationSeconds))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inspecting Media3 tracks: ${e.message}", e)
                            helper.release()
                            deferred.complete(HlsMedia3ExtractionResult(emptyList(), fallbackDurationSeconds))
                        }
                    }

                    override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                        Log.w(TAG, "DownloadHelper prepare failed: ${e.message}")
                        helper.release()
                        deferred.complete(HlsMedia3ExtractionResult(emptyList(), fallbackDurationSeconds))
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating DownloadHelper: ${e.message}", e)
                downloadHelper?.release()
                deferred.complete(HlsMedia3ExtractionResult(emptyList(), fallbackDurationSeconds))
            }
        }

        withTimeoutOrNull(TIMEOUT_MS) {
            deferred.await()
        } ?: run {
            Log.w(TAG, "Media3 HLS track extraction timed out for $manifestUrl")
            HlsMedia3ExtractionResult(emptyList(), fallbackDurationSeconds)
        }
    }

    /**
     * Builds a DownloadRequest targeted strictly to the selected quality rendition.
     */
    suspend fun createDownloadRequest(
        context: Context,
        manifestUrl: String,
        headers: Map<String, String>,
        selectedQuality: VideoQualityOption?,
        customDownloadId: String
    ): DownloadRequest = withContext(Dispatchers.IO) {
        val mediaUri = Uri.parse(manifestUrl)
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        Media3DownloadManagerProvider.registerRequestHeaders(manifestUrl, headers)
        val dataSourceFactory = Media3DownloadManagerProvider.getHttpDataSourceFactory(context, headers)

        val deferred = CompletableDeferred<DownloadRequest>()
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            var helper: DownloadHelper? = null
            try {
                val targetHeight = selectedQuality?.getResolutionHeight() ?: -1
                val targetWidth = selectedQuality?.resolution?.substringBefore("x")?.toIntOrNull() ?: -1
                val targetBitrate = selectedQuality?.bandwidthBps?.toInt() ?: -1

                val trackSelectorParams = DefaultTrackSelector.Parameters.Builder(context).apply {
                    if (targetHeight > 0) {
                        setMaxVideoSize(
                            if (targetWidth > 0) targetWidth else Int.MAX_VALUE,
                            targetHeight
                        )
                        setMinVideoSize(
                            if (targetWidth > 0) (targetWidth * 0.85).toInt() else 0,
                            (targetHeight * 0.85).toInt()
                        )
                    }
                    if (targetBitrate > 0) {
                        setMaxVideoBitrate((targetBitrate * 1.3).toInt())
                    }
                }.build()

                val helperInstance = DownloadHelper.forMediaItem(
                    mediaItem,
                    trackSelectorParams,
                    null,
                    dataSourceFactory
                )
                helper = helperInstance

                helperInstance.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(h: DownloadHelper) {
                        try {
                            // If a specific rendition was selected, configure track selections
                            if (targetHeight > 0) {
                                for (periodIndex in 0 until h.periodCount) {
                                    h.clearTrackSelections(periodIndex)
                                    val tracks = h.getTracks(periodIndex)
                                    for (groupInfo in tracks.groups) {
                                        val group = groupInfo.mediaTrackGroup
                                        if (groupInfo.type == C.TRACK_TYPE_VIDEO) {
                                            var bestTrack = 0
                                            var closestDiff = Int.MAX_VALUE
                                            for (i in 0 until group.length) {
                                                val f = group.getFormat(i)
                                                val diff = kotlin.math.abs(f.height - targetHeight)
                                                if (diff < closestDiff) {
                                                    closestDiff = diff
                                                    bestTrack = i
                                                }
                                            }
                                            h.addTrackSelectionForSingleRenderer(
                                                periodIndex,
                                                groupInfo.type,
                                                DefaultTrackSelector.Parameters.Builder(context).build(),
                                                listOf(
                                                    DefaultTrackSelector.SelectionOverride(
                                                        periodIndex,
                                                        bestTrack
                                                    )
                                                )
                                            )
                                        } else if (groupInfo.type == C.TRACK_TYPE_AUDIO) {
                                            // Always select default audio
                                            h.addTrackSelection(
                                                periodIndex,
                                                DefaultTrackSelector.Parameters.Builder(context).build()
                                            )
                                        }
                                    }
                                }
                            }

                            val request = h.getDownloadRequest(customDownloadId, null)
                            h.release()
                            deferred.complete(request)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get specific download request, falling back to full request: ${e.message}")
                            val fallbackRequest = DownloadRequest.Builder(customDownloadId, mediaUri)
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                            h.release()
                            deferred.complete(fallbackRequest)
                        }
                    }

                    override fun onPrepareError(h: DownloadHelper, e: IOException) {
                        Log.w(TAG, "DownloadHelper prepare failed on request creation: ${e.message}")
                        h.release()
                        val fallbackRequest = DownloadRequest.Builder(customDownloadId, mediaUri)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                        deferred.complete(fallbackRequest)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error in createDownloadRequest: ${e.message}", e)
                helper?.release()
                val fallbackRequest = DownloadRequest.Builder(customDownloadId, mediaUri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                deferred.complete(fallbackRequest)
            }
        }

        withTimeoutOrNull(8_000L) {
            deferred.await()
        } ?: run {
            DownloadRequest.Builder(customDownloadId, mediaUri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        }
    }
}
