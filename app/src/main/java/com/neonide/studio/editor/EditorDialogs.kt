package com.neonide.studio.app.editor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor

object EditorDialogs {

    fun showTypefaceChoice(context: Context, editor: CodeEditor?) {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu Mono", "Google/Roboto Mono")
        val assetsPaths =
            arrayOf("JetBrainsMono-Regular.ttf", "UbuntuMono-Regular.ttf", "RobotoMono-Regular.ttf")

        AlertDialog.Builder(context)
            .setTitle("Select Typeface")
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in assetsPaths.indices) {
                    runCatching {
                        editor?.typefaceText =
                            Typeface.createFromAsset(context.assets, assetsPaths[which])
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showThemeChoice(context: Context, editor: CodeEditor?) {
        val themes = arrayOf("QuietLight", "Darcula", "Ayu Dark", "Solarized Dark")

        AlertDialog.Builder(context)
            .setTitle("Select Color Scheme")
            .setItems(themes) { dialog, which ->
                if (editor == null) return@setItems
                val themeName = when (which) {
                    0 -> "quietlight"
                    1 -> "darcula"
                    2 -> "ayu-dark"
                    3 -> "solarized_dark"
                    else -> "darcula"
                }
                ThemeRegistry.getInstance().setTheme(themeName)
                editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
