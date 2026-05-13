package com.neonide.studio.app.editor

import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.app.EditorViewModel
import com.neonide.studio.app.lsp.LspManager
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

@Composable
fun SoraEditor(
    modifier: Modifier = Modifier,
    editorVm: EditorViewModel,
    filePath: String?,
    onEditorCreated: (CodeEditor) -> Unit
) {
    val context = LocalContext.current
    val languageProvider = remember { SoraLanguageProvider(context) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            CodeEditor(ctx).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE

                filePath?.let {
                    val file = File(it)
                    val lang = languageProvider.getLanguage(file)
                    setEditorLanguage(lang)

                    // Attach LSP if supported
                    val ext = file.extension.lowercase()
                    if (ext in listOf("java", "kt", "kts", "xml")) {
                        editorVm.lspController.attach(this, file, lang, editorVm.projectPath)
                    }
                }

                props.stickyScroll = true
                props.overScrollEnabled = true
                isCursorAnimationEnabled = true
                nonPrintablePaintingFlags =
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                    CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                    CodeEditor.FLAG_DRAW_SOFT_WRAP

                onEditorCreated(this)
            }
        },
        update = { editor ->
            filePath?.let {
                val file = File(it)
                val lang = languageProvider.getLanguage(file)
                if (editor.editorLanguage != lang) {
                    editor.setEditorLanguage(lang)
                    
                    val ext = file.extension.lowercase()
                    if (ext in listOf("java", "kt", "kts", "xml")) {
                        editorVm.lspController.attach(editor, file, lang, editorVm.projectPath)
                    } else {
                        editorVm.lspController.detach()
                    }
                }
            }
        },
        onRelease = { 
            it.release() 
        }
    )
}

