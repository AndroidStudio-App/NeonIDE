package com.neonide.studio.dialog

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.neonide.studio.R
import com.neonide.studio.ui.components.RadioListDialog
import com.neonide.studio.utils.EditorAppearance
import com.neonide.studio.utils.KEY_TYPEFACE
import com.neonide.studio.utils.PREFS_NAME
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun TypefaceChoiceDialog(editor: CodeEditor?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val selectedIndex = prefs.getInt(KEY_TYPEFACE, 0)

    fun applyTypeface(index: Int) {
        if (index in EditorAppearance.typefaceAssets.indices) {
            runCatching {
                editor?.typefaceText =
                    Typeface.createFromAsset(context.assets, EditorAppearance.typefaceAssets[index])
            }
            prefs.edit().putInt(KEY_TYPEFACE, index).apply()
        }
        onDismiss()
    }

    RadioListDialog(
        title = stringResource(R.string.select_typeface),
        items = EditorAppearance.fontNames.toList(),
        selectedIndex = selectedIndex,
        onItemClick = { applyTypeface(it) },
        onDismissRequest = onDismiss
    )
}
