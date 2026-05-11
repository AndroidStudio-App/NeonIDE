package com.neonide.studio.app.editor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019

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

    fun showLanguageChoice(context: Context, editor: CodeEditor?) {
        val languages =
            arrayOf("Java", "Kotlin", "Python", "C", "C++", "HTML", "JavaScript", "Markdown", "Text")

        AlertDialog.Builder(context)
            .setTitle("Select Language")
            .setItems(languages) { dialog, which ->
                if (editor == null) return@setItems
                when (languages[which]) {
                    "Java" -> editor.setEditorLanguage(JavaLanguage())
                    "Text" -> editor.setEditorLanguage(EmptyLanguage())
                    // Add other languages as needed
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showThemeChoice(context: Context, editor: CodeEditor?) {
        val themes = arrayOf("Default", "GitHub", "Eclipse", "Darcula", "VS2019", "NotepadXX")

        AlertDialog.Builder(context)
            .setTitle("Select Color Scheme")
            .setItems(themes) { dialog, which ->
                if (editor == null) return@setItems
                when (which) {
                    0 -> editor.colorScheme = EditorColorScheme()
                    1 -> editor.colorScheme = SchemeGitHub()
                    2 -> editor.colorScheme = SchemeEclipse()
                    3 -> editor.colorScheme = SchemeDarcula()
                    4 -> editor.colorScheme = SchemeVS2019()
                    5 -> editor.colorScheme = SchemeNotepadXX()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
