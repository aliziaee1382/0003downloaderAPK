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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme

/**
 * Enhanced Glass Biometric & PIN Lockpad Dialog.
 * Supports:
 * 1. Direct Biometric Enrollment on first usage (ثبت اثر انگشت/شناسه بیومتریک دستگاه)
 * 2. Biometric Verification for subsequent accesses (تأیید هویت فوری)
 * 3. 4-digit PIN fallback with AES-256 encrypted storage.
 */
@Composable
fun VaultLockpadDialog(
    isConfigured: Boolean,
    isBiometricRegistered: Boolean,
    isBiometricSupported: Boolean,
    pinInput: String,
    pinError: String?,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit,
    onDismiss: () -> Unit
) {
    // If not configured, user MUST create a 4-digit PIN first.
    // If configured and biometric is registered and supported, biometric screen is shown unless user taps PIN.
    var showPinFallback by remember(isConfigured) {
        mutableStateOf(!isConfigured || !isBiometricSupported || !isBiometricRegistered)
    }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_auth_modal"),
                shape = RoundedCornerShape(28.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.96f),
                borderColor = GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.6f)
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
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(GlassTheme.colors.accentGlow.copy(alpha = 0.18f))
                                    .border(1.dp, GlassTheme.colors.accentGlow, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = GlassTheme.colors.accentGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "گاوصندوق امن (SECURE VAULT)",
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBiometricRegistered) "احراز هویت بیومتریک گوشی" else "حفاظت با رمزنگاری سخت‌افزاری",
                                    color = GlassTheme.colors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
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

                    // Mode 1: Primary Biometric Enrollment / Unlock Card (Preferred)
                    if (!showPinFallback && isBiometricSupported) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Large Biometric Scanner Touch Area
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.radialGradient(
                                            listOf(
                                                GlassTheme.colors.accentGlow.copy(alpha = 0.35f),
                                                GlassTheme.colors.accentGlow.copy(alpha = 0.08f)
                                            )
                                        )
                                    )
                                    .border(1.5.dp, GlassTheme.colors.accentGlow, CircleShape)
                                    .clickable(onClick = onBiometricClick)
                                    .testTag("biometric_sensor_action_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "احراز هویت با حسگر بیومتریک",
                                    tint = GlassTheme.colors.accentGlow,
                                    modifier = Modifier.size(54.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (!isBiometricRegistered) "ثبت بیومتریک تلفن همراه" else "تأیید هویت با اثر انگشت / چهره",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (!isBiometricRegistered) {
                                    "برای اولین بار حسگر بیومتریک (اثر انگشت یا پین گوشی) را فعال کنید تا دفعات بعدی ورود فوق‌سریع و ۱۰۰٪ ایمن باشد."
                                } else {
                                    "انگشت خود را روی حسگر قرار دهید یا چهره خود را مقابل دوربین بگیرید."
                                },
                                color = GlassTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            GlassButton(
                                text = if (!isBiometricRegistered) "ثبت و باز کردن با بیومتریک" else "لمس حسگر و بازگشایی",
                                icon = Icons.Default.Fingerprint,
                                onClick = onBiometricClick,
                                isPrimary = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Switch to PIN mode button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showPinFallback = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pin,
                                        contentDescription = null,
                                        tint = GlassTheme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (!isConfigured) "تنظیم رمز ۴ رقمی عددی" else "ورود با رمز عبور ۴ رقمی",
                                        color = GlassTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // Mode 2: 4-Digit PIN Lockpad Display
                        Text(
                            text = if (!isConfigured) "تعیین پین‌کد ۴ رقمی گاوصندوق" else "رمز ۴ رقمی گاوصندوق",
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (!isConfigured) "یک کد ۴ رقمی امن برای پوشه مخفی خود وارد نمایید" else "محافظت‌شده با رمزنگاری AES-256",
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

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
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

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
                                            // Biometric shortcut button
                                            Box(
                                                modifier = Modifier
                                                    .size(62.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.5f))
                                                    .border(1.dp, GlassTheme.colors.glassBorder, CircleShape)
                                                .clickable(onClick = {
                                                    if (isBiometricSupported) {
                                                        showPinFallback = false
                                                        onBiometricClick()
                                                    } else {
                                                        onBiometricClick()
                                                    }
                                                }),
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
                                                    .size(62.dp)
                                                    .clip(CircleShape)
                                                    .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.5f))
                                                    .border(1.dp, GlassTheme.colors.glassBorder, CircleShape)
                                                    .clickable(onClick = onBackspaceClick),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
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
                                                    .size(62.dp)
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
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (isBiometricSupported && isConfigured && isBiometricRegistered) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showPinFallback = false }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "بازگشت به احراز هویت بیومتریک (اثر انگشت)",
                                    color = GlassTheme.colors.accentGlow,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
