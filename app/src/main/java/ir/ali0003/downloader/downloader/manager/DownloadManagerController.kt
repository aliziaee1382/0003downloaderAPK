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

                val destDir = if (task.isHidden) {
                    File(context.filesDir, "vault_media")
                } else {
                    File(context.getExternalFilesDir(null), "downloads")
                }
                if (!destDir.exists()) destDir.mkdirs()

                val sanitizedName = task.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val outputFile = File(destDir, sanitizedName)

                Log.d(TAG, "Starting task ${task.id} (${task.fileName}) -> ${outputFile.absolutePath}")

                downloadEngine.startDownload(task, outputFile).collect { progress ->
                    // Update state map
                    val currentMap = _taskProgressMap.value.toMutableMap()
                    currentMap[task.id] = progress
                    _taskProgressMap.value = currentMap

                    // Update Room at throttled cadence
                    downloadDao.updateProgress(
                        task.id,
                        progress.downloadedBytes,
                        progress.totalBytes,
                        progress.speedBps
                    )

                    if (progress.isCompleted) {
                        downloadDao.markCompleted(task.id, System.currentTimeMillis())
                        activeJobs.remove(task.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error for task ${task.id}: ${e.message}", e)
                downloadDao.markFailed(task.id)
                activeJobs.remove(task.id)
            }
        }

        activeJobs[task.id] = job
    }

    fun pauseTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            downloadDao.updateStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    fun resumeTask(taskId: Long) {
        scope.launch {
            val task = downloadDao.findDownloadById(taskId)
            if (task != null) {
                downloadDao.updateStatus(taskId, DownloadStatus.QUEUED)
            }
        }
    }

    fun cancelTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            downloadDao.deleteDownloadById(taskId)
        }
    }
}
