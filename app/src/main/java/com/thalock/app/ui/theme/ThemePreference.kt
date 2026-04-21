package com.thalock.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

/**
 * Process-wide holder for the user's theme choice. Settings writes to it; the root
 * composable collects [mode] and passes the resolved boolean to [ThaLockTheme].
 */
object ThemePreference {
    private const val PREFS = "thalock_theme_prefs"
    private const val KEY_MODE = "theme_mode"

    private lateinit var prefs: SharedPreferences
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _mode.value = runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_MODE, mode.name).apply()
        }
    }
}
