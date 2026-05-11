package com.neonide.studio.app

import android.view.MenuItem
import com.neonide.studio.R
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

/**
 * Controller for handling search and replace logic in the editor.
 */
class EditorSearchController(
    private val activity: androidx.activity.ComponentActivity,
    private val editor: CodeEditor,
    private val viewModel: EditorViewModel
) {

    private var searchOptions: EditorSearcher.SearchOptions =
        EditorSearcher.SearchOptions(
            EditorSearcher.SearchOptions.TYPE_NORMAL,
            true,
            RegexBackrefGrammar.DEFAULT
        )

    fun updateSearchOptions(type: Int, caseInsensitive: Boolean) {
        searchOptions =
            EditorSearcher.SearchOptions(type, caseInsensitive, RegexBackrefGrammar.DEFAULT)
        tryCommitSearch()
    }

    fun tryCommitSearch() {
        val query = viewModel.searchQuery
        if (query.isNotEmpty()) {
            runCatching {
                editor.searcher.search(query, searchOptions)
            }
        } else {
            editor.searcher.stopSearch()
        }
    }

    fun gotoNext() {
        runCatching { editor.searcher.gotoNext() }
    }

    fun gotoPrev() {
        runCatching { editor.searcher.gotoPrevious() }
    }

    fun replaceCurrent() {
        val replacement = viewModel.replacementText
        runCatching { editor.searcher.replaceCurrentMatch(replacement) }
    }

    fun replaceAll() {
        val replacement = viewModel.replacementText
        runCatching { editor.searcher.replaceAll(replacement) }
    }

    fun toggleSearchPanel(item: MenuItem?) {
        if (!viewModel.searchPanelVisible) {
            viewModel.replacementText = ""
            viewModel.searchQuery = ""
            editor.searcher.stopSearch()
            viewModel.searchPanelVisible = true
            item?.isChecked = true
        } else {
            viewModel.searchPanelVisible = false
            editor.searcher.stopSearch()
            item?.isChecked = false
        }
    }
}
