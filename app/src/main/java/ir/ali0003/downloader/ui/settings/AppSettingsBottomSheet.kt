package ir.ali0003.downloader.ui.settings

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
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
 * Premium Frosted Glass Settings & Preferences Sheet:
 * - 2-Tier Theme & Accent Color Appearance Experience
 * - Ad-Blocker engine toggle
 * - Fast Multi-Thread Chunking toggle
 * - Clear Cache & Cookies
 * - Default Download Directory
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsBottomSheet(
    currentPreset: GlassPreset = GlassPreset.SUNSET_AMBER,
    themeMode: ThemeMode = ThemeMode.DARK,
    themeColorKey: String = "orange",
    onSelectPreset: (GlassPreset) -> Unit = {},
    onApplyTheme: (ThemeMode, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onClearBrowserData: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var adBlockerEnabled by remember { mutableStateOf(true) }
    var multiThreadEnabled by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val isSystemDark = isSystemInDarkTheme()

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                                .border(1.dp, GlassTheme.colors.accentGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Settings & Preferences",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Theme palettes & engine controls",
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

                // SPECIFICATION 1: GLASS THEME TRIGGER CARD
                Text(
                    text = "THEME & GLASS APPEARANCE",
                    color = GlassTheme.colors.accentGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                GlassThemeTriggerCard(
                    themeMode = themeMode,
                    themeColorKey = themeColorKey,
                    onClick = { showThemeDialog = true }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tier 1: Quick Theme Mode Row
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

                Spacer(modifier = Modifier.height(12.dp))

                // Tier 2: Accent Color Palette Swatches (Horizontal Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ACCENT_PALETTES.forEach { palette ->
                        val isSelected = themeColorKey.equals(palette.key, ignoreCase = true)
                        Box(
                            modifier = Modifier
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
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(palette.primary)
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = palette.name,
                                    color = if (isSelected) GlassTheme.colors.textPrimary else GlassTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Engine & Protection Preferences
                Text(
                    text = "BROWSER & ENGINE CONTROLS",
                    color = GlassTheme.colors.accentGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingsToggleRow(
                    icon = Icons.Default.Shield,
                    title = "Built-In Ad & Tracker Blocker",
                    subtitle = "Blocks popups, banner ads, and invasive analytics",
                    checked = adBlockerEnabled,
                    onCheckedChange = { adBlockerEnabled = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SettingsToggleRow(
                    icon = Icons.Default.Speed,
                    title = "8-Thread Chunk Acceleration",
                    subtitle = "Splits large video downloads into parallel HTTP byte-ranges",
                    checked = multiThreadEnabled,
                    onCheckedChange = { multiThreadEnabled = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Download Directory Row
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                    borderColor = GlassTheme.colors.glassBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.accentGlow.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download Directory",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${Environment.DIRECTORY_DOWNLOADS}/0003_Downloader",
                                color = GlassTheme.colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Privacy Actions
                Text(
                    text = "PRIVACY & CLEANUP",
                    color = GlassTheme.colors.accentGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (!showClearConfirm) {
                    GlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showClearConfirm = true },
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                        borderColor = GlassTheme.colors.glassBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(GlassTheme.colors.dangerGlass.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = GlassTheme.colors.dangerGlass,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear Browsing Data & Cookies",
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Wipes cached web files, active logins, and history",
                                    color = GlassTheme.colors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = GlassTheme.colors.dangerGlass.copy(alpha = 0.12f),
                        borderColor = GlassTheme.colors.dangerGlass.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Confirm Clear Browsing Data?",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This will permanently remove cached cookies, temporary DOM resources, and browser tabs history.",
                                color = GlassTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassButton(
                                    text = "Cancel",
                                    onClick = { showClearConfirm = false },
                                    modifier = Modifier.weight(1f),
                                    isPrimary = false
                                )

                                GlassButton(
                                    text = "Clear Data",
                                    onClick = {
                                        showClearConfirm = false
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
        }
    }

    // Standalone 2-Tier Theme Selector Modal
    if (showThemeDialog) {
        ThemeSelectorDialog(
            currentMode = themeMode,
            currentColorKey = themeColorKey,
            onApplyTheme = { mode, colorKey ->
                onApplyTheme(mode, colorKey)
            },
            onDismiss = { showThemeDialog = false }
        )
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.22f)
                else GlassTheme.colors.surfaceGlassSubtle
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
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
                color = if (isSelected) GlassTheme.colors.textPrimary else GlassTheme.colors.textSecondary,
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
    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
        borderColor = GlassTheme.colors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = GlassTheme.colors.textMuted,
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GlassTheme.colors.accentGlow,
                    uncheckedThumbColor = GlassTheme.colors.textMuted,
                    uncheckedTrackColor = GlassTheme.colors.surfaceGlass
                )
            )
        }
    }
}
