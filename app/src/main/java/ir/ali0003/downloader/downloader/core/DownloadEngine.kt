package ir.ali0003.downloader.downloader.core

import android.content.Context
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.media3.Media3HlsDownloader
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Strict Dual-Engine Routing DownloadEngine:
 * 1. Progressive Direct MP4/MKV/WebM files: Routed to high-speed multi-threaded ChunkDownloader
 * 2. HLS (.m3u8) streams: Routed to official AndroidX Media3 offline DownloadManager/DownloadService
 * 3. DASH / Separated Audio+Video Streams: Routed to DashStreamDownloader with HardwareMediaMuxer
 */
class DownloadEngine(
    private val context: Context
) {
    private val chunkDownloader = ChunkDownloader(context)
    private val media3HlsDownloader = Media3HlsDownloader(context)
    private val dashDownloader = DashStreamDownloader(context, chunkDownloader)

    fun startDownload(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> {
        val isDash = dashDownloader.isDashOrSeparatedPair(task)
        val isHls = !isDash && (task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true))

        return when {
            isDash -> dashDownloader.downloadAndMux(task, outputFile)
            isHls -> media3HlsDownloader.downloadHls(task, outputFile)
            else -> chunkDownloader.download(task, outputFile)
        }
    }

    fun pauseDownload(task: DownloadTaskEntity) {
        val isHls = task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true)
        if (isHls) {
            media3HlsDownloader.pauseHls(task.id)
        }
    }

    fun resumeDownload(task: DownloadTaskEntity) {
        val isHls = task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true)
        if (isHls) {
            media3HlsDownloader.resumeHls(task.id)
        }
    }

    fun cancelDownload(task: DownloadTaskEntity) {
        val isHls = task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true)
        if (isHls) {
            media3HlsDownloader.cancelHls(task.id)
        }
    }
}


