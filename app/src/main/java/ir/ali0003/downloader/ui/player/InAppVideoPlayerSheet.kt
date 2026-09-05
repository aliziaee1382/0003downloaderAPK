package ir.ali0003.downloader.ui.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import ir.ali0003.downloader.data.vault.VaultFileManager
import ir.ali0003.downloader.ui.glass.GlassBadge
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fullscreen In-App Glassmorphic Video Player powered by Media3 ExoPlayer.
 * Seamlessly plays public MP4 files, raw streams, and encrypted/hidden .vault files.
 */
@OptIn(UnstableApi::class)
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

    // Resize Mode (Fit = 0, Fill = 3, Zoom = 4, FixedWidth = 1, FixedHeight = 2)
    val resizeModes = listOf(
        Pair("FIT", AspectRatioFrameLayout.RESIZE_MODE_FIT),
        Pair("FILL", AspectRatioFrameLayout.RESIZE_MODE_FILL),
        Pair("ZOOM", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    )
    var currentResizeIndex by remember { mutableIntStateOf(0) }

    // Playback Speed Options
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeedIndex by remember { mutableIntStateOf(2) } // default 1.0x

    // Create ExoPlayer instance with explicit AudioAttributes
    val exoPlayer = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
            }
    }

    // Auto hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Periodic position updater
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    // Resolve media URI (from Vault file, public storage, or direct URL)
    LaunchedEffect(task) {
        val targetFile = vaultFileManager.resolveTaskFile(task)
        val mediaUri = if (targetFile != null && targetFile.exists()) {
            Uri.fromFile(targetFile)
        } else {
            Uri.parse(task.url)
        }

        val mediaItem = MediaItem.fromUri(mediaUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        ) {
            // Video View Container
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Use our custom Glass UI overlay
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

            // Buffering Spinner
            if (isBuffering) {
                CircularProgressIndicator(
                    color = GlassTheme.colors.accentGlow,
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.Center)
                )
            }

            // Glass Overlay Controls
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    // Top Bar (Filename, Vault Badge, Aspect Ratio, Speed & Close)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (task.isHidden) {
                                GlassBadge(
                                    text = "VAULT",
                                    color = GlassTheme.colors.accentGlow
                                )
                            }
                            Text(
                                text = task.fileName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Aspect Ratio Toggle
                            GlassBox(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        currentResizeIndex = (currentResizeIndex + 1) % resizeModes.size
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = GlassTheme.colors.surfaceGlass.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = resizeModes[currentResizeIndex].first,
                                    color = GlassTheme.colors.accentGlow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Playback Speed Toggle
                            GlassBox(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
                                        val newSpeed = speeds[currentSpeedIndex]
                                        exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = GlassTheme.colors.surfaceGlass.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "${speeds[currentSpeedIndex]}x",
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Close Button
                            GlassIconButton(
                                icon = Icons.Default.Close,
                                onClick = onDismiss,
                                size = 36.dp,
                                iconSize = 18.dp,
                                contentDescription = "Close Player"
                            )
                        }
                    }

                    // Center Transport Controls (Rewind 10s, Play/Pause, Fast Forward 10s)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        GlassIconButton(
                            icon = Icons.Default.Replay10,
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            },
                            size = 50.dp,
                            iconSize = 28.dp,
                            contentDescription = "Rewind 10s"
                        )

                        // Play/Pause Hero Button
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.accentGlow)
                                .clickable {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Forward 10s
                        GlassIconButton(
                            icon = Icons.Default.Forward10,
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                                exoPlayer.seekTo(target)
                            },
                            size = 50.dp,
                            iconSize = 28.dp,
                            contentDescription = "Forward 10s"
                        )
                    }

                    // Bottom Scrubbing Bar & Time Badges
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatDuration(duration),
                                color = GlassTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Slider(
                            value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                if (duration > 0) {
                                    val target = (frac * duration).toLong()
                                    currentPosition = target
                                    exoPlayer.seekTo(target)
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = GlassTheme.colors.accentGlow,
                                activeTrackColor = GlassTheme.colors.accentGlow,
                                inactiveTrackColor = GlassTheme.colors.surfaceGlassSubtle
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
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
