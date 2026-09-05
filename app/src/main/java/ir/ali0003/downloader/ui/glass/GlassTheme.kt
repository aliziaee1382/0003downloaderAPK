package ir.ali0003.downloader.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2-Tier Theme Modes (Dark, Light, System Adaptive)
 */
enum class ThemeMode(val title: String) {
    DARK("Dark"),
    LIGHT("Light"),
    AUTO("Auto")
}

/**
 * 6 Vibrant Accent Color Palette Definitions
 */
data class AccentColorPalette(
    val key: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val hexCode: String
)

val ACCENT_PALETTES = listOf(
    AccentColorPalette(
        key = "blue",
        name = "Blue",
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFF0284C7),
        hexCode = "#38BDF8"
    ),
    AccentColorPalette(
        key = "red",
        name = "Red",
        primary = Color(0xFFFB7185),
        secondary = Color(0xFFE11D48),
        hexCode = "#FB7185"
    ),
    AccentColorPalette(
        key = "purple",
        name = "Purple",
        primary = Color(0xFFA78BFA),
        secondary = Color(0xFF7C3AED),
        hexCode = "#A78BFA"
    ),
    AccentColorPalette(
        key = "yellow",
        name = "Yellow",
        primary = Color(0xFFFBBF24),
        secondary = Color(0xFFD97706),
        hexCode = "#FBBF24"
    ),
    AccentColorPalette(
        key = "green",
        name = "Green",
        primary = Color(0xFF34D399),
        secondary = Color(0xFF059669),
        hexCode = "#34D399"
    ),
    AccentColorPalette(
        key = "orange",
        name = "Orange",
        primary = Color(0xFFFB923C),
        secondary = Color(0xFFEA580C),
        hexCode = "#FB923C"
    )
)

/**
 * Supported Dark Glassmorphic Presets (for backward compatibility & extended gallery)
 */
enum class GlassPreset(val displayName: String) {
    DARK_GREEN("Dark Emerald"),
    DARK_PURPLE("Cyber Amethyst"),
    DARK_BLUE("Neon Cyan"),
    CYBERPUNK("Cyberpunk 2077"),
    OBSIDIAN("Obsidian Slate"),
    MIDNIGHT_OLED("Midnight OLED"),
    CRIMSON_RUBY("Crimson Ruby"),
    SUNSET_AMBER("Sunset Amber"),
    MATRIX_NEON("Matrix Neon"),
    COSMIC_VAPORWAVE("Cosmic Vaporwave"),
    AURORA_BOREALIS("Aurora Borealis"),
    TITANIUM_ICE("Titanium Ice")
}

@Immutable
data class GlassColors(
    val preset: GlassPreset = GlassPreset.SUNSET_AMBER,
    val background: Color,
    val surfaceGlass: Color,
    val surfaceGlassSubtle: Color,
    val glassBorder: Color,
    val glassBorderHighlight: Color,
    val accentGlow: Color,
    val secondaryGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val cardBackground: Color,
    val successGlass: Color,
    val warningGlass: Color,
    val dangerGlass: Color,
    val orbColors: List<Color> = emptyList(),
    val glowColor: Color = accentGlow,
    val accentColor: Color = secondaryGlow,
    val bgGradient: List<Color> = listOf(background, cardBackground.copy(alpha = 0.85f), Color(0xFF020204)),
    val isLight: Boolean = false
)

// ==========================================
// 6 COLOR PALETTES: DARK VARIANTS
// ==========================================

val DarkBlueGlass = GlassColors(
    preset = GlassPreset.DARK_BLUE,
    background = Color(0xFF060E1A),
    surfaceGlass = Color(0x3338BDF8),
    surfaceGlassSubtle = Color(0x1A0C1E38),
    glassBorder = Color(0x4D38BDF8),
    glassBorderHighlight = Color(0x99BAE6FD),
    accentGlow = Color(0xFF38BDF8),
    secondaryGlow = Color(0xFF0284C7),
    textPrimary = Color(0xFFF0F9FF),
    textSecondary = Color(0xFFBAE6FD),
    textMuted = Color(0xFF64748B),
    cardBackground = Color(0x450A182E),
    successGlass = Color(0xFF34D399),
    warningGlass = Color(0xFFFBBF24),
    dangerGlass = Color(0xFFFB7185),
    orbColors = listOf(Color(0x6638BDF8), Color(0x550284C7), Color(0x330C4A6E)),
    glowColor = Color(0xFF38BDF8),
    accentColor = Color(0xFF0284C7),
    bgGradient = listOf(Color(0xFF0B192E), Color(0xFF060E1A), Color(0xFF02050A)),
    isLight = false
)

val DarkRedGlass = GlassColors(
    preset = GlassPreset.CRIMSON_RUBY,
    background = Color(0xFF140508),
    surfaceGlass = Color(0x33FB7185),
    surfaceGlassSubtle = Color(0x1A2E0A12),
    glassBorder = Color(0x4DFB7185),
    glassBorderHighlight = Color(0x99FECDD3),
    accentGlow = Color(0xFFFB7185),
    secondaryGlow = Color(0xFFE11D48),
    textPrimary = Color(0xFFFFF1F2),
    textSecondary = Color(0xFFFECDD3),
    textMuted = Color(0xFF886067),
    cardBackground = Color(0x45240810),
    successGlass = Color(0xFF34D399),
    warningGlass = Color(0xFFFBBF24),
    dangerGlass = Color(0xFFFB7185),
    orbColors = listOf(Color(0x66FB7185), Color(0x55E11D48), Color(0x33881337)),
    glowColor = Color(0xFFFB7185),
    accentColor = Color(0xFFE11D48),
    bgGradient = listOf(Color(0xFF22080D), Color(0xFF140508), Color(0xFF060103)),
    isLight = false
)

val DarkPurpleGlass = GlassColors(
    preset = GlassPreset.DARK_PURPLE,
    background = Color(0xFF0E071A),
    surfaceGlass = Color(0x33A78BFA),
    surfaceGlassSubtle = Color(0x1A220F3E),
    glassBorder = Color(0x4DA78BFA),
    glassBorderHighlight = Color(0x99DDD6FE),
    accentGlow = Color(0xFFA78BFA),
    secondaryGlow = Color(0xFF7C3AED),
    textPrimary = Color(0xFFFAF5FF),
    textSecondary = Color(0xFFDDD6FE),
    textMuted = Color(0xFF7E6E96),
    cardBackground = Color(0x451A0C30),
    successGlass = Color(0xFF34D399),
    warningGlass = Color(0xFFFBBF24),
    dangerGlass = Color(0xFFFB7185),
    orbColors = listOf(Color(0x66A78BFA), Color(0x557C3AED), Color(0x334C1D95)),
    glowColor = Color(0xFFA78BFA),
    accentColor = Color(0xFF7C3AED),
    bgGradient = listOf(Color(0xFF190C2F), Color(0xFF0E071A), Color(0xFF040108)),
    isLight = false
)

val DarkYellowGlass = GlassColors(
    preset = GlassPreset.SUNSET_AMBER,
    background = Color(0xFF140F03),
    surfaceGlass = Color(0x33FBBF24),
    surfaceGlassSubtle = Color(0x1A2B2005),
    glassBorder = Color(0x4DFBBF24),
    glassBorderHighlight = Color(0x99FEF3C7),
    accentGlow = Color(0xFFFBBF24),
    secondaryGlow = Color(0xFFD97706),
    textPrimary = Color(0xFFFFFBEB),
    textSecondary = Color(0xFFFEF3C7),
    textMuted = Color(0xFF8A7955),
    cardBackground = Color(0x45241B04),
    successGlass = Color(0xFF34D399),
    warningGlass = Color(0xFFFBBF24),
    dangerGlass = Color(0xFFFB7185),
    orbColors = listOf(Color(0x66FBBF24), Color(0x55D97706), Color(0x3378350F)),
    glowColor = Color(0xFFFBBF24),
    accentColor = Color(0xFFD97706),
    bgGradient = listOf(Color(0xFF241B06), Color(0xFF140F03), Color(0xFF050300)),
    isLight = false
)

val DarkGreenGlass = GlassColors(
    preset = GlassPreset.DARK_GREEN,
    background = Color(0xFF041209),
    surfaceGlass = Color(0x3334D399),
    surfaceGlassSubtle = Color(0x1A092A15),
    glassBorder = Color(0x4D34D399),
    glassBorderHighlight = Color(0x99A7F3D0),
    accentGlow = Color(0xFF34D399),
    secondaryGlow = Color(0xFF059669),
    textPrimary = Color(0xFFECFDF5),
    textSecondary = Color(0xFFA7F3D0),
    textMuted = Color(0xFF5B7D6C),
    cardBackground = Color(0x45072010),
    successGlass = Color(0xFF34D399),
    warningGlass = Color(0xFFFBBF24),
    dangerGlass = Color(0xFFFB7185),
    orbColors = listOf(Color(0x6634D399), Color(0x55059669), Color(0x33064E3B)),
    glowColor = Color(0xFF34D399),
    accentColor = Color(0xFF059669),
    bgGradient = listOf(Color(0xFF072412), Color(0xFF041209), Color(0xFF010502)),
    isLight = false
)

val DarkOrangeGlass = GlassColors(
    preset = GlassPreset.SUNSET_AMBER,
    background = Color(0xFF140802),
    surfaceGlass = Color(0x33FB923C),
    surfaceGlassSubtle = Color(0x1A291404),
    glassBorder = Color(0x4DFB923C),
    glassBorderHighlight = Color(0x99FFEDD5),
    accentGlow = Color(0xFFFB923C),
    secondaryGlow = Color(0xFFEA580C),
    textPrimary = Color(0xFFFFF7ED),
    textSecondary = Color(0xFFFFEDD5),
    textMuted = Color(0xFF8C6F5A),
    cardBackground = Color(0x45251103),
    successGlass = Color(0xFF34D399),
    warningGlass = Color(0xFFFBBF24),
    dangerGlass = Color(0xFFFB7185),
    orbColors = listOf(Color(0x66FB923C), Color(0x55EA580C), Color(0x337C2D12)),
    glowColor = Color(0xFFFB923C),
    accentColor = Color(0xFFEA580C),
    bgGradient = listOf(Color(0xFF261204), Color(0xFF140802), Color(0xFF050200)),
    isLight = false
)

// ==========================================
// 6 COLOR PALETTES: LIGHT VARIANTS
// ==========================================

val LightBlueGlass = GlassColors(
    preset = GlassPreset.DARK_BLUE,
    background = Color(0xFFF0F9FF),
    surfaceGlass = Color(0x66FFFFFF),
    surfaceGlassSubtle = Color(0x4DFFFFFF),
    glassBorder = Color(0x6638BDF8),
    glassBorderHighlight = Color(0x990284C7),
    accentGlow = Color(0xFF0284C7),
    secondaryGlow = Color(0xFF38BDF8),
    textPrimary = Color(0xFF0C243C),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    cardBackground = Color(0x80FFFFFF),
    successGlass = Color(0xFF059669),
    warningGlass = Color(0xFFD97706),
    dangerGlass = Color(0xFFE11D48),
    orbColors = listOf(Color(0x5538BDF8), Color(0x447DD3FC), Color(0x22BAE6FD)),
    glowColor = Color(0xFF38BDF8),
    accentColor = Color(0xFF0284C7),
    bgGradient = listOf(Color(0xFFE0F2FE), Color(0xFFF0F9FF), Color(0xFFFFFFFF)),
    isLight = true
)

val LightRedGlass = GlassColors(
    preset = GlassPreset.CRIMSON_RUBY,
    background = Color(0xFFFFF1F2),
    surfaceGlass = Color(0x66FFFFFF),
    surfaceGlassSubtle = Color(0x4DFFFFFF),
    glassBorder = Color(0x66FB7185),
    glassBorderHighlight = Color(0x99E11D48),
    accentGlow = Color(0xFFE11D48),
    secondaryGlow = Color(0xFFFB7185),
    textPrimary = Color(0xFF3B0711),
    textSecondary = Color(0xFF4C1D24),
    textMuted = Color(0xFF70434A),
    cardBackground = Color(0x80FFFFFF),
    successGlass = Color(0xFF059669),
    warningGlass = Color(0xFFD97706),
    dangerGlass = Color(0xFFE11D48),
    orbColors = listOf(Color(0x55FB7185), Color(0x44FDA4AF), Color(0x22FECDD3)),
    glowColor = Color(0xFFFB7185),
    accentColor = Color(0xFFE11D48),
    bgGradient = listOf(Color(0xFFFFE4E6), Color(0xFFFFF1F2), Color(0xFFFFFFFF)),
    isLight = true
)

val LightPurpleGlass = GlassColors(
    preset = GlassPreset.DARK_PURPLE,
    background = Color(0xFFFAF5FF),
    surfaceGlass = Color(0x66FFFFFF),
    surfaceGlassSubtle = Color(0x4DFFFFFF),
    glassBorder = Color(0x66A78BFA),
    glassBorderHighlight = Color(0x997C3AED),
    accentGlow = Color(0xFF7C3AED),
    secondaryGlow = Color(0xFFA78BFA),
    textPrimary = Color(0xFF260D4A),
    textSecondary = Color(0xFF381E54),
    textMuted = Color(0xFF6B587E),
    cardBackground = Color(0x80FFFFFF),
    successGlass = Color(0xFF059669),
    warningGlass = Color(0xFFD97706),
    dangerGlass = Color(0xFFE11D48),
    orbColors = listOf(Color(0x55A78BFA), Color(0x44C4B5FD), Color(0x22DDD6FE)),
    glowColor = Color(0xFFA78BFA),
    accentColor = Color(0xFF7C3AED),
    bgGradient = listOf(Color(0xFFF3E8FF), Color(0xFFFAF5FF), Color(0xFFFFFFFF)),
    isLight = true
)

val LightYellowGlass = GlassColors(
    preset = GlassPreset.SUNSET_AMBER,
    background = Color(0xFFFFFBEB),
    surfaceGlass = Color(0x66FFFFFF),
    surfaceGlassSubtle = Color(0x4DFFFFFF),
    glassBorder = Color(0x66FBBF24),
    glassBorderHighlight = Color(0x99D97706),
    accentGlow = Color(0xFFD97706),
    secondaryGlow = Color(0xFFFBBF24),
    textPrimary = Color(0xFF372002),
    textSecondary = Color(0xFF4A340C),
    textMuted = Color(0xFF7A653E),
    cardBackground = Color(0x80FFFFFF),
    successGlass = Color(0xFF059669),
    warningGlass = Color(0xFFD97706),
    dangerGlass = Color(0xFFE11D48),
    orbColors = listOf(Color(0x55FBBF24), Color(0x44FDE68A), Color(0x22FEF3C7)),
    glowColor = Color(0xFFFBBF24),
    accentColor = Color(0xFFD97706),
    bgGradient = listOf(Color(0xFFFEF3C7), Color(0xFFFFFBEB), Color(0xFFFFFFFF)),
    isLight = true
)

val LightGreenGlass = GlassColors(
    preset = GlassPreset.DARK_GREEN,
    background = Color(0xFFECFDF5),
    surfaceGlass = Color(0x66FFFFFF),
    surfaceGlassSubtle = Color(0x4DFFFFFF),
    glassBorder = Color(0x6634D399),
    glassBorderHighlight = Color(0x99059669),
    accentGlow = Color(0xFF059669),
    secondaryGlow = Color(0xFF34D399),
    textPrimary = Color(0xFF022C1A),
    textSecondary = Color(0xFF16432E),
    textMuted = Color(0xFF496B5B),
    cardBackground = Color(0x80FFFFFF),
    successGlass = Color(0xFF059669),
    warningGlass = Color(0xFFD97706),
    dangerGlass = Color(0xFFE11D48),
    orbColors = listOf(Color(0x5534D399), Color(0x446EE7B7), Color(0x22A7F3D0)),
    glowColor = Color(0xFF34D399),
    accentColor = Color(0xFF059669),
    bgGradient = listOf(Color(0xFFD1FAE5), Color(0xFFECFDF5), Color(0xFFFFFFFF)),
    isLight = true
)

val LightOrangeGlass = GlassColors(
    preset = GlassPreset.SUNSET_AMBER,
    background = Color(0xFFFFF7ED),
    surfaceGlass = Color(0x66FFFFFF),
    surfaceGlassSubtle = Color(0x4DFFFFFF),
    glassBorder = Color(0x66FB923C),
    glassBorderHighlight = Color(0x99EA580C),
    accentGlow = Color(0xFFEA580C),
    secondaryGlow = Color(0xFFFB923C),
    textPrimary = Color(0xFF381402),
    textSecondary = Color(0xFF4F2712),
    textMuted = Color(0xFF7D5945),
    cardBackground = Color(0x80FFFFFF),
    successGlass = Color(0xFF059669),
    warningGlass = Color(0xFFD97706),
    dangerGlass = Color(0xFFE11D48),
    orbColors = listOf(Color(0x55FB923C), Color(0x44FDBA74), Color(0x22FFEDD5)),
    glowColor = Color(0xFFFB923C),
    accentColor = Color(0xFFEA580C),
    bgGradient = listOf(Color(0xFFFFEDD5), Color(0xFFFFF7ED), Color(0xFFFFFFFF)),
    isLight = true
)

// ==========================================
// ADDITIONAL SPECIAL PRESETS (DARK)
// ==========================================

val CyberpunkGlass = GlassColors(
    preset = GlassPreset.CYBERPUNK,
    background = Color(0xFF0A040B),
    surfaceGlass = Color(0x33FF0055),
    surfaceGlassSubtle = Color(0x1A2E0D22),
    glassBorder = Color(0x66FFE600),
    glassBorderHighlight = Color(0x99FFF9C4),
    accentGlow = Color(0xFFFFE600),
    secondaryGlow = Color(0xFFFF0055),
    textPrimary = Color(0xFFFFFDE7),
    textSecondary = Color(0xFFFFCC80),
    textMuted = Color(0xFF8C6E7F),
    cardBackground = Color(0x4523091B),
    successGlass = Color(0xFF00E676),
    warningGlass = Color(0xFFFFE600),
    dangerGlass = Color(0xFFFF0055),
    orbColors = listOf(Color(0x77FFE600), Color(0x66FF0055), Color(0x5500F5D4)),
    glowColor = Color(0xFFFFE600),
    accentColor = Color(0xFFFF0055),
    bgGradient = listOf(Color(0xFF180512), Color(0xFF0A040B), Color(0xFF030104)),
    isLight = false
)

val ObsidianGlass = GlassColors(
    preset = GlassPreset.OBSIDIAN,
    background = Color(0xFF08090C),
    surfaceGlass = Color(0x2EFFFFFF),
    surfaceGlassSubtle = Color(0x1A1B202A),
    glassBorder = Color(0x40FFFFFF),
    glassBorderHighlight = Color(0x80FFFFFF),
    accentGlow = Color(0xFF90CAF9),
    secondaryGlow = Color(0xFFB0BEC5),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFCFD8DC),
    textMuted = Color(0xFF78909C),
    cardBackground = Color(0x40121620),
    successGlass = Color(0xFF81C784),
    warningGlass = Color(0xFFFFB74D),
    dangerGlass = Color(0xFFE57373),
    orbColors = listOf(Color(0x4490CAF9), Color(0x33B0BEC5), Color(0x2237474F)),
    glowColor = Color(0xFF90CAF9),
    accentColor = Color(0xFFB0BEC5),
    bgGradient = listOf(Color(0xFF12141A), Color(0xFF08090C), Color(0xFF030304)),
    isLight = false
)

val MidnightOledGlass = GlassColors(
    preset = GlassPreset.MIDNIGHT_OLED,
    background = Color(0xFF000000),
    surfaceGlass = Color(0x292979FF),
    surfaceGlassSubtle = Color(0x1A081328),
    glassBorder = Color(0x4D2979FF),
    glassBorderHighlight = Color(0x9982B1FF),
    accentGlow = Color(0xFF2979FF),
    secondaryGlow = Color(0xFF00E5FF),
    textPrimary = Color(0xFFF5F9FF),
    textSecondary = Color(0xFF90CAF9),
    textMuted = Color(0xFF4A6572),
    cardBackground = Color(0x40050C1A),
    successGlass = Color(0xFF00E676),
    warningGlass = Color(0xFFFFD600),
    dangerGlass = Color(0xFFFF1744),
    orbColors = listOf(Color(0x662979FF), Color(0x5500E5FF), Color(0x330D47A1)),
    glowColor = Color(0xFF2979FF),
    accentColor = Color(0xFF00E5FF),
    bgGradient = listOf(Color(0xFF030A18), Color(0xFF01040A), Color(0xFF000000)),
    isLight = false
)

val CrimsonRubyGlass = DarkRedGlass

val SunsetAmberGlass = DarkOrangeGlass

val MatrixNeonGlass = GlassColors(
    preset = GlassPreset.MATRIX_NEON,
    background = Color(0xFF020803),
    surfaceGlass = Color(0x3376FF03),
    surfaceGlassSubtle = Color(0x1A071E0B),
    glassBorder = Color(0x5576FF03),
    glassBorderHighlight = Color(0x99CCFF90),
    accentGlow = Color(0xFF76FF03),
    secondaryGlow = Color(0xFF00E676),
    textPrimary = Color(0xFFF4FFF0),
    textSecondary = Color(0xFFCCFF90),
    textMuted = Color(0xFF587A54),
    cardBackground = Color(0x45071F0A),
    successGlass = Color(0xFF76FF03),
    warningGlass = Color(0xFFFFD600),
    dangerGlass = Color(0xFFFF5252),
    orbColors = listOf(Color(0x6676FF03), Color(0x5500E676), Color(0x441B5E20)),
    glowColor = Color(0xFF76FF03),
    accentColor = Color(0xFF00E676),
    bgGradient = listOf(Color(0xFF061708), Color(0xFF020803), Color(0xFF000200)),
    isLight = false
)

val CosmicVaporwaveGlass = GlassColors(
    preset = GlassPreset.COSMIC_VAPORWAVE,
    background = Color(0xFF09040F),
    surfaceGlass = Color(0x33F50057),
    surfaceGlassSubtle = Color(0x1A250838),
    glassBorder = Color(0x55F50057),
    glassBorderHighlight = Color(0x99FF80AB),
    accentGlow = Color(0xFFF50057),
    secondaryGlow = Color(0xFF00E5FF),
    textPrimary = Color(0xFFFFF0F5),
    textSecondary = Color(0xFFF48FB1),
    textMuted = Color(0xFF88587A),
    cardBackground = Color(0x45230933),
    successGlass = Color(0xFF00E5FF),
    warningGlass = Color(0xFFFFD700),
    dangerGlass = Color(0xFFFF1744),
    orbColors = listOf(Color(0x66F50057), Color(0x5500E5FF), Color(0x444A148C)),
    glowColor = Color(0xFFF50057),
    accentColor = Color(0xFF00E5FF),
    bgGradient = listOf(Color(0xFF160620), Color(0xFF09040F), Color(0xFF020004)),
    isLight = false
)

val AuroraBorealisGlass = GlassColors(
    preset = GlassPreset.AURORA_BOREALIS,
    background = Color(0xFF02090A),
    surfaceGlass = Color(0x331DE9B6),
    surfaceGlassSubtle = Color(0x1A062124),
    glassBorder = Color(0x551DE9B6),
    glassBorderHighlight = Color(0x99A7FFEB),
    accentGlow = Color(0xFF1DE9B6),
    secondaryGlow = Color(0xFFD500F9),
    textPrimary = Color(0xFFF0FFFC),
    textSecondary = Color(0xFFA7FFEB),
    textMuted = Color(0xFF4A7D79),
    cardBackground = Color(0x45062226),
    successGlass = Color(0xFF1DE9B6),
    warningGlass = Color(0xFFFFD600),
    dangerGlass = Color(0xFFFF5252),
    orbColors = listOf(Color(0x661DE9B6), Color(0x55D500F9), Color(0x33004D40)),
    glowColor = Color(0xFF1DE9B6),
    accentColor = Color(0xFFD500F9),
    bgGradient = listOf(Color(0xFF041618), Color(0xFF02090A), Color(0xFF000203)),
    isLight = false
)

val TitaniumIceGlass = GlassColors(
    preset = GlassPreset.TITANIUM_ICE,
    background = Color(0xFF05090F),
    surfaceGlass = Color(0x2E80D8FF),
    surfaceGlassSubtle = Color(0x1A101C2E),
    glassBorder = Color(0x4D80D8FF),
    glassBorderHighlight = Color(0x99E1F5FE),
    accentGlow = Color(0xFF80D8FF),
    secondaryGlow = Color(0xFFECEFF1),
    textPrimary = Color(0xFFF5FAFF),
    textSecondary = Color(0xFFB3E5FC),
    textMuted = Color(0xFF5D7385),
    cardBackground = Color(0x400C1626),
    successGlass = Color(0xFF81D4FA),
    warningGlass = Color(0xFFFFD54F),
    dangerGlass = Color(0xFFFF8A80),
    orbColors = listOf(Color(0x6680D8FF), Color(0x55ECEFF1), Color(0x33263238)),
    glowColor = Color(0xFF80D8FF),
    accentColor = Color(0xFFECEFF1),
    bgGradient = listOf(Color(0xFF0D1826), Color(0xFF05090F), Color(0xFF010204)),
    isLight = false
)

fun getGlassColors(preset: GlassPreset): GlassColors = when (preset) {
    GlassPreset.DARK_GREEN -> DarkGreenGlass
    GlassPreset.DARK_PURPLE -> DarkPurpleGlass
    GlassPreset.DARK_BLUE -> DarkBlueGlass
    GlassPreset.CYBERPUNK -> CyberpunkGlass
    GlassPreset.OBSIDIAN -> ObsidianGlass
    GlassPreset.MIDNIGHT_OLED -> MidnightOledGlass
    GlassPreset.CRIMSON_RUBY -> CrimsonRubyGlass
    GlassPreset.SUNSET_AMBER -> SunsetAmberGlass
    GlassPreset.MATRIX_NEON -> MatrixNeonGlass
    GlassPreset.COSMIC_VAPORWAVE -> CosmicVaporwaveGlass
    GlassPreset.AURORA_BOREALIS -> AuroraBorealisGlass
    GlassPreset.TITANIUM_ICE -> TitaniumIceGlass
}

val LocalGlassColors = compositionLocalOf { DarkOrangeGlass }

object GlassTheme {
    val colors: GlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current

    /**
     * Resolves the exact combined theme for Mode and Accent Color
     */
    fun getThemeForModeAndColor(isLight: Boolean, colorKey: String): GlassColors {
        return when (colorKey.lowercase().trim()) {
            "blue" -> if (isLight) LightBlueGlass else DarkBlueGlass
            "red" -> if (isLight) LightRedGlass else DarkRedGlass
            "purple" -> if (isLight) LightPurpleGlass else DarkPurpleGlass
            "yellow" -> if (isLight) LightYellowGlass else DarkYellowGlass
            "green" -> if (isLight) LightGreenGlass else DarkGreenGlass
            "orange" -> if (isLight) LightOrangeGlass else DarkOrangeGlass
            else -> if (isLight) LightOrangeGlass else DarkOrangeGlass
        }
    }
}

/**
 * Animated Ambient Glowing Orbs Background with 100% Dynamic Theme Binding
 */
@Composable
fun GlassOrbBackground(
    modifier: Modifier = Modifier,
    theme: GlassColors = GlassTheme.colors
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glass_orbs")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )

    // Smooth reactive color interpolations when active theme changes
    val animatedGlowColor by animateColorAsState(
        targetValue = theme.glowColor,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "orb_glow"
    )

    val animatedAccentColor by animateColorAsState(
        targetValue = theme.accentColor,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "orb_accent"
    )

    val animatedBgStart by animateColorAsState(
        targetValue = theme.bgGradient.getOrElse(0) { theme.background },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bg_start"
    )

    val animatedBgMid by animateColorAsState(
        targetValue = theme.bgGradient.getOrElse(1) { theme.background },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bg_mid"
    )

    val animatedBgEnd by animateColorAsState(
        targetValue = theme.bgGradient.getOrElse(2) { if (theme.isLight) Color.White else Color(0xFF020204) },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bg_end"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(animatedBgStart, animatedBgMid, animatedBgEnd)
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            val width = size.width
            val height = size.height
            val maxDimension = width.coerceAtLeast(height)

            // Orb 1: Primary floating glow orb
            val orb1X = width * (0.3f + 0.25f * cos(phase.toDouble()).toFloat())
            val orb1Y = height * (0.25f + 0.15f * sin(phase.toDouble()).toFloat())
            val orb1Radius = maxDimension * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = if (theme.isLight) 0.35f else 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = orb1Radius
                ),
                radius = orb1Radius,
                center = Offset(orb1X, orb1Y)
            )

            // Orb 2: Secondary floating accent orb
            val orb2X = width * (0.75f - 0.2f * sin(phase.toDouble() * 1.2).toFloat())
            val orb2Y = height * (0.65f + 0.18f * cos(phase.toDouble() * 1.2).toFloat())
            val orb2Radius = maxDimension * 0.40f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccentColor.copy(alpha = if (theme.isLight) 0.28f else 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(orb2X, orb2Y),
                    radius = orb2Radius
                ),
                radius = orb2Radius,
                center = Offset(orb2X, orb2Y)
            )

            // Orb 3: Deep subtle accent orb
            val orb3X = width * (0.5f + 0.3f * cos(phase.toDouble() * 0.8 + 1.5).toFloat())
            val orb3Y = height * (0.85f - 0.15f * sin(phase.toDouble() * 0.8 + 1.5).toFloat())
            val orb3Radius = maxDimension * 0.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = if (theme.isLight) 0.22f else 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(orb3X, orb3Y),
                    radius = orb3Radius
                ),
                radius = orb3Radius,
                center = Offset(orb3X, orb3Y)
            )

            // Vignette overlay for contrast and focus
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        if (theme.isLight) Color(0xFF0F172A).copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.60f)
                    ),
                    center = Offset(width / 2f, height / 2f),
                    radius = maxDimension * 0.85f
                )
            )
        }
    }
}

/**
 * Overload allowing passing GlassPreset directly
 */
@Composable
fun GlassOrbBackground(
    modifier: Modifier = Modifier,
    preset: GlassPreset
) {
    GlassOrbBackground(
        modifier = modifier,
        theme = getGlassColors(preset)
    )
}

/**
 * GlassBackgroundContainer wrapping children over dynamic orb background
 */
@Composable
fun GlassBackgroundContainer(
    modifier: Modifier = Modifier,
    theme: GlassColors = GlassTheme.colors,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        GlassOrbBackground(
            modifier = Modifier.fillMaxSize(),
            theme = theme
        )
        content()
    }
}

/**
 * Root GlassTheme Composable accepting dynamic GlassColors
 */
@Composable
fun GlassTheme(
    colors: GlassColors,
    content: @Composable () -> Unit
) {
    val materialColorScheme = if (colors.isLight) {
        lightColorScheme(
            primary = colors.accentGlow,
            onPrimary = Color.White,
            primaryContainer = colors.surfaceGlass,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.secondaryGlow,
            onSecondary = Color.White,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.cardBackground,
            onSurface = colors.textPrimary,
            error = colors.dangerGlass,
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = colors.accentGlow,
            onPrimary = Color.Black,
            primaryContainer = colors.surfaceGlass,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.secondaryGlow,
            onSecondary = Color.Black,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.cardBackground,
            onSurface = colors.textPrimary,
            error = colors.dangerGlass,
            onError = Color.White
        )
    }

    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}

/**
 * Overload for backward compatibility with GlassPreset
 */
@Composable
fun GlassTheme(
    preset: GlassPreset = GlassPreset.SUNSET_AMBER,
    content: @Composable () -> Unit
) {
    GlassTheme(
        colors = getGlassColors(preset),
        content = content
    )
}
