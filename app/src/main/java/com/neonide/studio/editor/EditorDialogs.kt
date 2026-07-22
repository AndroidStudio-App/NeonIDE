package com.neonide.studio.editor

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    private val themeKeys = arrayOf("quietlight", "darcula", "ayu-dark", "solarized_dark")
    private val themeNames = arrayOf("QuietLight", "Darcula", "Ayu Dark", "Solarized Dark")

    @Composable
    fun ThemeChoiceDialog(editor: CodeEditor?, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedIndex = prefs.getInt(KEY_THEME, -1)

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.select_color_scheme), fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.padding(16.dp)) {
                    themeNames.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (index in themeKeys.indices && editor != null) {
                                        ThemeRegistry.getInstance().setTheme(themeKeys[index])
                                        editor.colorScheme =
                                            TextMateColorScheme.create(
                                                ThemeRegistry.getInstance()
                                            )
                                        prefs.edit().putInt(KEY_THEME, index).apply()
                                    }
                                    onDismiss()
                                }
                                .background(MaterialTheme.colorScheme.surface),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 12.dp),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (index == selectedIndex) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Text(text = stringResource(android.R.string.cancel), fontSize = 14.sp)
            }
        )
    }

    fun restoreAppearance(context: Context, editor: CodeEditor?, isDark: Boolean) {
        val typefaceAssets =
            arrayOf("JetBrainsMono-Regular.ttf", "UbuntuMono-Regular.ttf", "RobotoMono-Regular.ttf")
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
