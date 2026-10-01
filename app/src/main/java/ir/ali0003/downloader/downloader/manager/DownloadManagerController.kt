package ir.ali0003.downloader.downloader.manager

import android.content.Context
import android.util.Log
import ir.ali0003.downloader.data.local.AppDatabase
import ir.ali0003.downloader.data.local.DownloadDao
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.model.DownloadStatus
import ir.ali0003.downloader.downloader.core.DownloadEngine
import ir.ali0003.downloader.downloader.model.DownloadProgress
import ir.ali0003.downloader.downloader.service.DownloadForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Background Queue Controller:
 * - Regulates active download concurrency (e.g. max 2-3 parallel jobs, remaining stay QUEUED).
 * - Coordinates DownloadEngine execution, Room synchronization, and ForegroundService updates.
 */
class DownloadManagerController(
    private val context: Context,
    private val downloadDao: DownloadDao = AppDatabase.getInstance(context).downloadDao()
) {

    companion object {
        private const val TAG = "DownloadManagerCtrl"
        private const val MAX_CONCURRENT_DOWNLOADS = 2

        @Volatile
        private var INSTANCE: DownloadManagerController? = null

        fun getInstance(context: Context): DownloadManagerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManagerController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadEngine = DownloadEngine(context)

    // Map of taskId -> Active Coroutine Job
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // Real-time progress map for UI observation
    private val _taskProgressMap = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val taskProgressMap: StateFlow<Map<Long, DownloadProgress>> = _taskProgressMap.asStateFlow()

    init {
        // Observe queued and downloading tasks from Room
        scope.launch {
            downloadDao.getActiveDownloads().collectLatest { tasks ->
                processQueue(tasks)
            }
        }
    }

    private suspend fun processQueue(tasks: List<DownloadTaskEntity>) {
        val currentlyRunning = tasks.filter { it.status == DownloadStatus.DOWNLOADING && activeJobs.containsKey(it.id) }
        val availableSlots = MAX_CONCURRENT_DOWNLOADS - currentlyRunning.size

        if (availableSlots > 0) {
            val nextInQueue = tasks.filter { it.status == DownloadStatus.QUEUED && !activeJobs.containsKey(it.id) }
                .take(availableSlots)

            for (task in nextInQueue) {
                startTask(task)
            }
        }

        // Check if any service should be kept alive
        if (activeJobs.isNotEmpty()) {
            DownloadForegroundService.startService(context)
        }
    }

    fun startTask(task: DownloadTaskEntity) {
        if (activeJobs.containsKey(task.id)) return

        val job = scope.launch {
            try {
                downloadDao.updateStatus(task.id, DownloadStatus.DOWNLOADING)

                val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val appPublicDir = File(publicDir, "0003_Downloader")
                val destDir = if (task.isHidden) {
                    File(context.filesDir, "vault_media")
                } else if (appPublicDir.exists() || appPublicDir.mkdirs()) {
                    appPublicDir
                } else {
                    File(context.getExternalFilesDir(null), "downloads")
                }
                if (!destDir.exists()) destDir.mkdirs()

                val rawSanitized = task.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val urlLower = task.url.lowercase()
                val isExplicitMp4 = urlLower.contains(".mp4") || urlLower.contains(".webm") || urlLower.contains(".mkv") ||
                        urlLower.contains(".mp3") || task.mimeType.contains("video/mp4", ignoreCase = true) ||
                        task.mimeType.contains("audio/", ignoreCase = true) || rawSanitized.endsWith(".mp4", ignoreCase = true)
                val isHlsStream = !isExplicitMp4 && (task.isM3u8 || urlLower.contains(".m3u8") || task.mimeType.contains("mpegurl", ignoreCase = true))
                val sanitizedName = if (isHlsStream) {
                    if (rawSanitized.endsWith(".mp4", ignoreCase = true)) {
                        rawSanitized.substringBeforeLast('.') + ".ts"
                    } else if (!rawSanitized.endsWith(".ts", ignoreCase = true)) {
                        "$rawSanitized.ts"
                    } else {
                        rawSanitized
                    }
                } else {
                    if (!rawSanitized.contains(".") || rawSanitized.endsWith(".")) {
                        val defaultExt = if (task.mimeType.contains("audio") || task.fileName.endsWith(".mp3")) ".mp3" else ".mp4"
                        "${rawSanitized.trimEnd('.')}$defaultExt"
                    } else {
                        rawSanitized
                    }
                }
                val outputFile = File(destDir, sanitizedName)
                downloadDao.updateLocalFilePath(task.id, outputFile.absolutePath)
                downloadDao.updateDownloadPath(task.id, outputFile.absolutePath)

                Log.d(TAG, "Starting task ${task.id} (${task.fileName}) -> ${outputFile.absolutePath}")

                downloadEngine.startDownload(task, outputFile).collect { progress ->
                    // Update state map
                    val currentMap = _taskProgressMap.value.toMutableMap()
                    currentMap[task.id] = progress
                    _taskProgressMap.value = currentMap

                    // Update Room at throttled cadence
                    downloadDao.updateProgress(
                        id = task.id,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        speedBps = progress.speedBps,
                        etaSeconds = progress.etaSeconds
                    )

                    // Forward notification progress to foreground service
                    val percent = (progress.progress * 100).toInt()
                    val speedText = if (progress.speedBps > 0) "${DownloadTaskEntity.formatFileSize(progress.speedBps)}/s" else ""
                    val etaText = progress.formattedEta
                    DownloadForegroundService.updateProgress(
                        context = context,
                        taskName = task.fileName,
                        progressPercent = percent,
                        speedText = speedText,
                        etaText = etaText,
                        taskId = task.id,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        currentSegment = progress.currentSegment,
                        totalSegments = progress.totalSegments
                    )

                    if (progress.isCompleted) {
                        val vaultFileManager = ir.ali0003.downloader.data.vault.VaultFileManager(context, downloadDao)
                        val finalFile = if (outputFile.exists() && outputFile.length() > 0L) {
                            outputFile
                        } else {
                            vaultFileManager.resolveTaskFile(task) ?: outputFile
                        }
                        val finalBytes = if (finalFile.exists() && finalFile.length() > 0L) finalFile.length() else progress.downloadedBytes
                        val finalPath = finalFile.absolutePath

                        if (!task.isHidden && finalFile.exists()) {
                            try {
                                val mimeType = if (finalPath.endsWith(".ts", ignoreCase = true)) "video/mp2t" else "video/mp4"
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(finalPath),
                                    arrayOf(mimeType)
                                ) { path, uri ->
                                    Log.d(TAG, "MediaScanner indexed completed file: $path -> $uri ($mimeType)")
                                }
                            } catch (_: Exception) {}
                        }

                        // Atomically mark completed in Room with the verified physical file path and exact size
                        downloadDao.updateDownloadPath(task.id, finalPath)
                        downloadDao.markCompletedWithFile(task.id, finalPath, finalBytes)

                        val updatedMap = _taskProgressMap.value.toMutableMap()
                        updatedMap.remove(task.id)
                        _taskProgressMap.value = updatedMap
                        activeJobs.remove(task.id)
                    } else if (progress.isFailed) {
                        Log.e(TAG, "Task ${task.id} failed: ${progress.errorMessage}")
                        downloadDao.markFailed(task.id, progress.errorMessage ?: "Download failed")
                        activeJobs.remove(task.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error for task ${task.id}: ${e.message}", e)
                downloadDao.markFailed(task.id, e.message ?: "Download failed")
                activeJobs.remove(task.id)
            }
        }

        activeJobs[task.id] = job
    }

    fun pauseTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            val task = downloadDao.findDownloadById(taskId)
            if (task != null) {
                downloadEngine.pauseDownload(task)
            }
            downloadDao.updateStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    fun resumeTask(taskId: Long) {
        scope.launch {
            val task = downloadDao.findDownloadById(taskId)
            if (task != null) {
                downloadEngine.resumeDownload(task)
                downloadDao.updateStatus(taskId, DownloadStatus.QUEUED)
            }
        }
    }

    fun cancelTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            val task = downloadDao.findDownloadById(taskId)
            if (task != null) {
                downloadEngine.cancelDownload(task)
            }
            downloadDao.deleteDownloadById(taskId)
        }
    }
}
