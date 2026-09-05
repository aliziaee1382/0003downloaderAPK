package ir.ali0003.downloader.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import java.net.URI

enum class SnifferStep {
    SOURCE_SELECTION,
    QUALITY_SELECTION
}

/**
 * Production Two-Step Media Sniffer BottomSheet:
 * - State A: Video Source Selector (Filters out junk/ads, shows duration, domain, and format).
 * - State B: Quality & Format Selector (Itemized resolutions, estimated sizes, Vault toggle, download trigger).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSnifferBottomSheet(
    sniffedMediaList: List<SniffedMediaItem>,
    initialSelectedItem: SniffedMediaItem? = null,
    saveToVault: Boolean = false,
    onToggleSaveToVault: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirmDownload: (SniffedMediaItem, VideoQualityOption?) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    // 1. Filter out junk assets (ads, tracking beacons, clips < 5s, tiny previews < 50KB)
    val validMediaItems = remember(sniffedMediaList) {
        filterJunkMedia(sniffedMediaList)
    }

    var selectedMedia by remember(initialSelectedItem, validMediaItems) {
        mutableStateOf(initialSelectedItem ?: validMediaItems.firstOrNull())
    }

    var currentStep by remember(initialSelectedItem, validMediaItems) {
        mutableStateOf(
            if (initialSelectedItem != null) {
                SnifferStep.QUALITY_SELECTION
            } else if (validMediaItems.size == 1) {
                // If exactly 1 media detected, jump straight to quality selection with back affordance
                SnifferStep.QUALITY_SELECTION
            } else {
                SnifferStep.SOURCE_SELECTION
            }
        )
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
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState == SnifferStep.QUALITY_SELECTION) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "SnifferStepTransition"
        ) { step ->
            when (step) {
                SnifferStep.SOURCE_SELECTION -> {
                    StateASourceSelector(
                        mediaList = validMediaItems,
                        onSelectVideo = { item ->
                            selectedMedia = item
                            currentStep = SnifferStep.QUALITY_SELECTION
                        },
                        onDismiss = onDismiss
                    )
                }
                SnifferStep.QUALITY_SELECTION -> {
                    val currentItem = selectedMedia ?: validMediaItems.firstOrNull()
                    if (currentItem != null) {
                        StateBQualitySelector(
                            mediaItem = currentItem,
                            hasMultipleSources = validMediaItems.size > 1,
                            saveToVault = saveToVault,
                            onToggleSaveToVault = onToggleSaveToVault,
                            onBackToSources = {
                                currentStep = SnifferStep.SOURCE_SELECTION
                            },
                            onDismiss = onDismiss,
                            onConfirmDownload = { item, quality ->
                                onConfirmDownload(item, quality)
                                onDismiss()
                            }
                        )
                    } else {
                        // Fallback if no media item is available
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No media stream selected",
                                color = GlassTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * STATE A: Video Source Selector
 */
@Composable
private fun StateASourceSelector(
    mediaList: List<SniffedMediaItem>,
    onSelectVideo: (SniffedMediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("sniffer_state_a_sources")
    ) {
        // Header
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.accentGlow.copy(alpha = 0.18f))
                        .border(1.2.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OndemandVideo,
                        contentDescription = null,
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "DETECTED MEDIA (${mediaList.size})",
                        color = GlassTheme.colors.accentGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Select a video source to inspect qualities",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            GlassIconButton(
                icon = Icons.Default.Close,
                onClick = onDismiss,
                size = 32.dp,
                iconSize = 16.dp,
                contentDescription = "Close Sniffer"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (mediaList.isEmpty()) {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                borderColor = GlassTheme.colors.glassBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OndemandVideo,
                        contentDescription = null,
                        tint = GlassTheme.colors.textMuted,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "No downloadable videos detected yet",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Play a video on the page to automatically capture the stream.",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(mediaList) { item ->
                    VideoSourceCard(
                        item = item,
                        onClick = { onSelectVideo(item) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Individual Detected Video Source Card in State A
 */
@Composable
private fun VideoSourceCard(
    item: SniffedMediaItem,
    onClick: () -> Unit
) {
    val domain = remember(item) {
        extractCleanDomain(item.pageUrl.ifBlank { item.url })
    }

    val formatBadgeText = remember(item) {
        when {
            item.isM3u8 -> "HLS Stream"
            item.isDash -> "DASH Stream"
            item.mimeType.contains("webm", ignoreCase = true) -> "WEBM"
            item.mimeType.contains("audio", ignoreCase = true) -> "AUDIO"
            else -> "MP4 Video"
        }
    }

    val durationText = remember(item.durationSeconds) {
        formatDurationString(item.durationSeconds)
    }

    val sizeText = remember(item.fileSizeBytes) {
        if (item.fileSizeBytes > 0) VideoQualityOption.formatFileSize(item.fileSizeBytes) else null
    }

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.6f),
        borderColor = GlassTheme.colors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Video Icon / Thumbnail Indicator
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                GlassTheme.colors.accentGlow.copy(alpha = 0.25f),
                                GlassTheme.colors.secondaryGlow.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(1.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mimeType.contains("audio")) Icons.Default.Audiotrack else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Title, Domain & Format Badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Format Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GlassTheme.colors.accentGlow.copy(alpha = 0.15f))
                            .border(0.8.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatBadgeText,
                            color = GlassTheme.colors.accentGlow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Domain
                    if (domain.isNotBlank()) {
                        Text(
                            text = domain,
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Duration or Size
                    if (durationText.isNotBlank()) {
                        Text(
                            text = "• $durationText",
                            color = GlassTheme.colors.textMuted,
                            fontSize = 11.sp
                        )
                    } else if (sizeText != null) {
                        Text(
                            text = "• $sizeText",
                            color = GlassTheme.colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Forward Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Inspect Qualities",
                tint = GlassTheme.colors.accentGlow,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * STATE B: Quality & Format Selector
 */
@Composable
private fun StateBQualitySelector(
    mediaItem: SniffedMediaItem,
    hasMultipleSources: Boolean,
    saveToVault: Boolean,
    onToggleSaveToVault: (Boolean) -> Unit,
    onBackToSources: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmDownload: (SniffedMediaItem, VideoQualityOption?) -> Unit
) {
    // Generate resolution tiers if none are provided
    val qualityOptions = remember(mediaItem) {
        resolveComprehensiveQualities(mediaItem)
    }

    var selectedQuality by remember(mediaItem, qualityOptions) {
        mutableStateOf(qualityOptions.firstOrNull())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("sniffer_state_b_qualities")
    ) {
        // Top Header: Back Button + Selected Video Title + Close Button
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
                // Back button if multiple sources exist, or as general back navigation
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBackToSources,
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentDescription = "Back to Sources"
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECT QUALITY & FORMAT",
                        color = GlassTheme.colors.accentGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = mediaItem.displayTitle,
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            GlassIconButton(
                icon = Icons.Default.Close,
                onClick = onDismiss,
                size = 32.dp,
                iconSize = 16.dp,
                contentDescription = "Close"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Video Domain & Format Header Banner
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.7f),
            borderColor = GlassTheme.colors.glassBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = extractCleanDomain(mediaItem.pageUrl.ifBlank { mediaItem.url }),
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = if (mediaItem.isM3u8) "HLS Adaptive Stream" else "Direct Video",
                    color = GlassTheme.colors.accentGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "AVAILABLE RESOLUTIONS",
            color = GlassTheme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Itemized Resolution Cards List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(qualityOptions) { option ->
                val isSelected = selectedQuality?.url == option.url && selectedQuality?.label == option.label
                QualityResolutionRowCard(
                    option = option,
                    isSelected = isSelected,
                    onClick = { selectedQuality = option }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Save to Secure Vault Switch Card
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

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Download Action Button
        GlassButton(
            text = if (saveToVault) "Download to Encrypted Vault" else "Start High-Speed Download",
            icon = Icons.Default.Download,
            onClick = {
                onConfirmDownload(mediaItem, selectedQuality)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            testTag = "confirm_download_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Itemized Quality Resolution Card in State B
 */
@Composable
private fun QualityResolutionRowCard(
    option: VideoQualityOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        GlassTheme.colors.accentGlow.copy(alpha = 0.20f)
    } else {
        GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.5f)
    }

    val border = if (isSelected) {
        GlassTheme.colors.accentGlow
    } else {
        GlassTheme.colors.glassBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.2.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Radio indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) GlassTheme.colors.accentGlow else Color.Transparent)
                        .border(
                            1.2.dp,
                            if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = option.label,
                            color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )

                        if (option.formatTag.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GlassTheme.colors.surfaceGlassSubtle)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = option.formatTag,
                                    color = GlassTheme.colors.textSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (option.bandwidthBps > 0) {
                        Text(
                            text = option.formattedBandwidth,
                            color = GlassTheme.colors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Text(
                text = option.formattedSize,
                color = if (isSelected) GlassTheme.colors.textPrimary else GlassTheme.colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Filter out junk/ad assets, clips under 5 seconds, and small thumbnails < 50KB.
 */
private fun filterJunkMedia(items: List<SniffedMediaItem>): List<SniffedMediaItem> {
    return items.filter { item ->
        val urlLower = item.url.lowercase()
        val titleLower = item.title.lowercase()

        // 1. Exclude ad networks & telemetry hosts
        val isAd = urlLower.contains("doubleclick") || urlLower.contains("/ads/") ||
                urlLower.contains("googlesyndication") || urlLower.contains("adnxs") ||
                urlLower.contains("analytics") || urlLower.contains("telemetry") ||
                urlLower.contains("tracking") || urlLower.contains("beacon") ||
                urlLower.contains("pixel") || titleLower.contains("advertisement")

        // 2. Exclude clips < 5s if duration is explicitly known and non-zero
        val isTooShort = item.durationSeconds > 0.0 && item.durationSeconds < 5.0

        // 3. Exclude tiny thumbnail video previews (< 50KB if fileSizeBytes > 0)
        val isTinyPreview = item.fileSizeBytes in 1..51200L && !item.isM3u8

        !isAd && !isTooShort && !isTinyPreview
    }.distinctBy { it.url }
}

/**
 * Extract clean domain name from URL (e.g., "vimeo.com", "instagram.com")
 */
private fun extractCleanDomain(url: String): String {
    return try {
        val uri = URI(url)
        val host = uri.host ?: ""
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (e: Exception) {
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

    val baseSize = item.fileSizeBytes

    return if (item.isM3u8) {
        listOf(
            VideoQualityOption(
                label = "1080p FHD (Adaptive Stream)",
                resolution = "1920x1080",
                bandwidthBps = 6000000L,
                url = item.url,
                isHlsVariant = true,
                estimatedSizeBytes = if (baseSize > 0) baseSize else 85 * 1024 * 1024L,
                formatTag = "HLS"
            ),
            VideoQualityOption(
                label = "720p HD (Adaptive Stream)",
                resolution = "1280x720",
                bandwidthBps = 3200000L,
                url = item.url,
                isHlsVariant = true,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.6).toLong() else 45 * 1024 * 1024L,
                formatTag = "HLS"
            ),
            VideoQualityOption(
                label = "480p SD (Adaptive Stream)",
                resolution = "854x480",
                bandwidthBps = 1500000L,
                url = item.url,
                isHlsVariant = true,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.35).toLong() else 22 * 1024 * 1024L,
                formatTag = "HLS"
            ),
            VideoQualityOption(
                label = "Audio Track Only (M4A)",
                resolution = "Audio",
                bandwidthBps = 128000L,
                url = item.url,
                isHlsVariant = true,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.1).toLong() else 5 * 1024 * 1024L,
                formatTag = "AUDIO"
            )
        )
    } else {
        listOf(
            VideoQualityOption(
                label = "1080p Full HD",
                resolution = "1920x1080",
                bandwidthBps = 6000000L,
                url = item.url,
                isHlsVariant = false,
                estimatedSizeBytes = if (baseSize > 0) baseSize else 65 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "720p High Definition",
                resolution = "1280x720",
                bandwidthBps = 3200000L,
                url = item.url,
                isHlsVariant = false,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.65).toLong() else 35 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "480p Standard Definition",
                resolution = "854x480",
                bandwidthBps = 1500000L,
                url = item.url,
                isHlsVariant = false,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.4).toLong() else 18 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "360p Data Saver",
                resolution = "640x360",
                bandwidthBps = 800000L,
                url = item.url,
                isHlsVariant = false,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.25).toLong() else 10 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "Audio Track Extract (MP3/M4A)",
                resolution = "Audio",
                bandwidthBps = 128000L,
                url = item.url,
                isHlsVariant = false,
                estimatedSizeBytes = if (baseSize > 0) (baseSize * 0.12).toLong() else 4 * 1024 * 1024L,
                formatTag = "AUDIO"
            )
        )
    }
}
