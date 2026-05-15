package com.neonide.studio.app

import android.app.AlertDialog
import android.graphics.Typeface
import androidx.activity.result.ActivityResultLauncher
import com.neonide.studio.R
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

/**
 * Helper for showing various configuration dialogs in the editor.
 */
class EditorDialogHelper(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val loadTMTLauncher: ActivityResultLauncher<String>
) {

    fun chooseTypeface() {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu Mono", "Google/Roboto Mono")
        val assetsPaths =
            arrayOf("JetBrainsMono-Regular.ttf", "UbuntuMono-Regular.ttf", "RobotoMono-Regular.ttf")
        AlertDialog.Builder(activity)
            .setTitle(android.R.string.dialog_alert_title)
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in assetsPaths.indices) {
                    runCatching {
                        editor.typefaceText =
                            Typeface.createFromAsset(activity.assets, assetsPaths[which])
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun chooseTheme() {
        val themes = arrayOf(
            "QuietLight",
            "Darcula",
            "Ayu Dark",
            "Solarized Dark",
            "Load from file"
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.sora_color_scheme)
            .setSingleChoiceItems(themes, -1) { dialog, which ->
                when (which) {
                    0 -> {
                        ThemeRegistry.getInstance().setTheme("quietlight")
                        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    }

                    1 -> {
                        ThemeRegistry.getInstance().setTheme("darcula")
                        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    }

                    2 -> {
                        ThemeRegistry.getInstance().setTheme("ayu-dark")
                        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    }

                    3 -> {
                        ThemeRegistry.getInstance().setTheme("solarized_dark")
                        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    }

                    4 -> {
                        loadTMTLauncher.launch("*/*")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
