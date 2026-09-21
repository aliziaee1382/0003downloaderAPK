package ir.ali0003.downloader.ui.dialogs

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.util.IntentUrlExtractor

/**
 * Sleek Frosted Glass Dialog for pasting external video URLs,
 * resolving direct streams or portal links via VideoSnifferEngine,
 * and choosing whether to store in public storage or AES-256 Vault.
 */
@Composable
fun ManualDownloadUrlDialog(
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onEnqueueDirect: (url: String, isHidden: Boolean) -> Unit,
    onInspectInBrowser: (url: String, isHidden: Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember { mutableStateOf(initialUrl) }
    var saveToVault by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_download_dialog"),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    // Header Row: Icon, Title & Dismiss Button
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
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(GlassTheme.colors.surfaceGlassSubtle)
                                    .border(1.dp, GlassTheme.colors.glassBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = GlassTheme.colors.accentGlow,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Paste Link & Download",
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Direct stream or social web link",
                                    color = GlassTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        GlassIconButton(
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            size = 36.dp,
                            iconSize = 18.dp,
                            contentDescription = "Close Dialog",
                            testTag = "dialog_close_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Text Input with Auto-Paste and Clear Actions
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_url_input"),
                        placeholder = {
                            Text(
                                text = "https://example.com/video.mp4",
                                color = GlassTheme.colors.textMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { urlInput = "" },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear URL",
                                            tint = GlassTheme.colors.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Auto-paste clipboard button
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            val extracted = IntentUrlExtractor.findUrlInString(clipText) ?: clipText.trim()
                                            urlInput = extracted
                                            errorMessage = null
                                            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("paste_clipboard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from Clipboard",
                                        tint = GlassTheme.colors.accentGlow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassTheme.colors.textPrimary,
                            unfocusedTextColor = GlassTheme.colors.textPrimary,
                            focusedContainerColor = GlassTheme.colors.surfaceGlassSubtle,
                            unfocusedContainerColor = GlassTheme.colors.surfaceGlassSubtle,
                            focusedBorderColor = GlassTheme.colors.accentGlow,
                            unfocusedBorderColor = GlassTheme.colors.glassBorder,
                            cursorColor = GlassTheme.colors.accentGlow
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    // Error Message (if any)
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = GlassTheme.colors.dangerGlass,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // "Save to Vault" Option Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassTheme.colors.surfaceGlassSubtle)
                            .border(1.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(14.dp))
                            .clickable { saveToVault = !saveToVault }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (saveToVault) GlassTheme.colors.accentGlow.copy(alpha = 0.2f)
                                            else GlassTheme.colors.surfaceGlassSubtle
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (saveToVault) Icons.Default.Security else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (saveToVault) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Save to Vault",
                                        color = GlassTheme.colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "AES-256 encrypted hidden storage",
                                        color = GlassTheme.colors.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Checkbox(
                                checked = saveToVault,
                                onCheckedChange = { saveToVault = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GlassTheme.colors.accentGlow,
                                    uncheckedColor = GlassTheme.colors.textMuted,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.testTag("save_to_vault_checkbox")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Button: "Inspect & Download"
                    GlassButton(
                        text = "Inspect & Download",
                        icon = Icons.Default.Download,
                        onClick = {
                            val clean = if (urlInput.contains("|")) {
                                urlInput.trim()
                            } else {
                                IntentUrlExtractor.findUrlInString(urlInput) ?: urlInput.trim()
                            }

                            if (clean.isBlank()) {
                                errorMessage = "Please enter or paste a valid link"
                                return@GlassButton
                            }

                            val isSeparatedDash = clean.contains("|")
                            val validUrl = if (!isSeparatedDash && !clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
                                "https://$clean"
                            } else {
                                clean
                            }

                            if (isSeparatedDash || VideoSnifferEngine.isDirectMediaUrl(validUrl) || validUrl.contains(".mpd", ignoreCase = true)) {
                                // Direct media or DASH separated stream -> Enqueue into Download queue directly
                                onEnqueueDirect(validUrl, saveToVault)
                                val msg = if (isSeparatedDash) "Added DASH Muxer download task" else "Added direct download task"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                // Web video portal -> Load in In-App Browser to sniff all streams
                                onInspectInBrowser(validUrl, saveToVault)
                                Toast.makeText(context, "Opening in sniffer browser...", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "inspect_and_download_button"
                    )
                }
            }
        }
    }
}
