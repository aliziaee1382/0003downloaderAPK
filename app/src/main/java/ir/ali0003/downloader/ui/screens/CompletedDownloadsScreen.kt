package ir.ali0003.downloader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.vault.VaultFileManager
import ir.ali0003.downloader.ui.glass.GlassBadge
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Completed Downloads Library:
 * - Displays downloaded videos/audio with metadata, format badges, and playback controls.
 * - Multi-select mode for batch Share, Delete, and Vault Hide/Unhide.
 * - Frosted Glass Vault toggle header.
 */
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

    // Multi-selection state
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode = selectedIds.isNotEmpty()

    val currentList = if (showVaultSection && isVaultUnlocked) vaultDownloads else downloads

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
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
                        .padding(horizontal = 12.dp, vertical = 7.dp),
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
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = if (showVaultSection && isVaultUnlocked) GlassTheme.colors.accentGlow.copy(alpha = 0.25f) else GlassTheme.colors.surfaceGlassSubtle
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isVaultUnlocked) GlassTheme.colors.successGlass else GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isVaultUnlocked) "Vault (${vaultDownloads.size})" else "Vault Locked",
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
                        icon = if (showVaultSection) Icons.Default.VisibilityOff else Icons.Default.Lock,
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
                    "Long-press any video in the Public Library and tap 'Hide to Vault' to conceal it."
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
                    CompletedVideoCard(
                        task = task,
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
                            if (!isSelected) selectedIds.add(task.id)
                        },
                        onToggleVault = { onToggleVault(task) },
                        onShare = { shareTasks(context, listOf(task), vaultFileManager) },
                        onDelete = { onDelete(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedVideoCard(
    task: DownloadTaskEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleVault: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isAudio = task.mimeType.contains("audio") || task.fileName.endsWith(".mp3")
    val dateText = remember(task.completedAt) {
        val completed = task.completedAt
        if (completed != null && completed > 0) {
            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(completed))
        } else {
            "Offline Ready"
        }
    }

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.15f) else GlassTheme.colors.cardBackground.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail / Format Icon Placeholder with Play Overlay
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassTheme.colors.surfaceGlassSubtle)
                    .border(1.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.Movie,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(24.dp)
                )

                // Play triangle overlay badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.accentGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.fileName,
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (task.isHidden) {
                        GlassBadge(text = "VAULT", color = GlassTheme.colors.accentGlow)
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = task.formattedTotalSize,
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "•",
                        color = GlassTheme.colors.textMuted,
                        fontSize = 11.sp
                    )

                    Text(
                        text = dateText,
                        color = GlassTheme.colors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Icons (Vault, Share, Delete) or Selection Checkbox
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
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
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vault Toggle
                    GlassIconButton(
                        icon = if (task.isHidden) Icons.Default.VisibilityOff else Icons.Default.Lock,
                        onClick = onToggleVault,
                        tint = if (task.isHidden) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                        size = 34.dp,
                        iconSize = 16.dp,
                        contentDescription = "Toggle Vault"
                    )

                    // Share
                    GlassIconButton(
                        icon = Icons.Default.Share,
                        onClick = onShare,
                        size = 34.dp,
                        iconSize = 16.dp,
                        contentDescription = "Share Video"
                    )

                    // Delete
                    GlassIconButton(
                        icon = Icons.Default.Delete,
                        onClick = onDelete,
                        tint = GlassTheme.colors.dangerGlass,
                        size = 34.dp,
                        iconSize = 16.dp,
                        contentDescription = "Delete Video"
                    )
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
    } catch (e: Exception) {
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
