package ir.ali0003.downloader.downloader.media3

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import ir.ali0003.downloader.R

@OptIn(UnstableApi::class)
class AppDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    0
) {

    companion object {
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
