package ir.ali0003.downloader.browser.model

import java.text.DecimalFormat

data class VideoQualityOption(
    val label: String,
    val resolution: String = "",
    val bandwidthBps: Long = 0L,
    val url: String,
    val isHlsVariant: Boolean = false,
    val estimatedSizeBytes: Long = 0L,
    val formatTag: String = "MP4"
) {
    val formattedSize: String
        get() = if (estimatedSizeBytes > 0) formatFileSize(estimatedSizeBytes) else "Stream Size Varies"

    val formattedBandwidth: String
        get() = if (bandwidthBps > 0) "${bandwidthBps / 1000} kbps" else ""

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
