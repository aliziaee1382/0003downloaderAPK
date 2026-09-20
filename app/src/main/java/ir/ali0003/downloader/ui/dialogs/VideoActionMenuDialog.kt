package ir.ali0003.downloader.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.ui.glass.GlassBadge
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.media.VideoThumbnailLoader
import ir.ali0003.downloader.ui.media.VideoThumbnailView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Elegant Frosted Glass Contextual Action Sheet:
 * Triggered on card long-press with smooth spring physics entry.
 * Presents rich header preview and quick action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionMenuDialog(
    task: DownloadTaskEntity,
    file: File?,
    onDismiss: () -> Unit,
    onPlayVideo: () -> Unit,
    onShareVideo: () -> Unit,
    onToggleVault: () -> Unit,
    onDeleteVideo: () -> Unit,
    onEnterMultiSelect: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAudio = task.mimeType.contains("audio") || task.fileName.endsWith(".mp3")
    val resolutionTag = remember(task.fileName, task.url, file) {
        VideoThumbnailLoader.resolveResolutionTag(task, file)
    }
    val formattedDate = remember(task.completedAt, task.createdAt) {
        val ts = task.completedAt?.takeIf { it > 0 } ?: task.createdAt
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.8f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .testTag("video_action_menu_dialog")
        ) {
            // 1. Header with Video Thumbnail & Full Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 80x56 Thumbnail
                VideoThumbnailView(
                    file = file,
                    task = task,
                    isAudio = isAudio
                )

                // Title & Tag Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName.trimStart('.').removeSuffix(".vault"),
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle metadata line: Resolution • File Size • Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Resolution pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(GlassTheme.colors.accentGlow.copy(alpha = 0.15f))
                                .border(0.6.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = resolutionTag,
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "•",
                            color = GlassTheme.colors.textMuted,
                            fontSize = 10.sp
                        )

                        Text(
                            text = task.formattedTotalSize,
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "•",
                            color = GlassTheme.colors.textMuted,
                            fontSize = 10.sp
                        )

                        Text(
                            text = formattedDate,
                            color = GlassTheme.colors.textMuted,
                            fontSize = 11.sp
                        )

                        if (task.isHidden) {
                            Spacer(modifier = Modifier.width(2.dp))
                            GlassBadge(text = "VAULT", color = GlassTheme.colors.accentGlow)
                        }
                    }
                }

                // Close icon button
                GlassIconButton(
                    icon = Icons.Default.Close,
                    onClick = onDismiss,
                    size = 32.dp,
                    iconSize = 16.dp,
                    contentDescription = "Close"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Thin Frosted Divider Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(GlassTheme.colors.glassBorder.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Action Rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Action 1: Play Video
                ActionItemRow(
                    title = "Play Video",
                    subtitle = "Launch in-app fullscreen player",
                    icon = Icons.Default.PlayArrow,
                    iconTint = GlassTheme.colors.accentGlow,
                    iconBackground = GlassTheme.colors.accentGlow.copy(alpha = 0.18f),
                    testTag = "action_play_video",
                    onClick = {
                        onDismiss()
                        onPlayVideo()
                    }
                )

                // Action 2: Share Video
                ActionItemRow(
                    title = "Share Video",
                    subtitle = "Send video via WhatsApp, Telegram, or other apps",
                    icon = Icons.Default.Share,
                    iconTint = GlassTheme.colors.textPrimary,
                    iconBackground = GlassTheme.colors.surfaceGlassSubtle,
                    testTag = "action_share_video",
                    onClick = {
                        onDismiss()
                        onShareVideo()
                    }
                )

                // Action 3: Move to Vault / Encrypt
                val isVaulted = task.isHidden
                ActionItemRow(
                    title = if (isVaulted) "Unhide from Vault" else "Move to Vault / Encrypt",
                    subtitle = if (isVaulted) "Restore to public device media library" else "Conceal video with AES-256 encryption",
                    icon = if (isVaulted) Icons.Default.LockOpen else Icons.Default.Lock,
                    iconTint = if (isVaulted) GlassTheme.colors.successGlass else GlassTheme.colors.accentGlow,
                    iconBackground = if (isVaulted) GlassTheme.colors.successGlass.copy(alpha = 0.15f) else GlassTheme.colors.accentGlow.copy(alpha = 0.15f),
                    testTag = "action_vault_video",
                    onClick = {
                        onDismiss()
                        onToggleVault()
                    }
                )

                // Action 4: Delete from Device
                ActionItemRow(
                    title = "Delete from Device",
                    subtitle = "Permanently remove file and download history",
                    icon = Icons.Default.Delete,
                    iconTint = GlassTheme.colors.dangerGlass,
                    iconBackground = GlassTheme.colors.dangerGlass.copy(alpha = 0.15f),
                    titleColor = GlassTheme.colors.dangerGlass,
                    testTag = "action_delete_video",
                    onClick = {
                        onDismiss()
                        onDeleteVideo()
                    }
                )

                // Optional Action 5: Multi-Select
                if (onEnterMultiSelect != null) {
                    ActionItemRow(
                        title = "Select Multiple",
                        subtitle = "Select multiple files for batch actions",
                        icon = Icons.Default.Check,
                        iconTint = GlassTheme.colors.textSecondary,
                        iconBackground = GlassTheme.colors.surfaceGlassSubtle,
                        testTag = "action_multi_select",
                        onClick = {
                            onDismiss()
                            onEnterMultiSelect()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    titleColor: Color = GlassTheme.colors.textPrimary,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.6f))
            .border(0.7.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .heightIn(min = 52.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon Badge Circle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBackground)
                    .border(0.6.dp, iconTint.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = GlassTheme.colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
