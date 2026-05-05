package com.neonide.studio.app

import android.view.MenuItem
import io.github.rosemoe.sora.widget.CodeEditor

class EditorSearchManager(
    private val controller: EditorSearchController,
    private val editor: CodeEditor
) {

    fun toggleSearchPanel(item: MenuItem) {
        controller.toggleSearchPanel(item)
    }

    fun beginSearchMode() {
        controller.tryCommitSearch()
    }
}
