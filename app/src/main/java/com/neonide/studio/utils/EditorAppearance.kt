package com.neonide.studio.utils

import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource

internal const val PREFS_NAME = "editor_appearance"
internal const val KEY_TYPEFACE = "typeface"
internal const val KEY_THEME = "theme"

object EditorAppearance {

    private var textmateInitialized = false

    val themeKeys = arrayOf("quietlight", "darcula", "ayu-dark", "solarized_dark")
    val themeNames = arrayOf("QuietLight", "Darcula", "Ayu Dark", "Solarized Dark")
    val typefaceAssets = arrayOf(
        "JetBrainsMono-Regular.ttf",
        "UbuntuMono-Regular.ttf",
        "RobotoMono-Regular.ttf"
    )
    val fontNames = arrayOf("JetBrains Mono", "Ubuntu Mono", "Google/Roboto Mono")

    fun setupTextmate() {
        if (textmateInitialized) return
        textmateInitialized = true
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        themeKeys.forEach { name ->
            val path = "textmate/$name.json"
            val inputStream = FileProviderRegistry.getInstance().tryGetInputStream(path)
            ThemeRegistry.getInstance().loadTheme(
                ThemeModel(IThemeSource.fromInputStream(inputStream, path, null), name).apply {
                    if (name != "quietlight") isDark = true
                }
            )
        }
        ThemeRegistry.getInstance().setTheme("darcula")
    }

    fun restoreAppearance(context: Context, editor: CodeEditor?, isDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val typefaceIndex = prefs.getInt(KEY_TYPEFACE, -1)
        if (typefaceIndex in typefaceAssets.indices) {
            runCatching {
                editor?.typefaceText =
                    Typeface.createFromAsset(context.assets, typefaceAssets[typefaceIndex])
            }
        }

        val themeName = if (isDark) {
            val savedTheme = prefs.getInt(KEY_THEME, -1)
            if (savedTheme in themeKeys.indices) themeKeys[savedTheme] else "darcula"
        } else {
            "quietlight"
        }
        ThemeRegistry.getInstance().setTheme(themeName)
        editor?.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    }
}
