package ir.ali0003.downloader.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ali0003.downloader.ui.glass.ACCENT_PALETTES
import ir.ali0003.downloader.ui.glass.AccentColorPalette
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.glass.ThemeMode

/**
 * High-Fidelity Frosted Theme Settings Dialog / Modal
 *
 * Implements 2-Tier Theme Customization:
 * - Section 1: THEME MODE (Dark, Light, Auto)
 * - Section 2: ACCENT COLOR (2-Column Grid with 6 Vibrant Palettes)
 * - Bottom Action: "Confirm & Apply" Glowing Frosted Button
 */
@Composable
fun ThemeSettingsDialog(
    currentMode: ThemeMode,
    currentColorKey: String,
    onApplyTheme: (ThemeMode, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }
    var selectedColorKey by remember(currentColorKey) { mutableStateOf(currentColorKey.lowercase()) }

    // Find the currently selected palette object
    val activePalette = ACCENT_PALETTES.firstOrNull {
        it.key.equals(selectedColorKey, ignoreCase = true)
    } ?: ACCENT_PALETTES.first { it.key == "orange" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Floating Rounded Glass Dialog Container (24.dp corners, dark glass fill, subtle glowing border)
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = activePalette.primary.copy(alpha = 0.25f),
                        spotColor = activePalette.primary.copy(alpha = 0.4f)
                    ),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.96f),
                borderColor = activePalette.primary.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ==========================================
                    // DIALOG HEADER
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(activePalette.primary.copy(alpha = 0.18f))
                                    .border(1.2.dp, activePalette.primary.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = activePalette.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Theme Settings",
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                )
                                Text(
                                    text = "${activePalette.name} • ${selectedMode.title} Mode",
                                    color = activePalette.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Trailing Circular Frosted Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.surfaceGlassSubtle)
                                .border(1.dp, GlassTheme.colors.glassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = GlassTheme.colors.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ==========================================
                    // SECTION 1: THEME MODE
                    // ==========================================
                    Text(
                        text = "THEME MODE",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModePillCard(
                            modifier = Modifier.weight(1f),
                            title = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = selectedMode == ThemeMode.DARK,
                            accentColor = activePalette.primary,
                            onClick = {
                                selectedMode = ThemeMode.DARK
                                onApplyTheme(ThemeMode.DARK, selectedColorKey)
                            }
                        )

                        ThemeModePillCard(
                            modifier = Modifier.weight(1f),
                            title = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = selectedMode == ThemeMode.LIGHT,
                            accentColor = activePalette.primary,
                            onClick = {
                                selectedMode = ThemeMode.LIGHT
                                onApplyTheme(ThemeMode.LIGHT, selectedColorKey)
                            }
                        )

                        ThemeModePillCard(
                            modifier = Modifier.weight(1f),
                            title = "Auto",
                            icon = Icons.Default.SettingsSuggest,
                            isSelected = selectedMode == ThemeMode.AUTO,
                            accentColor = activePalette.primary,
                            onClick = {
                                selectedMode = ThemeMode.AUTO
                                onApplyTheme(ThemeMode.AUTO, selectedColorKey)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ==========================================
                    // SECTION 2: ACCENT COLOR
                    // ==========================================
                    Text(
                        text = "ACCENT COLOR",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2-Column Grid (3 rows x 2 columns)
                    val chunkedPalettes = ACCENT_PALETTES.chunked(2)
                    chunkedPalettes.forEachIndexed { index, rowPalettes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPalettes.forEach { palette ->
                                AccentColorGridCard(
                                    modifier = Modifier.weight(1f),
                                    palette = palette,
                                    isSelected = selectedColorKey.equals(palette.key, ignoreCase = true),
                                    onClick = {
                                        selectedColorKey = palette.key
                                        onApplyTheme(selectedMode, palette.key)
                                    }
                                )
                            }
                        }
                        if (index < chunkedPalettes.size - 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // ==========================================
                    // BOTTOM ACTION: CONFIRM & APPLY
                    // ==========================================
                    ConfirmApplyButton(
                        accentColor = activePalette.primary,
                        onClick = {
                            onApplyTheme(selectedMode, selectedColorKey)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Section 1: Equal-Width Rounded Pill Card for Theme Modes
 */
@Composable
private fun ThemeModePillCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "modeScale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else GlassTheme.colors.glassBorder,
        animationSpec = tween(220),
        label = "modeBorder"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.20f) else GlassTheme.colors.surfaceGlassSubtle,
        animationSpec = tween(220),
        label = "modeBg"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else GlassTheme.colors.textPrimary,
        animationSpec = tween(220),
        label = "modeText"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(animatedBgColor)
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = animatedTextColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = animatedTextColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Section 2: 2-Column Frosted Color Card with Circle Badge, Title, Subtitle, and Animated Checkmark
 */
@Composable
private fun AccentColorGridCard(
    modifier: Modifier = Modifier,
    palette: AccentColorPalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "colorCardScale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) palette.primary else GlassTheme.colors.glassBorder,
        animationSpec = tween(200),
        label = "colorBorder"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) palette.primary.copy(alpha = 0.16f) else GlassTheme.colors.surfaceGlassSubtle,
        animationSpec = tween(200),
        label = "colorBg"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(animatedBgColor)
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Left: Solid Color Circle Badge (24.dp)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(palette.primary, palette.secondary)
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                )

                // Center: Title + Subtitle
                Column {
                    Text(
                        text = palette.name,
                        color = if (isSelected) palette.primary else GlassTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Text(
                        text = palette.hexCode,
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Right: Animated Checkmark (visible ONLY when selected)
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = palette.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Bottom Action: Full-Width Frosted Glass Button ("Confirm & Apply")
 */
@Composable
private fun ConfirmApplyButton(
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.35f),
                spotColor = accentColor.copy(alpha = 0.6f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.95f),
                        accentColor.copy(alpha = 0.80f)
                    )
                )
            )
            .border(1.2.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Confirm & Apply",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}
