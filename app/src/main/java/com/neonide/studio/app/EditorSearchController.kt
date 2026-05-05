package com.neonide.studio.app

import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import com.neonide.studio.R
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

/**
 * Controller for handling search and replace logic in the editor.
 */
class EditorSearchController(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val searchPanel: View,
    private val searchEditor: EditText,
    private val replaceEditor: EditText,
    private val searchOptionsBtn: View
) {

    private var searchMenu: PopupMenu = PopupMenu(activity, searchOptionsBtn).apply {
        menuInflater.inflate(R.menu.menu_sora_search_options, menu)
        setOnMenuItemClickListener { item ->
            item.isChecked = !item.isChecked
            if (item.isChecked) {
                when (item.itemId) {
                    R.id.sora_search_option_regex -> menu.findItem(R.id.sora_search_option_whole_word)?.isChecked = false
                    R.id.sora_search_option_whole_word -> menu.findItem(R.id.sora_search_option_regex)?.isChecked = false
                }
            }
            computeSearchOptions()
            tryCommitSearch()
            true
        }
    }

    private var searchOptions: EditorSearcher.SearchOptions =
        EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true, RegexBackrefGrammar.DEFAULT)

    init {
        searchEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { tryCommitSearch() }
        })

        activity.findViewById<View>(R.id.btn_goto_prev).setOnClickListener { gotoPrev() }
        activity.findViewById<View>(R.id.btn_goto_next).setOnClickListener { gotoNext() }
        activity.findViewById<View>(R.id.btn_replace).setOnClickListener { replaceCurrent() }
        activity.findViewById<View>(R.id.btn_replace_all).setOnClickListener { replaceAll() }
        searchOptionsBtn.setOnClickListener { searchMenu.show() }
    }

    private fun computeSearchOptions() {
        val caseInsensitive = !searchMenu.menu.findItem(R.id.sora_search_option_match_case).isChecked
        var type = EditorSearcher.SearchOptions.TYPE_NORMAL
        val regex = searchMenu.menu.findItem(R.id.sora_search_option_regex).isChecked
        if (regex) type = EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        val wholeWord = searchMenu.menu.findItem(R.id.sora_search_option_whole_word).isChecked
        if (wholeWord) type = EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
        searchOptions = EditorSearcher.SearchOptions(type, caseInsensitive, RegexBackrefGrammar.DEFAULT)
    }

    fun tryCommitSearch() {
        val query = searchEditor.text
        if (!query.isNullOrEmpty()) {
            runCatching {
                editor.searcher.search(query.toString(), searchOptions)
            }
        } else {
            editor.searcher.stopSearch()
        }
    }

    private fun gotoNext() {
        runCatching { editor.searcher.gotoNext() }
    }

    private fun gotoPrev() {
        runCatching { editor.searcher.gotoPrevious() }
    }

    private fun replaceCurrent() {
        val replacement = replaceEditor.text.toString()
        runCatching { editor.searcher.replaceCurrentMatch(replacement) }
    }

    private fun replaceAll() {
        val replacement = replaceEditor.text.toString()
        runCatching { editor.searcher.replaceAll(replacement) }
    }

    fun toggleSearchPanel(item: MenuItem) {
        if (searchPanel.visibility == View.GONE) {
            replaceEditor.setText("")
            searchEditor.setText("")
            editor.searcher.stopSearch()
            searchPanel.visibility = View.VISIBLE
            item.isChecked = true
        } else {
            searchPanel.visibility = View.GONE
            editor.searcher.stopSearch()
            item.isChecked = false
        }
    }
}
