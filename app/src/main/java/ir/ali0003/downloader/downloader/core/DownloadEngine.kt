package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.util.Log
import android.widget.Toast
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
    private val dashDownloader = DashStreamDownloader(context)

    fun startDownload(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> {
        val urlLower = task.url.lowercase()
        val isDash = task.url.contains("|") || urlLower.contains(".mpd") || task.mimeType.contains("dash", ignoreCase = true)
        val isHls = task.isM3u8 || urlLower.contains(".m3u8") || task.mimeType.contains("mpegurl", ignoreCase = true)

        val selectedDownloader = when {
            isDash -> "DashStreamDownloader"
            isHls -> "HlsDownloader"
            else -> "ChunkDownloader"
        }

        // Diagnostic error-level log clearly recording routing decision and key parameters
        Log.e(
            "DownloadRouting",
            "Routing task ${task.id} [${task.fileName}]: selectedDownloader=$selectedDownloader, isM3u8=${task.isM3u8}, mimeType='${task.mimeType}', url='${task.url}'"
        )

        // Show a brief Toast on main thread so destination downloader is immediately visible on screen
        try {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    "Routed to: $selectedDownloader\nFile: ${task.fileName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.w("DownloadRouting", "Failed to display routing toast: ${e.message}")
        }

        return when {
            isDash -> dashDownloader.downloadDash(task, outputFile)
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


