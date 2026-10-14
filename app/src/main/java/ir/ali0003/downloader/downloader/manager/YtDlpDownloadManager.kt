package ir.ali0003.downloader.downloader.manager

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import ir.ali0003.downloader.data.local.AppDatabase
import ir.ali0003.downloader.data.local.DownloadDao
import ir.ali0003.downloader.data.model.DownloadStatus
import ir.ali0003.downloader.downloader.core.ChunkDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ultra-stable media download and processing engine based on ytdlnis architecture:
 * - Powered by youtubedl-android (yt-dlp core + FFmpeg remuxing)
 * - Automatically remuxes HLS/TS/DASH streams into standard MP4 with FastStart (moov atom upfront)
 * - Real-time progress updates directly synced to Room DownloadDao
 * - Clean process lifecycle control with destroyProcessById
 */
class YtDlpDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao = AppDatabase.getInstance(context).downloadDao()
) {

    companion object {
        private const val TAG = "YtDlpDownloadManager"

        @Volatile
        private var INSTANCE: YtDlpDownloadManager? = null

        fun getInstance(context: Context): YtDlpDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: YtDlpDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Executes yt-dlp download pipeline:
     * - Configures YoutubeDLRequest with --remux-video mp4, --no-mtime, --concurrent-fragments 5
     * - Hooks real-time progress callback to DownloadDao.updateProgress
     * - On completion, deterministically marks status as COMPLETED and records final local file path and size.
     */
    suspend fun download(
        taskId: Long,
        url: String,
        outputDir: File,
        fileName: String,
        headersJson: String? = null,
        onProgress: ((Float, Long, Long, Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val baseName = fileName.substringBeforeLast('.').ifBlank { "media_$taskId" }
            val cleanName = baseName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val expectedOutputFile = File(outputDir, "$cleanName.mp4")

            val request = YoutubeDLRequest(url).apply {
                addOption("-o", "${outputDir.absolutePath}/$cleanName.%(ext)s")
                addOption("--remux-video", "mp4")
                addOption("--no-mtime")
                addOption("--concurrent-fragments", 5)

                if (!headersJson.isNullOrBlank()) {
                    try {
                        val headers = ChunkDownloader.parseHeaders(headersJson, url)
                        headers.forEach { (key, value) ->
                            if (key.isNotBlank() && value.isNotBlank()) {
                                addOption("--add-header", "$key:$value")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Header injection notice for task $taskId: ${e.message}")
                    }
                }
            }

            val processId = taskId.toString()
            var lastReportedTime = 0L

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
                val now = System.currentTimeMillis()
                if (now - lastReportedTime > 250L || progress >= 99f) {
                    lastReportedTime = now
                    val normalizedPercent = progress.coerceIn(0f, 100f)
                    val downloadedBytes = if (expectedOutputFile.exists()) expectedOutputFile.length() else 0L
                    val estTotal = if (downloadedBytes > 0L && normalizedPercent > 0f) {
                        (downloadedBytes / (normalizedPercent / 100f)).toLong()
                    } else 0L

                    managerScope.launch {
                        try {
                            downloadDao.updateProgress(
                                id = taskId,
                                downloadedBytes = downloadedBytes,
                                totalBytes = estTotal,
                                speedBps = 0L,
                                etaSeconds = etaInSeconds
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update progress in Dao: ${e.message}")
                        }
                    }
                    onProgress?.invoke(normalizedPercent, downloadedBytes, estTotal, etaInSeconds)
                }
            }

            // Locate final remuxed file
            val finalFile = if (expectedOutputFile.exists() && expectedOutputFile.length() > 0L) {
                expectedOutputFile
            } else {
                outputDir.listFiles()?.firstOrNull { it.name.startsWith(cleanName) && it.length() > 0L }
                    ?: expectedOutputFile
            }

            val finalBytes = if (finalFile.exists()) finalFile.length() else 0L
            val finalPath = finalFile.absolutePath

            // Deterministic status update in Room
            downloadDao.updateStatus(taskId, DownloadStatus.COMPLETED)
            downloadDao.updateDownloadPath(taskId, finalPath)
            downloadDao.markCompletedWithFile(taskId, finalPath, finalBytes)

            Log.i(TAG, "Task $taskId completed successfully: $finalPath ($finalBytes bytes)")
            Result.success(finalFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for task $taskId: ${e.message}", e)
            downloadDao.markFailed(taskId, e.message ?: "yt-dlp execution error")
            Result.failure(e)
        }
    }

    /**
     * Cancels the active yt-dlp process using destroyProcessById
     */
    fun destroyProcessById(taskId: Long): Boolean {
        return try {
            YoutubeDL.getInstance().destroyProcessById(taskId.toString())
        } catch (e: Exception) {
            Log.w(TAG, "destroyProcessById error for task $taskId: ${e.message}")
            false
        }
    }
}
