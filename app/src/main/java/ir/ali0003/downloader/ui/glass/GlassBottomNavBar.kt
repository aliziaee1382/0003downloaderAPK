package ir.ali0003.downloader.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data representation for an icon-only navigation tab.
 */
data class NavItem(
    val icon: ImageVector,
    val contentDescription: String,
    val badgeCount: Int = 0
)

/**
 * Premium Icon-Only Glassmorphic Bottom Navigation Bar:
 * - Anchored flush against the bottom edge for seamless edge-to-edge immersion.
 * - Smooth animated sliding illuminated pill indicator behind the selected tab with low-bouncy spring physics.
 * - Icon scale and color transitions with reactive glow.
 * - Subtle non-intrusive badge dots for active background tasks / detected media.
 * - Full touch-target accessibility without clipping near device bezels.
 */
@Composable
fun GlassBottomNavBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    browserCount: Int = 0,
    activeCount: Int = 0,
    libraryCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val items = remember(browserCount, activeCount, libraryCount) {
        listOf(
            NavItem(
                icon = Icons.Default.Language,
                contentDescription = "In-App Browser",
                badgeCount = browserCount
            ),
            NavItem(
                icon = Icons.Default.Download,
                contentDescription = "Active Downloads",
                badgeCount = activeCount
            ),
            NavItem(
                icon = Icons.Default.VideoLibrary,
                contentDescription = "Media Library & Vault",
                badgeCount = libraryCount
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp, top = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Outer Glass Pill Container with visible all-around border
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.94f),
            borderColor = GlassTheme.colors.glassBorder.copy(alpha = 0.75f)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp, horizontal = 6.dp)
            ) {
                val totalWidth = maxWidth
                val itemCount = items.size
                val segmentWidth = totalWidth / itemCount
                val pillMarginHorizontal = 4.dp
                val pillWidth = segmentWidth - (pillMarginHorizontal * 2)

                // Animated sliding offset for the active indicator pill
                val indicatorOffset by animateDpAsState(
                    targetValue = (segmentWidth * selectedTab) + pillMarginHorizontal,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "IndicatorOffset"
                )

                // Sliding Illuminated Active Pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(pillWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    GlassTheme.colors.accentGlow.copy(alpha = 0.28f),
                                    GlassTheme.colors.accentGlow.copy(alpha = 0.12f),
                                    GlassTheme.colors.surfaceGlass.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.7f),
                                    GlassTheme.colors.accentGlow.copy(alpha = 0.3f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                )

                // Interactive Tab Icons Row
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index

                        // Scale animation on selection
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.18f else 0.92f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "IconScale"
                        )

                        // Animated Icon Tint
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) {
                                GlassTheme.colors.accentGlow
                            } else {
                                GlassTheme.colors.textSecondary.copy(alpha = 0.65f)
                            },
                            animationSpec = tween(durationMillis = 220),
                            label = "IconTint"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectTab(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.contentDescription,
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .scale(iconScale)
                                )

                                // Subtle Glowing Badge Dot / Counter
                                if (item.badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-2).dp)
                                            .clip(CircleShape)
                                            .background(GlassTheme.colors.accentGlow)
                                            .border(1.dp, GlassTheme.colors.background, CircleShape)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (item.badgeCount > 99) "99+" else "${item.badgeCount}",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
