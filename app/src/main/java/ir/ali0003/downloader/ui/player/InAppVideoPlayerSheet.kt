@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package ir.ali0003.downloader.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.model.DownloadStatus
import ir.ali0003.downloader.data.vault.VaultFileManager
import ir.ali0003.downloader.ui.glass.GlassTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * Premium Cinematic In-App Video Player powered by Media3 ExoPlayer.
 * Features:
 * - Floating Frosted Glass UI with top and bottom vignettes
 * - Custom scrub bar with live dragging feedback & time pill
 * - Double-tap left/right to seek 10s with ripple visual animations
 * - Polished pill controls for Aspect Ratio, Playback Speed, and Quick Seek
 * - Seamless support for offline downloads, vault encrypted files, and online streams
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InAppVideoPlayerSheet(
    task: DownloadTaskEntity,
    vaultFileManager: VaultFileManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Double-tap visual feedback state (+10s or -10s)
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) } // "left" or "right"

    // Scrubbing interactive state
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionFraction by remember { mutableFloatStateOf(0f) }

    // Resize Mode (Fit = 0, Fill = 3, Zoom = 4)
    val resizeModes = listOf(
        Pair("FIT", AspectRatioFrameLayout.RESIZE_MODE_FIT),
        Pair("FILL", AspectRatioFrameLayout.RESIZE_MODE_FILL),
        Pair("ZOOM", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    )
    var currentResizeIndex by remember { mutableIntStateOf(0) }

    // Playback Speed Options
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeedIndex by remember { mutableIntStateOf(2) } // default 1.0x

    // Create ExoPlayer instance: For completed tasks with a valid local target file,
    // play DIRECTLY via FileDataSource / DefaultDataSource and do NOT attempt to load from Media3 SimpleCache
    val exoPlayer = remember(task.id) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val headers = ir.ali0003.downloader.downloader.core.ChunkDownloader.parseHeaders(task.headersJson, task.url)
        val targetLocal = resolveLocalTargetFile(context, task, vaultFileManager)
        val isCompletedWithLocalFile = task.status == DownloadStatus.COMPLETED && targetLocal != null

        val mediaSourceFactory = if (isCompletedWithLocalFile) {
            // Standalone local file playback without SimpleCache overhead or cache keys
            val fileDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(fileDataSourceFactory)
        } else {
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                ir.ali0003.downloader.downloader.media3.Media3DownloadManagerProvider.getCacheDataSourceFactory(context, headers)
            )
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
            }
    }

    // Auto hide controls timer (stays visible when paused or user is scrubbing)
    LaunchedEffect(showControls, isPlaying, isUserScrubbing) {
        if (showControls && isPlaying && !isUserScrubbing) {
            delay(4000)
            showControls = false
        }
    }

    // Double-tap visual feedback auto-dismiss
    LaunchedEffect(doubleTapFeedbackSide) {
        if (doubleTapFeedbackSide != null) {
            delay(650)
            doubleTapFeedbackSide = null
        }
    }

    // Periodic position updater
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying && !isUserScrubbing) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    // Resolve media URI: seamlessly handling standalone local files, Media3 SimpleCache offline HLS, and direct streams
    LaunchedEffect(task.id) {
        val targetLocal = resolveLocalTargetFile(context, task, vaultFileManager)
        val isCompletedWithLocalFile = task.status == DownloadStatus.COMPLETED && targetLocal != null

        if (isCompletedWithLocalFile) {
            // Play DIRECTLY as a standalone local media item using standard FileDataSource / Uri.fromFile(file)
            // Do NOT attempt to load from Media3 SimpleCache for completed tasks that have already been remuxed into standalone MP4 files.
            val validFile = targetLocal!!
            val fileNameLower = validFile.name.lowercase()
            val mimeType = when {
                fileNameLower.endsWith(".webm") -> androidx.media3.common.MimeTypes.VIDEO_WEBM
                fileNameLower.endsWith(".mkv") -> androidx.media3.common.MimeTypes.VIDEO_MATROSKA
                fileNameLower.endsWith(".mp3") -> androidx.media3.common.MimeTypes.AUDIO_MPEG
                fileNameLower.endsWith(".m4a") -> androidx.media3.common.MimeTypes.AUDIO_AAC
                fileNameLower.endsWith(".ts") -> androidx.media3.common.MimeTypes.VIDEO_MP2T
                else -> androidx.media3.common.MimeTypes.VIDEO_MP4
            }
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(validFile))
                .setMimeType(mimeType)
                .build()

            val fileDataSourceFactory = androidx.media3.datasource.FileDataSource.Factory()
            val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(fileDataSourceFactory)
                .createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            val isHls = task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true)
            val isLocalProgressiveFile = targetLocal != null &&
                    targetLocal.exists() &&
                    targetLocal.length() > 512L &&
                    !isPlaceholderFile(targetLocal)

            // Make sure headers are registered in provider
            val headers = ir.ali0003.downloader.downloader.core.ChunkDownloader.parseHeaders(task.headersJson, task.url)
            ir.ali0003.downloader.downloader.media3.Media3DownloadManagerProvider.registerRequestHeaders(task.url, headers)

            val mediaItem = if (isHls && !isLocalProgressiveFile) {
                // Offline HLS downloaded via Media3 or online stream backed by CacheDataSource
                MediaItem.Builder()
                    .setUri(Uri.parse(task.url))
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    .build()
            } else if (isLocalProgressiveFile) {
                val validFile = targetLocal!!
                val fileNameLower = validFile.name.lowercase()
                val mimeType = when {
                    fileNameLower.endsWith(".webm") -> androidx.media3.common.MimeTypes.VIDEO_WEBM
                    fileNameLower.endsWith(".mkv") -> androidx.media3.common.MimeTypes.VIDEO_MATROSKA
                    fileNameLower.endsWith(".mp3") -> androidx.media3.common.MimeTypes.AUDIO_MPEG
                    fileNameLower.endsWith(".m4a") -> androidx.media3.common.MimeTypes.AUDIO_AAC
                    fileNameLower.endsWith(".ts") -> androidx.media3.common.MimeTypes.VIDEO_MP2T
                    else -> androidx.media3.common.MimeTypes.VIDEO_MP4
                }
                MediaItem.Builder()
                    .setUri(Uri.fromFile(validFile))
                    .setMimeType(mimeType)
                    .build()
            } else {
                // Online direct or streaming URL fallback
                if (isHls) {
                    MediaItem.Builder()
                        .setUri(Uri.parse(task.url))
                        .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                        .build()
                } else {
                    MediaItem.fromUri(Uri.parse(task.url))
                }
            }

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Player event listener & clean resource release
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("InAppVideoPlayer", "Playback error: ${error.message}", error)
                val targetLocal = resolveLocalTargetFile(context, task, vaultFileManager)
                if (task.status == DownloadStatus.COMPLETED && targetLocal != null) {
                    // Direct local file retry using DefaultMediaSourceFactory if ProgressiveMediaSource threw extractor issue
                    try {
                        val localItem = MediaItem.fromUri(Uri.fromFile(targetLocal))
                        val defaultMediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                            .createMediaSource(localItem)
                        exoPlayer.setMediaSource(defaultMediaSource)
                        exoPlayer.prepare()
                        exoPlayer.play()
                        return
                    } catch (_: Exception) {}
                }

                if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.scheme == "file" && task.url.isNotBlank()) {
                    Log.d("InAppVideoPlayer", "Falling back to stream URL: ${task.url}")
                    val fallbackItem = if (task.isM3u8 || task.url.contains(".m3u8", ignoreCase = true)) {
                        MediaItem.Builder()
                            .setUri(Uri.parse(task.url))
                            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                            .build()
                    } else {
                        MediaItem.fromUri(Uri.parse(task.url))
                    }
                    exoPlayer.setMediaItem(fallbackItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            try {
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. AndroidView Video Player Surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = resizeModes[currentResizeIndex].second
                    }
                },
                update = { view ->
                    view.resizeMode = resizeModes[currentResizeIndex].second
                },
                onRelease = { view ->
                    try {
                        view.player = null
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Gesture Capture Layer: Single tap toggles controls, Double tap seeks -10s / +10s
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth * 0.4f) {
                                    // Rewind 10 seconds
                                    val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    doubleTapFeedbackSide = "left"
                                } else if (offset.x > screenWidth * 0.6f) {
                                    // Fast-forward 10 seconds
                                    val target = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    doubleTapFeedbackSide = "right"
                                } else {
                                    // Middle double tap: Play / Pause toggle
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            }
                        )
                    }
            )

            // 3. Double-tap animated feedback badges
            AnimatedVisibility(
                visible = doubleTapFeedbackSide == "left",
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = null,
                            tint = GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "-10s",
                            color = GlassTheme.colors.accentGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = doubleTapFeedbackSide == "right",
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = null,
                            tint = GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "+10s",
                            color = GlassTheme.colors.accentGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Buffering Spinner
            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .border(1.dp, GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.3f), CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = GlassTheme.colors.accentGlow,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // 5. Cinematic Vignettes & Controls Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(220)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top Shadow Vignette
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.85f),
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Bottom Shadow Vignette
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )

                    // TOP BAR: Elegant Floating Glass Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title & Vault Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (task.isHidden) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                                        .border(0.8.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = GlassTheme.colors.accentGlow,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "VAULT",
                                            color = GlassTheme.colors.accentGlow,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                text = task.fileName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Top Action Pills (Speed, Aspect Ratio & Close)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Aspect Ratio Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        currentResizeIndex = (currentResizeIndex + 1) % resizeModes.size
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FitScreen,
                                        contentDescription = null,
                                        tint = GlassTheme.colors.accentGlow,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = resizeModes[currentResizeIndex].first,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Playback Speed Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
                                        val newSpeed = speeds[currentSpeedIndex]
                                        exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SlowMotionVideo,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${speeds[currentSpeedIndex]}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Close Button (Glass Circular Button)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                    .clickable(onClick = onDismiss)
                                    .testTag("player_close_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // CENTER TRANSPORT CONTROLS: Modern Floating Island
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s Button
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable {
                                    val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    doubleTapFeedbackSide = "left"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause Hero Button with Neon Glow Aura
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = CircleShape,
                                    ambientColor = GlassTheme.colors.accentGlow,
                                    spotColor = GlassTheme.colors.accentGlow
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            GlassTheme.colors.accentGlow,
                                            GlassTheme.colors.accentGlow.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                                .clickable {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                                .testTag("player_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF060A10),
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        // Forward 10s Button
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable {
                                    val target = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    doubleTapFeedbackSide = "right"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // BOTTOM CONTROLS: Floating Glass Bar with Modern Scrubbing Track
                    // Elevated with explicit safe insets so it never clips into system navigation gesture bars
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0D1420).copy(alpha = 0.88f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Scrubber Progress Bar with Sleek Neon Styling
                            val displayFraction = if (isUserScrubbing) {
                                scrubPositionFraction
                            } else if (duration > 0) {
                                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            // Interactive M3 Slider styled with neon glow
                            Slider(
                                value = displayFraction,
                                onValueChange = { frac ->
                                    isUserScrubbing = true
                                    scrubPositionFraction = frac
                                },
                                onValueChangeFinished = {
                                    if (duration > 0) {
                                        val target = (scrubPositionFraction * duration).toLong()
                                        currentPosition = target
                                        exoPlayer.seekTo(target)
                                    }
                                    isUserScrubbing = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = GlassTheme.colors.accentGlow,
                                    activeTrackColor = GlassTheme.colors.accentGlow,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 0.dp)
                            )

                            // Timestamp and Status Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current Position Pill
                                val displayedCurrentMs = if (isUserScrubbing && duration > 0) {
                                    (scrubPositionFraction * duration).toLong()
                                } else {
                                    currentPosition
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Live neon indicator dot
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlaying) GlassTheme.colors.accentGlow else Color.Gray)
                                    )
                                    Text(
                                        text = formatDuration(displayedCurrentMs),
                                        color = GlassTheme.colors.accentGlow,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "/",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = formatDuration(duration),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                // Remaining duration countdown badge
                                val remaining = (duration - displayedCurrentMs).coerceAtLeast(0L)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "-${formatDuration(remaining)}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun isPlaceholderFile(file: File): Boolean {
    if (!file.exists() || file.length() < 100L) return true
    return try {
        if (file.length() < 256L) {
            val content = file.readText().trim()
            content.startsWith("MEDIA3_OFFLINE_CACHE_COMPLETED")
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

private fun resolveLocalTargetFile(context: Context, task: DownloadTaskEntity, vaultFileManager: VaultFileManager): File? {
    // 1. Direct explicit task.localFilePath if non-blank and existing
    task.localFilePath?.takeIf { it.isNotBlank() }?.let { path ->
        val file = File(path)
        if (file.exists() && file.length() > 512L && !isPlaceholderFile(file)) {
            return file
        }
    }

    // 2. VaultFileManager multi-directory resolver
    val resolved = vaultFileManager.resolveTaskFile(task)
    if (resolved != null && resolved.exists() && resolved.length() > 512L && !isPlaceholderFile(resolved)) {
        return resolved
    }

    // 3. Search public Downloads and private app directories
    val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    val appPublicDir = File(publicDir, "0003_Downloader")
    val extDir = File(context.getExternalFilesDir(null), "downloads")
    val vaultDir = File(context.filesDir, "vault_media")

    val clean = task.fileName.trimStart('.')
    val sanitized = clean.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    val candidates = mutableListOf(clean, sanitized)
    if (clean.endsWith(".m3u8", ignoreCase = true)) {
        candidates.add(clean.removeSuffix(".m3u8") + ".mp4")
        candidates.add(sanitized.removeSuffix(".m3u8") + ".mp4")
    }
    if (!clean.endsWith(".mp4", ignoreCase = true)) {
        candidates.add("$clean.mp4")
        candidates.add("$sanitized.mp4")
    }

    val searchDirs = listOf(appPublicDir, publicDir, extDir, vaultDir, context.filesDir)
    for (dir in searchDirs) {
        if (!dir.exists()) continue
        for (name in candidates.distinct()) {
            val f = File(dir, name)
            if (f.exists() && f.length() > 512L && !isPlaceholderFile(f)) return f
            val vFile = File(dir, ".$name.vault")
            if (vFile.exists() && vFile.length() > 512L && !isPlaceholderFile(vFile)) return vFile
        }
    }

    return null
}
