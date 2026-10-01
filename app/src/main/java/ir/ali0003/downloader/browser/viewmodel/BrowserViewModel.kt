package ir.ali0003.downloader.browser.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    // Persistent WebView instance and saved state Bundle across tab changes and recompositions
    @SuppressLint("StaticFieldLeak")
    private var persistentWebView: WebView? = null
    val webViewStateBundle = Bundle()

    private val _webViewResetTrigger = MutableStateFlow(0)
    val webViewResetTrigger: StateFlow<Int> = _webViewResetTrigger.asStateFlow()

    fun getWebView(): WebView? = persistentWebView

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(context: Context): WebView {
        persistentWebView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        val newWebView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                if (_isDesktopMode.value) {
                    userAgentString = DESKTOP_USER_AGENT
                }
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(
                snifferEngine.VideoSnifferBridge(this),
                VideoSnifferEngine.JS_BRIDGE_NAME
            )
            addJavascriptInterface(
                snifferEngine.VideoSnifferBridge(this),
                "AndroidBridge"
            )

            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    try {
                        evaluateJavascript(VideoSnifferEngine.PORNHUB_FLASHVARS_EXTRACTOR_JS, null)
                    } catch (_: Exception) {}
                }
                false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return snifferEngine.shouldInterceptRequest(view, request)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldInterceptRequest(
                    view: WebView?,
                    url: String?
                ): WebResourceResponse? {
                    return snifferEngine.shouldInterceptRequestUrl(view, url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) {
                        this@BrowserViewModel.onPageStarted(url)
                    }
                    try {
                        view?.evaluateJavascript(VideoSnifferEngine.BLOB_HOOK_SNIFFER_JS, null)
                        view?.evaluateJavascript(VideoSnifferEngine.PLAY_EVENT_SNIFFER_JS, null)
                    } catch (_: Exception) {}
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (url != null && !isReload && url != _currentUrl.value && !url.startsWith("data:") && !url.startsWith("about:")) {
                        this@BrowserViewModel.onPageStarted(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        this@BrowserViewModel.onPageFinished(
                            url = url,
                            title = view?.title,
                            canBack = view?.canGoBack() ?: false,
                            canForward = view?.canGoForward() ?: false
                        )
                        try {
                            view?.evaluateJavascript(VideoSnifferEngine.BLOB_HOOK_SNIFFER_JS, null)
                            view?.evaluateJavascript(VideoSnifferEngine.DOM_SNIFFER_JS, null)
                            view?.evaluateJavascript(VideoSnifferEngine.PORNHUB_FLASHVARS_EXTRACTOR_JS, null)
                            view?.evaluateJavascript(VideoSnifferEngine.PLAY_EVENT_SNIFFER_JS, null)
                        } catch (_: Exception) {}
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        detail?.didCrash() ?: true
                    } else {
                        true
                    }
                    Log.e("InAppBrowser", "onRenderProcessGone detected (crashed: $didCrash)")
                    try {
                        view?.let {
                            it.stopLoading()
                            (it.parent as? ViewGroup)?.removeView(it)
                            it.destroy()
                        }
                    } catch (_: Exception) {}
                    if (persistentWebView == view) {
                        persistentWebView = null
                    }
                    webViewStateBundle.clear()
                    _webViewResetTrigger.value += 1
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    this@BrowserViewModel.onProgressChanged(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        this@BrowserViewModel.onPageFinished(
                            url = view?.url ?: "",
                            title = title,
                            canBack = view?.canGoBack() ?: false,
                            canForward = view?.canGoForward() ?: false
                        )
                    }
                }
            }
        }

        persistentWebView = newWebView
        return newWebView
    }

    fun saveWebViewState(view: WebView? = persistentWebView) {
        try {
            view?.saveState(webViewStateBundle)
        } catch (_: Exception) {}
    }

    fun restoreWebViewState(view: WebView? = persistentWebView) {
        try {
            if (!webViewStateBundle.isEmpty && view?.url.isNullOrBlank()) {
                view?.restoreState(webViewStateBundle)
            }
        } catch (_: Exception) {}
    }

    fun loadUrlInWebView(target: String) {
        val processed = target.trim()
        if (processed.isEmpty()) return
        navigateToUrl(processed)
        val finalUrl = _currentUrl.value
        if (finalUrl.isNotBlank()) {
            persistentWebView?.loadUrl(finalUrl)
        }
    }

    fun destroyWebView() {
        try {
            persistentWebView?.let {
                it.stopLoading()
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
        } catch (_: Exception) {}
        persistentWebView = null
        webViewStateBundle.clear()
    }

    override fun onCleared() {
        super.onCleared()
        destroyWebView()
    }

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
            BrowserBookmark("XNXX", "https://www.xnxx.com/video-18uo6v0d/fucking_sexy_milf"),
            BrowserBookmark("Pornhub", "https://www.pornhub.com/view_video.php?viewkey=ph57ab1c79e33b5"),
            BrowserBookmark("Instagram", "https://www.instagram.com")
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

    // Centralized deduplicated & sorted video qualities
    private val _detectedVideoQualities = MutableStateFlow<List<VideoQualityOption>>(emptyList())
    val detectedVideoQualities: StateFlow<List<VideoQualityOption>> = _detectedVideoQualities.asStateFlow()

    private val _saveToVault = MutableStateFlow(false)
    val saveToVault: StateFlow<Boolean> = _saveToVault.asStateFlow()

    private val _downloadToastMessage = MutableStateFlow<String?>(null)
    val downloadToastMessage: StateFlow<String?> = _downloadToastMessage.asStateFlow()

    private val _hasActivePlayingVideo = MutableStateFlow(false)
    val hasActivePlayingVideo: StateFlow<Boolean> = _hasActivePlayingVideo.asStateFlow()

    // Sniffer Engine instance
    val snifferEngine = VideoSnifferEngine(
        context = application,
        onMediaDetected = { detectedItem ->
            viewModelScope.launch {
                // When an active video is already playing, prevent background videos or ads from accumulating
                if (_hasActivePlayingVideo.value) {
                    val currentActive = _selectedMedia.value
                    if (currentActive != null && isSameVideo(currentActive, detectedItem)) {
                        addOrUpdateSniffedMedia(detectedItem, makeActive = false)
                    }
                    return@launch
                }
                val current = _sniffedMediaList.value
                val hasRichNativeMedia = current.any { it.qualities.any { q -> q.isYoutubeDl } }
                if (!hasRichNativeMedia) {
                    addOrUpdateSniffedMedia(detectedItem, makeActive = false)
                }
            }
        }
    ).apply {
        onActivePlayStarted = { url, _ ->
            viewModelScope.launch {
                _hasActivePlayingVideo.value = true
                val currentActive = _selectedMedia.value
                val isSame = currentActive?.let {
                    normalizeMediaUrl(it.url) == normalizeMediaUrl(url) ||
                    (it.isM3u8 && url.contains(".m3u8", ignoreCase = true) && isSameHlsStream(it.url, url))
                } ?: false
                if (!isSame) {
                    // Replace on Active Play:
                    // Instantly purge all previous background/preview videos from ViewModel & reset counter
                    _sniffedMediaList.value = emptyList()
                    _selectedMedia.value = null
                    _detectedVideoQualities.value = emptyList()
                }
            }
        }
        onActiveMediaDetected = { activeItem ->
            viewModelScope.launch {
                _hasActivePlayingVideo.value = true
                replaceOnActivePlay(activeItem)
            }
        }
    }

    private fun normalizeMediaUrl(url: String): String {
        return url.substringBefore('?').substringBefore('#').trim().lowercase()
    }

    private fun isSameHlsStream(url1: String, url2: String): Boolean {
        val norm1 = normalizeMediaUrl(url1)
        val norm2 = normalizeMediaUrl(url2)
        if (norm1 == norm2) return true
        val dir1 = norm1.substringBeforeLast('/')
        val dir2 = norm2.substringBeforeLast('/')
        return dir1.isNotBlank() && dir1 == dir2
    }

    private fun isSameVideo(v1: SniffedMediaItem, v2: SniffedMediaItem): Boolean {
        if (v1.id == v2.id) return true
        val norm1 = normalizeMediaUrl(v1.url)
        val norm2 = normalizeMediaUrl(v2.url)
        if (norm1 == norm2) return true
        if (v1.isM3u8 && v2.isM3u8 && isSameHlsStream(v1.url, v2.url)) return true
        return false
    }

    fun replaceOnActivePlay(mediaItem: SniffedMediaItem) {
        val cleanUrl = mediaItem.url.substringBefore('?').substringBefore('#').lowercase()
        // 1. Strictly ignore partial segments and subtitle fragments
        if (cleanUrl.endsWith(".ts") || cleanUrl.endsWith(".m4s") || cleanUrl.endsWith(".vtt") ||
            cleanUrl.endsWith(".key") || cleanUrl.endsWith(".cmfa") || cleanUrl.endsWith(".cmfv") ||
            cleanUrl.endsWith(".init")
        ) {
            return
        }

        // 2. Minimum size validation for direct video files:
        val isAudio = mediaItem.mimeType.contains("audio", ignoreCase = true) ||
                mediaItem.url.contains(".mp3", ignoreCase = true) ||
                mediaItem.url.contains(".m4a", ignoreCase = true)
        if (!mediaItem.isM3u8 && !mediaItem.isDash && !isAudio && mediaItem.fileSizeBytes in 1 until (1024 * 1024L)) {
            return
        }

        val deduplicatedQualities = deduplicateAndSortQualities(mediaItem.qualities)
        val sanitizedSize = if (mediaItem.isM3u8 || mediaItem.isDash) {
            if (mediaItem.fileSizeBytes >= 1024 * 1024L) mediaItem.fileSizeBytes else 0L
        } else {
            mediaItem.fileSizeBytes
        }
        val cleanItem = mediaItem.copy(
            fileSizeBytes = sanitizedSize,
            qualities = deduplicatedQualities
        )

        val currentActive = _selectedMedia.value
        if (currentActive != null && isSameVideo(currentActive, cleanItem)) {
            // Same active video: update/merge qualities
            val mergedQualities = deduplicateAndSortQualities(currentActive.qualities + cleanItem.qualities)
            val updated = currentActive.copy(
                title = if (currentActive.displayTitle.isNotBlank() && !currentActive.displayTitle.startsWith("video_")) currentActive.title else cleanItem.title,
                thumbnailUrl = cleanItem.thumbnailUrl ?: currentActive.thumbnailUrl,
                durationSeconds = maxOf(currentActive.durationSeconds, cleanItem.durationSeconds),
                fileSizeBytes = maxOf(currentActive.fileSizeBytes, cleanItem.fileSizeBytes),
                qualities = mergedQualities,
                isM3u8 = currentActive.isM3u8 || cleanItem.isM3u8,
                isDash = currentActive.isDash || cleanItem.isDash,
                headers = currentActive.headers + cleanItem.headers
            )
            _sniffedMediaList.value = listOf(updated)
            _selectedMedia.value = updated
            _detectedVideoQualities.value = mergedQualities
        } else {
            // Replace on Active Play:
            // Purge all previous background/preview videos, reset counter, and store ONLY this new active video
            _sniffedMediaList.value = listOf(cleanItem)
            _selectedMedia.value = cleanItem
            _detectedVideoQualities.value = deduplicatedQualities
        }
    }

    fun addOrUpdateSniffedMedia(mediaItem: SniffedMediaItem, makeActive: Boolean = false) {
        val cleanUrl = mediaItem.url.substringBefore('?').substringBefore('#').lowercase()
        // 1. Strictly ignore partial segments and subtitle fragments
        if (cleanUrl.endsWith(".ts") || cleanUrl.endsWith(".m4s") || cleanUrl.endsWith(".vtt") ||
            cleanUrl.endsWith(".key") || cleanUrl.endsWith(".cmfa") || cleanUrl.endsWith(".cmfv") ||
            cleanUrl.endsWith(".init")
        ) {
            return
        }

        // 2. Minimum size validation for direct video files:
        // Files under 1 MB are tracking pixels, previews, stickers, or server errors
        val isAudio = mediaItem.mimeType.contains("audio", ignoreCase = true) ||
                mediaItem.url.contains(".mp3", ignoreCase = true) ||
                mediaItem.url.contains(".m4a", ignoreCase = true)
        if (!mediaItem.isM3u8 && !mediaItem.isDash && !isAudio && mediaItem.fileSizeBytes in 1 until (1024 * 1024L)) {
            return
        }

        val currentList = _sniffedMediaList.value.toMutableList()
        val normalizedNew = normalizeMediaUrl(mediaItem.url)

        val existingIndex = currentList.indexOfFirst { existing ->
            existing.id == mediaItem.id ||
            normalizeMediaUrl(existing.url) == normalizedNew ||
            (existing.isM3u8 && mediaItem.isM3u8 && isSameHlsStream(existing.url, mediaItem.url))
        }

        val deduplicatedQualities = deduplicateAndSortQualities(mediaItem.qualities)
        val sanitizedSize = if (mediaItem.isM3u8 || mediaItem.isDash) {
            if (mediaItem.fileSizeBytes >= 1024 * 1024L) mediaItem.fileSizeBytes else 0L
        } else {
            mediaItem.fileSizeBytes
        }
        val cleanItem = mediaItem.copy(
            fileSizeBytes = sanitizedSize,
            qualities = deduplicatedQualities
        )

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val mergedQualities = deduplicateAndSortQualities(existing.qualities + cleanItem.qualities)
            val updated = existing.copy(
                title = if (existing.displayTitle.isNotBlank() && !existing.displayTitle.startsWith("video_")) existing.title else cleanItem.title,
                thumbnailUrl = cleanItem.thumbnailUrl ?: existing.thumbnailUrl,
                durationSeconds = maxOf(existing.durationSeconds, cleanItem.durationSeconds),
                fileSizeBytes = maxOf(existing.fileSizeBytes, cleanItem.fileSizeBytes),
                qualities = mergedQualities,
                isM3u8 = existing.isM3u8 || cleanItem.isM3u8,
                isDash = existing.isDash || cleanItem.isDash,
                headers = existing.headers + cleanItem.headers
            )
            if (makeActive) {
                currentList.removeAt(existingIndex)
                currentList.add(0, updated)
                _selectedMedia.value = updated
                _detectedVideoQualities.value = mergedQualities
            } else {
                currentList[existingIndex] = updated
                if (_selectedMedia.value?.id == existing.id || _selectedMedia.value == null) {
                    _selectedMedia.value = updated
                    _detectedVideoQualities.value = mergedQualities
                }
            }
        } else {
            // New distinct video detected!
            if (makeActive) {
                currentList.add(0, cleanItem)
                _selectedMedia.value = cleanItem
                _detectedVideoQualities.value = deduplicatedQualities
            } else {
                currentList.add(cleanItem)
                if (_selectedMedia.value == null) {
                    _selectedMedia.value = cleanItem
                    _detectedVideoQualities.value = deduplicatedQualities
                }
            }
        }

        _sniffedMediaList.value = currentList
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
        _hasActivePlayingVideo.value = false
        clearSniffedMedia()
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
        val newMode = !_isDesktopMode.value
        _isDesktopMode.value = newMode
        persistentWebView?.let { view ->
            view.settings.userAgentString = if (newMode) DESKTOP_USER_AGENT else null
            view.reload()
        }
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
        persistentWebView?.loadUrl("about:blank")
        webViewStateBundle.clear()
        resetToHome()
    }

    fun clearSniffedMedia() {
        _isExtractingNativeMedia.value = false
        _hasActivePlayingVideo.value = false
        _sniffedMediaList.value = emptyList()
        _selectedMedia.value = null
        _detectedVideoQualities.value = emptyList()
        snifferEngine.resetSession()
    }

    fun selectMedia(item: SniffedMediaItem?) {
        if (item != null) {
            val deduplicated = deduplicateAndSortQualities(item.qualities)
            val cleanItem = item.copy(qualities = deduplicated)
            _detectedVideoQualities.value = deduplicated
            _selectedMedia.value = cleanItem
        } else {
            _selectedMedia.value = null
            _detectedVideoQualities.value = emptyList()
        }
    }

    /**
     * Unified deduplication and sorting for video qualities:
     * - Group by standard resolution height (e.g. 1080p, 720p, 480p, 360p, 240p).
     * - When combining items from JavaScript bridge (onMediaDefinitionsFound) and network sniffing (HlsManifestParser):
     *   Prioritizes direct Progressive MP4 for faster and lighter downloading; falls back to distinct adaptive HLS renditions.
     * - Sorts the finalized quality list in descending order of resolution (1080p -> 720p -> 480p -> 240p).
     */
    fun deduplicateAndSortQualities(
        rawQualities: List<VideoQualityOption>,
        prioritizeDirectMp4: Boolean = true
    ): List<VideoQualityOption> {
        if (rawQualities.isEmpty()) return emptyList()

        // 1. Separate Audio and Video
        val audioOptions = rawQualities.filter {
            it.formatTag.contains("AUDIO", ignoreCase = true) ||
                    it.resolution.contains("Audio", ignoreCase = true) ||
                    it.label.contains("Audio", ignoreCase = true)
        }
        val videoOptions = rawQualities.filterNot {
            it.formatTag.contains("AUDIO", ignoreCase = true) ||
                    it.resolution.contains("Audio", ignoreCase = true) ||
                    it.label.contains("Audio", ignoreCase = true)
        }

        // 2. Filter out invalid/zero-length or preview/ad items
        val validVideos = videoOptions.filter { opt ->
            val u = opt.url.lowercase()
            opt.url.isNotBlank() &&
                    !opt.url.startsWith("blob:") &&
                    !opt.url.startsWith("data:") &&
                    !u.contains("doubleclick") &&
                    !u.contains("/ads/") &&
                    !u.contains("googlesyndication") &&
                    !u.contains("adnxs") &&
                    !u.contains("preroll")
        }

        // 3. Group by resolution height (e.g. 2160, 1440, 1080, 720, 480, 360, 240)
        val groupedByTier = validVideos.groupBy { it.getResolutionHeight() }
        val deduplicatedVideos = mutableListOf<VideoQualityOption>()

        for ((height, optionsInTier) in groupedByTier) {
            val mp4Candidate = optionsInTier.filter { !it.isHlsVariant && !it.formatTag.contains("HLS", ignoreCase = true) }
                .maxWithOrNull(
                    compareBy<VideoQualityOption> { if (it.estimatedSizeBytes > 0L) 1 else 0 }
                        .thenBy { it.bandwidthBps.coerceAtLeast(it.estimatedSizeBytes) }
                )
            val hlsCandidate = optionsInTier.filter { it.isHlsVariant || it.formatTag.contains("HLS", ignoreCase = true) }
                .maxWithOrNull(
                    compareBy<VideoQualityOption> { it.bandwidthBps }
                        .thenBy { it.estimatedSizeBytes }
                )

            // Prioritize direct MP4 candidate for fast, complete, high-speed single file download
            val chosen = mp4Candidate ?: hlsCandidate ?: optionsInTier.firstOrNull()
            if (chosen != null) {
                val isStreamHls = chosen.isHlsVariant || chosen.formatTag.contains("HLS", ignoreCase = true) || chosen.url.contains(".m3u8", ignoreCase = true)
                val format = if (isStreamHls) "HLS" else "MP4"
                deduplicatedVideos.add(standardizeOptionLabel(chosen, height, format))
            }
        }

        // 4. Sort finalized list in descending order of resolution (1080p -> 720p -> 480p -> 240p)
        val sortedVideos = deduplicatedVideos.sortedWith(
            compareByDescending<VideoQualityOption> { it.getResolutionHeight() }
                .thenByDescending { it.bandwidthBps }
                .thenByDescending { it.estimatedSizeBytes }
        )

        // 5. Enforce size coherence & append best audio at bottom
        val coherentVideos = ir.ali0003.downloader.browser.sniffer.HlsManifestParser.enforceSizeCoherence(sortedVideos)

        val bestAudio = audioOptions.maxByOrNull { it.estimatedSizeBytes.coerceAtLeast(it.bandwidthBps) }

        return if (bestAudio != null) {
            val audioPill = bestAudio.copy(
                label = "Audio Only (MP3)",
                formatTag = "AUDIO"
            )
            coherentVideos + audioPill
        } else {
            coherentVideos
        }
    }

    private fun standardizeOptionLabel(
        option: VideoQualityOption,
        height: Int,
        format: String
    ): VideoQualityOption {
        val tierBadge = when (height) {
            2160 -> "4K UHD"
            1440 -> "1440p 2K"
            1080 -> "1080p HD"
            720 -> "720p HD"
            480 -> "480p SD"
            360 -> "360p SD"
            240 -> "240p"
            else -> if (height > 0) "${height}p" else option.cleanResolutionBadge
        }
        val cleanRes = when (height) {
            2160 -> "3840x2160"
            1440 -> "2560x1440"
            1080 -> "1920x1080"
            720 -> "1280x720"
            480 -> "854x480"
            360 -> "640x360"
            240 -> "426x240"
            else -> option.resolution
        }
        val isStreamHls = format.contains("HLS", ignoreCase = true) || option.isHlsVariant || option.url.contains(".m3u8", ignoreCase = true)
        val cleanFormat = if (isStreamHls) "HLS" else "MP4"
        return option.copy(
            label = tierBadge,
            resolution = cleanRes,
            formatTag = cleanFormat,
            isHlsVariant = isStreamHls
        )
    }

    fun toggleSaveToVault(enabled: Boolean) {
        _saveToVault.value = enabled
    }

    fun enqueueDownload(
        mediaItem: SniffedMediaItem,
        selectedQuality: VideoQualityOption?,
        freshSessionHeaders: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val downloadUrl = selectedQuality?.url ?: mediaItem.url
            val urlLower = downloadUrl.lowercase()
            val isM3u8 = urlLower.contains(".m3u8") ||
                    (selectedQuality != null && (selectedQuality.isHlsVariant || selectedQuality.formatTag.contains("HLS", ignoreCase = true))) ||
                    (selectedQuality == null && mediaItem.isM3u8)

            val isAudio = selectedQuality?.formatTag?.contains("AUDIO", ignoreCase = true) == true ||
                    selectedQuality?.resolution?.contains("Audio", ignoreCase = true) == true
            // Determine totalBytes ONCE before download begins:
            // fixedTotalBytes = (averageBitrateBps * durationSeconds) / 8L
            // If bitrate/duration is unavailable, keep totalBytes = 0L
            val bitrateBps = selectedQuality?.bandwidthBps ?: 0L
            val durationSec = mediaItem.durationSeconds
            val totalBytes = if (isM3u8) {
                if (bitrateBps > 0L && durationSec > 0.0) {
                    ((bitrateBps * durationSec) / 8.0).toLong()
                } else {
                    0L
                }
            } else {
                selectedQuality?.estimatedSizeBytes ?: mediaItem.fileSizeBytes
            }
            val fileName = if (selectedQuality != null) {
                var base = mediaItem.cleanFileName.substringBeforeLast('.')
                if (base.endsWith(".m3u8", ignoreCase = true)) {
                    base = base.removeSuffix(".m3u8").removeSuffix(".M3U8")
                }
                val ext = when {
                    isAudio -> ".mp3"
                    isM3u8 -> ".ts"
                    else -> ".mp4"
                }
                val badge = selectedQuality.cleanResolutionBadge.replace(" ", "_")
                "${base}_$badge$ext"
            } else {
                val clean = mediaItem.cleanFileName
                if (clean.endsWith(".m3u8", ignoreCase = true)) {
                    "${clean.removeSuffix(".m3u8").removeSuffix(".M3U8")}.ts"
                } else {
                    clean
                }
            }

            // Populate headers JSON with fresh session context to eliminate 403 Forbidden
            val headersObj = try {
                JSONObject(mediaItem.headersJson)
            } catch (_: Exception) {
                JSONObject()
            }

            // 1. Inject passed freshSessionHeaders
            freshSessionHeaders.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    headersObj.put(k, v)
                }
            }

            // 2. Query CookieManager for fresh cookies right at tap-time
            try {
                val freshCookie = android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                    ?: (if (mediaItem.pageUrl.isNotBlank()) android.webkit.CookieManager.getInstance().getCookie(mediaItem.pageUrl) else null)
                if (!freshCookie.isNullOrBlank()) {
                    headersObj.put("Cookie", freshCookie)
                }
            } catch (_: Exception) {}

            // 3. Ensure Referer & Origin
            if (!headersObj.has("Referer") && mediaItem.pageUrl.isNotBlank()) {
                headersObj.put("Referer", mediaItem.pageUrl)
            }
            val refererForOrigin = headersObj.optString("Referer", mediaItem.pageUrl)
            if (!headersObj.has("Origin") && refererForOrigin.isNotBlank()) {
                try {
                    val uri = android.net.Uri.parse(refererForOrigin)
                    if (uri.scheme != null && uri.host != null) {
                        headersObj.put("Origin", "${uri.scheme}://${uri.host}")
                    }
                } catch (_: Exception) {}
            }

            // 4. Ensure User-Agent
            if (!headersObj.has("User-Agent")) {
                headersObj.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            }

            if (mediaItem.pageUrl.isNotBlank()) {
                headersObj.put("webpageUrl", mediaItem.pageUrl)
            }
            if (mediaItem.durationSeconds > 0.0) {
                headersObj.put("duration_seconds", mediaItem.durationSeconds)
            }
            if (selectedQuality != null) {
                if (!selectedQuality.renditionKey.isNullOrBlank()) {
                    headersObj.put("media3_rendition_key", selectedQuality.renditionKey)
                }
                if (selectedQuality.resolution.isNotBlank()) {
                    headersObj.put("target_resolution", selectedQuality.resolution)
                }
                if (selectedQuality.bandwidthBps > 0L) {
                    headersObj.put("target_bitrate", selectedQuality.bandwidthBps)
                }
            }

            downloadRepository.enqueueDownload(
                url = downloadUrl,
                websiteUrl = mediaItem.pageUrl,
                fileName = fileName,
                mimeType = when {
                    isAudio -> "audio/mpeg"
                    isM3u8 -> "video/mp2t"
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
