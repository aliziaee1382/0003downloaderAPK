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
            if (estimatedSizeBytes > 0L) {
                return formatFileSize(estimatedSizeBytes)
            }
            return ""
        }

    val formattedBandwidth: String
        get() = if (bandwidthBps > 0) "${bandwidthBps / 1000} kbps" else ""

    val cleanResolutionBadge: String
        get() {
            val lower = (label + " " + resolution).lowercase()
            if (formatTag.contains("AUDIO", ignoreCase = true) || resolution.contains("Audio", ignoreCase = true)) {
                return "Audio"
            }
            if (lower.contains("4k") || lower.contains("2160")) return "4K UHD"
            if (lower.contains("2k") || lower.contains("1440")) return "1440p 2K"
            if (lower.contains("1080")) return "1080p FHD"
            if (lower.contains("720")) return "720p HD"
            if (lower.contains("480")) return "480p SD"
            if (lower.contains("360")) return "360p Low"
            if (lower.contains("250")) return "250p"
            if (lower.contains("240")) return "240p"

            if (resolution.isNotBlank() && resolution.contains("x")) {
                val h = resolution.substringAfter("x").toIntOrNull() ?: 0
                return when {
                    h >= 2160 -> "4K UHD"
                    h >= 1440 -> "1440p 2K"
                    h >= 1080 -> "1080p FHD"
                    h >= 720 -> "720p HD"
                    h >= 480 -> "480p SD"
                    h >= 360 -> "360p Low"
                    h > 0 -> "${h}p"
                    else -> label.ifBlank { "Video" }
                }
            }

            val pMatch = Regex("""\b(\d{3,4})p\b""").find(lower)
            if (pMatch != null) {
                return pMatch.value
            }

            return label.ifBlank { "Video" }
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
