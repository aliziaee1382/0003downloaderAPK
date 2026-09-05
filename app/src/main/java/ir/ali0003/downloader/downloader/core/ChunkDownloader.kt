package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.util.Log
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.downloader.model.DownloadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance Multi-Threaded Chunk Downloader with HTTP Range slicing.
 * Splits large payload into 4-8 parallel OkHttp Range streams, writes to indexed scratch files,
 * and stitch-muxes safely into the destination file without RAM memory spikes.
 */
class ChunkDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "ChunkDownloader"
        private const val BUFFER_SIZE = 64 * 1024 // 64KB stream buffer
        private const val NUM_THREADS = 4

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    /**
     * Executes multi-chunk or single-stream download, emitting real-time DownloadProgress.
     */
    fun download(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = flow {
        val taskId = task.id
        val url = task.url
        val headers = parseHeaders(task.headersJson)

        // 1. Head or Range probe to determine content length & Accept-Ranges support
        val probe = probeServer(url, headers)
        val contentLength = probe.contentLength
        val supportsRange = probe.acceptsRanges && contentLength > 1024 * 1024 // Only range-slice files > 1MB

        Log.d(TAG, "Task $taskId: length=$contentLength bytes, supportsRange=$supportsRange")

        // Scratch temp directory for parallel chunks
        val scratchDir = File(context.cacheDir, "chunks_$taskId")
        if (!scratchDir.exists()) scratchDir.mkdirs()

        val totalDownloadedAtomic = AtomicLong(0L)
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        if (supportsRange && contentLength > 0) {
            // Multi-threaded chunk range download
            val chunkSize = contentLength / NUM_THREADS
            val chunkFiles = mutableListOf<File>()

            try {
                coroutineScope {
                    val deferredList = (0 until NUM_THREADS).map { index ->
                        val startByte = index * chunkSize
                        val endByte = if (index == NUM_THREADS - 1) contentLength - 1 else (index + 1) * chunkSize - 1
                        val chunkFile = File(scratchDir, "part_$index.tmp")
                        chunkFiles.add(chunkFile)

                        async(Dispatchers.IO) {
                            downloadRangeChunk(
                                url = url,
                                headers = headers,
                                startByte = startByte,
                                endByte = endByte,
                                outputFile = chunkFile,
                                onBytesRead = { bytesRead ->
                                    totalDownloadedAtomic.addAndGet(bytesRead)
                                }
                            )
                        }
                    }

                    // Progress reporting ticker loop while workers are downloading
                    while (deferredList.any { it.isActive }) {
                        if (!isActive) break

                        val now = System.currentTimeMillis()
                        val elapsed = (now - lastTime).coerceAtLeast(1L)
                        val currentBytes = totalDownloadedAtomic.get()
                        val bytesDiff = (currentBytes - lastBytes).coerceAtLeast(0L)
                        val speedBps = (bytesDiff * 1000L) / elapsed

                        val remainingBytes = (contentLength - currentBytes).coerceAtLeast(0L)
                        val etaSeconds = if (speedBps > 0) remainingBytes / speedBps else 0L

                        emit(
                            DownloadProgress(
                                taskId = taskId,
                                downloadedBytes = currentBytes,
                                totalBytes = contentLength,
                                speedBps = speedBps,
                                etaSeconds = etaSeconds
                            )
                        )

                        lastTime = now
                        lastBytes = currentBytes
                        kotlinx.coroutines.delay(400)
                    }

                    // Wait for all workers to finish
                    deferredList.awaitAll()
                }

                // Sequential stitch into final outputFile
                Log.d(TAG, "Stitching ${chunkFiles.size} chunks into ${outputFile.name}")
                stitchChunks(chunkFiles, outputFile)

                // Emit 100% completion
                emit(
                    DownloadProgress(
                        taskId = taskId,
                        downloadedBytes = contentLength,
                        totalBytes = contentLength,
                        speedBps = 0L,
                        etaSeconds = 0L,
                        isCompleted = true
                    )
                )

            } finally {
                // Cleanup temp scratch files
                scratchDir.deleteRecursively()
            }

        } else {
            // Single stream fallback (e.g. server doesn't support Range slicing or length is unknown)
            downloadSingleStream(
                url = url,
                headers = headers,
                outputFile = outputFile,
                totalBytesEstimated = if (contentLength > 0) contentLength else task.totalBytes,
                onProgress = { progress ->
                    emit(progress.copy(taskId = taskId))
                }
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun probeServer(url: String, headers: Map<String, String>): ServerProbeResult {
        try {
            val requestBuilder = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                return ServerProbeResult(contentLength, acceptRanges)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Head probe failed: ${e.message}, falling back to GET probe")
        }

        // Try small Range GET probe
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=0-1023")
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val isPartial = response.code == 206
            val contentRange = response.header("Content-Range")
            val total = contentRange?.substringAfterLast("/")?.toLongOrNull() ?: -1L
            return ServerProbeResult(total, isPartial)
        } catch (e: Exception) {
            Log.w(TAG, "Range probe failed: ${e.message}")
            return ServerProbeResult(-1L, false)
        }
    }

    private fun downloadRangeChunk(
        url: String,
        headers: Map<String, String>,
        startByte: Long,
        endByte: Long,
        outputFile: File,
        onBytesRead: (Long) -> Unit
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$startByte-$endByte")

        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            throw java.io.IOException("HTTP error ${response.code} downloading chunk range $startByte-$endByte")
        }

        val body = response.body ?: throw java.io.IOException("Empty response body for chunk")
        outputFile.outputStream().use { fos ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    onBytesRead(read.toLong())
                }
                fos.flush()
            }
        }
    }

    private suspend fun downloadSingleStream(
        url: String,
        headers: Map<String, String>,
        outputFile: File,
        totalBytesEstimated: Long,
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code} downloading stream")
        }

        val body = response.body ?: throw java.io.IOException("Empty response body")
        val realLength = if (totalBytesEstimated > 0) totalBytesEstimated else (body.contentLength().takeIf { it > 0 } ?: 0L)

        var totalBytesRead = 0L
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        outputFile.outputStream().use { fos ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    totalBytesRead += read

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 400) {
                        val elapsed = (now - lastTime).coerceAtLeast(1L)
                        val speedBps = ((totalBytesRead - lastBytes) * 1000L) / elapsed
                        val remaining = (realLength - totalBytesRead).coerceAtLeast(0L)
                        val eta = if (speedBps > 0 && realLength > 0) remaining / speedBps else 0L

                        onProgress(
                            DownloadProgress(
                                taskId = 0L,
                                downloadedBytes = totalBytesRead,
                                totalBytes = realLength,
                                speedBps = speedBps,
                                etaSeconds = eta
                            )
                        )
                        lastTime = now
                        lastBytes = totalBytesRead
                    }
                }
                fos.flush()
            }
        }

        onProgress(
            DownloadProgress(
                taskId = 0L,
                downloadedBytes = totalBytesRead,
                totalBytes = if (realLength > 0) realLength else totalBytesRead,
                speedBps = 0L,
                etaSeconds = 0L,
                isCompleted = true
            )
        )
    }

    private fun stitchChunks(chunkFiles: List<File>, destination: File) {
        if (destination.exists()) destination.delete()
        if (destination.parentFile?.exists() == false) destination.parentFile?.mkdirs()

        destination.outputStream().use { destOut ->
            for (chunk in chunkFiles) {
                if (!chunk.exists()) throw java.io.FileNotFoundException("Chunk missing: ${chunk.name}")
                chunk.inputStream().use { chunkIn ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len: Int
                    while (chunkIn.read(buffer).also { len = it } != -1) {
                        destOut.write(buffer, 0, len)
                    }
                }
            }
            destOut.flush()
        }
    }

    private fun parseHeaders(headersJson: String?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (headersJson.isNullOrBlank()) return map
        try {
            val json = JSONObject(headersJson)
            json.keys().forEach { key ->
                map[key] = json.optString(key)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse headersJson: ${e.message}")
        }
        return map
    }

    private data class ServerProbeResult(
        val contentLength: Long,
        val acceptsRanges: Boolean
    )
}
