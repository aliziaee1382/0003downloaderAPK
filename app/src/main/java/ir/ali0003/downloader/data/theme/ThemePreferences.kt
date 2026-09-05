package ir.ali0003.downloader.data.theme

import android.content.Context
import android.content.SharedPreferences
import ir.ali0003.downloader.ui.glass.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themeColorKey = MutableStateFlow(loadColorKey())
    val themeColorKey: StateFlow<String> = _themeColorKey.asStateFlow()

    companion object {
        private const val PREFS_NAME = "downloader_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode_enum"
        private const val KEY_COLOR_KEY = "theme_color_key"
        private const val KEY_THEME_INITIALIZED_ORANGE = "theme_initialized_dark_orange_v1"
        const val DEFAULT_COLOR_KEY = "orange"
    }

    init {
        // Automatically migrate if this is first launch or still on old legacy defaults (green / auto)
        if (!prefs.getBoolean(KEY_THEME_INITIALIZED_ORANGE, false)) {
            val savedColor = prefs.getString(KEY_COLOR_KEY, null)
            val savedMode = prefs.getString(KEY_THEME_MODE, null)
            val isOldDefault = (savedColor == null || savedColor.equals("green", ignoreCase = true)) &&
                    (savedMode == null || savedMode == ThemeMode.AUTO.name)

            if (isOldDefault) {
                prefs.edit()
                    .putString(KEY_THEME_MODE, ThemeMode.DARK.name)
                    .putString(KEY_COLOR_KEY, DEFAULT_COLOR_KEY)
                    .putBoolean(KEY_THEME_INITIALIZED_ORANGE, true)
                    .apply()
                _themeMode.value = ThemeMode.DARK
                _themeColorKey.value = DEFAULT_COLOR_KEY
            } else {
                prefs.edit().putBoolean(KEY_THEME_INITIALIZED_ORANGE, true).apply()
            }
        }
    }

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return try {
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun loadColorKey(): String {
        return prefs.getString(KEY_COLOR_KEY, DEFAULT_COLOR_KEY) ?: DEFAULT_COLOR_KEY
    }

    fun saveTheme(mode: ThemeMode, colorKey: String) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .putString(KEY_COLOR_KEY, colorKey.lowercase())
            .apply()

        _themeMode.value = mode
        _themeColorKey.value = colorKey.lowercase()
    }
}
