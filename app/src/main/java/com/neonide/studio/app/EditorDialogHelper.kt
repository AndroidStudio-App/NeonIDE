package com.neonide.studio.app

import android.app.AlertDialog
import android.graphics.Typeface
import androidx.activity.result.ActivityResultLauncher
import com.neonide.studio.R
import com.neonide.studio.app.editor.SoraLanguageProvider
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry as MonarchThemeRegistry
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import io.github.rosemoe.sora.widget.style.LineInfoPanelPosition
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode
import java.io.File

/**
 * Helper for showing various configuration dialogs in the editor.
 */
class EditorDialogHelper(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val languageProvider: SoraLanguageProvider,
    private val loadTMTLauncher: ActivityResultLauncher<String>,
    private val loadTMLLauncher: ActivityResultLauncher<String>
) {

    fun chooseTypeface() {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu", "Roboto")
        val assetsPaths = arrayOf("JetBrainsMono-Regular.ttf", "Ubuntu-Regular.ttf", "Roboto-Regular.ttf")
        AlertDialog.Builder(activity)
            .setTitle(android.R.string.dialog_alert_title)
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in assetsPaths.indices) {
                    runCatching {
                        editor.typefaceText = Typeface.createFromAsset(activity.assets, assetsPaths[which])
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun chooseLanguage() {
        val languageOptions = arrayOf(
            "Java",
            "TextMate Java",
            "TextMate Kotlin",
            "TextMate Python",
            "TextMate Html",
            "TextMate JavaScript",
            "TextMate MarkDown",
            "TM Language from file",
            "Tree-sitter Java",
            "Monarch Java",
            "Monarch Kotlin",
            "Monarch Python",
            "Monarch TypeScript",
            "Text"
        )

        val tmLanguages = mapOf(
            "TextMate Java" to Pair("source.java", "source.java"),
            "TextMate Kotlin" to Pair("source.kotlin", "source.kotlin"),
            "TextMate Python" to Pair("source.python", "source.python"),
            "TextMate Html" to Pair("text.html.basic", "text.html.basic"),
            "TextMate JavaScript" to Pair("source.js", "source.js"),
            "TextMate MarkDown" to Pair("text.html.markdown", "text.html.markdown")
        )

        val monarchLanguages = mapOf(
            "Monarch Java" to "source.java",
            "Monarch Kotlin" to "source.kotlin",
            "Monarch Python" to "source.python",
            "Monarch TypeScript" to "source.typescript"
        )

        AlertDialog.Builder(activity)
            .setTitle(R.string.sora_switch_language)
            .setSingleChoiceItems(languageOptions, -1) { dialog, which ->
                when (val selected = languageOptions[which]) {
                    in tmLanguages -> {
                        val info = tmLanguages[selected]!!
                        try {
                            ensureTextmateTheme()
                            val editorLanguage = editor.editorLanguage
                            val language = if (editorLanguage is TextMateLanguage) {
                                editorLanguage.updateLanguage(info.first)
                                editorLanguage
                            } else {
                                TextMateLanguage.create(info.second, true)
                            }
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    in monarchLanguages -> {
                        val info = monarchLanguages[selected]!!
                        try {
                            ensureMonarchTheme()
                            val editorLanguage = editor.editorLanguage
                            val language = if (editorLanguage is MonarchLanguage) {
                                editorLanguage.updateLanguage(info)
                                editorLanguage
                            } else {
                                MonarchLanguage.create(info, true)
                            }
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    else -> {
                        when (selected) {
                            "Java" -> editor.setEditorLanguage(JavaLanguage())
                            "Text" -> editor.setEditorLanguage(EmptyLanguage())
                            "TM Language from file" -> loadTMLLauncher.launch("*/*")
                            "Tree-sitter Java" -> {
                                editor.setEditorLanguage(languageProvider.getLanguage(File("dummy.java")))
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ensureTextmateTheme() {
        val cs = editor.colorScheme
        if (cs !is TextMateColorScheme) {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }
    }

    private fun ensureMonarchTheme() {
        if (editor.colorScheme !is MonarchColorScheme) {
            editor.colorScheme = MonarchColorScheme.create(MonarchThemeRegistry.currentTheme)
        }
    }

    fun chooseTheme() {
        val themes = arrayOf(
            "Default",
            "GitHub",
            "Eclipse",
            "Darcula",
            "VS2019",
            "NotepadXX",
            "QuietLight for TM(VSCode)",
            "Darcula for TM",
            "Ayu Dark for VSCode",
            "Solarized(Dark) for TM(VSCode)",
            "TM theme from file"
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.sora_color_scheme)
            .setSingleChoiceItems(themes, -1) { dialog, which ->
                when (which) {
                    0 -> editor.colorScheme = EditorColorScheme()
                    1 -> editor.colorScheme = SchemeGitHub()
                    2 -> editor.colorScheme = SchemeEclipse()
                    3 -> editor.colorScheme = SchemeDarcula()
                    4 -> editor.colorScheme = SchemeVS2019()
                    5 -> editor.colorScheme = SchemeNotepadXX()

                    6 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("quietlight")
                    }

                    7 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("darcula")
                    }

                    8 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("ayu-dark")
                    }

                    9 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("solarized_dark")
                    }

                    10 -> {
                        loadTMTLauncher.launch("*/*")
                    }
                }

                val cs = editor.colorScheme
                editor.colorScheme = cs

                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
