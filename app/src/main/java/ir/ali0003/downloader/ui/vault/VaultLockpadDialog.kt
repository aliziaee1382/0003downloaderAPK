package ir.ali0003.downloader.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme

/**
 * 4-Digit Glass Lockpad & Biometrics Authentication Modal
 */
@Composable
fun VaultLockpadDialog(
    isConfigured: Boolean,
    pinInput: String,
    pinError: String?,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "SECURE VAULT",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        GlassIconButton(
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            size = 32.dp,
                            iconSize = 16.dp,
                            contentDescription = "Close Lockpad"
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Title & Description
                    Text(
                        text = if (!isConfigured) "Create Vault PIN" else "Enter 4-Digit Passcode",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (!isConfigured) "Set a 4-digit code to protect your hidden media" else "Protected by AES-256 Passcode Encryption",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // PIN Dots Display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) GlassTheme.colors.accentGlow else GlassTheme.colors.surfaceGlassSubtle
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFilled) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Error Message
                    AnimatedVisibility(
                        visible = pinError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = pinError ?: "",
                            color = GlassTheme.colors.dangerGlass,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3x4 Glass Keypad Grid
                    val keypadRows = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('B', '0', 'D') // B = Biometrics, D = Delete/Backspace
                    )

                    keypadRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    'B' -> {
                                        // Biometric Button
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.5f))
                                                .border(1.dp, GlassTheme.colors.glassBorder, CircleShape)
                                                .clickable(onClick = onBiometricClick),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Unlock with Biometrics",
                                                tint = GlassTheme.colors.accentGlow,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    'D' -> {
                                        // Backspace Button
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.5f))
                                                .border(1.dp, GlassTheme.colors.glassBorder, CircleShape)
                                                .clickable(onClick = onBackspaceClick),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Backspace",
                                                tint = GlassTheme.colors.textPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        // Number Digit Button
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(GlassTheme.colors.surfaceGlassSubtle)
                                                .border(1.dp, GlassTheme.colors.glassBorder, CircleShape)
                                                .clickable { onDigitClick(key) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key.toString(),
                                                color = GlassTheme.colors.textPrimary,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}
