package ir.ali0003.downloader.downloader.media3

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import ir.ali0003.downloader.MainActivity
import ir.ali0003.downloader.R
import ir.ali0003.downloader.data.local.AppDatabase
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.repository.DownloadRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class AppDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    500L,
    CHANNEL_ID,
    R.string.app_name,
    0
) {

    companion object {
        private const val TAG = "AppDownloadService"
        const val CHANNEL_ID = "ir.ali0003.downloader.notification.active_downloads"
        const val FOREGROUND_NOTIFICATION_ID = 9002
        private const val JOB_ID = 1001

        fun sendAddDownload(
            context: Context,
            downloadRequest: androidx.media3.exoplayer.offline.DownloadRequest,
            stopReason: Int = Download.STOP_REASON_NONE
        ) {
            sendAddDownload(
                context,
                AppDownloadService::class.java,
                downloadRequest,
                stopReason,
                /* foreground= */ false
            )
        }

        fun sendPauseDownloads(context: Context) {
            sendPauseDownloads(
                context,
                AppDownloadService::class.java,
                /* foreground= */ false
            )
        }

        fun sendResumeDownloads(context: Context) {
            sendResumeDownloads(
                context,
                AppDownloadService::class.java,
                /* foreground= */ false
            )
        }

        fun sendRemoveDownload(context: Context, id: String) {
            sendRemoveDownload(
                context,
                AppDownloadService::class.java,
                id,
                /* foreground= */ false
            )
        }

        fun sendRemoveAllDownloads(context: Context) {
            sendRemoveAllDownloads(
                context,
                AppDownloadService::class.java,
                /* foreground= */ false
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val lastDownloadStats = ConcurrentHashMap<Long, Pair<Long, Long>>() // taskId -> (bytesDownloaded, timestamp)

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            manager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            checkAndManagePolling()

            val taskId = download.request.id.removePrefix("task_").toLongOrNull() ?: return
            if (download.state == Download.STATE_FAILED) {
                val errorMsg = finalException?.message
                    ?: "Media3 download failed (failure code: ${download.failureReason})"
                Log.e(TAG, "Media3 task $taskId failed: $errorMsg", finalException)
                serviceScope.launch {
                    try {
                        val repository = DownloadRepositoryImpl(AppDatabase.getInstance(applicationContext).downloadDao())
                        repository.markFailed(taskId, errorMsg)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating failed status for task $taskId: ${e.message}")
                    }
                }
            }
        }

        override fun onDownloadsPausedChanged(manager: DownloadManager, downloadsPaused: Boolean) {
            checkAndManagePolling()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val dm = getDownloadManager()
            dm.addListener(downloadListener)
            checkAndManagePolling()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register listener on DownloadManager: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            getDownloadManager().removeListener(downloadListener)
        } catch (_: Exception) {}
        pollingJob?.cancel()
        serviceScope.cancel()
    }

    private fun checkAndManagePolling() {
        try {
            val dm = getDownloadManager()
            val hasActive = dm.currentDownloads.any {
                it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING
            }
            if (hasActive) {
                if (pollingJob == null || pollingJob?.isActive != true) {
                    startPollingLoop()
                }
            } else {
                pollingJob?.cancel()
                pollingJob = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking active downloads: ${e.message}")
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            val database = AppDatabase.getInstance(applicationContext)
            val repository = DownloadRepositoryImpl(database.downloadDao())

            while (isActive) {
                try {
                    val dm = getDownloadManager()
                    val downloads = dm.currentDownloads
                    val activeDownloads = downloads.filter { it.state == Download.STATE_DOWNLOADING }

                    if (activeDownloads.isEmpty()) {
                        val anyInFlight = downloads.any {
                            it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING
                        }
                        if (!anyInFlight) {
                            break
                        }
                        delay(500)
                        continue
                    }

                    for (download in activeDownloads) {
                        val taskId = download.request.id.removePrefix("task_").toLongOrNull() ?: continue
                        val bytesDownloaded = download.bytesDownloaded
                        val percentDownloaded = download.percentDownloaded
                        val now = System.currentTimeMillis()

                        val prev = lastDownloadStats[taskId]
                        val dt = if (prev != null) (now - prev.second).coerceAtLeast(1) else 500L
                        val db = if (prev != null) (bytesDownloaded - prev.first).coerceAtLeast(0) else 0L
                        val speed = (db * 1000L / dt)
                        lastDownloadStats[taskId] = Pair(bytesDownloaded, now)

                        val taskEntity = database.downloadDao().findDownloadById(taskId)
                        val totalBytes = if (taskEntity != null && taskEntity.totalBytes > 0L) {
                            taskEntity.totalBytes
                        } else if (download.contentLength > 0L) {
                            download.contentLength
                        } else {
                            0L
                        }

                        val eta = if (speed > 0 && totalBytes > bytesDownloaded) {
                            (totalBytes - bytesDownloaded) / speed
                        } else {
                            0L
                        }

                        // 1. Update Room database with fresh live metrics
                        repository.updateProgress(
                            id = taskId,
                            downloadedBytes = bytesDownloaded,
                            totalBytes = totalBytes,
                            speedBps = speed,
                            etaSeconds = eta
                        )

                        // 2. Update active foreground notification with live percentage and byte count
                        updateLiveNotification(
                            taskName = taskEntity?.fileName ?: "Downloading Media",
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            percent = percentDownloaded,
                            speedBps = speed,
                            etaSeconds = eta
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error in download service polling ticker: ${e.message}")
                }

                delay(500)
            }
        }
    }

    private fun updateLiveNotification(
        taskName: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        percent: Float,
        speedBps: Long,
        etaSeconds: Long
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val percentInt = if (totalBytes > 0) {
            ((bytesDownloaded.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
        } else {
            percent.toInt().coerceIn(0, 100)
        }

        val downloadedFormatted = DownloadTaskEntity.formatFileSize(bytesDownloaded)
        val totalFormatted = if (totalBytes > 0) DownloadTaskEntity.formatFileSize(totalBytes) else "..."
        val speedFormatted = if (speedBps > 0) "${DownloadTaskEntity.formatFileSize(speedBps)}/s" else ""
        val etaFormatted = if (etaSeconds > 0) {
            val mins = etaSeconds / 60
            val secs = etaSeconds % 60
            if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        } else ""

        val contentText = buildString {
            append("$downloadedFormatted / $totalFormatted ($percentInt%)")
            if (speedFormatted.isNotEmpty()) append(" • $speedFormatted")
            if (etaFormatted.isNotEmpty()) append(" • ETA: $etaFormatted")
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(taskName)
            .setContentText(contentText)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (totalBytes > 0) {
            builder.setProgress(100, percentInt, false)
        } else {
            builder.setProgress(100, 0, true)
        }

        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, builder.build())
    }

    override fun getDownloadManager(): DownloadManager {
        return Media3DownloadManagerProvider.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler? {
        return if (Util.SDK_INT >= 21) PlatformScheduler(this, JOB_ID) else null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = DownloadNotificationHelper(this, CHANNEL_ID)
        return helper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
