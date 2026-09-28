package ir.ali0003.downloader.downloader.core

import android.content.Context
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Clean Direct Stream Pipeline DownloadEngine:
 * 1. HLS (.m3u8) streams: Routed directly to HlsSegmentDownloader (streams MPEG-TS segments directly to .ts container)
 * 2. Progressive Direct MP4/MKV/WebM/Audio files: Routed to high-speed multi-threaded ChunkDownloader
 */
class DownloadEngine(
    private val context: Context
) {
    private val chunkDownloader = ChunkDownloader(context)
    private val hlsDownloader = HlsSegmentDownloader(context)

    fun startDownload(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> {
        val isHls = task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true)

        return when {
            isHls -> hlsDownloader.downloadHls(task, outputFile)
            else -> chunkDownloader.download(task, outputFile)
        }
    }

    fun pauseDownload(task: DownloadTaskEntity) {
        // Cancellation handled cooperatively by active coroutine job
    }

    fun resumeDownload(task: DownloadTaskEntity) {
        // Resumption handled by controller queue
    }

    fun cancelDownload(task: DownloadTaskEntity) {
        // Cancellation handled cooperatively by active coroutine job
    }
}


