package com.neonide.studio.app.editor

import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow

@Composable
fun SoraEditor(
    modifier: Modifier = Modifier,
    filePath: String?,
    onEditorCreated: (CodeEditor) -> Unit
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                onEditorCreated(this)
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE
                props.stickyScroll = true
                props.overScrollEnabled = true
                isCursorAnimationEnabled = true
                nonPrintablePaintingFlags =
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                    CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                    CodeEditor.FLAG_DRAW_SOFT_WRAP
                getComponent(EditorDiagnosticTooltipWindow::class.java).isEnabled = false
            }
        },
        onRelease = { it.release() }
    )
}
