package ir.ali0003.downloader.ui.browser

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption

/**
 * Backward-compatible adapter for MediaSnifferBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoResolutionPickerBottomSheet(
    mediaItem: SniffedMediaItem,
    saveToVault: Boolean,
    onToggleSaveToVault: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmDownload: (SniffedMediaItem, VideoQualityOption?) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    MediaSnifferBottomSheet(
        sniffedMediaList = listOf(mediaItem),
        initialSelectedItem = mediaItem,
        saveToVault = saveToVault,
        onToggleSaveToVault = onToggleSaveToVault,
        onDismiss = onDismiss,
        onConfirmDownload = onConfirmDownload,
        sheetState = sheetState
    )
}
