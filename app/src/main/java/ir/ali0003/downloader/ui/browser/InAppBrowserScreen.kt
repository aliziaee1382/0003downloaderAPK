package ir.ali0003.downloader.ui.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ir.ali0003.downloader.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine
import ir.ali0003.downloader.browser.viewmodel.BrowserViewModel
import ir.ali0003.downloader.data.local.WebShortcutEntity
import ir.ali0003.downloader.ui.glass.GlassBox
import ir.ali0003.downloader.ui.glass.GlassButton
import ir.ali0003.downloader.ui.glass.GlassCard
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassPreset
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.glass.ThemeMode
import ir.ali0003.downloader.ui.settings.AppSettingsBottomSheet

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserScreen(
    viewModel: BrowserViewModel,
    currentPreset: GlassPreset = GlassPreset.SUNSET_AMBER,
    themeMode: ThemeMode = ThemeMode.DARK,
    themeColorKey: String = "orange",
    onSelectPreset: (GlassPreset) -> Unit = {},
    onApplyTheme: (ThemeMode, String) -> Unit = { _, _ -> },
    onNavigateToDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val isBrowserHome by viewModel.isBrowserHome.collectAsStateWithLifecycle()
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val inputUrl by viewModel.inputUrl.collectAsStateWithLifecycle()
    val pageTitle by viewModel.pageTitle.collectAsStateWithLifecycle()
    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val isDesktopMode by viewModel.isDesktopMode.collectAsStateWithLifecycle()
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val sniffedMediaList by viewModel.sniffedMediaList.collectAsStateWithLifecycle()
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
    val saveToVault by viewModel.saveToVault.collectAsStateWithLifecycle()
    val toastMessage by viewModel.downloadToastMessage.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val isExtractingMedia by viewModel.isExtractingNativeMedia.collectAsStateWithLifecycle()
    val lastActiveUrl by viewModel.lastActiveUrl.collectAsStateWithLifecycle()
    val lastActiveTitle by viewModel.lastActiveTitle.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webViewResetKey by remember { mutableIntStateOf(0) }
    var isEditingUrl by rememberSaveable { mutableStateOf(false) }
    var showSnifferSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    // Smart Clipboard URL Sniffer State
    var detectedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var lastHandledClipboardUrl by remember { mutableStateOf<String?>(null) }
    var lastClipboardCheckTimestamp by remember { mutableStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Automatic background clipboard sniffing popup is completely disabled to prevent annoyance.
    // Users can paste URLs manually whenever they want via the search bar paste icon or the paste card.

    // Intercept Back Press: Collapse search -> WebView goBack -> Return to Home
    BackHandler(enabled = isEditingUrl || !isBrowserHome) {
        if (isEditingUrl) {
            isEditingUrl = false
        } else if (!isBrowserHome) {
            if (webViewInstance?.canGoBack() == true) {
                webViewInstance?.goBack()
            } else {
                viewModel.resetToHome()
            }
        }
    }

    // Pulsing spring animation for Sniffer FAB
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_fab")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Handle toast notification auto-dismiss
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearToastMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            if (isBrowserHome) {
                // ==========================================
                // STATE A: BROWSER HOME / PORTAL HUB
                // ==========================================
                BrowserHomePortalView(
                    shortcuts = shortcuts,
                    lastActiveUrl = lastActiveUrl,
                    lastActiveTitle = lastActiveTitle,
                    recentHistoryUrl = history.firstOrNull()?.url,
                    recentHistoryTitle = history.firstOrNull()?.title,
                    onResumePreviousSession = {
                        val restored = viewModel.restorePreviousSession()
                        webViewInstance?.loadUrl(restored)
                    },
                    onSearchOrNavigate = { query ->
                        viewModel.navigateToUrl(query)
                        webViewInstance?.loadUrl(viewModel.currentUrl.value)
                    },
                    onSaveShortcut = { slotIndex, title, url, id ->
                        viewModel.saveShortcut(slotIndex, title, url, id)
                    },
                    onDeleteShortcut = { slotIndex ->
                        viewModel.deleteShortcut(slotIndex)
                    },
                    onOpenSettings = { showSettingsSheet = true },
                    onOpenTutorial = { showTutorialDialog = true },
                    onOpenBookmarks = { showBookmarksSheet = true },
                    onOpenHistory = { showHistorySheet = true },
                    onNewTab = { viewModel.addNewTab() }
                )
            } else {
                // ==========================================
                // STATE B: ACTIVE WEB BROWSING VIEWPORT
                // ==========================================
                ActiveWebBrowsingTopBar(
                    inputUrl = inputUrl,
                    pageTitle = pageTitle,
                    isLoading = isLoading,
                    isEditingUrl = isEditingUrl,
                    isDesktopMode = isDesktopMode,
                    onToggleEditUrl = { isEditingUrl = it },
                    onUrlChange = { viewModel.onUrlInputChanged(it) },
                    onNavigate = { url ->
                        isEditingUrl = false
                        viewModel.navigateToUrl(url)
                        webViewInstance?.loadUrl(viewModel.currentUrl.value)
                    },
                    onBack = {
                        if (webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                        } else {
                            viewModel.resetToHome()
                        }
                    },
                    onRefresh = { webViewInstance?.reload() },
                    onStop = { webViewInstance?.stopLoading() },
                    onToggleDesktop = {
                        viewModel.toggleDesktopMode()
                        webViewInstance?.settings?.userAgentString =
                            if (!isDesktopMode) DESKTOP_USER_AGENT else null
                        webViewInstance?.reload()
                    },
                    onNewTab = { viewModel.addNewTab() },
                    onShareLink = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video Link"))
                    },
                    onCopyLink = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onAddBookmark = {
                        viewModel.addBookmark(pageTitle, currentUrl)
                    },
                    onOpenBookmarks = { showBookmarksSheet = true },
                    onOpenHistory = { showHistorySheet = true },
                    onOpenSettings = { showSettingsSheet = true }
                )

                // Progress Bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = GlassTheme.colors.accentGlow,
                        trackColor = GlassTheme.colors.surfaceGlassSubtle
                    )
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                // WebView Edge-to-Edge Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    key(webViewResetKey) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
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
                                    }

                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    addJavascriptInterface(
                                        viewModel.snifferEngine.VideoSnifferBridge(this),
                                        VideoSnifferEngine.JS_BRIDGE_NAME
                                    )

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            return viewModel.snifferEngine.shouldInterceptRequest(view, request)
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            if (url != null) {
                                                viewModel.onPageStarted(url)
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (url != null) {
                                                viewModel.onPageFinished(
                                                    url = url,
                                                    title = view?.title,
                                                    canBack = view?.canGoBack() ?: false,
                                                    canForward = view?.canGoForward() ?: false
                                                )
                                                try {
                                                    view?.evaluateJavascript(VideoSnifferEngine.DOM_SNIFFER_JS, null)
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
                                            val didCrash = detail?.didCrash() ?: true
                                            android.util.Log.e("InAppBrowser", "onRenderProcessGone detected (crashed: $didCrash)")
                                            try {
                                                view?.let {
                                                    it.stopLoading()
                                                    (it.parent as? ViewGroup)?.removeView(it)
                                                    it.destroy()
                                                }
                                            } catch (_: Exception) {}
                                            webViewInstance = null
                                            // Safely trigger recreation of WebView
                                            webViewResetKey++
                                            return true
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            viewModel.onProgressChanged(newProgress)
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            if (!title.isNullOrBlank()) {
                                                viewModel.onPageFinished(
                                                    url = view?.url ?: "",
                                                    title = title,
                                                    canBack = view?.canGoBack() ?: false,
                                                    canForward = view?.canGoForward() ?: false
                                                )
                                            }
                                        }
                                    }

                                    if (currentUrl.isNotBlank()) {
                                        loadUrl(currentUrl)
                                    }
                                    webViewInstance = this
                                }
                            },
                            update = { view ->
                                try {
                                    if (currentUrl.isNotBlank() && view.url != currentUrl) {
                                        view.loadUrl(currentUrl)
                                    }
                                } catch (_: Exception) {}
                            },
                            onRelease = { view ->
                                try {
                                    view.stopLoading()
                                    view.clearHistory()
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    view.destroy()
                                } catch (_: Exception) {}
                                if (webViewInstance == view) {
                                    webViewInstance = null
                                }
                            }
                        )
                    }

                    // Sniffed Media Floating Action Button
                    if (sniffedMediaList.isNotEmpty() || isExtractingMedia) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                        ) {
                            SniffedMediaFab(
                                count = sniffedMediaList.size,
                                scale = pulseScale,
                                isExtracting = isExtractingMedia && sniffedMediaList.isEmpty(),
                                onClick = {
                                    showSnifferSheet = true
                                }
                            )
                        }
                    }
                }

                // Dedicated 5-Action Bottom Web Toolbar
                DedicatedWebBottomToolbar(
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    tabCount = tabCount,
                    downloadCount = sniffedMediaList.size,
                    onBack = {
                        if (webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                        } else {
                            viewModel.resetToHome()
                        }
                    },
                    onForward = { webViewInstance?.goForward() },
                    onHome = { viewModel.resetToHome() },
                    onDownloads = onNavigateToDownloads,
                    onTabsClick = { showBookmarksSheet = true }
                )
            }
        }

        // Two-Step Media Sniffer Bottom Sheet (Source Selector -> Quality & Format Selector)
        if (showSnifferSheet) {
            MediaSnifferBottomSheet(
                sniffedMediaList = sniffedMediaList,
                initialSelectedItem = selectedMedia,
                isExtracting = isExtractingMedia,
                saveToVault = saveToVault,
                onToggleSaveToVault = { viewModel.toggleSaveToVault(it) },
                onDismiss = {
                    showSnifferSheet = false
                    viewModel.selectMedia(null)
                },
                onConfirmDownload = { item, quality ->
                    val webUrl = webViewInstance?.url ?: currentUrl
                    val webUa = webViewInstance?.settings?.userAgentString
                    val targetUrl = quality?.url ?: item.url
                    val freshCookies = try {
                        android.webkit.CookieManager.getInstance().getCookie(targetUrl)
                            ?: (if (webUrl.isNotBlank()) android.webkit.CookieManager.getInstance().getCookie(webUrl) else null)
                    } catch (_: Exception) { null }

                    val freshHeaders = mutableMapOf<String, String>()
                    if (!freshCookies.isNullOrBlank()) {
                        freshHeaders["Cookie"] = freshCookies
                    }
                    if (!webUa.isNullOrBlank()) {
                        freshHeaders["User-Agent"] = webUa
                    }
                    val effectiveReferer = when {
                        webUrl.isNotBlank() -> webUrl
                        item.pageUrl.isNotBlank() -> item.pageUrl
                        else -> targetUrl
                    }
                    if (effectiveReferer.isNotBlank()) {
                        freshHeaders["Referer"] = effectiveReferer
                        try {
                            val uri = android.net.Uri.parse(effectiveReferer)
                            if (uri.scheme != null && uri.host != null) {
                                freshHeaders["Origin"] = "${uri.scheme}://${uri.host}"
                            }
                        } catch (_: Exception) {}
                    }

                    viewModel.enqueueDownload(item, quality, freshHeaders)
                    showSnifferSheet = false
                    viewModel.selectMedia(null)
                }
            )
        }

        // Settings Bottom Sheet
        if (showSettingsSheet) {
            AppSettingsBottomSheet(
                currentPreset = currentPreset,
                themeMode = themeMode,
                themeColorKey = themeColorKey,
                onSelectPreset = onSelectPreset,
                onApplyTheme = onApplyTheme,
                onDismiss = { showSettingsSheet = false },
                onClearBrowserData = {
                    webViewInstance?.clearCache(true)
                    webViewInstance?.clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)
                    viewModel.clearHistory()
                    try {
                        val jsCacheDir = java.io.File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                        if (!jsCacheDir.exists()) jsCacheDir.mkdirs()
                    } catch (_: Exception) {}
                }
            )
        }

        // "How to Download" Tutorial Dialog
        if (showTutorialDialog) {
            HowToDownloadTutorialDialog(onDismiss = { showTutorialDialog = false })
        }

        // Bookmarks Modal Sheet
        if (showBookmarksSheet) {
            BookmarksBottomSheet(
                bookmarks = bookmarks,
                onSelect = { url ->
                    showBookmarksSheet = false
                    viewModel.navigateToUrl(url)
                    webViewInstance?.loadUrl(url)
                },
                onDelete = { url -> viewModel.removeBookmark(url) },
                onDismiss = { showBookmarksSheet = false }
            )
        }

        // History Modal Sheet
        if (showHistorySheet) {
            HistoryBottomSheet(
                history = history,
                onSelect = { url ->
                    showHistorySheet = false
                    viewModel.navigateToUrl(url)
                    webViewInstance?.loadUrl(url)
                },
                onClearAll = { viewModel.clearHistory() },
                onDismiss = { showHistorySheet = false }
            )
        }

        // Floating Status Toast
        AnimatedVisibility(
            visible = toastMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            if (toastMessage != null) {
                GlassBox(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.95f),
                    borderColor = GlassTheme.colors.accentGlow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = toastMessage ?: "",
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Floating Clipboard URL Sniffer Card (Disabled)
        AnimatedVisibility(
            visible = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            detectedClipboardUrl?.let { url ->
                ClipboardSnifferFloatingCard(
                    url = url,
                    onDismiss = {
                        lastHandledClipboardUrl = url
                        detectedClipboardUrl = null
                    },
                    onDownload = {
                        lastHandledClipboardUrl = url
                        detectedClipboardUrl = null
                        viewModel.navigateToUrl(url)
                        webViewInstance?.loadUrl(viewModel.currentUrl.value)
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
}

/**
 * STATE A: Browser Home / Portal Hub View
 */
@Composable
fun BrowserHomePortalView(
    shortcuts: List<WebShortcutEntity>,
    lastActiveUrl: String?,
    lastActiveTitle: String?,
    recentHistoryUrl: String?,
    recentHistoryTitle: String?,
    onResumePreviousSession: () -> Unit,
    onSearchOrNavigate: (String) -> Unit,
    onSaveShortcut: (slotIndex: Int, title: String, url: String, id: Long) -> Unit,
    onDeleteShortcut: (slotIndex: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewTab: () -> Unit
) {
    var searchInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // Session resumption details
    val targetSessionUrl = lastActiveUrl?.takeIf { it.isNotBlank() } ?: recentHistoryUrl
    val targetSessionTitle = lastActiveTitle?.takeIf { it.isNotBlank() }
        ?: recentHistoryTitle?.takeIf { it.isNotBlank() }
        ?: targetSessionUrl?.let { url ->
            try {
                val host = java.net.URI(url).host
                if (!host.isNullOrBlank()) host.removePrefix("www.").removePrefix("m.") else url
            } catch (_: Exception) {
                url
            }
        } ?: "صفحه قبل"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Portal Top Bar: Title "Downloader", Help, Settings, 3-Dots Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                        .border(1.2.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo),
                        contentDescription = "0003 Downloader Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Column {
                    Text(
                        text = "0003 Downloader",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "High-Speed Video Sniffer & Vault",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Help/FAQ Button
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    onClick = onOpenTutorial,
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentDescription = "How to Download"
                )

                // Settings Button
                GlassIconButton(
                    icon = Icons.Default.Settings,
                    onClick = onOpenSettings,
                    size = 36.dp,
                    iconSize = 18.dp,
                    tint = GlassTheme.colors.accentGlow,
                    contentDescription = "Settings"
                )

                // 3-Dots Menu Button
                Box {
                    GlassIconButton(
                        icon = Icons.Default.MoreVert,
                        onClick = { showMenu = true },
                        size = 36.dp,
                        iconSize = 18.dp,
                        contentDescription = "More Options"
                    )

                    BrowserDropdownMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        isDesktopMode = false,
                        showPageSpecificActions = false,
                        onNewTab = onNewTab,
                        onShare = {},
                        onCopyLink = {},
                        onAddBookmark = {},
                        onBookmarks = onOpenBookmarks,
                        onHistory = onOpenHistory,
                        onToggleDesktop = {},
                        onSettings = onOpenSettings
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search / URL Input Box
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(27.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.95f),
            borderColor = GlassTheme.colors.accentGlow.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(20.dp)
                )

                BasicTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("browser_portal_search_input"),
                    textStyle = TextStyle(
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(GlassTheme.colors.accentGlow),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (searchInput.isNotBlank()) {
                                onSearchOrNavigate(searchInput)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchInput.isEmpty()) {
                                Text(
                                    text = "Search Google or type URL",
                                    color = GlassTheme.colors.textMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (searchInput.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = GlassTheme.colors.textMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { searchInput = "" }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste from clipboard",
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable {
                                try {
                                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                    if (!text.isNullOrBlank()) {
                                        searchInput = text
                                        onSearchOrNavigate(text)
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {}
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.accentGlow)
                        .clickable {
                            if (searchInput.isNotBlank()) {
                                onSearchOrNavigate(searchInput)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                        .testTag("browser_portal_go_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Smart Paste Video Link Card
        SmartPasteCard(
            onPasteAndGo = { url ->
                onSearchOrNavigate(url)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Access Platforms Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK ACCESS",
                color = GlassTheme.colors.accentGlow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Text(
                text = "${shortcuts.size}/12 Slots",
                color = GlassTheme.colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic 12-Slot Grid of Quick Platforms
        QuickAccessShortcutsGrid(
            shortcuts = shortcuts,
            onNavigate = onSearchOrNavigate,
            onSaveShortcut = onSaveShortcut,
            onDeleteShortcut = onDeleteShortcut
        )

        Spacer(modifier = Modifier.height(18.dp))

        // "Continue Previous Session" / "ادامه مرور قبلی" Action Card
        GlassCard(
            onClick = onResumePreviousSession,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("continue_previous_session_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    GlassTheme.colors.accentGlow.copy(alpha = 0.35f),
                                    GlassTheme.colors.accentGlow.copy(alpha = 0.12f)
                                )
                            )
                        )
                        .border(1.2.dp, GlassTheme.colors.accentGlow, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "ادامه مرور قبلی",
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ادامه مرور قبلی",
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GlassTheme.colors.accentGlow.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Resume",
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (!targetSessionUrl.isNullOrBlank()) {
                            "بازگشت به: $targetSessionTitle"
                        } else {
                            "بازآوری آخرین صفحه یا جستجوی در حال مرور"
                        },
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.surfaceGlassSubtle)
                        .border(0.8.dp, GlassTheme.colors.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * STATE B: Active Web Browsing Top Bar
 */
@Composable
fun ActiveWebBrowsingTopBar(
    inputUrl: String,
    pageTitle: String?,
    isLoading: Boolean,
    isEditingUrl: Boolean,
    isDesktopMode: Boolean,
    onToggleEditUrl: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onToggleDesktop: () -> Unit,
    onNewTab: () -> Unit,
    onShareLink: () -> Unit,
    onCopyLink: () -> Unit,
    onAddBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var textState by remember {
        mutableStateOf(
            TextFieldValue(
                text = inputUrl,
                selection = TextRange(0, inputUrl.length)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Request focus and show keyboard when entering URL editing mode
    LaunchedEffect(isEditingUrl) {
        if (isEditingUrl) {
            textState = TextFieldValue(
                text = inputUrl,
                selection = TextRange(0, inputUrl.length)
            )
            delay(50)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val displayHost = remember(inputUrl, pageTitle) {
        try {
            if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
                val uri = java.net.URI(inputUrl)
                uri.host ?: inputUrl
            } else if (inputUrl.isNotBlank()) {
                inputUrl
            } else {
                "Search Google or type URL"
            }
        } catch (e: Exception) {
            if (!pageTitle.isNullOrBlank()) pageTitle else inputUrl
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        if (!isEditingUrl) {
            // Normal Collapsed Browsing Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Back Button
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentDescription = "Back"
                )

                // Compact Address Pill
                GlassBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .clickable {
                            onToggleEditUrl(true)
                        },
                    shape = RoundedCornerShape(19.dp),
                    backgroundColor = GlassTheme.colors.surfaceGlass.copy(alpha = 0.6f),
                    borderColor = GlassTheme.colors.glassBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (inputUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                            contentDescription = null,
                            tint = if (inputUrl.startsWith("https")) GlassTheme.colors.accentGlow else GlassTheme.colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )

                        Text(
                            text = displayHost,
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isLoading) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stop",
                                tint = GlassTheme.colors.textSecondary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onStop() }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = GlassTheme.colors.textSecondary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onRefresh() }
                            )
                        }
                    }
                }

                // 3-Dots Dropdown Menu
                Box {
                    GlassIconButton(
                        icon = Icons.Default.MoreVert,
                        onClick = { showMenu = true },
                        size = 36.dp,
                        iconSize = 18.dp,
                        contentDescription = "More Options"
                    )

                    BrowserDropdownMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        isDesktopMode = isDesktopMode,
                        showPageSpecificActions = true,
                        onNewTab = onNewTab,
                        onShare = onShareLink,
                        onCopyLink = onCopyLink,
                        onAddBookmark = onAddBookmark,
                        onBookmarks = onOpenBookmarks,
                        onHistory = onOpenHistory,
                        onToggleDesktop = onToggleDesktop,
                        onSettings = onOpenSettings
                    )
                }
            }
        } else {
            // Dynamic Expanded Full-Width Address Bar (Active Editing Mode)
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
                borderColor = GlassTheme.colors.accentGlow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                onToggleEditUrl(false)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel Edit",
                            tint = GlassTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    BasicTextField(
                        value = textState,
                        onValueChange = {
                            textState = it
                            onUrlChange(it.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .testTag("browser_address_input"),
                        textStyle = TextStyle(
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(GlassTheme.colors.accentGlow),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val url = textState.text.trim()
                                if (url.isNotBlank()) {
                                    onNavigate(url)
                                    onToggleEditUrl(false)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (textState.text.isEmpty()) {
                                    Text(
                                        text = "Search Google or type URL...",
                                        color = GlassTheme.colors.textMuted,
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (textState.text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                            .clickable {
                                textState = TextFieldValue("")
                                onUrlChange("")
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear input",
                                tint = GlassTheme.colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassTheme.colors.accentGlow)
                            .clickable {
                                val url = textState.text.trim()
                                if (url.isNotBlank()) {
                                    onNavigate(url)
                                    onToggleEditUrl(false)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            }
                            .testTag("browser_navigate_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Navigate",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated 5-Action Web Bottom Toolbar
 */
@Composable
fun DedicatedWebBottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    downloadCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onDownloads: () -> Unit,
    onTabsClick: () -> Unit
) {
    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.94f),
        borderColor = GlassTheme.colors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 1. Back
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                enabled = canGoBack,
                size = 40.dp,
                iconSize = 20.dp,
                tint = if (canGoBack) GlassTheme.colors.textPrimary else GlassTheme.colors.textMuted.copy(alpha = 0.5f),
                contentDescription = "Web Back"
            )

            // 2. Forward
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = onForward,
                enabled = canGoForward,
                size = 40.dp,
                iconSize = 20.dp,
                tint = if (canGoForward) GlassTheme.colors.textPrimary else GlassTheme.colors.textMuted.copy(alpha = 0.5f),
                contentDescription = "Web Forward"
            )

            // 3. Home
            GlassIconButton(
                icon = Icons.Default.Home,
                onClick = onHome,
                size = 40.dp,
                iconSize = 22.dp,
                tint = GlassTheme.colors.accentGlow,
                contentDescription = "Browser Home"
            )

            // 4. Downloads Shortcut
            Box(contentAlignment = Alignment.Center) {
                GlassIconButton(
                    icon = Icons.Default.Download,
                    onClick = onDownloads,
                    size = 40.dp,
                    iconSize = 20.dp,
                    contentDescription = "Downloads Queue"
                )
                if (downloadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GlassTheme.colors.accentGlow)
                    )
                }
            }

            // 5. Tabs Counter
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.5.dp, GlassTheme.colors.textPrimary, RoundedCornerShape(6.dp))
                    .clickable(onClick = onTabsClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$tabCount",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * 3-Dots Dropdown Menu
 */
@Composable
fun BrowserDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isDesktopMode: Boolean,
    showPageSpecificActions: Boolean,
    onNewTab: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onAddBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onToggleDesktop: () -> Unit,
    onSettings: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(220.dp)
            .background(GlassTheme.colors.cardBackground)
            .border(1.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(12.dp))
    ) {
        DropdownMenuItem(
            text = { Text("New Tab", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.AddBox,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onNewTab()
            }
        )

        if (showPageSpecificActions) {
            DropdownMenuItem(
                text = { Text("Share Link", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = GlassTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            DropdownMenuItem(
                text = { Text("Copy Link", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = GlassTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onCopyLink()
                }
            )

            DropdownMenuItem(
                text = { Text("Add Bookmark", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onAddBookmark()
                }
            )
        }

        DropdownMenuItem(
            text = { Text("Bookmarks", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = GlassTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onBookmarks()
            }
        )

        DropdownMenuItem(
            text = { Text("History", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = GlassTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onHistory()
            }
        )

        if (showPageSpecificActions) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = GlassTheme.colors.glassBorder
            )

            DropdownMenuItem(
                text = { Text("Desktop Site", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = if (isDesktopMode) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Checkbox(
                        checked = isDesktopMode,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = GlassTheme.colors.accentGlow,
                            checkmarkColor = Color.Black
                        )
                    )
                },
                onClick = {
                    onDismiss()
                    onToggleDesktop()
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = GlassTheme.colors.glassBorder
        )

        DropdownMenuItem(
            text = { Text("Settings", color = GlassTheme.colors.textPrimary, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = GlassTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onSettings()
            }
        )
    }
}

/**
 * Sniffed Media Floating Action Button
 */
@Composable
fun SniffedMediaFab(
    count: Int,
    scale: Float,
    isExtracting: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .scale(scale)
            .size(62.dp)
            .clickable(onClick = onClick)
            .testTag("sniffed_media_fab"),
        contentAlignment = Alignment.Center
    ) {
        // Main circular FAB
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            GlassTheme.colors.accentGlow,
                            GlassTheme.colors.secondaryGlow
                        )
                    )
                )
                .border(1.8.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isExtracting && count == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Sniffed Media Detected ($count)",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Top-End Anchored 24.dp Badge
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.accentColor)
                    .border(
                        width = 1.5.dp,
                        color = GlassTheme.colors.glassBorderHighlight,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 99) "99+" else "$count",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Resolution Picker Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoResolutionPickerBottomSheet(
    mediaItem: SniffedMediaItem,
    saveToVault: Boolean,
    onToggleSaveToVault: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmDownload: (SniffedMediaItem, VideoQualityOption?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedQuality by remember {
        mutableStateOf(mediaItem.qualities.firstOrNull())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
            borderColor = GlassTheme.colors.accentGlow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = GlassTheme.colors.accentGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Download Video",
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    GlassIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 16.dp,
                        contentDescription = "Close"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Video Title Card
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                    borderColor = GlassTheme.colors.glassBorder
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = mediaItem.displayTitle,
                            color = GlassTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (mediaItem.isM3u8) "HLS Stream (m3u8)" else mediaItem.mimeType,
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (mediaItem.fileSizeBytes > 0) {
                                Text(
                                    text = "• ${VideoQualityOption.formatFileSize(mediaItem.fileSizeBytes)}",
                                    color = GlassTheme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Resolution Options
                if (mediaItem.qualities.isNotEmpty()) {
                    Text(
                        text = "SELECT QUALITY",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        mediaItem.qualities.forEach { quality ->
                            val isSelected = selectedQuality?.url == quality.url || (selectedQuality == null && quality == mediaItem.qualities.first())
                            GlassBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedQuality = quality },
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = if (isSelected) GlassTheme.colors.accentGlow.copy(alpha = 0.15f) else GlassTheme.colors.surfaceGlassSubtle,
                                borderColor = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = quality.label,
                                            color = if (isSelected) GlassTheme.colors.accentGlow else GlassTheme.colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (quality.resolution.isNotBlank()) {
                                            Text(
                                                text = "(${quality.resolution})",
                                                color = GlassTheme.colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = GlassTheme.colors.accentGlow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Save to Vault Toggle
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                    borderColor = GlassTheme.colors.glassBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (saveToVault) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Save to Secure Vault",
                                    color = GlassTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "AES-256 encrypted • Hidden from gallery",
                                    color = GlassTheme.colors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Checkbox(
                            checked = saveToVault,
                            onCheckedChange = onToggleSaveToVault,
                            colors = CheckboxDefaults.colors(
                                checkedColor = GlassTheme.colors.accentGlow,
                                checkmarkColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Download Button
                GlassButton(
                    text = "Start High-Speed Download",
                    onClick = {
                        onConfirmDownload(mediaItem, selectedQuality)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * "How to Download" Tutorial Modal Dialog
 */
@Composable
fun HowToDownloadTutorialDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
            borderColor = GlassTheme.colors.accentGlow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                        .border(1.5.dp, GlassTheme.colors.accentGlow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GlassTheme.colors.accentGlow,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "How to Download Videos",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(18.dp))

                TutorialStepRow(
                    stepNumber = "1",
                    title = "Search or Browse",
                    description = "Enter any video site link or search for videos using the portal shortcuts."
                )

                Spacer(modifier = Modifier.height(14.dp))

                TutorialStepRow(
                    stepNumber = "2",
                    title = "Play the Video",
                    description = "Start video playback so our high-speed engine can sniff the video stream source."
                )

                Spacer(modifier = Modifier.height(14.dp))

                TutorialStepRow(
                    stepNumber = "3",
                    title = "Tap Download Button",
                    description = "Tap the glowing floating download button to select quality (1080p, 720p, etc.) and save to public storage or private vault."
                )

                Spacer(modifier = Modifier.height(24.dp))

                GlassButton(
                    text = "Got It!",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TutorialStepRow(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(GlassTheme.colors.accentGlow),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = GlassTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = GlassTheme.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Bookmarks Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksBottomSheet(
    bookmarks: List<ir.ali0003.downloader.browser.viewmodel.BrowserBookmark>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.96f),
            borderColor = GlassTheme.colors.glassBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bookmarks",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    GlassIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 16.dp,
                        contentDescription = "Close"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (bookmarks.isEmpty()) {
                    Text(
                        text = "No saved bookmarks yet.",
                        color = GlassTheme.colors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bookmarks.forEach { bookmark ->
                            GlassBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelect(bookmark.url) },
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                                borderColor = GlassTheme.colors.glassBorder
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bookmark.title,
                                            color = GlassTheme.colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = bookmark.url,
                                            color = GlassTheme.colors.textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = GlassTheme.colors.textMuted,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onDelete(bookmark.url) }
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

/**
 * History Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    history: List<ir.ali0003.downloader.browser.viewmodel.BrowserHistoryEntry>,
    onSelect: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.96f),
            borderColor = GlassTheme.colors.glassBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browsing History",
                        color = GlassTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (history.isNotEmpty()) {
                            Text(
                                text = "Clear All",
                                color = GlassTheme.colors.accentGlow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onClearAll() }
                            )
                        }
                        GlassIconButton(
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            size = 32.dp,
                            iconSize = 16.dp,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (history.isEmpty()) {
                    Text(
                        text = "No browsing history.",
                        color = GlassTheme.colors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        history.forEach { entry ->
                            GlassBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelect(entry.url) },
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = GlassTheme.colors.surfaceGlassSubtle,
                                borderColor = GlassTheme.colors.glassBorder
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = entry.title,
                                        color = GlassTheme.colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = entry.url,
                                        color = GlassTheme.colors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

/**
 * Smart Paste Video Link Card on Browser Home Portal:
 * Inspects clipboard on load, previews copied link if present, and triggers instant paste and download.
 */
@Composable
fun SmartPasteCard(
    onPasteAndGo: (String) -> Unit
) {
    val context = LocalContext.current
    var clipboardPreview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
            if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://"))) {
                clipboardPreview = text
            }
        } catch (_: Exception) {}
    }

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        onPasteAndGo(text)
                    } else {
                        Toast.makeText(context, "Clipboard is empty. Copy a link first!", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {}
            },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.88f),
        borderColor = if (clipboardPreview != null) GlassTheme.colors.accentGlow else GlassTheme.colors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.accentGlow.copy(alpha = 0.18f))
                    .border(0.8.dp, GlassTheme.colors.accentGlow.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (clipboardPreview != null) "Paste Copied Link & Download" else "Paste Video Link Directly",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (clipboardPreview != null) clipboardPreview!! else "Supports Instagram, YouTube, Aparat, TikTok, Twitter...",
                    color = if (clipboardPreview != null) GlassTheme.colors.accentGlow else GlassTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassTheme.colors.accentGlow)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PASTE",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Floating Smart Clipboard Link Sniffer Dialog / Banner:
 * Appears when returning to app or opening with a valid video / web link copied on the clipboard.
 */
@Composable
fun ClipboardSnifferFloatingCard(
    url: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val cleanHost = try {
        val uri = java.net.URI(url)
        val host = uri.host ?: ""
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (_: Exception) {
        url.substringAfter("://").substringBefore("/").removePrefix("www.")
    }

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clipboard_sniffer_dialog"),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = GlassTheme.colors.cardBackground.copy(alpha = 0.98f),
        borderColor = GlassTheme.colors.accentGlow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                            .border(0.8.dp, GlassTheme.colors.accentGlow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = GlassTheme.colors.accentGlow,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "COPIED LINK DETECTED",
                            color = GlassTheme.colors.accentGlow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        if (cleanHost.isNotBlank()) {
                            Text(
                                text = cleanHost,
                                color = GlassTheme.colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = GlassTheme.colors.textMuted,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // URL Preview snippet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassTheme.colors.surfaceGlass)
                    .border(0.6.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = url,
                    color = GlassTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dismiss Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassTheme.colors.surfaceGlass)
                        .border(0.8.dp, GlassTheme.colors.glassBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dismiss",
                        color = GlassTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Download / Inspect Button
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassTheme.colors.accentGlow)
                        .clickable(onClick = onDownload)
                        .testTag("clipboard_download_action"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Download / Open",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
