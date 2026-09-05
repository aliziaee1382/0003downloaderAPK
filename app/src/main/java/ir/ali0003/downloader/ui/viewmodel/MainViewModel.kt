package ir.ali0003.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.ali0003.downloader.data.local.AppDatabase
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.model.DownloadStatus
import ir.ali0003.downloader.data.repository.DownloadRepository
import ir.ali0003.downloader.data.repository.DownloadRepositoryImpl
import ir.ali0003.downloader.data.vault.VaultFileManager
import ir.ali0003.downloader.data.vault.VaultManager
import ir.ali0003.downloader.data.theme.ThemePreferences
import ir.ali0003.downloader.downloader.manager.DownloadManagerController
import ir.ali0003.downloader.downloader.model.DownloadProgress
import ir.ali0003.downloader.ui.glass.GlassPreset
import ir.ali0003.downloader.ui.glass.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository: DownloadRepository = DownloadRepositoryImpl(database.downloadDao())
    val vaultManager: VaultManager = VaultManager(application)
    val vaultFileManager: VaultFileManager = VaultFileManager(application, database.downloadDao())
    val downloadController: DownloadManagerController = DownloadManagerController.getInstance(application)
    val themePreferences: ThemePreferences = ThemePreferences(application)

    // Real-time task progress map from DownloadManagerController
    val taskProgressMap: StateFlow<Map<Long, DownloadProgress>> = downloadController.taskProgressMap

    // 2-Tier Theme States: ThemeMode (Dark, Light, Auto) & Accent Color Key
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
    val themeColorKey: StateFlow<String> = themePreferences.themeColorKey

    // Current Glassmorphic Theme Preset (for compatibility)
    private val _currentPreset = MutableStateFlow(GlassPreset.SUNSET_AMBER)
    val currentPreset: StateFlow<GlassPreset> = _currentPreset.asStateFlow()

    // Active Tab in Master Navigation (0: Browser, 1: Downloading, 2: Library)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // PIN Entry State & Auth Modal
    private val _showVaultAuthDialog = MutableStateFlow(false)
    val showVaultAuthDialog: StateFlow<Boolean> = _showVaultAuthDialog.asStateFlow()

    private val _pinInput = MutableStateFlow("")
    val pinInput: StateFlow<String> = _pinInput.asStateFlow()

    private val _pinErrorMessage = MutableStateFlow<String?>(null)
    val pinErrorMessage: StateFlow<String?> = _pinErrorMessage.asStateFlow()

    // In-App Video Player Active Task
    private val _selectedPlayerTask = MutableStateFlow<DownloadTaskEntity?>(null)
    val selectedPlayerTask: StateFlow<DownloadTaskEntity?> = _selectedPlayerTask.asStateFlow()

    // Reactive Data Flows
    val publicDownloads: StateFlow<List<DownloadTaskEntity>> = repository.allPublicDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadTaskEntity>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadTaskEntity>> = repository.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultDownloads: StateFlow<List<DownloadTaskEntity>> = repository.vaultDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicCount: StateFlow<Int> = repository.publicCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val vaultCount: StateFlow<Int> = repository.vaultCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isVaultUnlocked: StateFlow<Boolean> = vaultManager.isVaultUnlocked
    val isVaultConfigured: Boolean get() = vaultManager.isVaultConfigured()

    private var simulationJob: Job? = null

    init {
        seedInitialDataIfEmpty()
    }

    fun applyTheme(mode: ThemeMode, colorKey: String) {
        themePreferences.saveTheme(mode, colorKey)
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.saveTheme(mode, themeColorKey.value)
    }

    fun setThemeColor(colorKey: String) {
        themePreferences.saveTheme(themeMode.value, colorKey)
    }

    fun setPreset(preset: GlassPreset) {
        _currentPreset.value = preset
        val colorKey = when (preset) {
            GlassPreset.DARK_BLUE, GlassPreset.MIDNIGHT_OLED, GlassPreset.TITANIUM_ICE -> "blue"
            GlassPreset.CRIMSON_RUBY -> "red"
            GlassPreset.DARK_PURPLE, GlassPreset.COSMIC_VAPORWAVE, GlassPreset.CYBERPUNK -> "purple"
            GlassPreset.SUNSET_AMBER -> "orange"
            GlassPreset.DARK_GREEN, GlassPreset.MATRIX_NEON, GlassPreset.AURORA_BOREALIS -> "green"
            GlassPreset.OBSIDIAN -> "blue"
        }
        applyTheme(ThemeMode.DARK, colorKey)
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun openVaultAuthDialog() {
        _showVaultAuthDialog.value = true
        _pinInput.value = ""
        _pinErrorMessage.value = null
    }

    fun closeVaultAuthDialog() {
        _showVaultAuthDialog.value = false
        _pinInput.value = ""
        _pinErrorMessage.value = null
    }

    fun openVideoPlayer(task: DownloadTaskEntity) {
        _selectedPlayerTask.value = task
    }

    fun closeVideoPlayer() {
        _selectedPlayerTask.value = null
    }

    fun onPinDigit(digit: Char) {
        if (_pinInput.value.length < 4 && digit.isDigit()) {
            _pinInput.value += digit
            _pinErrorMessage.value = null
            if (_pinInput.value.length == 4) {
                submitPin()
            }
        }
    }

    fun onPinBackspace() {
        if (_pinInput.value.isNotEmpty()) {
            _pinInput.value = _pinInput.value.dropLast(1)
            _pinErrorMessage.value = null
        }
    }

    fun onPinClear() {
        _pinInput.value = ""
        _pinErrorMessage.value = null
    }

    private fun submitPin() {
        val pin = _pinInput.value
        if (pin.length != 4) return

        if (!vaultManager.isVaultConfigured()) {
            // Initial PIN setup
            val success = vaultManager.setupPin(pin)
            if (success) {
                _pinInput.value = ""
                _pinErrorMessage.value = null
                _showVaultAuthDialog.value = false
            } else {
                _pinErrorMessage.value = "Invalid PIN format"
            }
        } else {
            // Verification
            val valid = vaultManager.verifyPin(pin)
            if (valid) {
                _pinInput.value = ""
                _pinErrorMessage.value = null
                _showVaultAuthDialog.value = false
            } else {
                _pinErrorMessage.value = "Incorrect Passcode"
                _pinInput.value = ""
            }
        }
    }

    fun unlockWithBiometrics() {
        vaultManager.unlockWithBiometrics()
        _pinInput.value = ""
        _pinErrorMessage.value = null
        _showVaultAuthDialog.value = false
    }

    fun lockVault() {
        vaultManager.lockVault()
        _pinInput.value = ""
        _pinErrorMessage.value = null
    }

    fun togglePause(task: DownloadTaskEntity) {
        if (task.status == DownloadStatus.DOWNLOADING) {
            downloadController.pauseTask(task.id)
        } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.QUEUED) {
            downloadController.resumeTask(task.id)
        }
    }

    fun toggleVaultHidden(task: DownloadTaskEntity) {
        viewModelScope.launch {
            if (task.isHidden) {
                vaultFileManager.unhideFromVault(task)
            } else {
                vaultFileManager.moveToVault(task)
            }
        }
    }

    fun deleteDownload(task: DownloadTaskEntity) {
        viewModelScope.launch {
            downloadController.cancelTask(task.id)
            vaultFileManager.deleteFileAndTask(task)
        }
    }

    fun addDemoDownload(isHidden: Boolean = false) {
        viewModelScope.launch {
            val sampleFiles = listOf(
                Triple("4K_Neon_Cyberpunk_City.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", 1024L * 1024L * 85L),
                Triple("Deep_Sea_Bioluminescence_1080p.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", 1024L * 1024L * 42L),
                Triple("Space_Nebula_Stream.m3u8", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", 1024L * 1024L * 128L),
                Triple("Confidential_Security_Report.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", 1024L * 1024L * 25L)
            )
            val random = sampleFiles.random()
            val isM3u8 = random.first.endsWith(".m3u8")

            repository.enqueueDownload(
                url = random.second,
                websiteUrl = "https://mediahub.example.com/watch",
                fileName = random.first,
                mimeType = if (isM3u8) "application/x-mpegURL" else "video/mp4",
                totalBytes = random.third,
                isM3u8 = isM3u8,
                headersJson = "{\"User-Agent\":\"VideoVault/1.0\"}",
                isHidden = isHidden
            )
        }
    }

    fun simulateProgress() {
        if (simulationJob?.isActive == true) {
            simulationJob?.cancel()
            simulationJob = null
            return
        }

        simulationJob = viewModelScope.launch {
            while (true) {
                delay(800)
                val active = activeDownloads.value
                for (task in active) {
                    if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                        val added = (1024L * 1024L * 2L..1024L * 1024L * 8L).random()
                        val newDownloaded = (task.downloadedBytes + added).coerceAtMost(task.totalBytes)
                        val speed = added * 1000L / 800L
                        if (newDownloaded >= task.totalBytes && task.totalBytes > 0) {
                            repository.markCompleted(task.id)
                        } else {
                            repository.updateProgress(task.id, newDownloaded, task.totalBytes, speed)
                        }
                    }
                }
            }
        }
    }

    private fun seedInitialDataIfEmpty() {
        viewModelScope.launch {
            val existing = repository.findDownloadById(1L)
            if (existing == null) {
                repository.insertTasks(
                    listOf(
                        DownloadTaskEntity(
                            id = 1L,
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                            websiteUrl = "https://videoshare.io/watch?v=9283",
                            fileName = "Emerald_Waves_Cinematic_4K.mp4",
                            mimeType = "video/mp4",
                            totalBytes = 64L * 1024L * 1024L,
                            downloadedBytes = 38L * 1024L * 1024L,
                            status = DownloadStatus.DOWNLOADING,
                            speedBps = 3L * 1024L * 1024L,
                            isM3u8 = false,
                            isHidden = false,
                            createdAt = System.currentTimeMillis() - 120_000
                        ),
                        DownloadTaskEntity(
                            id = 2L,
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                            websiteUrl = "https://synthstream.net/live",
                            fileName = "Cyber_Amethyst_Concert.mp4",
                            mimeType = "video/mp4",
                            totalBytes = 120L * 1024L * 1024L,
                            downloadedBytes = 120L * 1024L * 1024L,
                            status = DownloadStatus.COMPLETED,
                            speedBps = 0L,
                            isM3u8 = false,
                            isHidden = false,
                            createdAt = System.currentTimeMillis() - 86_400_000,
                            completedAt = System.currentTimeMillis() - 86_000_000
                        ),
                        DownloadTaskEntity(
                            id = 3L,
                            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                            websiteUrl = "https://securematrix.io/archive",
                            fileName = "Confidential_Archive_Matrix_Raw.mp4",
                            mimeType = "video/mp4",
                            totalBytes = 250L * 1024L * 1024L,
                            downloadedBytes = 250L * 1024L * 1024L,
                            status = DownloadStatus.COMPLETED,
                            speedBps = 0L,
                            isM3u8 = false,
                            isHidden = true,
                            createdAt = System.currentTimeMillis() - 172_800_000,
                            completedAt = System.currentTimeMillis() - 172_000_000
                        )
                    )
                )
            }
        }
    }
}
