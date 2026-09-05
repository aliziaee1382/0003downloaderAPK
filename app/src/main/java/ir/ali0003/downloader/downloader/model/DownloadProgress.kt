package ir.ali0003.downloader.downloader.model

data class DownloadProgress(
    val taskId: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSeconds: Long,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (isCompleted) {
            1.0f
        } else {
            0.0f
        }

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
}
