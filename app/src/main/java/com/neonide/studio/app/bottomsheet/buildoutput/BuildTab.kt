package com.neonide.studio.app.bottomsheet.buildoutput

import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun BuildTab(contentStream: String) {
    AndroidView(
        factory = { context ->
            CodeEditor(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                setEditable(false)
                setTextSize(12f)
                setScalable(true)
                props.stickyScroll = true
                props.overScrollEnabled = true
                setInterceptParentHorizontalScrollIfNeeded(false)
            }
        },
        update = { editor ->
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
