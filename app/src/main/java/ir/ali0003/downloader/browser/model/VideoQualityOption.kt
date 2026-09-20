package ir.ali0003.downloader.browser.model

import java.text.DecimalFormat

data class VideoQualityOption(
    val label: String,
    val resolution: String = "",
    val bandwidthBps: Long = 0L,
    val url: String,
    val isHlsVariant: Boolean = false,
    val estimatedSizeBytes: Long = 0L,
    val formatTag: String = "MP4",
    val formatId: String? = null,
    val isYoutubeDl: Boolean = false
) {
    val formattedSize: String
        get() {
            if (estimatedSizeBytes > 0) {
                return formatFileSize(estimatedSizeBytes)
            }
            if (bandwidthBps > 0) {
                // Approximate size based on standard 3-minute video duration (180s)
                val computed = (bandwidthBps * 180L) / 8L
                return formatFileSize(computed)
            }
            return "24.5 MB"
        }

    val formattedBandwidth: String
        get() = if (bandwidthBps > 0) "${bandwidthBps / 1000} kbps" else ""

    val cleanResolutionBadge: String
        get() {
            val lower = label.lowercase()
            return when {
                formatTag.contains("AUDIO", ignoreCase = true) || resolution.contains("Audio", ignoreCase = true) -> "Audio"
                lower.contains("1080") || resolution.contains("1080") ||
                        lower.contains("4k") || resolution.contains("2160") ||
                        lower.contains("2k") || resolution.contains("1440") -> "1080p FHD"
                lower.contains("720") || resolution.contains("720") -> "720p HD"
                lower.contains("480") || resolution.contains("480") -> "480p SD"
                lower.contains("360") || resolution.contains("360") -> "360p Low"
                resolution.isNotBlank() && resolution.contains("x") -> {
                    val h = resolution.substringAfter("x").toIntOrNull() ?: 0
                    when {
                        h >= 1080 -> "1080p FHD"
                        h >= 720 -> "720p HD"
                        h >= 480 -> "480p SD"
                        h >= 360 -> "360p Low"
                        h > 0 -> "${h}p"
                        else -> "1080p FHD"
                    }
                }
                else -> "1080p FHD"
            }
        }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val df = DecimalFormat("#,##0.#")
            return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
        }
    }
}
