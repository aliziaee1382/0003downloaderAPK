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
                isM3u8 -> ".m3u8"
                isDash -> ".mpd"
                mimeType.contains("webm") -> ".webm"
                mimeType.contains("audio") || mimeType.contains("mp3") -> ".mp3"
                else -> ".mp4"
            }
            return if (baseName.endsWith(ext, ignoreCase = true)) baseName else "$baseName$ext"
        }

    val headersJson: String
        get() {
            if (headers.isEmpty()) return "{}"
            val entries = headers.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }
            return "{$entries}"
        }

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
