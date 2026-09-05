package ir.ali0003.downloader.ui.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ali0003.downloader.data.local.WebShortcutEntity
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassTheme

private const val TOTAL_SHORTCUT_SLOTS = 12

/**
 * 12-Slot Dynamic Quick Access Grid View for Home Portal
 */
@Composable
fun QuickAccessShortcutsGrid(
    shortcuts: List<WebShortcutEntity>,
    onNavigate: (String) -> Unit,
    onSaveShortcut: (slotIndex: Int, title: String, url: String, id: Long) -> Unit,
    onDeleteShortcut: (slotIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var editingShortcut by remember { mutableStateOf<WebShortcutEntity?>(null) }
    var contextMenuShortcut by remember { mutableStateOf<WebShortcutEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Grid of 12 Slots (4 columns x 3 rows)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .testTag("quick_access_grid_12_slots"),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(TOTAL_SHORTCUT_SLOTS) { slotIndex ->
                val shortcut = shortcuts.find { it.slotIndex == slotIndex }
                if (shortcut != null) {
                    FilledShortcutSlotItem(
                        shortcut = shortcut,
                        onClick = { onNavigate(shortcut.url) },
                        onLongClick = { contextMenuShortcut = shortcut }
                    )
                } else {
                    EmptyShortcutSlotItem(
                        slotIndex = slotIndex,
                        onClick = {
                            editingSlotIndex = slotIndex
                            editingShortcut = null
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Dialog
    if (editingSlotIndex != null) {
        AddEditShortcutDialog(
            slotIndex = editingSlotIndex!!,
            existingShortcut = editingShortcut,
            onDismiss = {
                editingSlotIndex = null
                editingShortcut = null
            },
            onSave = { title, url ->
                onSaveShortcut(
                    editingSlotIndex!!,
                    title,
                    url,
                    editingShortcut?.id ?: 0L
                )
                editingSlotIndex = null
                editingShortcut = null
            }
        )
    }

    // Context Menu Dialog (Edit / Delete / Open)
    if (contextMenuShortcut != null) {
        val activeItem = contextMenuShortcut!!
        ShortcutContextMenuDialog(
            shortcut = activeItem,
            onDismiss = { contextMenuShortcut = null },
            onOpen = {
                contextMenuShortcut = null
                onNavigate(activeItem.url)
            },
            onEdit = {
                editingSlotIndex = activeItem.slotIndex
                editingShortcut = activeItem
                contextMenuShortcut = null
            },
            onDelete = {
                onDeleteShortcut(activeItem.slotIndex)
                contextMenuShortcut = null
            }
        )
    }
}

/**
 * Filled Shortcut Slot with Circular Avatars, Official Badges & Press Animations
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilledShortcutSlotItem(
    shortcut: WebShortcutEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "ShortcutSlotScale"
    )

    val isYouTube = remember(shortcut) {
        shortcut.title.equals("YouTube", ignoreCase = true) ||
                shortcut.url.contains("youtube.com", ignoreCase = true) ||
                shortcut.url.contains("youtu.be", ignoreCase = true)
    }

    val brandColor = remember(shortcut.primaryColorHex) {
        Color(shortcut.primaryColorHex)
    }

    val monogram = remember(shortcut.title) {
        val clean = shortcut.title.trim()
        if (clean.length <= 2) clean.uppercase()
        else clean.take(2).uppercase()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 2.dp)
            .testTag("shortcut_slot_${shortcut.slotIndex}")
    ) {
        // Circular Glass / Brand Icon Container
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (isYouTube) {
                        Brush.verticalGradient(
                            listOf(Color(0xFFFF0000), Color(0xFFCC0000))
                        )
                    } else {
                        Brush.radialGradient(
                            listOf(
                                brandColor.copy(alpha = 0.35f),
                                GlassTheme.colors.cardBackground.copy(alpha = 0.9f)
                            )
                        )
                    }
                )
                .border(
                    width = 1.4.dp,
                    color = if (isYouTube) Color(0xFFFF4D4D) else brandColor.copy(alpha = 0.75f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isYouTube) {
                // Official YouTube Red Play Icon
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                // High-Contrast Monogram Letter Avatar
                Text(
                    text = monogram,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = shortcut.title,
            color = GlassTheme.colors.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Empty Frosted Glass Slot Item featuring "+" (Add) icon
 */
@Composable
private fun EmptyShortcutSlotItem(
    slotIndex: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "EmptySlotScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp)
            .testTag("empty_slot_$slotIndex")
    ) {
        // Dashed / Frosted Add Icon Container
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.4f))
                .border(
                    width = 1.2.dp,
                    color = GlassTheme.colors.glassBorderHighlight.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Shortcut to Slot ${slotIndex + 1}",
                tint = GlassTheme.colors.accentGlow,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add",
            color = GlassTheme.colors.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Frosted Glass Dialog to Add or Edit a Shortcut
 */
@Composable
fun AddEditShortcutDialog(
    slotIndex: Int,
    existingShortcut: WebShortcutEntity? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Unit
) {
    var title by remember(existingShortcut) {
        mutableStateOf(existingShortcut?.title ?: "")
    }
    var url by remember(existingShortcut) {
        mutableStateOf(existingShortcut?.url ?: "")
    }

    val isEditing = existingShortcut != null

    val presets = listOf(
        "Aparat" to "https://www.aparat.com",
        "Instagram" to "https://www.instagram.com",
        "TikTok" to "https://www.tiktok.com",
        "GitHub" to "https://www.github.com",
        "Reddit" to "https://www.reddit.com",
        "Twitch" to "https://www.twitch.tv",
        "Vimeo" to "https://vimeo.com",
        "X / Twitter" to "https://x.com"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent
        ) {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
                borderColor = GlassTheme.colors.glassBorderHighlight
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    // Header Title & Slot Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEditing) "Edit Shortcut" else "Add Custom Shortcut",
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Slot #${slotIndex + 1} of $TOTAL_SHORTCUT_SLOTS",
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassTheme.colors.accentGlow.copy(alpha = 0.15f))
                                .border(
                                    1.dp,
                                    GlassTheme.colors.accentGlow.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Quick Access",
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Input Field 1: Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Shortcut Name / Title") },
                        placeholder = { Text("e.g., Aparat, Instagram, GitHub") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Title,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow
                            )
                        },
                        trailingIcon = {
                            if (title.isNotEmpty()) {
                                IconButton(onClick = { title = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = GlassTheme.colors.textSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlassTheme.colors.accentGlow,
                            unfocusedBorderColor = GlassTheme.colors.glassBorder,
                            focusedTextColor = GlassTheme.colors.textPrimary,
                            unfocusedTextColor = GlassTheme.colors.textPrimary,
                            focusedLabelColor = GlassTheme.colors.accentGlow,
                            unfocusedLabelColor = GlassTheme.colors.textSecondary,
                            cursorColor = GlassTheme.colors.accentGlow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shortcut_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Field 2: Target URL
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Target URL / Web Address") },
                        placeholder = { Text("e.g., https://www.aparat.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow
                            )
                        },
                        trailingIcon = {
                            if (url.isNotEmpty()) {
                                IconButton(onClick = { url = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = GlassTheme.colors.textSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (url.isNotBlank()) {
                                    onSave(title, url)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlassTheme.colors.accentGlow,
                            unfocusedBorderColor = GlassTheme.colors.glassBorder,
                            focusedTextColor = GlassTheme.colors.textPrimary,
                            unfocusedTextColor = GlassTheme.colors.textPrimary,
                            focusedLabelColor = GlassTheme.colors.accentGlow,
                            unfocusedLabelColor = GlassTheme.colors.textSecondary,
                            cursorColor = GlassTheme.colors.accentGlow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shortcut_url_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Popular Suggestions Chips
                    Text(
                        text = "POPULAR SUGGESTIONS",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets) { (presetName, presetUrl) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GlassTheme.colors.surfaceGlassSubtle)
                                    .border(
                                        0.8.dp,
                                        GlassTheme.colors.glassBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        title = presetName
                                        url = presetUrl
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = presetName,
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                color = GlassTheme.colors.textSecondary,
                                fontSize = 14.sp
                            )
                        }

                        GlassButton(
                            text = if (isEditing) "Update Shortcut" else "Save Shortcut",
                            onClick = {
                                if (url.isNotBlank()) {
                                    onSave(title, url)
                                }
                            },
                            enabled = url.isNotBlank(),
                            modifier = Modifier.testTag("save_shortcut_button")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Long-Press Context Actions Dialog
 */
@Composable
fun ShortcutContextMenuDialog(
    shortcut: WebShortcutEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
            borderColor = GlassTheme.colors.glassBorderHighlight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(shortcut.primaryColorHex).copy(alpha = 0.25f))
                            .border(1.dp, Color(shortcut.primaryColorHex), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(shortcut.primaryColorHex),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortcut.title,
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = shortcut.url,
                            color = GlassTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action 1: Open
                ContextActionRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open Website",
                    subtitle = "Navigate to ${shortcut.url}",
                    tint = GlassTheme.colors.accentGlow,
                    onClick = onOpen
                )

                // Action 2: Edit
                ContextActionRow(
                    icon = Icons.Default.Edit,
                    title = "Edit Shortcut",
                    subtitle = "Change title or web address",
                    tint = GlassTheme.colors.textPrimary,
                    onClick = onEdit
                )

                // Action 3: Delete
                ContextActionRow(
                    icon = Icons.Default.Delete,
                    title = "Delete Shortcut",
                    subtitle = "Clear slot and reset to Add placeholder",
                    tint = Color(0xFFEF4444),
                    onClick = onDelete
                )

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Close",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassTheme.colors.surfaceGlassSubtle.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = GlassTheme.colors.textSecondary,
                fontSize = 10.5.sp
            )
        }
    }
}
