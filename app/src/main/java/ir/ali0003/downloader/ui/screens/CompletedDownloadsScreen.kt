package ir.ali0003.downloader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.vault.VaultFileManager
import ir.ali0003.downloader.ui.dialogs.VideoActionMenuDialog
import ir.ali0003.downloader.ui.glass.GlassBadge
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.media.VideoThumbnailLoader
import ir.ali0003.downloader.ui.media.VideoThumbnailView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Completed Downloads Library:
 * - Minimalist, high-performance video cards with 15-second dynamic thumbnails.
 * - Contextual long-press frosted action sheet (`VideoActionMenuDialog`).
 * - Full-bleed video title (2 lines) and clean metadata tags (Resolution, Size, Date).
 * - Multi-select mode for batch Share, Delete, and Vault Hide/Unhide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedDownloadsScreen(
    downloads: List<DownloadTaskEntity>,
    vaultDownloads: List<DownloadTaskEntity>,
    isVaultUnlocked: Boolean,
    onOpenVaultAuth: () -> Unit,
    onLockVault: () -> Unit,
    onPlayVideo: (DownloadTaskEntity) -> Unit,
    onToggleVault: (DownloadTaskEntity) -> Unit,
    onDelete: (DownloadTaskEntity) -> Unit,
    vaultFileManager: VaultFileManager
) {
    val context = LocalContext.current
    var showVaultSection by remember { mutableStateOf(false) }

    // Automatically transition to vault view when unlocked, and back to library when locked
    LaunchedEffect(isVaultUnlocked) {
        if (isVaultUnlocked) {
            showVaultSection = true
        } else {
            showVaultSection = false
        }
    }

    // Multi-selection state
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedIds.isNotEmpty()

    // Contextual Action Menu Sheet state (Long-press)
    var actionMenuTask by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<DownloadTaskEntity?>(null) }

    val currentList = if (showVaultSection && isVaultUnlocked) vaultDownloads else downloads

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("completed_downloads_screen")
    ) {
        // Library Sub-Header & Vault Shortcut
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab Switcher (Public Library vs Secret Vault)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassBox(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showVaultSection = false }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("tab_public_library"),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = if (!showVaultSection) GlassTheme.colors.accentGlow.copy(alpha = 0.25f) else GlassTheme.colors.surfaceGlassSubtle
                ) {
                    Text(
                        text = "Public Library (${downloads.size})",
                        color = if (!showVaultSection) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                GlassBox(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (isVaultUnlocked) {
                                showVaultSection = true
                            } else {
                                onOpenVaultAuth()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("tab_vault_library"),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = if (showVaultSection && isVaultUnlocked) GlassTheme.colors.accentGlow.copy(alpha = 0.25f) else GlassTheme.colors.surfaceGlassSubtle
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isVaultUnlocked) GlassTheme.colors.successGlass else GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isVaultUnlocked) "Secret Vault (${vaultDownloads.size})" else "Secret Vault",
                            color = if (showVaultSection && isVaultUnlocked) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Selection Mode Actions or Vault Lock
            if (isSelectionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        color = GlassTheme.colors.accentGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassIconButton(
                        icon = Icons.Default.Close,
                        onClick = { selectedIds.clear() },
                        size = 32.dp,
                        iconSize = 16.dp,
                        contentDescription = "Cancel Selection"
                    )
                }
            } else if (isVaultUnlocked && showVaultSection) {
                GlassButton(
                    text = "Lock Vault",
                    icon = Icons.Default.Lock,
                    onClick = {
                        onLockVault()
                        showVaultSection = false
                    },
                    isPrimary = false,
                    modifier = Modifier.height(34.dp)
                )
            }
        }

        // Selection Action Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.95f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share
                    GlassButton(
                        text = "Share",
                        icon = Icons.Default.Share,
                        onClick = {
                            val selectedTasks = currentList.filter { selectedIds.contains(it.id) }
                            shareTasks(context, selectedTasks, vaultFileManager)
                            selectedIds.clear()
                        },
                        isPrimary = false,
                        modifier = Modifier.height(36.dp)
                    )

                    // Move to Vault / Unhide
                    GlassButton(
                        text = if (showVaultSection) "Unhide" else "Hide to Vault",
                        icon = if (showVaultSection) Icons.Default.LockOpen else Icons.Default.Security,
                        onClick = {
                            val selectedTasks = currentList.filter { selectedIds.contains(it.id) }
                            selectedTasks.forEach { onToggleVault(it) }
                            selectedIds.clear()
                        },
                        modifier = Modifier.height(36.dp)
                    )

                    // Delete
                    GlassButton(
                        text = "Delete",
                        icon = Icons.Default.Delete,
                        onClick = {
                            val selectedTasks = currentList.filter { selectedIds.contains(it.id) }
                            selectedTasks.forEach { onDelete(it) }
                            selectedIds.clear()
                        },
                        isPrimary = false,
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }

        if (currentList.isEmpty()) {
            EmptyStateLibraryView(
                icon = if (showVaultSection) Icons.Default.Security else Icons.Default.Folder,
                title = if (showVaultSection) "Secret Vault is Empty" else "No Completed Videos",
                subtitle = if (showVaultSection) {
                    "Long-press any video in the Public Library and select 'Move to Secret Vault' to protect it with PIN/Biometric encryption."
                } else {
                    "Download videos through the in-app browser or active queue to access offline playback."
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { it.id }) { task ->
                    val isSelected = selectedIds.contains(task.id)
                    val taskFile = remember(task.id) { vaultFileManager.resolveTaskFile(task) }

                    CompletedVideoCard(
                        task = task,
                        file = taskFile,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedIds.remove(task.id) else selectedIds.add(task.id)
                            } else {
                                onPlayVideo(task)
                            }
                        },
                        onLongClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedIds.remove(task.id) else selectedIds.add(task.id)
                            } else {
                                actionMenuTask = task
                            }
                        }
                    )
                }
            }
        }
    }

    // Contextual Long-Press Action Sheet
    actionMenuTask?.let { task ->
        val file = remember(task.id) { vaultFileManager.resolveTaskFile(task) }
        VideoActionMenuDialog(
            task = task,
            file = file,
            onDismiss = { actionMenuTask = null },
            onPlayVideo = {
                actionMenuTask = null
                onPlayVideo(task)
            },
            onShareVideo = {
                actionMenuTask = null
                shareTasks(context, listOf(task), vaultFileManager)
            },
            onToggleVault = {
                actionMenuTask = null
                onToggleVault(task)
            },
            onDeleteVideo = {
                actionMenuTask = null
                taskToDelete = task
            },
            onEnterMultiSelect = {
                actionMenuTask = null
                if (!selectedIds.contains(task.id)) {
                    selectedIds.add(task.id)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    taskToDelete?.let { targetTask ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            containerColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
            title = {
                Text(
                    text = "Delete Video?",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${targetTask.fileName.trimStart('.').removeSuffix(".vault")}\"? This file will be removed from your device storage.",
                    color = GlassTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                GlassButton(
                    text = "Delete",
                    icon = Icons.Default.Delete,
                    onClick = {
                        onDelete(targetTask)
                        taskToDelete = null
                    },
                    isPrimary = true,
                    modifier = Modifier.height(38.dp)
                )
            },
            dismissButton = {
                GlassButton(
                    text = "Cancel",
                    onClick = { taskToDelete = null },
                    isPrimary = false,
                    modifier = Modifier.height(38.dp)
                )
            }
        )
    }
}

/**
 * Minimalist Completed Video Card:
 * - Prominent 80x56 thumbnail with 15s native video frame capture.
 * - Full horizontal real-estate: bold 2-line title and clean subtitle pills (1080p • 30.4 MB • Sep 5).
 * - Click to play, Long-press for contextual action sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletedVideoCard(
    task: DownloadTaskEntity,
    file: File?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAudio = task.mimeType.contains("audio") || task.fileName.endsWith(".mp3")
    val resolutionTag = remember(task.fileName, task.url, file) {
        VideoThumbnailLoader.resolveResolutionTag(task, file)
    }
    val dateText = remember(task.completedAt, task.createdAt) {
        val completed = task.completedAt
        val ts = if (completed != null && completed > 0) completed else task.createdAt
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
    val displayTitle = remember(task.fileName) {
        task.fileName.trimStart('.').removeSuffix(".vault")
    }

    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("completed_video_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.15f) else GlassTheme.colors.cardBackground.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Dynamic 15-Second Video Thumbnail (80.dp x 56.dp)
            VideoThumbnailView(
                file = file,
                task = task,
                isAudio = isAudio
            )

            // 2. Info Column with 2 Full Lines of Title and Clean Subtitle Pills
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayTitle,
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

                // Subtitle Line: 1080p • 30.4 MB • Sep 5
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Resolution Badge Pill
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
                        text = dateText,
                        color = GlassTheme.colors.textMuted,
                        fontSize = 11.sp
                    )

                    if (task.isHidden) {
                        Spacer(modifier = Modifier.width(2.dp))
                        GlassBadge(text = "VAULT", color = GlassTheme.colors.accentGlow)
                    }
                }
            }

            // 3. Multi-Select Indicator Circle (Only visible during selection mode)
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.surfaceGlassSubtle)
                        .border(1.2.dp, if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun shareTasks(context: Context, tasks: List<DownloadTaskEntity>, vaultFileManager: VaultFileManager) {
    if (tasks.isEmpty()) return
    try {
        val uris = ArrayList<Uri>()
        for (task in tasks) {
            val file = vaultFileManager.resolveTaskFile(task)
            if (file != null && file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                uris.add(uri)
            }
        }

        if (uris.isNotEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
        }
    } catch (_: Exception) {
        // Fallback for direct share
    }
}

@Composable
fun EmptyStateLibraryView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GlassTheme.colors.accentGlow.copy(alpha = 0.75f),
                modifier = Modifier.size(50.dp)
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
