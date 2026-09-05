package ir.ali0003.downloader.ui.settings

import androidx.compose.runtime.Composable
import ir.ali0003.downloader.ui.glass.ThemeMode

/**
 * Backward-compatible entry point that delegates to [ThemeSettingsDialog].
 */
@Composable
fun ThemeSelectorDialog(
    currentMode: ThemeMode,
    currentColorKey: String,
    onApplyTheme: (ThemeMode, String) -> Unit,
    onDismiss: () -> Unit
) {
    ThemeSettingsDialog(
        currentMode = currentMode,
        currentColorKey = currentColorKey,
        onApplyTheme = onApplyTheme,
        onDismiss = onDismiss
    )
}
