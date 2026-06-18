package com.neonide.studio.editor

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.utils.HexColorScanner
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.widget.CodeEditor

class NeonCodeEditor(context: Context) : CodeEditor(context) {
    override fun setHighlightTexts(highlights: HighlightTextContainer?) {
        val container = highlights ?: HighlightTextContainer()
        HexColorScanner.appendHighlights(text, container)
        super.setHighlightTexts(container)
    }
}

@Composable
fun SoraEditor(modifier: Modifier = Modifier, onEditorCreated: (CodeEditor) -> Unit) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            NeonCodeEditor(context).apply {
                onEditorCreated(this)
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE
                props.stickyScroll = true
                props.overScrollEnabled = true
                props.cancelCompletionNs = 150 * 1000000L // 150ms
                isCursorAnimationEnabled = true
                nonPrintablePaintingFlags =
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                    CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                    CodeEditor.FLAG_DRAW_SOFT_WRAP
            }
        },
        onRelease = { it.release() }
    )
}
