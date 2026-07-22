package com.neonide.studio.dialog

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.neonide.studio.R
import com.neonide.studio.ui.components.RadioListDialog
import com.neonide.studio.utils.EditorAppearance
import com.neonide.studio.utils.KEY_THEME
import com.neonide.studio.utils.PREFS_NAME
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun EditorColorScheme(editor: CodeEditor?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val selectedIndex = prefs.getInt(KEY_THEME, -1)

    fun applyTheme(index: Int) {
        if (index in EditorAppearance.themeKeys.indices) {
            runCatching {
                ThemeRegistry.getInstance().setTheme(EditorAppearance.themeKeys[index])
                editor?.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            }
            prefs.edit().putInt(KEY_THEME, index).apply()
        }
        onDismiss()
    }

    RadioListDialog(
        title = stringResource(R.string.select_color_scheme),
        items = EditorAppearance.themeNames.toList(),
        selectedIndex = selectedIndex,
        onItemClick = { applyTheme(it) },
        onDismissRequest = onDismiss
    )
}
