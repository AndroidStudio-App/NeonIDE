package com.neonide.studio.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.neonide.studio.R
import com.neonide.studio.utils.PersistedString

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

const val APP_SETTINGS_PREFS = "app_settings"
const val KEY_COLOR_SCHEME_MODE = "color_scheme_mode"

@Composable
fun rememberColorSchemeMode(): ColorSchemeMode {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
    }
    val modeKey by remember(prefs) {
        PersistedString(prefs, KEY_COLOR_SCHEME_MODE, ColorSchemeMode.SYSTEM.key)
    }
    return ColorSchemeMode.fromKey(modeKey)
}

/**
 * Forces Compose resource/night resolution to follow [colorSchemeMode].
 *
 * - [LocalConfiguration] makes [isSystemInDarkTheme] respect the app setting.
 * - [LocalContext] resources use the forced night mode so [painterResource]
 *   resolves drawable / drawable-night correctly.
 * - [LocalActivityResultRegistryOwner] is re-provided from the host Activity so
 *   launchers keep working under the themed context wrapper.
 */
@Composable
fun ThemedContent(colorSchemeMode: ColorSchemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentConfig = LocalConfiguration.current
    val isDark = colorSchemeMode.isDark()
    val themedConfig = remember(isDark, currentConfig) {
        Configuration(currentConfig).apply {
            uiMode = if (isDark) {
                uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or Configuration.UI_MODE_NIGHT_YES
            } else {
                uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or Configuration.UI_MODE_NIGHT_NO
            }
        }
    }
    val themedContext = remember(isDark, context, themedConfig) {
        ThemedContextWrapper(context, themedConfig)
    }
    val registryOwner = remember(context) {
        context.findActivityResultRegistryOwner()
    }

    if (registryOwner != null) {
        CompositionLocalProvider(
            LocalContext provides themedContext,
            LocalConfiguration provides themedConfig,
            LocalActivityResultRegistryOwner provides registryOwner,
            content = content
        )
    } else {
        CompositionLocalProvider(
            LocalContext provides themedContext,
            LocalConfiguration provides themedConfig,
            content = content
        )
    }
}

/** Walks ContextWrappers to recover the host [ComponentActivity]. */
fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return this as? ComponentActivity
}

/** Walks ContextWrappers to recover any [Activity]. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? Activity
}

/**
 * Context wrapper that only overrides resource resolution so night-qualified
 * drawables follow the app color scheme, while keeping the original base
 * context for Activity identity via unwrapping helpers.
 */
private class ThemedContextWrapper(base: Context, private val config: Configuration) :
    ContextWrapper(base) {
    private var themedResources: Resources? = null

    override fun getResources(): Resources {
        if (themedResources == null) {
            themedResources = createConfigurationContext(config).resources
        }
        return themedResources!!
    }
}

private fun Context.findActivityResultRegistryOwner(): ActivityResultRegistryOwner? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ActivityResultRegistryOwner) return ctx
        ctx = ctx.baseContext
    }
    return this as? ActivityResultRegistryOwner
}
