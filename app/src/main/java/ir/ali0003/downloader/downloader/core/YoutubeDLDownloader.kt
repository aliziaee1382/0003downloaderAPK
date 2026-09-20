package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

/**
 * Native Downloader utilizing YoutubeDL & FFmpeg for downloading and automatic MP4 muxing.
 */
class YoutubeDLDownloader(
    private val context: Context
) {
    companion object {
        private const val TAG = "YoutubeDLDownloader"
        private val SPEED_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([kKmMgG])i?B/s")

        fun isYoutubeDlTask(task: DownloadTaskEntity): Boolean {
            return task.headersJson.contains("\"isYoutubeDl\":true") ||
                    task.headersJson.contains("\"formatId\"")
        }
    }

    fun download(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = channelFlow {
        val json = try {
            JSONObject(task.headersJson)
        } catch (e: Exception) {
            JSONObject()
        }

        val formatId = json.optString("formatId").takeIf { it.isNotBlank() } ?: "best"
        val webpageUrl = json.optString("webpageUrl").takeIf { it.isNotBlank() }
            ?: task.websiteUrl.takeIf { it.isNotBlank() }
            ?: task.url

        val totalEstimatedBytes = if (task.totalBytes > 0L) task.totalBytes else 50_000_000L

        trySend(
            DownloadProgress(
                taskId = task.id,
                downloadedBytes = 0L,
                totalBytes = totalEstimatedBytes,
                speedBps = 0L,
                etaSeconds = 0L,
                isCompleted = false
            )
        )

        val tempParentDir = outputFile.parentFile ?: context.cacheDir
        if (!tempParentDir.exists()) tempParentDir.mkdirs()

        // Prepare YoutubeDLRequest with target format and ffmpeg merge to MP4 container
        val request = YoutubeDLRequest(webpageUrl).apply {
            addOption("-f", formatId)
            addOption("-o", outputFile.absolutePath)
            addOption("--merge-output-format", "mp4")
            addOption("--no-mtime")
            addOption("--socket-timeout", "20")
            addOption("--no-playlist")
            addOption("--no-update")
            addOption("--no-warnings")
        }

        val processId = "task_${task.id}"
        var lastDownloaded = 0L
        var currentSpeed = 0L

        Log.d(TAG, "Executing YoutubeDL for task ${task.id}: format=$formatId, url=$webpageUrl -> ${outputFile.name}")

        try {
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                val progressRatio = (progress.coerceIn(0f, 100f) / 100f)
                val downloaded = (progressRatio * totalEstimatedBytes).toLong()

                val speed = parseSpeed(line)
                if (speed > 0L) {
                    currentSpeed = speed
                } else if (downloaded > lastDownloaded) {
                    currentSpeed = (downloaded - lastDownloaded) * 2L
                }
                lastDownloaded = downloaded

                // Check real file length if partially written
                val realFileLength = if (outputFile.exists()) outputFile.length() else downloaded
                val emittedDownloaded = maxOf(downloaded, realFileLength)

                trySend(
                    DownloadProgress(
                        taskId = task.id,
                        downloadedBytes = emittedDownloaded,
                        totalBytes = totalEstimatedBytes,
                        speedBps = currentSpeed,
                        etaSeconds = eta,
                        isCompleted = false
                    )
                )
            }

            // Post-execution: Ensure destination file exists and is finalized
            val finalFile = resolveFinalOutputFile(outputFile)
            val finalBytes = if (finalFile.exists()) finalFile.length() else totalEstimatedBytes

            Log.i(TAG, "YoutubeDL download complete for task ${task.id}: ${finalFile.absolutePath} ($finalBytes bytes)")

            trySend(
                DownloadProgress(
                    taskId = task.id,
                    downloadedBytes = finalBytes,
                    totalBytes = finalBytes,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    isCompleted = true
                )
            )
            close()
        } catch (e: Exception) {
            Log.w(TAG, "YoutubeDL execution error for task ${task.id}: ${e.message}")
            trySend(
                DownloadProgress(
                    taskId = task.id,
                    downloadedBytes = 0L,
                    totalBytes = totalEstimatedBytes,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = e.message ?: "YoutubeDL execution failed"
                )
            )
            close(e)
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveFinalOutputFile(targetFile: File): File {
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile
        }

        val parent = targetFile.parentFile ?: return targetFile
        val baseName = targetFile.nameWithoutExtension

        // yt-dlp might have appended an extension or written directly to .mp4 / .mkv / .m4a
        val candidates = listOf(
            File(parent, "$baseName.mp4"),
            File(parent, "$baseName.mkv"),
            File(parent, "$baseName.webm"),
            File(parent, "$baseName.m4a"),
            File(parent, "$baseName.mp3"),
            File("${targetFile.absolutePath}.mp4")
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.length() > 0L) {
                if (candidate.absolutePath != targetFile.absolutePath) {
                    candidate.renameTo(targetFile)
                }
                return targetFile
            }
        }

        return targetFile
    }

    private fun parseSpeed(line: String?): Long {
        if (line.isNullOrBlank()) return 0L
        val matcher = SPEED_PATTERN.matcher(line)
        if (matcher.find()) {
            val numStr = matcher.group(1) ?: return 0L
            val unit = (matcher.group(2) ?: "K").uppercase()
            val num = numStr.toDoubleOrNull() ?: return 0L
            return when (unit) {
                "G" -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
                "M" -> (num * 1024.0 * 1024.0).toLong()
                "K" -> (num * 1024.0).toLong()
                else -> num.toLong()
            }
        }
        return 0L
    }
}
