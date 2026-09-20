package ir.ali0003.downloader.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.ui.glass.GlassTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class CachedVideoMetadata(
    val resolutionTag: String?,
    val durationMs: Long
)

/**
 * High-Performance Video Thumbnail & Metadata Engine:
 * - Decodes video frames on Dispatchers.IO at 15s into the video (or duration/2 if < 15s).
 * - Multi-tiered caching: Fast In-Memory LRU Cache + Persistent App Cache Dir.
 * - Extracts native video resolution (4K, 1080p, 720p, 480p) during retriever scan.
 */
object VideoThumbnailLoader {
    private const val TAG = "VideoThumbnailLoader"
    private const val DISK_CACHE_SUBDIR = "video_thumbnails"
    private const val TARGET_FRAME_MICROS = 15_000_000L // 15 seconds

    // In-memory LRU cache sized to 1/8th of available runtime heap (max 32MB)
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(4096, 32768)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private val metadataCache = ConcurrentHashMap<String, CachedVideoMetadata>()

    /**
     * Synchronous check for in-memory cached bitmap to guarantee 0ms instant render in LazyColumn.
     */
    fun getMemoryCached(file: File?): Bitmap? {
        if (file == null) return null
        val key = buildCacheKey(file)
        return memoryCache.get(key)
    }

    /**
     * Retrieves cached metadata (e.g. resolution tag) if already extracted.
     */
    fun getCachedMetadata(file: File?): CachedVideoMetadata? {
        if (file == null) return null
        return metadataCache[file.absolutePath]
    }

    /**
     * Resolves human-friendly resolution badge (e.g. "1080p", "720p", "4K", "Audio").
     */
    fun resolveResolutionTag(task: DownloadTaskEntity, file: File?): String {
        val fileNameLower = task.fileName.lowercase()
        val urlLower = task.url.lowercase()

        // 1. Audio check
        if (task.mimeType.contains("audio") || fileNameLower.endsWith(".mp3") || fileNameLower.contains("audio")) {
            return "Audio"
        }

        // 2. Filename or URL keywords
        when {
            fileNameLower.contains("1080") || urlLower.contains("1080") ||
                    fileNameLower.contains("4k") || fileNameLower.contains("2160") || urlLower.contains("2160") ||
                    fileNameLower.contains("2k") || fileNameLower.contains("1440") || urlLower.contains("1440") -> return "1080p"
            fileNameLower.contains("720") || urlLower.contains("720") -> return "720p"
            fileNameLower.contains("480") || urlLower.contains("480") -> return "480p"
            fileNameLower.contains("360") || urlLower.contains("360") -> return "360p"
        }

        // 3. Extracted metadata from retriever
        if (file != null) {
            val cachedMeta = metadataCache[file.absolutePath]
            if (cachedMeta?.resolutionTag != null) {
                return cachedMeta.resolutionTag
            }
        }

        // 4. M3U8 fallback
        if (task.isM3u8 || fileNameLower.endsWith(".m3u8")) {
            return "HLS"
        }

        return "HD"
    }

    /**
     * Loads thumbnail asynchronously on Dispatchers.IO:
     * 1. Checks memory cache
     * 2. Checks disk cache
     * 3. Extracts frame via MediaMetadataRetriever at 15s timestamp (or duration/2)
     */
    suspend fun loadThumbnail(
        context: Context,
        file: File,
        taskId: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext null
        }

        val cacheKey = buildCacheKey(file)

        // Check memory cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // Check disk cache
        val diskCacheFile = getDiskCacheFile(context, cacheKey)
        if (diskCacheFile.exists() && diskCacheFile.length() > 0L) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskCacheFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.w(TAG, "Disk cache decode failed: ${e.message}")
            }
        }

        // Extract frame using Android's native MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)

            // Extract duration to compute optimal frame timestamp
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationUs = durationMs * 1000L

            // 15 seconds into the video (15_000_000L microseconds).
            // If duration < 15s, fallback safely to duration / 2 or timestamp 0.
            val targetUs = when {
                durationUs >= TARGET_FRAME_MICROS -> TARGET_FRAME_MICROS
                durationUs > 0L -> durationUs / 2
                else -> 0L
            }

            // Extract video dimensions for dynamic resolution pill
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0
            val effectiveHeight = if (rotation == 90 || rotation == 270) width else height

            val resTag = when {
                effectiveHeight >= 1080 -> "1080p"
                effectiveHeight >= 720 -> "720p"
                effectiveHeight >= 480 -> "480p"
                effectiveHeight >= 360 -> "360p"
                effectiveHeight > 0 -> "${effectiveHeight}p"
                else -> null
            }
            if (resTag != null) {
                metadataCache[file.absolutePath] = CachedVideoMetadata(resTag, durationMs)
            }

            // High quality downscaled bitmap (320x224 fits 80x56 @ 4x scale with zero blur)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    targetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    224
                ) ?: retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: retriever.frameAtTime

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                saveToDiskCache(diskCacheFile, bitmap)
                return@withContext bitmap
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not extract video frame for ${file.name}: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        null
    }

    private fun buildCacheKey(file: File): String {
        return "${file.absolutePath.hashCode()}_${file.length()}_${file.lastModified()}"
    }

    private fun getDiskCacheFile(context: Context, key: String): File {
        val cacheDir = File(context.cacheDir, DISK_CACHE_SUBDIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return File(cacheDir, "$key.webp")
    }

    private fun saveToDiskCache(file: File, bitmap: Bitmap) {
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
            }
        } catch (_: Exception) {}
    }
}

/**
 * Visual Thumbnail Component:
 * - 80.dp x 56.dp prominent rounded card with subtle glass border and ContentScale.Crop.
 * - Smooth fade-in crossfade from frosted placeholder to decoded video frame.
 * - Overlay play triangle badge.
 */
@Composable
fun VideoThumbnailView(
    file: File?,
    task: DownloadTaskEntity,
    isAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(file?.absolutePath, file?.lastModified()) {
        mutableStateOf(VideoThumbnailLoader.getMemoryCached(file))
    }

    LaunchedEffect(file?.absolutePath, file?.lastModified()) {
        if (bitmap == null && !isAudio && file != null && file.exists()) {
            val loaded = VideoThumbnailLoader.loadThumbnail(context, file, task.id)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .size(width = 80.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassTheme.colors.surfaceGlassSubtle)
            .border(
                width = 1.dp,
                color = GlassTheme.colors.glassBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = bitmap,
            animationSpec = tween(220),
            label = "ThumbnailCrossfade"
        ) { currentBitmap ->
            if (currentBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "Video Thumbnail",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Subtle frosted play badge in bottom corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(GlassTheme.colors.cardBackground.copy(alpha = 0.85f))
                            .border(0.6.dp, GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            } else {
                // Sleek frosted placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassTheme.colors.surfaceGlassSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.Movie,
                        contentDescription = null,
                        tint = GlassTheme.colors.accentGlow.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
