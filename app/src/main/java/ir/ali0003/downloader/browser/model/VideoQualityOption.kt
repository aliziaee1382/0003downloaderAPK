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
    val isYoutubeDl: Boolean = false,
    val renditionKey: String? = null,
    val isExactSize: Boolean = false
) {
    val formattedSize: String
        get() {
            if (!isHlsVariant && estimatedSizeBytes > 0L) {
                val sizeStr = formatFileSize(estimatedSizeBytes)
                return if (isExactSize) sizeStr else "~$sizeStr"
            }
            return if (isHlsVariant) "Adaptive Stream" else ""
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
            if (lower.contains("360")) return "360p SD"
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
                    h >= 360 -> "360p SD"
                    h > 0 -> "${h}p"
                    else -> label.ifBlank { "Video" }
                }
            }

            val pMatch = Regex("""\b(\d{3,4})p\b""").find(lower)
            if (pMatch != null) {
                return pMatch.value
            }

            // Secondary Fallback: RFC 8216 bandwidth mapping
            if (bandwidthBps > 0L) {
                return when {
                    bandwidthBps >= 12_000_000L -> "4K UHD"
                    bandwidthBps >= 7_000_000L -> "1440p 2K"
                    bandwidthBps >= 3_500_000L -> "1080p FHD"
                    bandwidthBps in 1_600_000L..3_499_999L -> "720p HD"
                    bandwidthBps in 750_000L..1_599_999L -> "480p SD"
                    else -> "360p SD"
                }
            }

            return label.ifBlank { "Video" }
        }

    fun getResolutionHeight(): Int {
        if (formatTag.contains("AUDIO", ignoreCase = true) || resolution.contains("Audio", ignoreCase = true)) {
            return -1
        }
        val lower = (label + " " + resolution).lowercase()
        val dimRegex = """(\d{3,4})x(\d{3,4})""".toRegex()
        val dimMatch = dimRegex.find(lower)
        if (dimMatch != null) {
            val h = dimMatch.groupValues[2].toIntOrNull() ?: 0
            if (h > 0) return h
        }
        val pRegex = """(\d{3,4})p\b""".toRegex()
        val pMatch = pRegex.find(lower)
        if (pMatch != null) {
            val h = pMatch.groupValues[1].toIntOrNull() ?: 0
            if (h > 0) return h
        }
        val kw = when {
            lower.contains("4k") || lower.contains("2160") -> 2160
            lower.contains("2k") || lower.contains("1440") -> 1440
            lower.contains("1080") -> 1080
            lower.contains("720") -> 720
            lower.contains("480") -> 480
            lower.contains("360") -> 360
            lower.contains("250") -> 250
            lower.contains("240") -> 240
            else -> 0
        }
        if (kw > 0) return kw

        // Secondary fallback by RFC 8216 bandwidth
        return when {
            bandwidthBps >= 12_000_000L -> 2160
            bandwidthBps >= 7_000_000L -> 1440
            bandwidthBps >= 3_500_000L -> 1080
            bandwidthBps in 1_600_000L..3_499_999L -> 720
            bandwidthBps in 750_000L..1_599_999L -> 480
            bandwidthBps > 0L -> 360
            else -> 0
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
