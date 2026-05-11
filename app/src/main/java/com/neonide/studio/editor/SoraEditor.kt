package com.neonide.studio.app.editor

import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SoraEditor(
    modifier: Modifier = Modifier,
    filePath: String?,
    onEditorCreated: (CodeEditor) -> Unit
) {
    var editor by remember { mutableStateOf<CodeEditor?>(null) }

    LaunchedEffect(filePath) {
        filePath?.let { path ->
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    File(path).readText()
                }.getOrNull()
            }
            content?.let { editor?.setText(it) }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                editor = this
                onEditorCreated(this)
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE
                setEditorLanguage(JavaLanguage())
                props.stickyScroll = true
                props.overScrollEnabled = true
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
