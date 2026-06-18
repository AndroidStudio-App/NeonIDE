package com.neonide.studio.app.lsp

import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

object NoopEditorLspController : EditorLspController {
    override fun attach(
        editor: CodeEditor,
        file: File,
        wrapperLanguage: Language,
        projectRoot: File?
    ): Boolean = false
    override fun detach() = Unit
    override fun dispose() = Unit
    override fun currentEditor(): LspEditor? = null
}
