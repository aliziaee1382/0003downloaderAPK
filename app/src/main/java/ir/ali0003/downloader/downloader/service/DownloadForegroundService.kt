package ir.ali0003.downloader.downloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import ir.ali0003.downloader.MainActivity
import ir.ali0003.downloader.R
import ir.ali0003.downloader.data.local.DownloadTaskEntity

/**
 * Foreground Service managing active downloads with throttled notification updates
 * and Play Store compliant foregroundServiceType="dataSync".
 */
class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ir.ali0003.downloader.notification.active_downloads"
        const val CHANNEL_NAME = "0003 Downloader Active Downloads"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_SERVICE = "ir.ali0003.downloader.action.START_SERVICE"
        const val ACTION_PAUSE = "ir.ali0003.downloader.action.PAUSE_TASK"
        const val ACTION_RESUME = "ir.ali0003.downloader.action.RESUME_TASK"
        const val ACTION_CANCEL = "ir.ali0003.downloader.action.CANCEL_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initialNotification = buildNotification(
            title = "0003 Downloader",
            content = "Download engine ready",
            progress = 0,
            speedText = "",
            isIndeterminate = true
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, foregroundType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val taskId = it.getLongExtra(EXTRA_TASK_ID, -1L)
            when (it.action) {
                ACTION_PAUSE -> {
                    // Handled by DownloadManagerController broadcast or controller binding
                }
                ACTION_RESUME -> {
                    // Handled by controller
                }
                ACTION_CANCEL -> {
                    // Handled by controller
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress and speed for video downloads"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Updates notification with 500ms throttling to avoid OS rate-limiting/jank.
     */
    fun updateDownloadProgress(
        taskName: String,
        progressPercent: Int,
        speedText: String,
        etaText: String,
        taskId: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime < 500L && progressPercent < 100) {
            return
        }
        lastNotificationUpdateTime = now

        val content = if (speedText.isNotEmpty()) "$progressPercent% • $speedText • ETA: $etaText" else "$progressPercent%"
        val notification = buildNotification(
            title = taskName,
            content = content,
            progress = progressPercent,
            speedText = speedText,
            isIndeterminate = false,
            taskId = taskId
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        speedText: String,
        isIndeterminate: Boolean,
        taskId: Long? = null
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isIndeterminate) {
            builder.setProgress(100, 0, true)
        } else {
            builder.setProgress(100, progress, false)
        }

        if (taskId != null) {
            val pauseIntent = Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val pendingPause = PendingIntent.getService(
                this,
                (taskId * 10 + 1).toInt(),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
        }

        return builder.build()
    }
}
