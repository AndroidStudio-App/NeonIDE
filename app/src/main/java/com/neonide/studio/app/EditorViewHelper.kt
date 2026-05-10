package com.neonide.studio.app

import android.widget.TextView
import com.neonide.studio.R
import io.github.rosemoe.sora.widget.CodeEditor

class EditorViewHelper(
    private val activity: SoraEditorActivityK, 
    private val editor: CodeEditor,
    private val viewModel: EditorViewModel
) {
    fun updatePositionText() {
        val cursor = editor.cursor
        var text = "${cursor.leftLine + 1}:${cursor.leftColumn};${cursor.left} "
        text += if (cursor.isSelected) "(${cursor.right - cursor.left} chars)" 
                else "(${editor.text.getLine(cursor.leftLine).toString().getOrNull(cursor.leftColumn) ?: ' '})"
        
        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            val idx = searcher.currentMatchedPositionIndex
            val count = searcher.matchedPositionCount
            text += if (idx == -1) "(no match)" else "(${idx + 1} of $count matches)"
        }
        viewModel.positionText = text
    }
}
