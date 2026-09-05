package ir.ali0003.downloader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.model.DownloadStatus
import ir.ali0003.downloader.downloader.model.DownloadProgress
import ir.ali0003.downloader.ui.glass.GlassBadge
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme

/**
 * Download Hub UI Tab displaying active & queued downloads with glassmorphic cards,
 * real-time progress bars, speed, ETA, and Pause/Resume/Cancel controls.
 */
@Composable
fun ActiveDownloadsScreen(
    downloads: List<DownloadTaskEntity>,
    progressMap: Map<Long, DownloadProgress> = emptyMap(),
    onAddDownload: () -> Unit,
    onTogglePause: (DownloadTaskEntity) -> Unit,
    onToggleVault: (DownloadTaskEntity) -> Unit,
    onDelete: (DownloadTaskEntity) -> Unit,
    onSimulate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Quick Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                text = "+ Add Task",
                icon = Icons.Default.Add,
                onClick = onAddDownload,
                modifier = Modifier.weight(1f),
                testTag = "add_download_button"
            )
            GlassButton(
                text = "Simulate Speed",
                icon = Icons.Default.Speed,
                onClick = onSimulate,
                isPrimary = false,
                modifier = Modifier.weight(1f),
                testTag = "simulate_speed_button"
            )
        }

        if (downloads.isEmpty()) {
            EmptyStateQueueView(
                icon = Icons.Default.ArrowDownward,
                title = "No Active Downloads",
                subtitle = "Browse the web or tap '+ Add Task' to start multi-threaded chunk downloads."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { task ->
                    val liveProgress = progressMap[task.id]
                    ActiveDownloadTaskCard(
                        task = task,
                        liveProgress = liveProgress,
                        onTogglePause = { onTogglePause(task) },
                        onToggleVault = { onToggleVault(task) },
                        onDelete = { onDelete(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadTaskCard(
    task: DownloadTaskEntity,
    liveProgress: DownloadProgress?,
    onTogglePause: () -> Unit,
    onToggleVault: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = liveProgress?.progress ?: task.progress
    val downloadedBytes = liveProgress?.downloadedBytes ?: task.downloadedBytes
    val totalBytes = liveProgress?.totalBytes ?: task.totalBytes
    val speedBps = liveProgress?.speedBps ?: task.speedBps
    val etaText = liveProgress?.formattedEta ?: "--"

    val isAudio = task.mimeType.contains("audio") || task.fileName.endsWith(".mp3")

    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Media Format Icon, Name & Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassTheme.colors.surfaceGlassSubtle)
                            .border(1.dp, GlassTheme.colors.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.Movie,
                            contentDescription = "Media Type",
                            tint = GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.fileName,
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = task.websiteUrl,
                            color = GlassTheme.colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.isM3u8) {
                        GlassBadge(text = "HLS Stream", color = GlassTheme.colors.secondaryGlow)
                    }
                    val statusColor = when (task.status) {
                        DownloadStatus.DOWNLOADING -> GlassTheme.colors.accentGlow
                        DownloadStatus.COMPLETED -> GlassTheme.colors.successGlass
                        DownloadStatus.PAUSED -> GlassTheme.colors.warningGlass
                        DownloadStatus.FAILED -> GlassTheme.colors.dangerGlass
                        DownloadStatus.QUEUED -> GlassTheme.colors.textSecondary
                    }
                    GlassBadge(text = task.status.displayName, color = statusColor)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = GlassTheme.colors.accentGlow,
                trackColor = GlassTheme.colors.surfaceGlassSubtle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Stats and Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size, Speed & ETA
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${DownloadTaskEntity.formatFileSize(downloadedBytes)} / ${if (totalBytes > 0) DownloadTaskEntity.formatFileSize(totalBytes) else "..."}",
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (task.status == DownloadStatus.DOWNLOADING && speedBps > 0) {
                            Text(
                                text = "• ${DownloadTaskEntity.formatFileSize(speedBps)}/s",
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (task.status == DownloadStatus.DOWNLOADING && etaText != "--") {
                        Text(
                            text = "ETA: $etaText",
                            color = GlassTheme.colors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Action Buttons (Pause/Resume, Vault Lock, Delete)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.status.isActive || task.status == DownloadStatus.PAUSED) {
                        GlassIconButton(
                            icon = if (task.status == DownloadStatus.DOWNLOADING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            onClick = onTogglePause,
                            size = 36.dp,
                            iconSize = 18.dp,
                            contentDescription = if (task.status == DownloadStatus.DOWNLOADING) "Pause Download" else "Resume Download"
                        )
                    }

                    // Vault Hide / Unhide button
                    GlassIconButton(
                        icon = if (task.isHidden) Icons.Default.VisibilityOff else Icons.Default.Lock,
                        onClick = onToggleVault,
                        tint = if (task.isHidden) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                        size = 36.dp,
                        iconSize = 18.dp,
                        contentDescription = "Toggle Vault Lock"
                    )

                    // Delete button
                    GlassIconButton(
                        icon = Icons.Default.Delete,
                        onClick = onDelete,
                        tint = GlassTheme.colors.dangerGlass,
                        size = 36.dp,
                        iconSize = 18.dp,
                        contentDescription = "Delete Download"
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateQueueView(icon: ImageVector, title: String, subtitle: String) {
    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GlassTheme.colors.accentGlow.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = GlassTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = GlassTheme.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
