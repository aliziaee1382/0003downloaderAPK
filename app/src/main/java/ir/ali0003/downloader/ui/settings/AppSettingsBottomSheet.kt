package ir.ali0003.downloader.ui.settings

import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.downloader.ui.glass.ACCENT_PALETTES
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassPreset
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.glass.ThemeMode

/**
 * Enumeration of the Settings Sub-Panels for 2-Stage Navigation.
 * مرحله اول: انتخاب بخش تنظیمات از منوی اصلی
 * مرحله دوم: ورود به صفحه تنظیمات تخصصی همان بخش
 */
enum class SettingsSubSection {
    MAIN_MENU,
    THEME_APPEARANCE,
    DOWNLOAD_ACCELERATION,
    ADBLOCK_SECURITY,
    STORAGE_DIRECTORY,
    PRIVACY_DATA
}

/**
 * 2-Stage Frosted Glass Settings & Preferences Bottom Sheet:
 * Stage 1: Categorized Main Menu Overview with interactive trigger cards and summaries
 * Stage 2: In-depth Sub-Screen configuration with Back Navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsBottomSheet(
    currentPreset: GlassPreset = GlassPreset.SUNSET_AMBER,
    themeMode: ThemeMode = ThemeMode.DARK,
    themeColorKey: String = "orange",
    threadCount: Int = 6,
    isMultiSegmentEnabled: Boolean = true,
    onThreadCountChange: (Int) -> Unit = {},
    onMultiSegmentToggle: (Boolean) -> Unit = {},
    onSelectPreset: (GlassPreset) -> Unit = {},
    onApplyTheme: (ThemeMode, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onClearBrowserData: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentSection by remember { mutableStateOf(SettingsSubSection.MAIN_MENU) }
    var adBlockerEnabled by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.96f),
            borderColor = GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.6f)
        ) {
            AnimatedContent(
                targetState = currentSection,
                transitionSpec = {
                    if (targetState == SettingsSubSection.MAIN_MENU) {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    } else {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    }
                },
                label = "SettingsSectionTransition"
            ) { section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    when (section) {
                        SettingsSubSection.MAIN_MENU -> {
                            SettingsMainMenuStage(
                                themeMode = themeMode,
                                themeColorKey = themeColorKey,
                                threadCount = threadCount,
                                isMultiSegmentEnabled = isMultiSegmentEnabled,
                                adBlockerEnabled = adBlockerEnabled,
                                onNavigateTo = { currentSection = it },
                                onDismiss = onDismiss
                            )
                        }
                        SettingsSubSection.THEME_APPEARANCE -> {
                            SettingsThemeSubStage(
                                currentPreset = currentPreset,
                                themeMode = themeMode,
                                themeColorKey = themeColorKey,
                                onSelectPreset = onSelectPreset,
                                onApplyTheme = onApplyTheme,
                                onBack = { currentSection = SettingsSubSection.MAIN_MENU }
                            )
                        }
                        SettingsSubSection.DOWNLOAD_ACCELERATION -> {
                            SettingsAccelerationSubStage(
                                threadCount = threadCount,
                                isMultiSegmentEnabled = isMultiSegmentEnabled,
                                onThreadCountChange = onThreadCountChange,
                                onMultiSegmentToggle = onMultiSegmentToggle,
                                onBack = { currentSection = SettingsSubSection.MAIN_MENU }
                            )
                        }
                        SettingsSubSection.ADBLOCK_SECURITY -> {
                            SettingsSecuritySubStage(
                                adBlockerEnabled = adBlockerEnabled,
                                onAdBlockerToggle = { adBlockerEnabled = it },
                                onBack = { currentSection = SettingsSubSection.MAIN_MENU }
                            )
                        }
                        SettingsSubSection.STORAGE_DIRECTORY -> {
                            SettingsStorageSubStage(
                                onBack = { currentSection = SettingsSubSection.MAIN_MENU }
                            )
                        }
                        SettingsSubSection.PRIVACY_DATA -> {
                            SettingsPrivacySubStage(
                                showConfirm = showClearConfirm,
                                onShowConfirmChange = { showClearConfirm = it },
                                onClearBrowserData = onClearBrowserData,
                                onBack = { currentSection = SettingsSubSection.MAIN_MENU }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stage 1: Main Settings Menu List
 * Every item is a high-level interactive portal card leading to Stage 2.
 */
@Composable
private fun SettingsMainMenuStage(
    themeMode: ThemeMode,
    themeColorKey: String,
    threadCount: Int,
    isMultiSegmentEnabled: Boolean,
    adBlockerEnabled: Boolean,
    onNavigateTo: (SettingsSubSection) -> Unit,
    onDismiss: () -> Unit
) {
    // Header Bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                    .border(1.dp, GlassTheme.colors.accentGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "تنظیمات برنامه (Settings)",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "برای پیکربندی روی هر بخش کلیک کنید (تنظیمات ۲ مرحله‌ای)",
                    color = GlassTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        GlassIconButton(
            icon = Icons.Default.Close,
            onClick = onDismiss,
            size = 32.dp,
            iconSize = 16.dp,
            contentDescription = "Close Settings"
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 1. Appearance & Theme Item
    val activePalette = ACCENT_PALETTES.find { it.key.equals(themeColorKey, ignoreCase = true) } ?: ACCENT_PALETTES.first()
    SettingsSectionTriggerRow(
        icon = Icons.Default.ColorLens,
        iconTint = activePalette.primary,
        title = "پوسته‌ها و رنگ تم (Appearance & Colors)",
        subtitle = "حالت: ${themeMode.name} • پالت فعال: ${activePalette.name}",
        tag = "مرحله ۱",
        onClick = { onNavigateTo(SettingsSubSection.THEME_APPEARANCE) }
    )

    Spacer(modifier = Modifier.height(10.dp))

    // 2. Multi-Segment Engine Item
    SettingsSectionTriggerRow(
        icon = Icons.Default.Speed,
        iconTint = GlassTheme.colors.accentGlow,
        title = "موتور شتاب و بخش‌بندی دانلود (Acceleration)",
        subtitle = if (isMultiSegmentEnabled) "$threadCount بخش موازی فعال (HTTP Byte-Ranges)" else "تک‌استریم معمولی (شتاب غیرفعال)",
        tag = if (isMultiSegmentEnabled) "$threadCount Threads" else "Off",
        onClick = { onNavigateTo(SettingsSubSection.DOWNLOAD_ACCELERATION) }
    )

    Spacer(modifier = Modifier.height(10.dp))

    // 3. Security & Ad-Blocker
    SettingsSectionTriggerRow(
        icon = Icons.Default.Shield,
        iconTint = GlassTheme.colors.accentGlow,
        title = "امنیت و ضد تبلیغات مرورگر (Security)",
        subtitle = if (adBlockerEnabled) "مسدودکننده پاپ‌آپ و تبلیغات فعال است" else "مسدودکننده غیرفعال است",
        tag = if (adBlockerEnabled) "Active" else "Disabled",
        onClick = { onNavigateTo(SettingsSubSection.ADBLOCK_SECURITY) }
    )

    Spacer(modifier = Modifier.height(10.dp))

    // 4. Storage Directory Item
    SettingsSectionTriggerRow(
        icon = Icons.Default.Folder,
        iconTint = GlassTheme.colors.accentGlow,
        title = "مسیر ذخیره‌سازی فایل‌ها (Storage)",
        subtitle = "${Environment.DIRECTORY_DOWNLOADS}/0003_Downloader",
        tag = "پوشه",
        onClick = { onNavigateTo(SettingsSubSection.STORAGE_DIRECTORY) }
    )

    Spacer(modifier = Modifier.height(10.dp))

    // 5. Privacy & Browsing Data Item
    SettingsSectionTriggerRow(
        icon = Icons.Default.DeleteSweep,
        iconTint = GlassTheme.colors.dangerGlass,
        title = "حریم خصوصی و پاک‌سازی کش (Privacy & Cache)",
        subtitle = "حذف کوکی‌ها، حافظه پنهان و سوابق مرورگر",
        tag = "پاک‌سازی",
        onClick = { onNavigateTo(SettingsSubSection.PRIVACY_DATA) }
    )
}

/**
 * Reusable Trigger Row for Stage 1 -> Stage 2 Navigation
 */
@Composable
private fun SettingsSectionTriggerRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    tag: String? = null,
    onClick: () -> Unit
) {
    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
        borderColor = GlassTheme.colors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.16f))
                        .border(1.dp, iconTint.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (tag != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GlassTheme.colors.cardBackground.copy(alpha = 0.7f))
                            .border(0.8.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tag,
                            color = iconTint,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Go to sub-setting",
                    tint = GlassTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Stage 2 Header with Back Button
 */
@Composable
private fun SettingsSubStageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            size = 36.dp,
            iconSize = 18.dp,
            contentDescription = "بازگشت به منوی اصلی تنظیمات"
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = GlassTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = GlassTheme.colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(18.dp))
}

/**
 * Stage 2: Theme & Appearance Sub-Stage
 */
@Composable
private fun SettingsThemeSubStage(
    currentPreset: GlassPreset,
    themeMode: ThemeMode,
    themeColorKey: String,
    onSelectPreset: (GlassPreset) -> Unit,
    onApplyTheme: (ThemeMode, String) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubStageHeader(
        title = "پوسته‌ها و رنگ تم (Appearance)",
        subtitle = "تنظیم حالت تاریک/روشن و پالت نوری شیشه‌ای",
        onBack = onBack
    )

    Text(
        text = "حالت تم (THEME MODE)",
        color = GlassTheme.colors.accentGlow,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeModeCompactButton(
            modifier = Modifier.weight(1f),
            title = "Dark",
            icon = Icons.Default.DarkMode,
            isSelected = themeMode == ThemeMode.DARK,
            onClick = { onApplyTheme(ThemeMode.DARK, themeColorKey) }
        )

        ThemeModeCompactButton(
            modifier = Modifier.weight(1f),
            title = "Light",
            icon = Icons.Default.LightMode,
            isSelected = themeMode == ThemeMode.LIGHT,
            onClick = { onApplyTheme(ThemeMode.LIGHT, themeColorKey) }
        )

        ThemeModeCompactButton(
            modifier = Modifier.weight(1f),
            title = "Auto",
            icon = Icons.Default.SettingsSuggest,
            isSelected = themeMode == ThemeMode.AUTO,
            onClick = { onApplyTheme(ThemeMode.AUTO, themeColorKey) }
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "پالت‌های رنگی نئونی (ACCENT COLOR PALETTES)",
        color = GlassTheme.colors.accentGlow,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(10.dp))

    // Display 6 colors in two rows of 3 items each (no horizontal scroll)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ACCENT_PALETTES.chunked(3).forEach { rowPalettes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPalettes.forEach { palette ->
                    val isSelected = themeColorKey.equals(palette.key, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) palette.primary.copy(alpha = 0.22f)
                                else GlassTheme.colors.surfaceGlassSubtle
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) palette.primary else GlassTheme.colors.glassBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onApplyTheme(themeMode, palette.key) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(palette.primary, palette.secondary)
                                        )
                                    )
                            )
                            Text(
                                text = palette.name,
                                color = if (isSelected) palette.primary else GlassTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = "پیش‌تنظیم‌های شیشه‌ای (GLASS PRESETS)",
        color = GlassTheme.colors.accentGlow,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassPreset.entries.forEach { preset ->
            val isSelected = (currentPreset == preset)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.25f)
                        else GlassTheme.colors.surfaceGlassSubtle
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPreset(preset) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = preset.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Stage 2: Download Acceleration Sub-Stage
 */
@Composable
private fun SettingsAccelerationSubStage(
    threadCount: Int,
    isMultiSegmentEnabled: Boolean,
    onThreadCountChange: (Int) -> Unit,
    onMultiSegmentToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubStageHeader(
        title = "شتاب‌دهنده چندبخشی (Acceleration)",
        subtitle = "تنظیم تعداد بخش‌ها و تردهای موازی دانلود",
        onBack = onBack
    )

    SettingsToggleRow(
        icon = Icons.Default.Speed,
        title = "دانلود چندبخشی شتاب‌یافته (Multi-Segment)",
        subtitle = if (isMultiSegmentEnabled) {
            "فعال • دانلود موازی فایل‌ها در $threadCount قطعه هم‌زمان برای ماکسیمم سرعت"
        } else {
            "غیرفعال • تک‌استریم معمولی بدون شتاب"
        },
        checked = isMultiSegmentEnabled,
        onCheckedChange = onMultiSegmentToggle
    )

    Spacer(modifier = Modifier.height(14.dp))

    if (isMultiSegmentEnabled) {
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
            borderColor = GlassTheme.colors.glassBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تعداد بخش‌های هم‌زمان (Concurrent Threads)",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$threadCount بخش",
                        color = GlassTheme.colors.accentGlow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val segmentOptions = listOf(2, 4, 6, 8, 12, 16)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    segmentOptions.forEach { count ->
                        val isSelected = (threadCount == count)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) GlassTheme.colors.accentGlow
                                    else GlassTheme.colors.cardBackground.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onThreadCountChange(count) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "$count Threads",
                                color = if (isSelected) Color.Black else GlassTheme.colors.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stage 2: Security & Ad-Blocker Sub-Stage
 */
@Composable
private fun SettingsSecuritySubStage(
    adBlockerEnabled: Boolean,
    onAdBlockerToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubStageHeader(
        title = "امنیت و ضد تبلیغ (Security)",
        subtitle = "مسدودسازی هوشمند تبلیغات و درخواست‌های مخرب",
        onBack = onBack
    )

    SettingsToggleRow(
        icon = Icons.Default.Shield,
        title = "ضد تبلیغات و پاپ‌آپ (Ad-Block Engine)",
        subtitle = "جلوگیری از تبلیغات آزاردهنده، باز شدن پاپ‌آپ‌های ناخواسته و ردیاب‌ها",
        checked = adBlockerEnabled,
        onCheckedChange = onAdBlockerToggle
    )
}

/**
 * Stage 2: Storage Directory Sub-Stage
 */
@Composable
private fun SettingsStorageSubStage(
    onBack: () -> Unit
) {
    SettingsSubStageHeader(
        title = "محل ذخیره‌سازی (Storage Directory)",
        subtitle = "مسیر ذخیره ویدیوها و فایل‌های دانلود شده",
        onBack = onBack
    )

    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
        borderColor = GlassTheme.colors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.accentGlow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مسیر پیش‌فرض حافظه",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${Environment.DIRECTORY_DOWNLOADS}/0003_Downloader",
                    color = GlassTheme.colors.accentGlow,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

/**
 * Stage 2: Privacy & Data Cleanup Sub-Stage
 */
@Composable
private fun SettingsPrivacySubStage(
    showConfirm: Boolean,
    onShowConfirmChange: (Boolean) -> Unit,
    onClearBrowserData: () -> Unit,
    onBack: () -> Unit
) {
    SettingsSubStageHeader(
        title = "حریم خصوصی و پاک‌سازی کش (Privacy)",
        subtitle = "مدیریت حافظه پنهان و اطلاعات هویتی مرورگر",
        onBack = onBack
    )

    if (!showConfirm) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onShowConfirmChange(true) },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
            borderColor = GlassTheme.colors.dangerGlass.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.dangerGlass.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = GlassTheme.colors.dangerGlass,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "پاک‌سازی کوکی‌ها و حافظه مرورگر",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "حذف فایل‌های کش، لاگین‌های موقت و تاریخچه",
                        color = GlassTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    } else {
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            backgroundColor = GlassTheme.colors.dangerGlass.copy(alpha = 0.14f),
            borderColor = GlassTheme.colors.dangerGlass.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "آیا از پاک‌سازی کامل اطلاعات اطمینان دارید؟",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تمام سشن‌های فعال، کش صفحات وب و کوکی‌ها به طور دائمی حذف خواهند شد.",
                    color = GlassTheme.colors.textSecondary,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton(
                        text = "انصراف",
                        onClick = { onShowConfirmChange(false) },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )

                    GlassButton(
                        text = "تأیید و پاک‌سازی",
                        onClick = {
                            onShowConfirmChange(false)
                            onClearBrowserData()
                        },
                        modifier = Modifier.weight(1f),
                        isPrimary = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCompactButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.22f)
                else GlassTheme.colors.surfaceGlassSubtle
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.accentGlow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = GlassTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GlassTheme.colors.accentGlow,
                uncheckedThumbColor = GlassTheme.colors.textMuted,
                uncheckedTrackColor = GlassTheme.colors.surfaceGlassSubtle
            )
        )
    }
}
