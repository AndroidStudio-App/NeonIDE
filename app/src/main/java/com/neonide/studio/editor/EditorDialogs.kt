package com.neonide.studio.editor

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import com.neonide.studio.R
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource

private const val PREFS_NAME = "editor_appearance"
private const val KEY_TYPEFACE = "typeface"
private const val KEY_THEME = "theme"

object EditorDialogs {

    private var textmateInitialized = false

    fun setupTextmate() {
        if (textmateInitialized) return
        textmateInitialized = true
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        themes.forEach { name ->
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

    private val typefaceAssets =
        arrayOf("JetBrainsMono-Regular.ttf", "UbuntuMono-Regular.ttf", "RobotoMono-Regular.ttf")

    fun showTypefaceChoice(context: Context, editor: CodeEditor?) {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu Mono", "Google/Roboto Mono")

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.select_typeface))
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in typefaceAssets.indices) {
                    runCatching {
                        editor?.typefaceText =
                            Typeface.createFromAsset(context.assets, typefaceAssets[which])
                    }
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putInt(KEY_TYPEFACE, which).apply()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private val themeKeys = arrayOf("quietlight", "darcula", "ayu-dark", "solarized_dark")

    fun showThemeChoice(context: Context, editor: CodeEditor?) {
        val themes = arrayOf("QuietLight", "Darcula", "Ayu Dark", "Solarized Dark")

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.select_color_scheme))
            .setItems(themes) { _, which ->
                if (editor == null) return@setItems
                if (which !in themeKeys.indices) return@setItems
                ThemeRegistry.getInstance().setTheme(themeKeys[which])
                editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_THEME, which).apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun restoreAppearance(context: Context, editor: CodeEditor?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val typefaceIndex = prefs.getInt(KEY_TYPEFACE, -1)
        if (typefaceIndex in typefaceAssets.indices) {
            runCatching {
                editor?.typefaceText =
                    Typeface.createFromAsset(context.assets, typefaceAssets[typefaceIndex])
            }
        }

        val savedTheme = prefs.getInt(KEY_THEME, -1)
        val themeName = if (savedTheme in themeKeys.indices) {
            themeKeys[savedTheme]
        } else {
            val isDark =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            if (isDark) "darcula" else "quietlight"
        }
        ThemeRegistry.getInstance().setTheme(themeName)
        editor?.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    }
}
