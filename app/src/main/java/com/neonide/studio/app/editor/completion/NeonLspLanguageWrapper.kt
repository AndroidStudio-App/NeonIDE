package com.neonide.studio.app.editor.completion

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * A wrapper for [LspLanguage] that adds custom logic for:
 * 1. Fallback to [wrapperLanguage] when LSP is disconnected.
 * 2. Integration with custom [LspManager] for Java files.
 * 3. Merging completions for XML files.
 */
class NeonLspLanguageWrapper(
    private val delegate: LspLanguage,
    private val editor: CodeEditor,
    private val lspEditor: LspEditor,
    private val wrapperLanguage: Language?
) : Language by delegate {

    init {
        // Set wrapperLanguage on the delegate so it can use it for indentation, etc.
        delegate.wrapperLanguage = wrapperLanguage
    }

    override fun getAnalyzeManager(): AnalyzeManager {
        // Use wrapper's analyze manager (e.g. Tree-sitter squiggles) if available.
        // LspLanguage handles its own diagnostics via LSP events independently of the AnalyzeManager.
        return wrapperLanguage?.analyzeManager ?: delegate.analyzeManager
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val fileExt = lspEditor.uri.path.substringAfterLast('.', "")
        val isXml = fileExt.equals("xml", ignoreCase = true)
        val isJava = fileExt.equals("java", ignoreCase = true)

        if (!lspEditor.isConnected) {
            // Fallback to wrapper language completions when LSP is not connected.
            wrapperLanguage?.requireAutoComplete(content, position, publisher, extraArguments)
            return
        }

        // Call original LspLanguage (Maven library) to get LSP completions
        delegate.requireAutoComplete(content, position, publisher, extraArguments)

        if (isXml || isJava) {
            wrapperLanguage?.requireAutoComplete(content, position, publisher, extraArguments)
        }
    }
}
