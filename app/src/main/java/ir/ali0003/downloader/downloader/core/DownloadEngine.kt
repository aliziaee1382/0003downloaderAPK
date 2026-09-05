package ir.ali0003.downloader.downloader.core

import android.content.Context
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Unified DownloadEngine routing dynamically between ChunkDownloader (Range MP4)
 * and HlsSegmentDownloader (M3U8 Playlists).
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
        return if (isHls) {
            hlsDownloader.downloadHls(task, outputFile)
        } else {
            chunkDownloader.download(task, outputFile)
        }
    }
}
