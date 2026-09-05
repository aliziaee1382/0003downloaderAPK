package ir.ali0003.downloader.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-performance frosted glass container with dual gradient borders and specular highlight.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = GlassTheme.colors.cardBackground,
    borderColor: Color = GlassTheme.colors.glassBorder,
    borderWidth: Dp = 1.2.dp,
    glowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val highlightColor = GlassTheme.colors.glassBorderHighlight
    
    val shadowModifier = if (glowElevation > 0.dp) {
        modifier.shadow(
            elevation = glowElevation,
            shape = shape,
            ambientColor = GlassTheme.colors.accentGlow.copy(alpha = 0.3f),
            spotColor = GlassTheme.colors.accentGlow.copy(alpha = 0.4f)
        )
    } else modifier

    Box(
        modifier = shadowModifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = backgroundColor.alpha * 1.15f),
                        backgroundColor.copy(alpha = backgroundColor.alpha * 0.75f)
                    )
                )
            )
            .border(
                border = BorderStroke(
                    width = borderWidth,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            highlightColor.copy(alpha = 0.6f),
                            borderColor,
                            borderColor.copy(alpha = 0.15f)
                        )
                    )
                ),
                shape = shape
            )
            .drawBehind {
                // Subtle specular highlight line across the top edge
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlightColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(20f, 1f),
                    end = Offset(size.width - 20f, 1f),
                    strokeWidth = 1.5f
                )
            },
        content = content
    )
}

/**
 * Clickable / Selectable Glass Card with interactive elevation and neon hover border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    isSelected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(120),
        label = "card_scale"
    )

    val currentBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> GlassTheme.colors.accentGlow
            isPressed -> GlassTheme.colors.glassBorderHighlight
            else -> GlassTheme.colors.glassBorder
        },
        label = "card_border_color"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (isSelected) {
            GlassTheme.colors.surfaceGlass.copy(alpha = 0.35f)
        } else {
            GlassTheme.colors.cardBackground
        },
        label = "card_bg_color"
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .then(clickableModifier)
    ) {
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            backgroundColor = currentBgColor,
            borderColor = currentBorderColor,
            borderWidth = if (isSelected) 1.8.dp else 1.2.dp,
            glowElevation = if (isSelected) 12.dp else 0.dp,
            content = content
        )
    }
}

/**
 * Frosted Glass Action Button with neon accent glow and pressing scale.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    isPrimary: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    testTag: String = "glass_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = tween(100),
        label = "button_scale"
    )

    val buttonBgColor = if (isPrimary) {
        GlassTheme.colors.accentGlow.copy(alpha = 0.22f)
    } else {
        GlassTheme.colors.surfaceGlassSubtle
    }

    val buttonBorderColor = if (isPrimary) {
        GlassTheme.colors.accentGlow.copy(alpha = 0.7f)
    } else {
        GlassTheme.colors.glassBorder
    }

    val contentColor = if (isPrimary) {
        GlassTheme.colors.accentGlow
    } else {
        GlassTheme.colors.textPrimary
    }

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag)
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        buttonBgColor.copy(alpha = if (enabled) buttonBgColor.alpha else 0.1f),
                        buttonBgColor.copy(alpha = if (enabled) buttonBgColor.alpha * 0.7f else 0.05f)
                    )
                )
            )
            .border(
                width = 1.4.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        buttonBorderColor,
                        GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.8f),
                        buttonBorderColor
                    )
                ),
                shape = shape
            )
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

/**
 * Tactile Glass Icon Button for toolbars, actions, and media controls.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = GlassTheme.colors.textPrimary,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = CircleShape,
    isAccent: Boolean = false,
    enabled: Boolean = true,
    testTag: String = "glass_icon_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1.0f,
        animationSpec = tween(100),
        label = "icon_btn_scale"
    )

    val bg = if (isAccent) {
        GlassTheme.colors.accentGlow.copy(alpha = 0.25f)
    } else {
        GlassTheme.colors.surfaceGlassSubtle.copy(alpha = if (enabled) 0.5f else 0.2f)
    }

    val border = if (isAccent) {
        GlassTheme.colors.accentGlow
    } else {
        GlassTheme.colors.glassBorder.copy(alpha = if (enabled) 1f else 0.3f)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .testTag(testTag)
            .clip(shape)
            .background(bg)
            .border(1.2.dp, border, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isAccent) GlassTheme.colors.accentGlow else tint.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Glowing Glass Slider with luminous track and tactile thumb.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    accentColor: Color = GlassTheme.colors.accentGlow
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = GlassTheme.colors.surfaceGlass,
            activeTickColor = GlassTheme.colors.glassBorderHighlight,
            inactiveTickColor = Color.Transparent
        )
    )
}

/**
 * Glass Badge / Chip with luminous dot for status tags (Downloading, Paused, Encrypted, etc.)
 */
@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GlassTheme.colors.accentGlow,
    showDot: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.45f), shape),
        color = color.copy(alpha = 0.12f),
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                        .shadow(4.dp, CircleShape, spotColor = color)
                )
            }
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

/**
 * Frosted Glass Text Field
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.6f))
            .border(1.2.dp, GlassTheme.colors.glassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = GlassTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = GlassTheme.colors.textMuted,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(GlassTheme.colors.accentGlow),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

/**
 * Glass Divider with luminous center gradient
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    color: Color = GlassTheme.colors.glassBorder
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.5f),
                        color,
                        color.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}
