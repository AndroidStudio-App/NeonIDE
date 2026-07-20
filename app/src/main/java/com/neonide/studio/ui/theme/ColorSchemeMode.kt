package com.neonide.studio.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.neonide.studio.R

enum class ColorSchemeMode(val key: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.follow_system),
    DARK("dark", R.string.dark_mode),
    LIGHT("light", R.string.light_mode);

    companion object {
        fun fromKey(key: String): ColorSchemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@Composable
fun ColorSchemeMode.isDark(): Boolean = when (this) {
    ColorSchemeMode.SYSTEM -> isSystemInDarkTheme()
    ColorSchemeMode.DARK -> true
    ColorSchemeMode.LIGHT -> false
}
