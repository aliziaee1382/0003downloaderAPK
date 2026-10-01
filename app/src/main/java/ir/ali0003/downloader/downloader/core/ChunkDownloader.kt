package ir.ali0003.downloader.downloader.core

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.settings.DownloadSettingsPreferences
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Resilient Direct Progressive & Multi-Threaded Chunk Downloader.
 * Features:
 * 1. Preserves full browser context headers (Cookies from CookieManager, Referer from websiteUrl, Origin, User-Agent)
 *    to prevent CDN 403 Forbidden / anti-hotlink blocks.
 * 2. Probes server with non-destructive Range GET requests.
 * 3. Graceful multi-chunk to single-stream fallback: if concurrent Range requests are rejected, throttled,
 *    or fail at runtime, automatically falls back to a clean continuous single-stream GET download.
 * 4. Writes streams directly to disk with proper buffer flushing and MediaScanner indexing on completion.
 */
class ChunkDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "ChunkDownloader"
        private const val BUFFER_SIZE = 64 * 1024 // 64KB stream buffer
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val urlStr = original.url.toString()
                    val builder = original.newBuilder()

                    // Ensure fresh session cookie is attached across redirects
                    if (original.header("Cookie").isNullOrBlank()) {
                        try {
                            val cookie = android.webkit.CookieManager.getInstance().getCookie(urlStr)
                            if (!cookie.isNullOrBlank()) {
                                builder.header("Cookie", cookie)
                            }
                        } catch (_: Exception) {}
                    }

                    if (original.header("User-Agent").isNullOrBlank()) {
                        builder.header("User-Agent", DEFAULT_USER_AGENT)
                    }

                    chain.proceed(builder.build())
                }
                .build()
        }

        fun parseHeaders(
            headersJson: String?,
            url: String? = null,
            websiteUrl: String? = null
        ): Map<String, String> {
            val map = mutableMapOf<String, String>()

            // 1. Parse custom headers from JSON, strictly ignoring Range, Content-Length and Host
            if (!headersJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(headersJson)
                    json.keys().forEach { key ->
                        val v = json.optString(key)
                        val lk = key.lowercase()
                        if (key.isNotBlank() && v.isNotBlank() &&
                            !key.startsWith("target_") &&
                            !key.startsWith("media3_") &&
                            lk != "range" &&
                            lk != "if-range" &&
                            lk != "content-length" &&
                            lk != "host"
                        ) {
                            map[key] = v
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse headersJson: ${e.message}")
                }
            }

            val effectiveWebpage = when {
                !websiteUrl.isNullOrBlank() -> websiteUrl
                map.containsKey("webpageUrl") -> map["webpageUrl"]
                map.containsKey("Referer") -> map["Referer"]
                map.containsKey("referer") -> map["referer"]
                else -> null
            }

            // 2. Fresh session cookies from CookieManager for website URL and media URL
            if (!map.containsKey("Cookie") && !map.containsKey("cookie")) {
                try {
                    val cookie = if (!effectiveWebpage.isNullOrBlank()) {
                        android.webkit.CookieManager.getInstance().getCookie(effectiveWebpage)
                    } else if (!url.isNullOrBlank()) {
                        android.webkit.CookieManager.getInstance().getCookie(url)
                    } else null

                    val fallbackCookie = if (!url.isNullOrBlank() && cookie.isNullOrBlank()) {
                        android.webkit.CookieManager.getInstance().getCookie(url)
                    } else null

                    val chosenCookie = cookie ?: fallbackCookie
                    if (!chosenCookie.isNullOrBlank()) {
                        map["Cookie"] = chosenCookie
                    }
                } catch (_: Exception) {}
            }

            // 3. Referer & Origin from websiteUrl (eliminates CDN anti-hotlinking 403 Forbidden)
            if (!map.containsKey("Referer") && !map.containsKey("referer")) {
                if (!effectiveWebpage.isNullOrBlank()) {
                    map["Referer"] = effectiveWebpage
                } else if (!url.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(url)
                        if (uri.scheme != null && uri.host != null) {
                            map["Referer"] = "${uri.scheme}://${uri.host}/"
                        }
                    } catch (_: Exception) {}
                }
            }

            if (!map.containsKey("Origin") && !map.containsKey("origin")) {
                val ref = map["Referer"] ?: map["referer"] ?: effectiveWebpage ?: url
                if (!ref.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(ref)
                        if (uri.scheme != null && uri.host != null) {
                            map["Origin"] = "${uri.scheme}://${uri.host}"
                        }
                    } catch (_: Exception) {}
                }
            }

            // 4. Modern User-Agent
            val ua = map["User-Agent"] ?: map["user-agent"]
            if (ua.isNullOrBlank() || ua.contains("VideoVault", ignoreCase = true)) {
                map["User-Agent"] = DEFAULT_USER_AGENT
            }

            return map
        }

        /**
         * Robust parser to extract direct playable video stream URLs from JSON API responses.
         * Searches for standard media keys ("url", "video_url", "stream", "file", "src", "videoUrl", "playback_url", etc.)
         * across both top-level and nested structures, with regex fallback.
         */
        fun extractVideoUrlFromJson(jsonStr: String): String? {
            if (jsonStr.isBlank()) return null
            val candidateKeys = listOf(
                "url", "video_url", "stream", "file", "src", "videoUrl",
                "playback_url", "source", "download_url", "link", "media_url",
                "stream_url", "play_url", "hls_url", "mp4_url"
            )

            fun isValidMediaUrl(u: String?): Boolean {
                if (u.isNullOrBlank()) return false
                val trimmed = u.trim()
                return trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true)
            }

            try {
                val trimmed = jsonStr.trim()
                if (trimmed.startsWith("{")) {
                    val root = JSONObject(trimmed)
                    for (k in candidateKeys) {
                        val v = root.optString(k, "")
                        if (isValidMediaUrl(v)) return v
                    }

                    fun searchJson(obj: Any?): String? {
                        when (obj) {
                            is JSONObject -> {
                                for (k in candidateKeys) {
                                    val v = obj.optString(k, "")
                                    if (isValidMediaUrl(v)) return v
                                }
                                val it = obj.keys()
                                while (it.hasNext()) {
                                    val key = it.next()
                                    val child = obj.opt(key)
                                    val found = searchJson(child)
                                    if (found != null) return found
                                }
                            }
                            is org.json.JSONArray -> {
                                for (i in 0 until obj.length()) {
                                    val found = searchJson(obj.opt(i))
                                    if (found != null) return found
                                }
                            }
                        }
                        return null
                    }

                    val found = searchJson(root)
                    if (found != null) return found
                } else if (trimmed.startsWith("[")) {
                    val arr = org.json.JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        if (item is JSONObject) {
                            for (k in candidateKeys) {
                                val v = item.optString(k, "")
                                if (isValidMediaUrl(v)) return v
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing JSON for video URL: ${e.message}")
            }

            try {
                val regex = Regex(
                    """\"(?:url|video_url|stream|file|src|videoUrl|playback_url|source|download_url)\"\s*:\s*\"(https?:\\?/\\?/[^\"]+)\"""",
                    RegexOption.IGNORE_CASE
                )
                val match = regex.find(jsonStr)
                if (match != null) {
                    val rawUrl = match.groupValues[1].replace("\\/", "/")
                    if (isValidMediaUrl(rawUrl)) return rawUrl
                }
            } catch (_: Exception) {}

            return null
        }
    }

    fun parseHeaders(
        headersJson: String?,
        url: String? = null,
        websiteUrl: String? = null
    ): Map<String, String> = Companion.parseHeaders(headersJson, url, websiteUrl)

    /**
     * Executes resilient progressive download (multi-chunk with automatic single-stream fallback).
     */
    fun download(
        task: DownloadTaskEntity,
        outputFile: File
    ): Flow<DownloadProgress> = flow {
        val taskId = task.id
        val url = task.url
        val websiteUrl = task.websiteUrl
        val headers = Companion.parseHeaders(task.headersJson, url, websiteUrl)

        Log.d(TAG, "Starting download for task $taskId: $url (dest: ${outputFile.name}, website: $websiteUrl)")

        // 1. Range/Head probe to determine content length & Accept-Ranges support
        val probe = probeServer(url, headers, websiteUrl)
        val hasKnownLength = probe.contentLength > 0L
        val contentLength = if (hasKnownLength) probe.contentLength else task.totalBytes.takeIf { it > 0L } ?: -1L
        val supportsRange = hasKnownLength && probe.acceptsRanges && contentLength > 1024 * 1024L

        Log.d(TAG, "Task $taskId: length=$contentLength bytes, supportsRange=$supportsRange (probeLength=${probe.contentLength})")

        // Scratch temp directory for parallel chunks
        val scratchDir = File(context.cacheDir, "chunks_$taskId")
        val totalDownloadedAtomic = AtomicLong(0L)
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        var multiChunkSucceeded = false

        val settings = DownloadSettingsPreferences.getInstance(context)
        val numThreads = settings.getEffectiveThreadCount().coerceIn(1, 16)

        // 1. Smart bypass of multi-threading:
        // If probe.contentLength <= 0 (e.g. server answered with 400/403/405 or chunked dynamic stream),
        // strictly bypass multi-threaded coroutineScope block and call downloadSingleStream directly!
        if (hasKnownLength && supportsRange && contentLength > 1024 * 1024L && numThreads > 1) {
            try {
                if (!scratchDir.exists()) scratchDir.mkdirs()
                val chunkSize = contentLength / numThreads
                val chunkFiles = mutableListOf<File>()

                coroutineScope {
                    val deferredList = (0 until numThreads).map { index ->
                        val startByte = index * chunkSize
                        val endByte = if (index == numThreads - 1) contentLength - 1 else (index + 1) * chunkSize - 1
                        val chunkFile = File(scratchDir, "part_$index.tmp")
                        chunkFiles.add(chunkFile)

                        async(Dispatchers.IO) {
                            downloadRangeChunk(
                                url = url,
                                headers = headers,
                                websiteUrl = websiteUrl,
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
                        kotlinx.coroutines.delay(350)
                    }

                    deferredList.awaitAll()
                }

                // Sequential stitch into final outputFile
                Log.d(TAG, "Stitching ${chunkFiles.size} chunks into ${outputFile.name}")
                stitchChunks(chunkFiles, outputFile)
                multiChunkSucceeded = true

                if (!task.isHidden && outputFile.exists()) {
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outputFile.absolutePath),
                            arrayOf(task.mimeType.ifBlank { "video/mp4" })
                        ) { path, uri ->
                            Log.d(TAG, "MediaScanner indexed chunk downloaded file: $path -> $uri")
                        }
                    } catch (_: Exception) {}
                }

                val finalSize = if (outputFile.exists() && outputFile.length() > 0L) outputFile.length() else contentLength

                emit(
                    DownloadProgress(
                        taskId = taskId,
                        downloadedBytes = finalSize,
                        totalBytes = finalSize,
                        speedBps = 0L,
                        etaSeconds = 0L,
                        isCompleted = true,
                        explicitProgress = 1.0f
                    )
                )

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Multi-chunk download failed for task $taskId (${e.message}), gracefully falling back to single-stream GET", e)
                multiChunkSucceeded = false
                if (outputFile.exists()) outputFile.delete()
            } finally {
                scratchDir.deleteRecursively()
            }
        }

        // If multi-chunk was not supported OR failed at runtime, execute continuous single-stream download
        if (!multiChunkSucceeded) {
            try {
                Log.d(TAG, "Executing single-stream download for task $taskId (${task.fileName})")
                downloadSingleStream(
                    url = url,
                    headers = headers,
                    websiteUrl = websiteUrl,
                    outputFile = outputFile,
                    totalBytesEstimated = contentLength,
                    onProgress = { progress ->
                        if (progress.isCompleted && !task.isHidden && outputFile.exists()) {
                            try {
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(outputFile.absolutePath),
                                    arrayOf(task.mimeType.ifBlank { "video/mp4" })
                                ) { path, uri ->
                                    Log.d(TAG, "MediaScanner indexed single-stream downloaded file: $path -> $uri")
                                }
                            } catch (_: Exception) {}
                        }
                        emit(progress.copy(taskId = taskId))
                    }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Single-stream download failed for task $taskId: ${e.message}", e)
                emit(
                    DownloadProgress(
                        taskId = taskId,
                        downloadedBytes = outputFile.takeIf { it.exists() }?.length() ?: 0L,
                        totalBytes = task.totalBytes,
                        speedBps = 0L,
                        etaSeconds = 0L,
                        isFailed = true,
                        errorMessage = e.message ?: "Download failed"
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mergeCookieStrings(vararg cookieStrings: String?): String {
        val cookieMap = linkedMapOf<String, String>()
        for (cs in cookieStrings) {
            if (cs.isNullOrBlank()) continue
            val parts = cs.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) continue
                val equalIdx = trimmed.indexOf('=')
                if (equalIdx > 0) {
                    val key = trimmed.substring(0, equalIdx).trim()
                    val value = trimmed.substring(equalIdx + 1).trim()
                    cookieMap[key] = value
                } else {
                    cookieMap[trimmed] = ""
                }
            }
        }
        return cookieMap.entries.joinToString("; ") { (k, v) -> if (v.isNotEmpty()) "$k=$v" else k }
    }

    private fun applyBrowserContextHeaders(
        builder: Request.Builder,
        headers: Map<String, String>,
        targetUrl: String,
        websiteUrl: String? = null
    ) {
        val mergedHeaders = Companion.parseHeaders(null, targetUrl, websiteUrl).toMutableMap()
        mergedHeaders.putAll(headers)

        // 1. Fetch live fresh session cookies from CookieManager for source domain and destination domain
        val effectiveWebsite = when {
            !websiteUrl.isNullOrBlank() -> websiteUrl
            headers.containsKey("webpageUrl") -> headers["webpageUrl"]
            headers.containsKey("Referer") -> headers["Referer"]
            headers.containsKey("referer") -> headers["referer"]
            else -> null
        }

        var liveOriginCookie: String? = null
        var liveTargetCookie: String? = null
        try {
            val cm = android.webkit.CookieManager.getInstance()
            if (!effectiveWebsite.isNullOrBlank()) {
                liveOriginCookie = cm.getCookie(effectiveWebsite)
            }
            if (targetUrl.isNotBlank()) {
                liveTargetCookie = cm.getCookie(targetUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch CookieManager cookies: ${e.message}")
        }

        val existingCookie = mergedHeaders["Cookie"] ?: mergedHeaders["cookie"]
        val finalCookie = mergeCookieStrings(existingCookie, liveOriginCookie, liveTargetCookie)
        if (finalCookie.isNotBlank()) {
            mergedHeaders["Cookie"] = finalCookie
            mergedHeaders.remove("cookie")
        }

        mergedHeaders.forEach { (k, v) ->
            val lk = k.lowercase()
            if (k.isNotBlank() && v.isNotBlank() &&
                !k.startsWith("target_") &&
                !k.startsWith("media3_") &&
                lk != "range" &&
                lk != "if-range" &&
                lk != "content-length" &&
                lk != "host"
            ) {
                try {
                    builder.header(k, v)
                } catch (_: Exception) {}
            }
        }

        // 2. Video player emulation headers to make CDN treat download requests like in-browser video playback
        builder.header("Accept", "*/*")
        builder.header("Sec-Fetch-Dest", "video")
        builder.header("Sec-Fetch-Mode", "no-cors")
        builder.header("Sec-Fetch-Site", "cross-site")

        // Strictly purge any Range or Content-Length headers so requests never get clipped to tiny ranges
        builder.removeHeader("Range")
        builder.removeHeader("range")
        builder.removeHeader("If-Range")
        builder.removeHeader("if-range")
        builder.removeHeader("Content-Length")
        builder.removeHeader("content-length")
        builder.removeHeader("Host")
        builder.removeHeader("host")
    }

    private fun probeServer(
        url: String,
        headers: Map<String, String>,
        websiteUrl: String? = null
    ): ServerProbeResult {
        // Try Range GET probe first with a tiny 1KB chunk:
        // Range GET is far more reliable on CDNs than HEAD (which is often blocked with 403 or 405)
        try {
            val requestBuilder = Request.Builder().url(url)
            applyBrowserContextHeaders(requestBuilder, headers, url, websiteUrl)
            requestBuilder.header("Range", "bytes=0-1023")
            requestBuilder.header("Connection", "close")
            okHttpClient.newCall(requestBuilder.build()).execute().use { res ->
                val ct = (res.header("Content-Type") ?: "").lowercase()
                if (ct.contains("application/json") || ct.contains("text/javascript")) {
                    Log.d(TAG, "probeServer: $url returned JSON ($ct), delegating directly to downloadSingleStream")
                    return ServerProbeResult(-1L, false)
                }

                if (res.code == 206) {
                    val contentRange = res.header("Content-Range")
                    val total = contentRange?.substringAfterLast("/")?.trim()?.toLongOrNull() ?: -1L
                    val acceptRanges = res.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) != false
                    if (total > 0L) {
                        return ServerProbeResult(total, acceptRanges)
                    }
                } else if (res.isSuccessful && res.code == 200) {
                    // Server returned 200 OK (ignored Range header -> does NOT support partial content)
                    val contentLength = res.header("Content-Length")?.toLongOrNull() ?: -1L
                    return ServerProbeResult(contentLength, false)
                } else {
                    Log.d(TAG, "Range GET probe returned HTTP ${res.code}, will attempt fallback or single stream")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Range GET probe failed: ${e.message}, trying HEAD probe", e)
        }

        // Fallback: HEAD probe
        try {
            val requestBuilder = Request.Builder().url(url).head()
            applyBrowserContextHeaders(requestBuilder, headers, url, websiteUrl)
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { res ->
                if (res.isSuccessful && res.code == 200) {
                    val contentLength = res.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = res.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    return ServerProbeResult(contentLength, acceptRanges)
                } else {
                    Log.d(TAG, "HEAD probe returned HTTP ${res.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Head probe failed: ${e.message}")
        }

        // If probe requests threw exception or returned server error codes (400, 403, 405, etc.),
        // return ServerProbeResult(-1L, false) so download automatically falls back to downloadSingleStream without failing
        return ServerProbeResult(-1L, false)
    }

    private fun downloadRangeChunk(
        url: String,
        headers: Map<String, String>,
        websiteUrl: String?,
        startByte: Long,
        endByte: Long,
        outputFile: File,
        onBytesRead: (Long) -> Unit
    ) {
        val requestBuilder = Request.Builder().url(url)
        applyBrowserContextHeaders(requestBuilder, headers, url, websiteUrl)
        requestBuilder.header("Range", "bytes=$startByte-$endByte")

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful || response.code != 206) {
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
        websiteUrl: String?,
        outputFile: File,
        totalBytesEstimated: Long,
        onProgress: suspend (DownloadProgress) -> Unit
    ) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val requestBuilder = Request.Builder().url(url).get()
        applyBrowserContextHeaders(requestBuilder, headers, url, websiteUrl)
        requestBuilder.removeHeader("Range")
        requestBuilder.removeHeader("range")
        // Explicitly set Connection: keep-alive to prevent early socket teardowns by CDNs on streaming responses
        requestBuilder.header("Connection", "keep-alive")

        val call = okHttpClient.newCall(requestBuilder.build())
        val response = call.execute()
        if (!response.isSuccessful) {
            val errorBody = try {
                response.body?.string() ?: "<empty body>"
            } catch (e: Exception) {
                "<failed to read body: ${e.message}>"
            }
            val responseHeaders = response.headers.toMultimap().entries.joinToString("; ") { "${it.key}=${it.value}" }
            Log.e(TAG, "HTTP error ${response.code} downloading stream from $url. Response headers: [$responseHeaders], Response body: $errorBody")
            throw java.io.IOException("HTTP error ${response.code} downloading stream from $url: $errorBody")
        }

        val body = response.body ?: throw java.io.IOException("Empty response body from $url")
        val contentType = (response.header("Content-Type") ?: "").lowercase()

        // 1. Support API Endpoints returning JSON with real direct video URLs
        if (contentType.contains("application/json") || contentType.contains("text/javascript")) {
            val jsonBody = try {
                body.string()
            } catch (e: Exception) {
                throw java.io.IOException("Failed reading JSON response body from $url: ${e.message}")
            }

            Log.d(TAG, "Server returned JSON response ($contentType) for $url: ${jsonBody.take(400)}")
            val extractedMediaUrl = extractVideoUrlFromJson(jsonBody)
            if (!extractedMediaUrl.isNullOrBlank() && extractedMediaUrl != url) {
                Log.e(TAG, "Extracted real media stream URL from API JSON: $extractedMediaUrl (replacing: $url)")
                return downloadSingleStream(
                    url = extractedMediaUrl,
                    headers = headers,
                    websiteUrl = websiteUrl,
                    outputFile = outputFile,
                    totalBytesEstimated = totalBytesEstimated,
                    onProgress = onProgress
                )
            } else {
                Log.e(TAG, "Server returned JSON without a recognized media URL: ${jsonBody.take(300)}")
                throw java.io.IOException("Server returned non-media page ($contentType) instead of video content")
            }
        }

        if (contentType.contains("text/html")) {
            throw java.io.IOException("Server returned non-media page ($contentType) instead of video content")
        }

        val remoteLength = body.contentLength()
        val realLength = when {
            remoteLength > 0L -> remoteLength
            totalBytesEstimated > 0L -> totalBytesEstimated
            else -> 0L
        }

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
                    if (now - lastTime >= 350L) {
                        val elapsed = (now - lastTime).coerceAtLeast(1L)
                        val speedBps = ((totalBytesRead - lastBytes) * 1000L) / elapsed
                        val remaining = if (realLength > 0L) (realLength - totalBytesRead).coerceAtLeast(0L) else 0L
                        val eta = if (speedBps > 0L && realLength > 0L) remaining / speedBps else 0L

                        onProgress(
                            DownloadProgress(
                                taskId = 0L,
                                downloadedBytes = totalBytesRead,
                                totalBytes = if (realLength > 0L) realLength else totalBytesRead,
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

        val finalLength = if (outputFile.exists() && outputFile.length() > 0L) outputFile.length() else totalBytesRead
        if (finalLength < 1024L) {
            if (outputFile.exists()) outputFile.delete()
            throw java.io.IOException("Downloaded MP4 file is incomplete or corrupted ($finalLength bytes)")
        }

        onProgress(
            DownloadProgress(
                taskId = 0L,
                downloadedBytes = finalLength,
                totalBytes = finalLength,
                speedBps = 0L,
                etaSeconds = 0L,
                isCompleted = true,
                explicitProgress = 1.0f
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

        if (destination.exists() && destination.length() < 1024L) {
            destination.delete()
            throw java.io.IOException("Stitched multi-chunk file is too small (${destination.length()} bytes)")
        }
    }

    private data class ServerProbeResult(
        val contentLength: Long,
        val acceptsRanges: Boolean
    )
}
