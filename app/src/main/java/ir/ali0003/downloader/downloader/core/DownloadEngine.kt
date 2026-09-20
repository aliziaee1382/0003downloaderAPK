package ir.ali0003.downloader.downloader.core

import android.content.Context
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Unified DownloadEngine routing dynamically between:
 * 1. YoutubeDLDownloader: Native yt-dlp & FFmpeg execution with automatic MP4 muxing
 * 2. DashStreamDownloader: DASH / Separated Audio+Video Streams with Media3 Transformer Muxing
 * 3. HlsSegmentDownloader: M3U8 Playlists and HLS variant chunks
 * 4. ChunkDownloader: Multi-threaded HTTP Range slicing for direct MP4/MKV files
 */
class DownloadEngine(
    private val context: Context
) {
    private val youtubeDlDownloader = YoutubeDLDownloader(context)
    private val chunkDownloader = ChunkDownloader(context)
    private val hlsDownloader = HlsSegmentDownloader(context)
    private val dashDownloader = DashStreamDownloader(context, chunkDownloader)

    fun startDownload(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> {
        val isYtdl = YoutubeDLDownloader.isYoutubeDlTask(task)
        val isDash = !isYtdl && dashDownloader.isDashOrSeparatedPair(task)
        val isHls = !isYtdl && !isDash && (task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true))

        return when {
            isYtdl -> youtubeDlDownloader.download(task, outputFile)
            isDash -> dashDownloader.downloadAndMux(task, outputFile)
            isHls -> hlsDownloader.downloadHls(task, outputFile)
            else -> chunkDownloader.download(task, outputFile)
        }
    }
}
