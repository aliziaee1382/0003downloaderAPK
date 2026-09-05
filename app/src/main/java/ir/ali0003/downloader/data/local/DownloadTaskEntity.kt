package ir.ali0003.downloader.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.ali0003.downloader.data.model.DownloadStatus
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["isHidden"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val websiteUrl: String,
    val fileName: String,
    val mimeType: String = "video/mp4",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBps: Long = 0L,
    val isM3u8: Boolean = false,
    val headersJson: String = "{}",
    val isHidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == DownloadStatus.COMPLETED) {
            1.0f
        } else {
            0.0f
        }

    val formattedDownloadedSize: String
        get() = formatFileSize(downloadedBytes)

    val formattedTotalSize: String
        get() = if (totalBytes > 0L) formatFileSize(totalBytes) else "Unknown"

    val formattedSpeed: String
        get() = if (status == DownloadStatus.DOWNLOADING && speedBps > 0) {
            "${formatFileSize(speedBps)}/s"
        } else {
            "--"
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val df = DecimalFormat("#,##0.#")
            return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
        }
    }
}
