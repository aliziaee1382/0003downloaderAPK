package ir.ali0003.downloader.ui.browser

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
                            text = if (mediaItem.mimeType.contains("audio")) "Audio" else "Video",
                            color = GlassTheme.colors.accentGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val headerSize = when {
                            (selectedOption?.estimatedSizeBytes ?: 0L) >= 1024 * 1024L -> {
                                val s = VideoQualityOption.formatFileSize(selectedOption!!.estimatedSizeBytes)
                                if (selectedOption!!.isExactSize) s else "~$s"
                            }
                            mediaItem.bestFileSizeBytes >= 1024 * 1024L -> {
                                VideoQualityOption.formatFileSize(mediaItem.bestFileSizeBytes)
                            }
                            mediaItem.durationSeconds > 0.0 -> {
                                val est = ((2_000_000L * mediaItem.durationSeconds) / 8.0).toLong()
                                if (est >= 1024 * 1024L) "~${VideoQualityOption.formatFileSize(est)}"
                                else if (mediaItem.isM3u8) "استریم HLS"
                                else if (mediaItem.isDash) "استریم DASH"
                                else "محاسبه حین دانلود"
                            }
                            mediaItem.isM3u8 -> "استریم HLS"
                            mediaItem.isDash -> "استریم DASH"
                            else -> "محاسبه حین دانلود"
                        }
                        if (headerSize.isNotBlank()) {
                            Text(
                                text = "•",
                                color = GlassTheme.colors.textMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = headerSize,
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                    mediaDurationSeconds = mediaItem?.durationSeconds ?: 0.0,
                    allOptions = qualityOptions,
                    baseFileSizeBytes = mediaItem?.bestFileSizeBytes ?: 0L,
                    onSelect = {
                        selectedOption = option
                        val effectiveUrl = option.url.ifBlank { mediaItem.url }
                        if (effectiveUrl.isBlank()) {
                            Log.e("MediaSnifferSheet", "Quality ${option.cleanResolutionBadge} clicked but URL is blank! mediaItem.url='${mediaItem.url}'")
                            Toast.makeText(context, "خطا: لینک دانلود معتبر یافت نشد", Toast.LENGTH_SHORT).show()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val targetOption = if (option.url.isBlank()) option.copy(url = effectiveUrl) else option
                            val confirmedItem = mediaItem.copy(title = editedTitle.ifBlank { mediaItem.displayTitle })
                            Log.e("MediaSnifferSheet", "Quality clicked [${targetOption.cleanResolutionBadge}]: starting download for URL=$effectiveUrl")
                            onConfirmDownload(confirmedItem, targetOption)
                            onDismiss()
                        }
                    },
                    onInstantDownload = {
                        val effectiveUrl = option.url.ifBlank { mediaItem.url }
                        if (effectiveUrl.isBlank()) {
                            Log.e("MediaSnifferSheet", "Instant download clicked but URL is blank! mediaItem.url='${mediaItem.url}'")
                            Toast.makeText(context, "خطا: لینک دانلود معتبر یافت نشد", Toast.LENGTH_SHORT).show()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val targetOption = if (option.url.isBlank()) option.copy(url = effectiveUrl) else option
                            val confirmedItem = mediaItem.copy(title = editedTitle.ifBlank { mediaItem.displayTitle })
                            Log.e("MediaSnifferSheet", "Instant download clicked [${targetOption.cleanResolutionBadge}]: starting download for URL=$effectiveUrl")
                            onConfirmDownload(confirmedItem, targetOption)
                            onDismiss()
                        }
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
                    val chosen = selectedOption ?: qualityOptions.firstOrNull()
                    val effectiveUrl = chosen?.url?.ifBlank { mediaItem.url } ?: mediaItem.url
                    if (effectiveUrl.isBlank()) {
                        Log.e("MediaSnifferSheet", "Master download clicked but URL is blank! chosen=$chosen, mediaItem.url='${mediaItem.url}'")
                        Toast.makeText(context, "خطا: لینک دانلود معتبر یافت نشد", Toast.LENGTH_SHORT).show()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val targetOption = chosen?.let { if (it.url.isBlank()) it.copy(url = effectiveUrl) else it }
                        val confirmedItem = mediaItem.copy(title = editedTitle.ifBlank { mediaItem.displayTitle })
                        Log.e("MediaSnifferSheet", "Master download button clicked [${targetOption?.cleanResolutionBadge ?: "Default"}]: starting download for URL=$effectiveUrl")
                        onConfirmDownload(confirmedItem, targetOption)
                        onDismiss()
                    }
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
fun calculateOptionDisplaySize(
    option: VideoQualityOption,
    mediaDurationSeconds: Double,
    allOptions: List<VideoQualityOption> = emptyList(),
    baseFileSizeBytes: Long = 0L
): String {
    // 1. Direct exact or estimated size already on this option
    if (option.isExactSize && option.estimatedSizeBytes >= 1024 * 1024L) {
        return VideoQualityOption.formatFileSize(option.estimatedSizeBytes)
    }
    if (option.estimatedSizeBytes >= 1024 * 1024L) {
        return "~" + VideoQualityOption.formatFileSize(option.estimatedSizeBytes)
    }

    val isAudio = option.formatTag.contains("AUDIO", ignoreCase = true) ||
            option.resolution.contains("Audio", ignoreCase = true)

    // 2. If duration is known and bandwidth is known
    if (mediaDurationSeconds > 0.0 && option.bandwidthBps > 0L) {
        val est = ((option.bandwidthBps * mediaDurationSeconds) / 8.0).toLong()
        if (est >= 1024 * 1024L) return "~" + VideoQualityOption.formatFileSize(est)
    }

    // 3. If duration is known and bitrate can be estimated from height
    if (mediaDurationSeconds > 0.0) {
        val h = option.getResolutionHeight()
        val estBitrate = when {
            h >= 2160 -> 12_000_000L
            h >= 1440 -> 6_000_000L
            h >= 1080 -> 3_500_000L
            h >= 720 -> 2_000_000L
            h >= 480 -> 1_000_000L
            h >= 360 -> 600_000L
            h >= 240 -> 350_000L
            isAudio -> 128_000L
            else -> 1_500_000L
        }
        val est = ((estBitrate * mediaDurationSeconds) / 8.0).toLong()
        if (est >= 1024 * 1024L) return "~" + VideoQualityOption.formatFileSize(est)
    }

    // 4. Extrapolate from another option in the same list that HAS a known estimatedSizeBytes
    val refOpt = allOptions.firstOrNull { it.estimatedSizeBytes >= 1024 * 1024L && it.getResolutionHeight() > 0 }
    val currentHeight = option.getResolutionHeight()
    if (refOpt != null && currentHeight > 0) {
        val refHeight = refOpt.getResolutionHeight()
        val scale = Math.pow(currentHeight.toDouble() / refHeight.toDouble(), 1.25)
        val extrapolated = (refOpt.estimatedSizeBytes * scale).toLong()
        if (extrapolated >= 1024 * 1024L) {
            return "~" + VideoQualityOption.formatFileSize(extrapolated)
        }
    }

    // 5. Extrapolate from baseFileSizeBytes (only if base size is a genuine video file >= 1MB)
    if (baseFileSizeBytes >= 1024 * 1024L) {
        if (currentHeight > 0) {
            val scale = when {
                currentHeight >= 1080 -> 1.0
                currentHeight >= 720 -> 0.55
                currentHeight >= 480 -> 0.32
                currentHeight >= 360 -> 0.20
                else -> 0.12
            }
            val est = (baseFileSizeBytes * scale).toLong()
            if (est >= 1024 * 1024L) return "~" + VideoQualityOption.formatFileSize(est)
        } else {
            return "~" + VideoQualityOption.formatFileSize(baseFileSizeBytes)
        }
    }

    // 6. Bandwidth with default estimated duration (e.g. 180 seconds / 3 mins)
    if (option.bandwidthBps > 0L) {
        val est = ((option.bandwidthBps * 180.0) / 8.0).toLong()
        if (est >= 1024 * 1024L) return "~" + VideoQualityOption.formatFileSize(est)
    }

    // 7. Standard plausible estimation based on resolution height
    if (currentHeight > 0) {
        val defaultEstimate = when {
            currentHeight >= 2160 -> 120 * 1024 * 1024L
            currentHeight >= 1440 -> 70 * 1024 * 1024L
            currentHeight >= 1080 -> 45 * 1024 * 1024L
            currentHeight >= 720 -> 24 * 1024 * 1024L
            currentHeight >= 480 -> 14 * 1024 * 1024L
            currentHeight >= 360 -> 8 * 1024 * 1024L
            currentHeight >= 240 -> 5 * 1024 * 1024L
            isAudio -> 3 * 1024 * 1024L
            else -> 20 * 1024 * 1024L
        }
        return "~" + VideoQualityOption.formatFileSize(defaultEstimate)
    }

    return if (option.isHlsVariant) "استریم HLS" else "محاسبه حین دانلود"
}

/**
 * Clean Single-Tap Quality Card (InShot Inspired):
 * Displays radio selection indicator, resolution label, subtitle, exact or estimated file size,
 * and an instant download action circle.
 */
@Composable
private fun InShotQualityCard(
    option: VideoQualityOption,
    isSelected: Boolean,
    mediaDurationSeconds: Double = 0.0,
    allOptions: List<VideoQualityOption> = emptyList(),
    baseFileSizeBytes: Long = 0L,
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

                // Label & Format Subtitle (No redundant card next to numbers)
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = option.label,
                        color = if (isSelected) GlassTheme.colors.textPrimary else GlassTheme.colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (option.resolution.isNotBlank() && !isAudio) {
                        Text(
                            text = option.resolution,
                            color = GlassTheme.colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (isAudio) {
                        Text(
                            text = "Audio Only",
                            color = GlassTheme.colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right side: Exact file size pill + Instant 1-Tap Download arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Exact/Estimated File Size Pill (Always guarantees an accurate or estimated size)
                val displaySize = calculateOptionDisplaySize(
                    option = option,
                    mediaDurationSeconds = mediaDurationSeconds,
                    allOptions = allOptions,
                    baseFileSizeBytes = baseFileSizeBytes
                )
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
 * Sort options for sheet, grouping by resolution tier and prioritizing direct Progressive MP4,
 * sorting descending from highest resolution to lowest without duplicate tiers.
 */
private fun deduplicateAndSortOptionsForSheet(list: List<VideoQualityOption>): List<VideoQualityOption> {
    if (list.isEmpty()) return emptyList()

    // 1. Separate audio and video
    val audioOptions = list.filter {
        it.formatTag.contains("AUDIO", ignoreCase = true) ||
                it.resolution.contains("Audio", ignoreCase = true) ||
                it.label.contains("Audio", ignoreCase = true)
    }
    val videoOptions = list.filterNot {
        it.formatTag.contains("AUDIO", ignoreCase = true) ||
                it.resolution.contains("Audio", ignoreCase = true) ||
                it.label.contains("Audio", ignoreCase = true)
    }

    // 2. Filter out ad / preview options
    val validVideos = videoOptions.filter { opt ->
        val u = opt.url.lowercase()
        opt.url.isNotBlank() && !opt.url.startsWith("blob:") && !opt.url.startsWith("data:") &&
                !u.contains("doubleclick") && !u.contains("/ads/") && !u.contains("preroll") &&
                !u.contains("googlesyndication") && !u.contains("adnxs")
    }

    // If named resolution tiers exist (1080p, 720p, etc.), remove vague Source Stream / Direct Stream stubs
    val hasNamedTiers = validVideos.any { it.getResolutionHeight() > 0 }
    val cleanVideos = if (hasNamedTiers) {
        validVideos.filterNot {
            it.label.contains("Source Stream", ignoreCase = true) ||
                    it.label.contains("Direct Stream", ignoreCase = true) ||
                    (it.getResolutionHeight() <= 0 && it.resolution.isBlank())
        }
    } else {
        validVideos
    }

    // 3. Group by resolution height (e.g. 2160, 1440, 1080, 720, 480, 360, 240)
    val groupedByHeight = cleanVideos.groupBy { it.getResolutionHeight() }
    val deduplicatedVideos = mutableListOf<VideoQualityOption>()

    for ((height, optionsInHeight) in groupedByHeight) {
        val hlsCandidate = optionsInHeight.filter { it.isHlsVariant || it.formatTag.contains("HLS", ignoreCase = true) || it.url.contains(".m3u8", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<VideoQualityOption> { it.bandwidthBps }
                    .thenBy { it.estimatedSizeBytes }
            )
        val mp4Candidate = optionsInHeight.filter { !it.isHlsVariant && !it.formatTag.contains("HLS", ignoreCase = true) && !it.url.contains(".m3u8", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<VideoQualityOption> { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                    .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
            )

        val tierBadge = when (height) {
            2160 -> "4K UHD"
            1440 -> "1440p 2K"
            1080 -> "1080p HD"
            720 -> "720p HD"
            480 -> "480p SD"
            360 -> "360p SD"
            240 -> "240p"
            else -> if (height > 0) "${height}p" else ""
        }

        val chosen = hlsCandidate ?: mp4Candidate ?: optionsInHeight.firstOrNull()
        if (chosen != null) {
            val label = if (tierBadge.isNotBlank()) tierBadge else chosen.label
            val isHls = chosen.isHlsVariant || chosen.formatTag.contains("HLS", ignoreCase = true) || chosen.url.contains(".m3u8", ignoreCase = true)
            deduplicatedVideos.add(chosen.copy(label = label, formatTag = if (isHls) "HLS" else "MP4", isHlsVariant = isHls))
        }
    }

    // 4. Sort descending from highest resolution to lowest (1080p -> 720p -> 480p -> 240p)
    val sortedVideos = deduplicatedVideos.sortedWith(
        compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
            .thenByDescending { it.bandwidthBps }
            .thenByDescending { it.estimatedSizeBytes }
    )

    val bestAudio = audioOptions.maxByOrNull { it.estimatedSizeBytes.coerceAtLeast(it.bandwidthBps) }

    val combined = if (bestAudio != null) {
        val audioOpt = bestAudio.copy(label = "Audio Only (MP3)", formatTag = "AUDIO")
        sortedVideos + audioOpt
    } else {
        sortedVideos
    }

    return combined.ifEmpty { list.take(1) }
}
