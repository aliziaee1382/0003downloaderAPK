package ir.ali0003.downloader.downloader.core

import android.content.Context
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Unified DownloadEngine routing dynamically between:
 * 1. DashStreamDownloader / HardwareMediaMuxer: DASH / Separated Audio+Video Streams with HardwareMediaMuxer
 * 2. HlsSegmentDownloader: M3U8 Playlists and HLS variant chunks (.ts/.m4s)
 * 3. ChunkDownloader: Multi-threaded parallel HTTP Range slicing (4-8 workers) for direct MP4/MKV/WebM files
 */
class DownloadEngine(
    private val context: Context
) {
    private val chunkDownloader = ChunkDownloader(context)
    private val hlsDownloader = HlsSegmentDownloader(context)
    private val dashDownloader = DashStreamDownloader(context, chunkDownloader)

    fun startDownload(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> {
        val isDash = dashDownloader.isDashOrSeparatedPair(task)
        val isHls = !isDash && (task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true))

        return when {
            isDash -> dashDownloader.downloadAndMux(task, outputFile)
            isHls -> hlsDownloader.downloadHls(task, outputFile)
            else -> chunkDownloader.download(task, outputFile)
        }
    }
}

