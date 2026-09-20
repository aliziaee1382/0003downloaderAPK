package ir.ali0003.downloader.browser.model

import java.util.UUID

data class SniffedMediaItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val pageUrl: String,
    val title: String,
    val mimeType: String = "video/mp4",
    val isM3u8: Boolean = false,
    val isDash: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val thumbnailUrl: String? = null,
    val durationSeconds: Double = 0.0,
    val fileSizeBytes: Long = 0L,
    val qualities: List<VideoQualityOption> = emptyList(),
    val discoveredTimestamp: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        get() = if (title.isNotBlank()) title else extractFileNameFromUrl(url)

    val cleanFileName: String
        get() {
            val baseName = displayTitle.replace("[^a-zA-Z0-9._-]".toRegex(), "_").trim('_')
            val ext = when {
                mimeType.contains("audio") || mimeType.contains("mp3") -> ".mp3"
                isDash -> ".mpd"
                mimeType.contains("webm") -> ".webm"
                // HLS streams download and merge chunks into a standalone playable MP4 video
                isM3u8 -> ".mp4"
                else -> ".mp4"
            }
            // Strip .m3u8 if present in baseName before appending .mp4
            val sanitizedBase = if (baseName.endsWith(".m3u8", ignoreCase = true)) {
                baseName.removeSuffix(".m3u8").removeSuffix(".M3U8")
            } else {
                baseName
            }
            return if (sanitizedBase.endsWith(ext, ignoreCase = true)) sanitizedBase else "$sanitizedBase$ext"
        }

    val headersJson: String
        get() {
            if (headers.isEmpty()) return "{}"
            val entries = headers.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }
            return "{$entries}"
        }

    val bestResolutionBadge: String
        get() {
            qualities.firstOrNull()?.cleanResolutionBadge?.let {
                if (it.isNotBlank()) return it
            }
            val lower = (title + " " + url).lowercase()
            return when {
                mimeType.contains("audio", ignoreCase = true) -> "AUDIO"
                lower.contains("1080") ||
                        lower.contains("4k") || lower.contains("2160") ||
                        lower.contains("2k") || lower.contains("1440") -> "1080p FHD"
                lower.contains("720") -> "720p HD"
                lower.contains("480") -> "480p SD"
                lower.contains("360") -> "360p"
                isM3u8 -> "1080p FHD"
                else -> "1080p FHD"
            }
        }

    val bestFileSizeBytes: Long
        get() {
            if (fileSizeBytes > 0L) return fileSizeBytes
            val qualitySize = qualities.firstOrNull()?.estimatedSizeBytes ?: 0L
            if (qualitySize > 0L) return qualitySize
            if (durationSeconds > 0.0) {
                // Estimate based on standard 2.5 Mbps HD bitrate
                return ((2_500_000L * durationSeconds) / 8.0).toLong()
            }
            return 35 * 1024 * 1024L
        }

    val bestFormattedSize: String
        get() = VideoQualityOption.formatFileSize(bestFileSizeBytes)

    companion object {
        fun extractFileNameFromUrl(url: String): String {
            return try {
                val clean = url.substringBefore('?').substringBefore('#')
                val lastPart = clean.substringAfterLast('/')
                if (lastPart.isNotBlank()) lastPart else "video_${System.currentTimeMillis()}"
            } catch (e: Exception) {
                "video_${System.currentTimeMillis()}"
            }
        }
    }
}
