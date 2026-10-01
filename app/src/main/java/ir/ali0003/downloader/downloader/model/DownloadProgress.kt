package ir.ali0003.downloader.downloader.model

import java.text.DecimalFormat

data class DownloadProgress(
    val taskId: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSeconds: Long,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val explicitProgress: Float? = null,
    val currentSegment: Int = 0,
    val totalSegments: Int = 0
) {
    val progress: Float
        get() = explicitProgress ?: if (totalSegments > 0) {
            (currentSegment.toFloat() / totalSegments.toFloat()).coerceIn(0f, 1f)
        } else if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (isCompleted) {
            1.0f
        } else {
            0.0f
        }

    val formattedMetricProgress: String
        get() = formatCleanMetric(downloadedBytes, totalBytes, progress)

    val formattedEta: String
        get() {
            if (isCompleted) return "Done"
            if (etaSeconds <= 0 || speedBps <= 0) return "--"
            val hours = etaSeconds / 3600
            val minutes = (etaSeconds % 3600) / 60
            val seconds = etaSeconds % 60
            return if (hours > 0) {
                String.format("%dh %02dm", hours, minutes)
            } else if (minutes > 0) {
                String.format("%dm %02ds", minutes, seconds)
            } else {
                String.format("%ds", seconds)
            }
        }

    companion object {
        fun formatCleanMetric(downloadedBytes: Long, totalBytes: Long, progressFraction: Float): String {
            val df = DecimalFormat("#,##0.#")
            val downloadedMB = df.format(downloadedBytes.toDouble() / (1024.0 * 1024.0))
            val percentage = (progressFraction * 100).toInt().coerceIn(0, 100)

            val effectiveTotalBytes = when {
                totalBytes > 0L && totalBytes > downloadedBytes -> totalBytes
                totalBytes > 0L && percentage >= 100 -> totalBytes
                progressFraction > 0.005f && downloadedBytes > 0L -> {
                    (downloadedBytes.toDouble() / progressFraction.toDouble()).toLong()
                }
                totalBytes > 0L -> totalBytes
                else -> 0L
            }

            return if (effectiveTotalBytes > 0L) {
                val totalMB = df.format(effectiveTotalBytes.toDouble() / (1024.0 * 1024.0))
                val isApprox = (totalBytes <= 0L || totalBytes <= downloadedBytes) && percentage < 100
                val prefix = if (isApprox) "~" else ""
                "$downloadedMB MB / $prefix$totalMB MB  •  $percentage%"
            } else {
                "$downloadedMB MB  •  $percentage%"
            }
        }
    }
}
