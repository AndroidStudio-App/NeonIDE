package com.neonide.studio.dialog

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.neonide.studio.R
import io.github.rosemoe.sora.widget.CodeEditor

private const val KEY_TYPEFACE = "typeface"
private const val PREFS_NAME = "editor_appearance"

@Composable
fun TypefaceChoiceDialog(editor: CodeEditor?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val typefaceAssets =
        arrayOf("JetBrainsMono-Regular.ttf", "UbuntuMono-Regular.ttf", "RobotoMono-Regular.ttf")
    val fontNames = arrayOf("JetBrains Mono", "Ubuntu Mono", "Google/Roboto Mono")
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val selectedIndex = prefs.getInt(KEY_TYPEFACE, 0)

    fun applyTypeface(index: Int) {
        if (index in typefaceAssets.indices) {
            runCatching {
                editor?.typefaceText =
                    Typeface.createFromAsset(context.assets, typefaceAssets[index])
            }
            prefs.edit().putInt(KEY_TYPEFACE, index).apply()
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_typeface),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                fontNames.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyTypeface(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        dismissButton = {},
        confirmButton = {}
    )
}
