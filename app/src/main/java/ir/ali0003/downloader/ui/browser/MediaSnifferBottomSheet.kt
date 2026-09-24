package ir.ali0003.downloader.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import java.net.URI

/**
 * InShot-style Single-Step Media Sniffer Quality Sheet:
 * - Large video thumbnail preview with duration badge
 * - Editable title (rename video before downloading)
 * - Clean multi-tier resolution picker (1080p, 720p, 480p, 360p, MP3) with exact calculated sizes
 * - Instant 1-tap download or prominent master Download action button
 * - Save to Encrypted Vault toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSnifferBottomSheet(
    sniffedMediaList: List<SniffedMediaItem>,
    initialSelectedItem: SniffedMediaItem? = null,
    isExtracting: Boolean = false,
    saveToVault: Boolean = false,
    onToggleSaveToVault: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirmDownload: (SniffedMediaItem, VideoQualityOption?) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val mediaItem = initialSelectedItem ?: sniffedMediaList.firstOrNull()

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
        when {
            mediaItem != null -> {
                DirectQualitySheetContent(
                    mediaItem = mediaItem,
                    saveToVault = saveToVault,
                    onToggleSaveToVault = onToggleSaveToVault,
                    onDismiss = onDismiss,
                    onConfirmDownload = onConfirmDownload
                )
            }
            isExtracting -> {
                ExtractingMediaSnifferContent()
            }
            else -> {
                EmptyMediaSnifferContent(onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun DirectQualitySheetContent(
    mediaItem: SniffedMediaItem,
    saveToVault: Boolean,
    onToggleSaveToVault: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmDownload: (SniffedMediaItem, VideoQualityOption?) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val qualityOptions = remember(mediaItem) {
        val raw = if (mediaItem.qualities.isNotEmpty()) {
            mediaItem.qualities
        } else {
            resolveComprehensiveQualities(mediaItem)
        }
        deduplicateAndSortOptionsForSheet(raw)
    }

    var selectedOption by remember(qualityOptions) {
        mutableStateOf(qualityOptions.firstOrNull())
    }

    var editedTitle by remember(mediaItem.id) {
        mutableStateOf(mediaItem.displayTitle)
    }
    var isEditingTitle by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("direct_quality_sheet")
    ) {
        // 1. Header: Ready eyebrow + Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.accentGlow)
                )
                Text(
                    text = "READY TO DOWNLOAD",
                    color = GlassTheme.colors.accentGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            GlassIconButton(
                icon = Icons.Default.Close,
                onClick = onDismiss,
                size = 32.dp,
                iconSize = 16.dp,
                contentDescription = "Close"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Video Preview Card: Thumbnail + Editable Title + Source Domain
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
            borderColor = GlassTheme.colors.glassBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Thumbnail with Duration Overlay
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassTheme.colors.surfaceGlass)
                        .border(
                            0.8.dp,
                            GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!mediaItem.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = mediaItem.thumbnailUrl,
                            contentDescription = "Video Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (mediaItem.mimeType.contains("audio", true)) Icons.Default.Audiotrack else Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = GlassTheme.colors.accentGlow.copy(alpha = 0.8f),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Duration Badge overlay
                    val durStr = formatDurationString(mediaItem.durationSeconds)
                    if (durStr.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = durStr,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Title + Editable inline + Metadata Row
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (isEditingTitle) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(GlassTheme.colors.surfaceGlass, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("edit_title_input"),
                                textStyle = TextStyle(
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(GlassTheme.colors.accentGlow)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(GlassTheme.colors.accentGlow)
                                    .clickable { isEditingTitle = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save Title",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = editedTitle,
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Video",
                                tint = GlassTheme.colors.accentGlow,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { isEditingTitle = true }
                                    .padding(3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = extractCleanDomain(mediaItem.pageUrl.ifBlank { mediaItem.url }),
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
                            text = if (mediaItem.isM3u8) "HLS Adaptive" else "MP4 Direct",
                            color = GlassTheme.colors.accentGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "SELECT RESOLUTION",
            color = GlassTheme.colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Quality Options List (InShot Style: Radio Selection + 1-Tap Download Option)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            qualityOptions.forEach { option ->
                val isSelected = option == selectedOption
                InShotQualityCard(
                    option = option,
                    isSelected = isSelected,
                    onSelect = {
                        selectedOption = option
                    },
                    onInstantDownload = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val confirmedItem = mediaItem.copy(title = editedTitle.ifBlank { mediaItem.displayTitle })
                        onConfirmDownload(confirmedItem, option)
                        onDismiss()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Save to Encrypted Vault Toggle
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = if (saveToVault) GlassTheme.colors.accentGlow.copy(alpha = 0.15f) else GlassTheme.colors.surfaceGlassSubtle,
            borderColor = if (saveToVault) GlassTheme.colors.accentGlow.copy(alpha = 0.5f) else GlassTheme.colors.glassBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (saveToVault) Icons.Default.Lock else Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (saveToVault) GlassTheme.colors.accentGlow else GlassTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (saveToVault) "Save to Secure Vault" else "Save to Public Downloads",
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (saveToVault) "AES-256 encrypted • Hidden from gallery" else "Visible in device gallery",
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = saveToVault,
                    onCheckedChange = onToggleSaveToVault,
                    modifier = Modifier.testTag("save_to_vault_toggle"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlassTheme.colors.accentGlow,
                        checkedTrackColor = GlassTheme.colors.accentGlow.copy(alpha = 0.35f),
                        uncheckedThumbColor = GlassTheme.colors.textSecondary,
                        uncheckedTrackColor = GlassTheme.colors.surfaceGlass
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Big Prominent "DOWNLOAD" Action Button
        val selectedBadge = selectedOption?.cleanResolutionBadge ?: "Best"
        val selectedSize = selectedOption?.formattedSize ?: ""
        val downloadButtonText = buildString {
            append("Download")
            if (selectedBadge.isNotBlank()) append(" • $selectedBadge")
            if (selectedSize.isNotBlank()) append(" ($selectedSize)")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GlassTheme.colors.accentGlow)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val confirmedItem = mediaItem.copy(title = editedTitle.ifBlank { mediaItem.displayTitle })
                    onConfirmDownload(confirmedItem, selectedOption)
                    onDismiss()
                }
                .testTag("confirm_download_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = downloadButtonText,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

/**
 * Clickable quality card with radio indicator, resolution badge, format, exact size pill, and instant download arrow.
 */
@Composable
private fun InShotQualityCard(
    option: VideoQualityOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInstantDownload: () -> Unit
) {
    val isAudio = option.formatTag.contains("AUDIO", ignoreCase = true) ||
            option.resolution.contains("Audio", ignoreCase = true)
    val isHighDef = option.cleanResolutionBadge.contains("1080") ||
            option.cleanResolutionBadge.contains("4K", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.16f)
                else GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.6f)
            )
            .border(
                width = if (isSelected) 1.5.dp else if (isHighDef) 1.dp else 0.7.dp,
                color = when {
                    isSelected -> GlassTheme.colors.accentGlow
                    isHighDef -> GlassTheme.colors.accentGlow.copy(alpha = 0.35f)
                    else -> GlassTheme.colors.glassBorder
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("quality_option_${option.cleanResolutionBadge.replace(" ", "_").lowercase()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Radio Selection Indicator
                Icon(
                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Unselected",
                    tint = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )

                // Resolution Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isAudio) GlassTheme.colors.surfaceGlass
                            else if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.25f)
                            else GlassTheme.colors.accentGlow.copy(alpha = 0.14f)
                        )
                        .border(
                            0.8.dp,
                            if (isAudio) GlassTheme.colors.glassBorder
                            else GlassTheme.colors.accentGlow.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = option.cleanResolutionBadge,
                        color = if (isAudio) GlassTheme.colors.textPrimary else GlassTheme.colors.accentGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Label & Format Subtitle
                Column {
                    Text(
                        text = option.label,
                        color = if (isSelected) GlassTheme.colors.textPrimary else GlassTheme.colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = option.formatTag,
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (option.resolution.isNotBlank() && !isAudio) {
                            Text(
                                text = "• ${option.resolution}",
                                color = GlassTheme.colors.textMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Right side: Exact file size pill + Instant 1-Tap Download arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Exact/Estimated File Size Pill
                val displaySize = if (option.formattedSize.isNotBlank()) {
                    option.formattedSize
                } else if (option.isHlsVariant) {
                    "Adaptive HLS"
                } else {
                    option.formatTag
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.2f) else GlassTheme.colors.surfaceGlass)
                        .border(
                            0.8.dp,
                            if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.4f) else GlassTheme.colors.glassBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = displaySize,
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Instant 1-Tap Download Action Circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) GlassTheme.colors.accentGlow
                            else GlassTheme.colors.accentGlow.copy(alpha = 0.15f)
                        )
                        .clickable(onClick = onInstantDownload)
                        .border(
                            0.8.dp,
                            GlassTheme.colors.accentGlow.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Instant Download ${option.label}",
                        tint = if (isSelected) Color.Black else GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractingMediaSnifferContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = GlassTheme.colors.accentColor,
            strokeWidth = 3.5.dp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Extracting Video Streams...",
            color = GlassTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Resolving available resolutions and muxing options with native engine",
            color = GlassTheme.colors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EmptyMediaSnifferContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.OndemandVideo,
            contentDescription = null,
            tint = GlassTheme.colors.textMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Video Stream Detected Yet",
            color = GlassTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Play a video on this webpage or refresh to trigger automatic media detection.",
            color = GlassTheme.colors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        GlassButton(
            text = "Dismiss",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Extract clean domain name from URL (e.g., "vimeo.com", "instagram.com")
 */
private fun extractCleanDomain(url: String): String {
    return try {
        val uri = URI(url)
        val host = uri.host ?: ""
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (_: Exception) {
        url.substringAfter("://").substringBefore("/").removePrefix("www.")
    }
}

/**
 * Format duration string from seconds (e.g., "03:42")
 */
private fun formatDurationString(seconds: Double): String {
    if (seconds <= 0.0) return ""
    val totalSec = seconds.toInt()
    val mins = totalSec / 60
    val secs = totalSec % 60
    val hrs = mins / 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins % 60, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

/**
 * Build rich resolution quality options for a given SniffedMediaItem
 */
private fun resolveComprehensiveQualities(item: SniffedMediaItem): List<VideoQualityOption> {
    if (item.qualities.isNotEmpty()) {
        return item.qualities
    }

    val formatTag = when {
        item.isM3u8 -> "HLS"
        item.mimeType.contains("webm", ignoreCase = true) || item.url.contains(".webm", ignoreCase = true) -> "WEBM"
        item.mimeType.contains("audio", ignoreCase = true) -> "AUDIO"
        else -> "MP4"
    }

    val singleOption = VideoQualityOption(
        label = item.bestResolutionBadge.ifBlank { "Direct Stream" },
        resolution = item.bestResolutionBadge,
        bandwidthBps = 0L,
        url = item.url,
        isHlsVariant = item.isM3u8,
        estimatedSizeBytes = item.fileSizeBytes,
        formatTag = formatTag
    )
    return listOf(singleOption)
}

/**
 * Deduplicate by URL and distinct quality tier, hide preview clips, and guarantee
 * that identical resolutions with identical file sizes never render twice.
 */
private fun deduplicateAndSortOptionsForSheet(list: List<VideoQualityOption>): List<VideoQualityOption> {
    if (list.isEmpty()) return emptyList()

    // 1. Strict deduplication by clean URL
    val urlDeduplicated = list
        .groupBy { it.url.substringBefore('?').substringBefore('#') }
        .mapNotNull { (_, options) ->
            options.maxWithOrNull(
                compareBy<VideoQualityOption> { if (!it.isHlsVariant) 1 else 0 }
                    .thenBy { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                    .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
            )
        }

    // 2. Separate audio and video
    val audioOptions = urlDeduplicated.filter {
        it.formatTag.contains("AUDIO", ignoreCase = true) || it.resolution.contains("Audio", ignoreCase = true)
    }
    val videoOptions = urlDeduplicated.filterNot {
        it.formatTag.contains("AUDIO", ignoreCase = true) || it.resolution.contains("Audio", ignoreCase = true)
    }

    // 3. Suppress vague "Direct Stream" if named video options exist
    val hasNamedVideo = videoOptions.any {
        it.resolution.isNotBlank() && !it.label.contains("Direct Stream", ignoreCase = true)
    }
    val cleanVideos = if (hasNamedVideo) {
        videoOptions.filterNot { it.label.contains("Direct Stream", ignoreCase = true) || it.resolution.isBlank() }
    } else {
        videoOptions
    }

    // 4. Group streams by distinct resolution height (e.g., 1080, 720, 480, 360) and keep highest bandwidth
    val distinctTiers = cleanVideos
        .groupBy { opt ->
            val h = opt.getResolutionHeight()
            if (h > 0) "${h}p" else opt.cleanResolutionBadge.ifBlank { opt.resolution.ifBlank { opt.label } }
        }
        .mapNotNull { (_, opts) ->
            opts.maxWithOrNull(
                compareBy<VideoQualityOption> { if (!it.isHlsVariant) 1 else 0 }
                    .thenBy { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                    .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
            )
        }

    // 5. Present a clean, descending list from highest resolution to lowest resolution
    val sortedVideos = distinctTiers.sortedWith(
        compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
            .thenByDescending { it.bandwidthBps }
            .thenByDescending { it.estimatedSizeBytes }
    )

    val coherentVideos = ir.ali0003.downloader.browser.sniffer.HlsManifestParser.enforceSizeCoherence(sortedVideos)

    // 6. Never allow identical resolutions with the same file size to render twice
    val uniqueVideos = mutableListOf<VideoQualityOption>()
    val seenHeights = mutableSetOf<Int>()
    val seenResolutions = mutableSetOf<String>()
    val seenSizes = mutableSetOf<Long>()

    for (opt in coherentVideos) {
        val h = opt.getResolutionHeight()
        val resKey = opt.cleanResolutionBadge.ifBlank { opt.resolution.ifBlank { opt.label } }
        if (h > 0 && seenHeights.contains(h)) continue
        if (resKey.isNotBlank() && seenResolutions.contains(resKey)) continue
        if (opt.estimatedSizeBytes > 0L && seenSizes.contains(opt.estimatedSizeBytes)) continue

        if (h > 0) seenHeights.add(h)
        if (resKey.isNotBlank()) seenResolutions.add(resKey)
        if (opt.estimatedSizeBytes > 0L) seenSizes.add(opt.estimatedSizeBytes)
        uniqueVideos.add(opt)
    }

    val bestAudio = audioOptions.maxByOrNull { it.estimatedSizeBytes.coerceAtLeast(it.bandwidthBps) }

    val combined = if (bestAudio != null) {
        uniqueVideos + bestAudio
    } else {
        uniqueVideos
    }

    return combined.ifEmpty { list.take(1) }
}
