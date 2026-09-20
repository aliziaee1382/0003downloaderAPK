package ir.ali0003.downloader.ui.navigation

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ali0003.downloader.browser.viewmodel.BrowserViewModel
import ir.ali0003.downloader.util.BiometricHelper
import ir.ali0003.downloader.ui.browser.InAppBrowserScreen
import ir.ali0003.downloader.ui.glass.GlassBottomNavBar
import ir.ali0003.downloader.ui.glass.GlassIconButton
import ir.ali0003.downloader.ui.glass.GlassOrbBackground
import ir.ali0003.downloader.ui.glass.GlassPreset
import ir.ali0003.downloader.ui.glass.GlassTheme
import ir.ali0003.downloader.ui.glass.ThemeMode
import ir.ali0003.downloader.ui.player.InAppVideoPlayerSheet
import ir.ali0003.downloader.ui.screens.ActiveDownloadsScreen
import ir.ali0003.downloader.ui.screens.CompletedDownloadsScreen
import ir.ali0003.downloader.ui.settings.AppSettingsBottomSheet
import ir.ali0003.downloader.ui.settings.ThemeSelectorDialog
import ir.ali0003.downloader.ui.vault.VaultLockpadDialog
import ir.ali0003.downloader.ui.viewmodel.MainViewModel

/**
 * Master Navigation Shell connecting the 3 Core Tabs:
 * 1. Browser (Dedicated Portal Hub / Active In-App Browser)
 * 2. Downloading (Active Multi-Thread Chunk Downloader Queue)
 * 3. Library (Completed Media & AES-256 Vault Access)
 */
@Composable
fun MainAppNavigation(
    viewModel: MainViewModel,
    browserViewModel: BrowserViewModel,
    onReEnforceImmersive: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentPreset by viewModel.currentPreset.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeColorKey by viewModel.themeColorKey.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    // Re-enforce immersive mode whenever the user switches navigation tabs
    LaunchedEffect(selectedTab) {
        onReEnforceImmersive()
    }

    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val vaultDownloads by viewModel.vaultDownloads.collectAsStateWithLifecycle()
    val taskProgressMap by viewModel.taskProgressMap.collectAsStateWithLifecycle()
    val sniffedMediaList by browserViewModel.sniffedMediaList.collectAsStateWithLifecycle()
    val isBrowserHome by browserViewModel.isBrowserHome.collectAsStateWithLifecycle()

    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val showVaultAuth by viewModel.showVaultAuthDialog.collectAsStateWithLifecycle()
    val pinInput by viewModel.pinInput.collectAsStateWithLifecycle()
    val pinError by viewModel.pinErrorMessage.collectAsStateWithLifecycle()
    val selectedPlayerTask by viewModel.selectedPlayerTask.collectAsStateWithLifecycle()

    // Multi-segment thread count states
    val threadCount by viewModel.threadCount.collectAsStateWithLifecycle()
    val isMultiSegmentEnabled by viewModel.isMultiSegmentEnabled.collectAsStateWithLifecycle()

    val isBiometricSupported = remember(context) { BiometricHelper.canAuthenticate(context) }
    val isBiometricRegistered = viewModel.isBiometricRegistered

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Dynamic 2-Tier Theme Resolution (Mode + Accent Color with System Dark Mode awareness)
    val isSystemDark = isSystemInDarkTheme()
    val isLight = when (themeMode) {
        ThemeMode.LIGHT -> true
        ThemeMode.DARK -> false
        ThemeMode.AUTO -> !isSystemDark
    }
    val activeThemeColors = GlassTheme.getThemeForModeAndColor(isLight = isLight, colorKey = themeColorKey)

    GlassTheme(colors = activeThemeColors) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dynamic Floating Glass Orbs Background
            GlassOrbBackground(
                modifier = Modifier.fillMaxSize(),
                theme = activeThemeColors
            )

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    // Show global brand header only on Downloading (1) and Library (2) tabs
                    if (selectedTab != 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                            AppHeaderBar(
                                currentPreset = currentPreset,
                                isVaultUnlocked = isVaultUnlocked,
                                onOpenSettings = { showSettingsSheet = true }
                            )
                        }
                    } else {
                        // Keep status bar padding for browser screen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        )
                    }
                },
                bottomBar = {
                    // Show global bottom nav on tabs 1, 2 or when Browser is on Home Portal Hub (State A)
                    if (selectedTab != 0 || isBrowserHome) {
                        GlassBottomNavBar(
                            selectedTab = selectedTab,
                            onSelectTab = {
                                viewModel.setSelectedTab(it)
                                onReEnforceImmersive()
                            },
                            browserCount = sniffedMediaList.size,
                            activeCount = activeDownloads.size,
                            libraryCount = completedDownloads.size
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (selectedTab) {
                        0 -> InAppBrowserScreen(
                            viewModel = browserViewModel,
                            currentPreset = currentPreset,
                            themeMode = themeMode,
                            themeColorKey = themeColorKey,
                            onSelectPreset = { viewModel.setPreset(it) },
                            onApplyTheme = { mode, colorKey -> viewModel.applyTheme(mode, colorKey) },
                            onNavigateToDownloads = { viewModel.setSelectedTab(1) }
                        )
                        1 -> ActiveDownloadsScreen(
                            downloads = activeDownloads,
                            progressMap = taskProgressMap,
                            onAddDownload = { viewModel.addDemoDownload(isHidden = false) },
                            onEnqueueDirect = { url, isHidden ->
                                viewModel.enqueueManualDownload(url = url, isHidden = isHidden)
                            },
                            onInspectInBrowser = { url, isHidden ->
                                browserViewModel.toggleSaveToVault(isHidden)
                                viewModel.setSelectedTab(0)
                                browserViewModel.navigateToUrl(url)
                            },
                            onTogglePause = { viewModel.togglePause(it) },
                            onToggleVault = { viewModel.toggleVaultHidden(it) },
                            onDelete = { viewModel.deleteDownload(it) },
                            onSimulate = { viewModel.simulateProgress() }
                        )
                        2 -> CompletedDownloadsScreen(
                            downloads = completedDownloads,
                            vaultDownloads = vaultDownloads,
                            isVaultUnlocked = isVaultUnlocked,
                            onOpenVaultAuth = { viewModel.openVaultAuthDialog() },
                            onLockVault = { viewModel.lockVault() },
                            onPlayVideo = { viewModel.openVideoPlayer(it) },
                            onToggleVault = { viewModel.toggleVaultHidden(it) },
                            onDelete = { viewModel.deleteDownload(it) },
                            vaultFileManager = viewModel.vaultFileManager
                        )
                    }
                }
            }

            // Global Settings Bottom Sheet
            if (showSettingsSheet) {
                AppSettingsBottomSheet(
                    currentPreset = currentPreset,
                    themeMode = themeMode,
                    themeColorKey = themeColorKey,
                    threadCount = threadCount,
                    isMultiSegmentEnabled = isMultiSegmentEnabled,
                    onThreadCountChange = { count ->
                        viewModel.setThreadCount(count)
                    },
                    onMultiSegmentToggle = { enabled ->
                        viewModel.setMultiSegmentEnabled(enabled)
                    },
                    onSelectPreset = {
                        viewModel.setPreset(it)
                    },
                    onApplyTheme = { mode, colorKey ->
                        viewModel.applyTheme(mode, colorKey)
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }

            // Standalone 2-Tier Theme Dialog
            if (showThemeDialog) {
                ThemeSelectorDialog(
                    currentMode = themeMode,
                    currentColorKey = themeColorKey,
                    onApplyTheme = { mode, colorKey ->
                        viewModel.applyTheme(mode, colorKey)
                    },
                    onDismiss = { showThemeDialog = false }
                )
            }

            // In-App Video Player Sheet
            selectedPlayerTask?.let { task ->
                InAppVideoPlayerSheet(
                    task = task,
                    vaultFileManager = viewModel.vaultFileManager,
                    onDismiss = { viewModel.closeVideoPlayer() }
                )
            }

            // Vault PIN & Biometric Lockpad Modal
            if (showVaultAuth) {
                VaultLockpadDialog(
                    isConfigured = viewModel.isVaultConfigured,
                    isBiometricRegistered = isBiometricRegistered,
                    isBiometricSupported = isBiometricSupported,
                    pinInput = pinInput,
                    pinError = pinError,
                    onDigitClick = { viewModel.onPinDigit(it) },
                    onBackspaceClick = { viewModel.onPinBackspace() },
                    onBiometricClick = {
                        launchBiometricPrompt(
                            context = context,
                            isFirstTimeRegistration = !isBiometricRegistered
                        ) { success ->
                            if (success) {
                                if (!isBiometricRegistered) {
                                    viewModel.registerBiometricAndUnlock()
                                } else {
                                    viewModel.unlockWithBiometrics()
                                }
                            }
                        }
                    },
                    onDismiss = { viewModel.closeVaultAuthDialog() }
                )
            }
        }
    }
}

@Composable
fun AppHeaderBar(
    currentPreset: GlassPreset,
    isVaultUnlocked: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.colors.accentGlow.copy(alpha = 0.2f))
                    .border(1.2.dp, GlassTheme.colors.accentGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = GlassTheme.colors.accentGlow,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "0003 DOWNLOADER",
                    color = GlassTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (isVaultUnlocked) "Vault Unlocked • AES-256" else "Multi-Threaded • Stealth Hub",
                    color = if (isVaultUnlocked) GlassTheme.colors.successGlass else GlassTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Settings Button
        GlassIconButton(
            icon = Icons.Default.Settings,
            onClick = onOpenSettings,
            size = 38.dp,
            iconSize = 18.dp,
            tint = GlassTheme.colors.accentGlow,
            contentDescription = "Open Settings"
        )
    }
}

private fun launchBiometricPrompt(
    context: Context,
    isFirstTimeRegistration: Boolean = false,
    onResult: (Boolean) -> Unit
) {
    if (context is FragmentActivity) {
        val title = if (isFirstTimeRegistration) "ثبت بیومتریک گاوصندوق امن" else "احراز هویت بیومتریک پوشه مخفی"
        val subtitle = if (isFirstTimeRegistration) {
            "اثر انگشت یا شناسه بیومتریک گوشی خود را تأیید کنید تا گاوصندوق فعال شود"
        } else {
            "اثر انگشت خود را روی حسگر قرار دهید یا چهره خود را تأیید کنید"
        }

        BiometricHelper.authenticate(
            activity = context,
            title = title,
            subtitle = subtitle,
            onSuccess = { onResult(true) },
            onError = { _, _ -> onResult(false) },
            onFailed = { /* Biometric failed, allow retry */ }
        )
    } else {
        onResult(false)
    }
}
