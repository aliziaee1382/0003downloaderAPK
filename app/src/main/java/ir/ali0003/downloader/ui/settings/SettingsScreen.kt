package ir.ali0003.downloader.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.downloader.ui.glass.ACCENT_PALETTES
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassPreset
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.glass.ThemeMode

/**
 * Production Settings Screen featuring the Glass Theme Customization Trigger Card
 * and interactive engine preferences.
 */
@Composable
fun SettingsScreen(
    currentPreset: GlassPreset = GlassPreset.SUNSET_AMBER,
    themeMode: ThemeMode = ThemeMode.DARK,
    themeColorKey: String = "orange",
    onSelectPreset: (GlassPreset) -> Unit = {},
    onApplyTheme: (ThemeMode, String) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit = {},
    onClearBrowserData: () -> Unit = {}
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var maxThreads by remember { mutableIntStateOf(8) }
    var stealthModeEnabled by remember { mutableStateOf(true) }
    var autoSnifferEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onNavigateBack,
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "Back"
                    )

                    Column {
                        Text(
                            text = "Settings",
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Customization & Engine Preferences",
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // SPECIFICATION 1: GLASS THEME TRIGGER CARD
            // ==========================================
            Text(
                text = "APPEARANCE & PALETTES",
                color = GlassTheme.colors.accentGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassThemeTriggerCard(
                themeMode = themeMode,
                themeColorKey = themeColorKey,
                onClick = { showThemeDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // ENGINE & THREADING SETTINGS
            // ==========================================
            Text(
                text = "DOWNLOADING ENGINE",
                color = GlassTheme.colors.accentGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.90f),
                borderColor = GlassTheme.colors.glassBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingToggleRow(
                        icon = Icons.Default.Speed,
                        title = "Turbo Sniffer & Multi-Thread",
                        subtitle = "Accelerate downloads with $maxThreads parallel chunks",
                        checked = autoSnifferEnabled,
                        onCheckedChange = { autoSnifferEnabled = it }
                    )

                    SettingToggleRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Stealth Vault Biometrics",
                        subtitle = "Auto-lock vault when leaving application",
                        checked = stealthModeEnabled,
                        onCheckedChange = { stealthModeEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // DATA & PRIVACY
            // ==========================================
            Text(
                text = "PRIVACY & STORAGE",
                color = GlassTheme.colors.accentGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearBrowserData() },
                shape = RoundedCornerShape(18.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.90f),
                borderColor = GlassTheme.colors.glassBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.dangerGlass.copy(alpha = 0.15f))
                                .border(1.dp, GlassTheme.colors.dangerGlass.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = GlassTheme.colors.dangerGlass,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Clear Browser Cache & History",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Erase temporary cookies, history, and active sessions",
                                color = GlassTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GlassTheme.colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Frosted Theme Settings Dialog
        if (showThemeDialog) {
            ThemeSettingsDialog(
                currentMode = themeMode,
                currentColorKey = themeColorKey,
                onApplyTheme = onApplyTheme,
                onDismiss = { showThemeDialog = false }
            )
        }
    }
}

/**
 * Key Architectural Component: Settings Screen Trigger Card
 *
 * - Wide frosted glass card with rounded corners (18.dp)
 * - Leading Element: Circular accent-colored badge (44.dp) hosting a frosted palette icon
 * - Middle Content:
 *   * Title: "Glass Theme Customization" (Bold, white)
 *   * Subtitle: Dynamic summary string showing current state, e.g. "Green • Dark Mode" (in theme.accentColor)
 * - Trailing Element: Forward chevron
 * - Clicking this card opens the Theme Settings Dialog with spring feedback
 */
@Composable
fun GlassThemeTriggerCard(
    themeMode: ThemeMode,
    themeColorKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "triggerCardScale"
    )

    // Resolve color title & palette
    val activePalette = ACCENT_PALETTES.firstOrNull {
        it.key.equals(themeColorKey, ignoreCase = true)
    } ?: ACCENT_PALETTES.first { it.key == "green" }

    val modeTitle = when (themeMode) {
        ThemeMode.DARK -> "Dark Mode"
        ThemeMode.LIGHT -> "Light Mode"
        ThemeMode.AUTO -> "Auto"
    }

    val subtitleString = "${activePalette.name} • $modeTitle"

    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.94f),
        borderColor = activePalette.primary.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Leading Element: Circular Accent Badge (44.dp) hosting Palette Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(activePalette.primary.copy(alpha = 0.18f))
                        .border(1.5.dp, activePalette.primary.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme Palette",
                        tint = activePalette.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Middle Content: Title & Dynamic State Subtitle
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Glass Theme Customization",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitleString,
                        color = activePalette.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Trailing Element: Forward Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Theme Settings",
                tint = GlassTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
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
                    .size(36.dp)
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
