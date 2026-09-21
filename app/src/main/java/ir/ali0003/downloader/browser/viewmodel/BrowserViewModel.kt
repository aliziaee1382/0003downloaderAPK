package ir.ali0003.downloader.browser.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine
import ir.ali0003.downloader.data.local.AppDatabase
import ir.ali0003.downloader.data.local.WebShortcutEntity
import ir.ali0003.downloader.data.repository.DownloadRepository
import ir.ali0003.downloader.data.repository.DownloadRepositoryImpl
import ir.ali0003.downloader.data.repository.ShortcutRepository
import ir.ali0003.downloader.data.repository.ShortcutRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class WebShortcut(
    val title: String,
    val url: String,
    val subtitle: String = "",
    val iconEmoji: String = "",
    val primaryColorHex: Long = 0xFF10B981
)

data class BrowserBookmark(
    val title: String,
    val url: String,
    val addedAt: Long = System.currentTimeMillis()
)

data class BrowserHistoryEntry(
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val downloadRepository: DownloadRepository = DownloadRepositoryImpl(database.downloadDao())
    val shortcutRepository: ShortcutRepository = ShortcutRepositoryImpl(database.shortcutDao())

    // Browser State: True = State A (Home Portal Hub), False = State B (Active Web Browsing)
    private val _isBrowserHome = MutableStateFlow(true)
    val isBrowserHome: StateFlow<Boolean> = _isBrowserHome.asStateFlow()

    // Dynamic Room-persisted 12-slot shortcuts
    val shortcuts: StateFlow<List<WebShortcutEntity>> = shortcutRepository.getAllShortcuts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            shortcutRepository.initializeDefaultPresetsIfEmpty()
        }
    }

    fun saveShortcut(slotIndex: Int, title: String, url: String, id: Long = 0L) {
        viewModelScope.launch {
            shortcutRepository.saveOrUpdateShortcut(
                slotIndex = slotIndex,
                title = title,
                url = url,
                id = id
            )
            _downloadToastMessage.value = "Shortcut saved"
        }
    }

    fun deleteShortcut(slotIndex: Int) {
        viewModelScope.launch {
            shortcutRepository.deleteShortcutBySlot(slotIndex)
            _downloadToastMessage.value = "Shortcut removed"
        }
    }

    fun deleteShortcutById(id: Long) {
        viewModelScope.launch {
            shortcutRepository.deleteShortcutById(id)
            _downloadToastMessage.value = "Shortcut removed"
        }
    }

    // Web navigation state
    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _lastActiveUrl = MutableStateFlow<String?>(null)
    val lastActiveUrl: StateFlow<String?> = _lastActiveUrl.asStateFlow()

    private val _lastActiveTitle = MutableStateFlow<String?>(null)
    val lastActiveTitle: StateFlow<String?> = _lastActiveTitle.asStateFlow()

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("Downloader")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _loadProgress = MutableStateFlow(0)
    val loadProgress: StateFlow<Int> = _loadProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(false)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val _blockedAdsCount = MutableStateFlow(0)
    val blockedAdsCount: StateFlow<Int> = _blockedAdsCount.asStateFlow()

    private val _tabCount = MutableStateFlow(1)
    val tabCount: StateFlow<Int> = _tabCount.asStateFlow()

    // Bookmarks and History
    private val _bookmarks = MutableStateFlow<List<BrowserBookmark>>(
        listOf(
            BrowserBookmark("YouTube", "https://m.youtube.com"),
            BrowserBookmark("Instagram", "https://www.instagram.com"),
            BrowserBookmark("Mux HLS Test", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        )
    )
    val bookmarks: StateFlow<List<BrowserBookmark>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow<List<BrowserHistoryEntry>>(emptyList())
    val history: StateFlow<List<BrowserHistoryEntry>> = _history.asStateFlow()

    // Sniffer state
    private val _sniffedMediaList = MutableStateFlow<List<SniffedMediaItem>>(emptyList())
    val sniffedMediaList: StateFlow<List<SniffedMediaItem>> = _sniffedMediaList.asStateFlow()

    private val _selectedMedia = MutableStateFlow<SniffedMediaItem?>(null)
    val selectedMedia: StateFlow<SniffedMediaItem?> = _selectedMedia.asStateFlow()

    private val _saveToVault = MutableStateFlow(false)
    val saveToVault: StateFlow<Boolean> = _saveToVault.asStateFlow()

    private val _downloadToastMessage = MutableStateFlow<String?>(null)
    val downloadToastMessage: StateFlow<String?> = _downloadToastMessage.asStateFlow()

    // Sniffer Engine instance
    val snifferEngine = VideoSnifferEngine { detectedCanonical ->
        viewModelScope.launch {
            val current = _sniffedMediaList.value
            val hasRichNativeMedia = current.any { it.qualities.any { q -> q.isYoutubeDl } }
            if (!hasRichNativeMedia) {
                _sniffedMediaList.value = listOf(detectedCanonical)
                _selectedMedia.value = detectedCanonical
            }
        }
    }

    fun onUrlInputChanged(newQuery: String) {
        _inputUrl.value = newQuery
    }

    fun navigateToUrl(target: String) {
        var processed = target.trim()
        if (processed.isEmpty()) return

        if (!processed.startsWith("http://") && !processed.startsWith("https://")) {
            processed = if (processed.contains(".") && !processed.contains(" ") && !processed.startsWith(" ")) {
                "https://$processed"
            } else {
                // Default Search Engine: Google
                try {
                    "https://www.google.com/search?q=" + java.net.URLEncoder.encode(processed, "UTF-8")
                } catch (e: Exception) {
                    "https://www.google.com/search?q=" + Uri.encode(processed)
                }
            }
        }

        _isBrowserHome.value = false
        _inputUrl.value = processed
        _currentUrl.value = processed
        _lastActiveUrl.value = processed
        clearSniffedMedia()
    }

    fun resetToHome() {
        val active = _currentUrl.value.ifBlank { _inputUrl.value }
        if (active.isNotBlank()) {
            _lastActiveUrl.value = active
            _lastActiveTitle.value = _pageTitle.value
        }
        _isBrowserHome.value = true
        _inputUrl.value = ""
        _currentUrl.value = ""
        _pageTitle.value = "Downloader"
        clearSniffedMedia()
    }

    fun restorePreviousSession(fallbackUrl: String = "https://m.youtube.com"): String {
        val targetUrl = _lastActiveUrl.value?.takeIf { it.isNotBlank() }
            ?: _history.value.firstOrNull()?.url
            ?: fallbackUrl
        navigateToUrl(targetUrl)
        return targetUrl
    }

    fun onPageStarted(url: String) {
        _isBrowserHome.value = false
        _isLoading.value = true
        _inputUrl.value = url
        _currentUrl.value = url
        snifferEngine.updateCurrentPageInfo(url, null)
    }

    fun onPageFinished(url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        _isLoading.value = false
        _loadProgress.value = 100
        _inputUrl.value = url
        _currentUrl.value = url
        val currentTitle = if (!title.isNullOrBlank()) title else "Web Video Portal"
        _pageTitle.value = currentTitle
        _canGoBack.value = canBack
        _canGoForward.value = canForward
        _lastActiveUrl.value = url
        _lastActiveTitle.value = currentTitle
        snifferEngine.updateCurrentPageInfo(url, currentTitle)

        // Record History Entry
        val entry = BrowserHistoryEntry(title = currentTitle, url = url)
        val historyList = _history.value.toMutableList()
        historyList.removeAll { it.url == url }
        historyList.add(0, entry)
        _history.value = historyList.take(50)
    }

    private val _isExtractingNativeMedia = MutableStateFlow(false)
    val isExtractingNativeMedia: StateFlow<Boolean> = _isExtractingNativeMedia.asStateFlow()

    fun onProgressChanged(progress: Int) {
        _loadProgress.value = progress
        _isLoading.value = progress in 1..99
    }

    fun toggleDesktopMode() {
        _isDesktopMode.value = !_isDesktopMode.value
    }

    fun incrementBlockedAds() {
        _blockedAdsCount.value += 1
    }

    fun addBookmark(title: String, url: String) {
        val current = _bookmarks.value.toMutableList()
        if (current.none { it.url == url }) {
            current.add(0, BrowserBookmark(title = title.ifBlank { url }, url = url))
            _bookmarks.value = current
            _downloadToastMessage.value = "Bookmark saved"
        }
    }

    fun removeBookmark(url: String) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.url == url }
        _bookmarks.value = current
    }

    fun clearHistory() {
        _history.value = emptyList()
        _downloadToastMessage.value = "History cleared"
    }

    fun addNewTab() {
        _tabCount.value += 1
        resetToHome()
    }

    fun clearSniffedMedia() {
        _isExtractingNativeMedia.value = false
        _sniffedMediaList.value = emptyList()
        _selectedMedia.value = null
        snifferEngine.resetSession()
    }

    fun selectMedia(item: SniffedMediaItem?) {
        _selectedMedia.value = item
    }

    fun toggleSaveToVault(enabled: Boolean) {
        _saveToVault.value = enabled
    }

    fun enqueueDownload(
        mediaItem: SniffedMediaItem,
        selectedQuality: VideoQualityOption?
    ) {
        viewModelScope.launch {
            val downloadUrl = selectedQuality?.url ?: mediaItem.url
            val isM3u8 = mediaItem.isM3u8 || selectedQuality?.isHlsVariant == true
            val isAudio = selectedQuality?.formatTag?.contains("AUDIO", ignoreCase = true) == true ||
                    selectedQuality?.resolution?.contains("Audio", ignoreCase = true) == true
            val totalBytes = selectedQuality?.estimatedSizeBytes ?: mediaItem.fileSizeBytes
            val fileName = if (selectedQuality != null) {
                var base = mediaItem.cleanFileName.substringBeforeLast('.')
                if (base.endsWith(".m3u8", ignoreCase = true)) {
                    base = base.removeSuffix(".m3u8").removeSuffix(".M3U8")
                }
                val ext = when {
                    isAudio -> ".mp3"
                    // When downloaded, HLS segments are merged into standard playable MP4 video
                    else -> ".mp4"
                }
                val badge = selectedQuality.cleanResolutionBadge.replace(" ", "_")
                "${base}_$badge$ext"
            } else {
                val clean = mediaItem.cleanFileName
                if (clean.endsWith(".m3u8", ignoreCase = true)) {
                    "${clean.removeSuffix(".m3u8").removeSuffix(".M3U8")}.mp4"
                } else {
                    clean
                }
            }

            // Populate headers JSON
            val headersObj = try {
                JSONObject(mediaItem.headersJson)
            } catch (_: Exception) {
                JSONObject()
            }
            if (mediaItem.pageUrl.isNotBlank()) {
                headersObj.put("webpageUrl", mediaItem.pageUrl)
            }

            downloadRepository.enqueueDownload(
                url = downloadUrl,
                websiteUrl = mediaItem.pageUrl,
                fileName = fileName,
                mimeType = when {
                    isAudio -> "audio/mpeg"
                    // Saved file is a standard video/mp4 playable by all local players
                    isM3u8 -> "video/mp4"
                    else -> mediaItem.mimeType
                },
                totalBytes = totalBytes,
                isM3u8 = isM3u8,
                headersJson = headersObj.toString(),
                isHidden = _saveToVault.value
            )

            val dest = if (_saveToVault.value) "Secure Vault" else "Download Queue"
            _downloadToastMessage.value = "Added to $dest: $fileName"
            _selectedMedia.value = null
        }
    }

    fun clearToastMessage() {
        _downloadToastMessage.value = null
    }
}
