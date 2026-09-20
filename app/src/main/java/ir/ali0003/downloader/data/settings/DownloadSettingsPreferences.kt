package ir.ali0003.downloader.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadSettingsPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _threadCount = MutableStateFlow(loadThreadCount())
    val threadCount: StateFlow<Int> = _threadCount.asStateFlow()

    private val _isMultiSegmentEnabled = MutableStateFlow(loadMultiSegmentEnabled())
    val isMultiSegmentEnabled: StateFlow<Boolean> = _isMultiSegmentEnabled.asStateFlow()

    companion object {
        private const val PREFS_NAME = "downloader_engine_prefs"
        private const val KEY_THREAD_COUNT = "engine_thread_count"
        private const val KEY_MULTI_SEGMENT_ENABLED = "engine_multi_segment_enabled"

        const val DEFAULT_THREAD_COUNT = 6
        val SUPPORTED_SEGMENTS = listOf(1, 2, 4, 6, 8, 12, 16)

        @Volatile
        private var INSTANCE: DownloadSettingsPreferences? = null

        fun getInstance(context: Context): DownloadSettingsPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadSettingsPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun loadThreadCount(): Int {
        return prefs.getInt(KEY_THREAD_COUNT, DEFAULT_THREAD_COUNT).coerceIn(1, 16)
    }

    private fun loadMultiSegmentEnabled(): Boolean {
        return prefs.getBoolean(KEY_MULTI_SEGMENT_ENABLED, true)
    }

    fun setThreadCount(count: Int) {
        val safeCount = count.coerceIn(1, 16)
        prefs.edit().putInt(KEY_THREAD_COUNT, safeCount).apply()
        _threadCount.value = safeCount
    }

    fun setMultiSegmentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MULTI_SEGMENT_ENABLED, enabled).apply()
        _isMultiSegmentEnabled.value = enabled
    }

    fun getEffectiveThreadCount(): Int {
        return if (_isMultiSegmentEnabled.value) _threadCount.value else 1
    }
}
