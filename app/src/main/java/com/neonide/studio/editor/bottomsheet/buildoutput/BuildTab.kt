package com.neonide.studio.editor.bottomsheet.buildoutput

import android.view.ViewGroup.LayoutParams
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun BuildTab(contentStream: String, isDark: Boolean) {
    AndroidView(
        factory = { context ->
            CodeEditor(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                setEditable(false)
                setTextSize(12f)
                setScalable(true)
                props.stickyScroll = true
                props.overScrollEnabled = true
                setInterceptParentHorizontalScrollIfNeeded(false)
            }
        },
        update = { editor ->
            val texmateTheme = if (isDark) "darcula" else "quietlight"
            ThemeRegistry.getInstance().setTheme(texmateTheme)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.setText(contentStream)
            editor.post {
                val line = editor.text.lineCount - 1
                if (line >= 0) {
                    editor.setSelection(line, editor.text.getColumnCount(line))
                    editor.ensurePositionVisible(line, editor.text.getColumnCount(line))
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
