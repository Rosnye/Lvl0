package io.gymtracker.app.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Preferencia de apariencia. Por defecto la app es oscura. */
@Singleton
class ThemeSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        prefs.getString(KEY, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
    )
    val mode: StateFlow<ThemeMode> = _mode

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit { putString(KEY, mode.name) }
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
