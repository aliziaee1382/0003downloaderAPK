package ir.ali0003.downloader.ui.theme

import androidx.compose.runtime.Composable
import ir.ali0003.downloader.ui.glass.GlassPreset
import ir.ali0003.downloader.ui.glass.GlassTheme

@Composable
fun DownloaderTheme(
    preset: GlassPreset = GlassPreset.SUNSET_AMBER,
    content: @Composable () -> Unit
) {
    GlassTheme(preset = preset, content = content)
}

@Composable
fun VideoVaultTheme(
    preset: GlassPreset = GlassPreset.SUNSET_AMBER,
    content: @Composable () -> Unit
) {
    GlassTheme(preset = preset, content = content)
}

// Backward-compatible alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    GlassTheme(preset = GlassPreset.SUNSET_AMBER, content = content)
}

