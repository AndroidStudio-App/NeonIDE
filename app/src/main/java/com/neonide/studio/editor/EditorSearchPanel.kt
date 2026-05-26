package com.neonide.studio.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.ui.components.ToggleMenuItem
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

class EditorSearchState(private val editor: CodeEditor) {
    var searchQuery by mutableStateOf("")
    var replacementText by mutableStateOf("")
    var isVisible by mutableStateOf(false)
    var isCaseInsensitive by mutableStateOf(false)
    var isRegex by mutableStateOf(false)
    var showReplace by mutableStateOf(false)

    private var searchOptions: EditorSearcher.SearchOptions =
        EditorSearcher.SearchOptions(
            EditorSearcher.SearchOptions.TYPE_NORMAL,
            false,
            RegexBackrefGrammar.DEFAULT
        )

    fun updateSearchOptions() {
        val type = if (isRegex) {
            EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        } else {
            EditorSearcher.SearchOptions.TYPE_NORMAL
        }
        searchOptions =
            EditorSearcher.SearchOptions(type, isCaseInsensitive, RegexBackrefGrammar.DEFAULT)
        tryCommitSearch()
    }

    fun toggle() {
        if (!isVisible) {
            replacementText = ""
            searchQuery = ""
            editor.searcher.stopSearch()
            isVisible = true
        } else {
            isVisible = false
            editor.searcher.stopSearch()
        }
    }

    fun tryCommitSearch() {
        if (searchQuery.isNotEmpty()) {
            runCatching { editor.searcher.search(searchQuery, searchOptions) }
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
        runCatching { editor.searcher.replaceCurrentMatch(replacementText) }
    }

    fun replaceAll() {
        runCatching { editor.searcher.replaceAll(replacementText) }
    }
}

@Composable
fun EditorSearchPanel(state: EditorSearchState) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { state.gotoPrev() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
            }
            IconButton(onClick = { state.gotoNext() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
            }
            AnimatedVisibility(visible = state.showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { state.replaceCurrent() }) {
                        Text("Replace")
                    }
                    TextButton(onClick = { state.replaceAll() }) {
                        Text("All")
                    }
                }
            }
            IconButton(onClick = { state.showReplace = !state.showReplace }) {
                Icon(Icons.Default.FindReplace, contentDescription = "Toggle replace")
            }
            IconButton(onClick = { state.toggle() }) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
            var optionsExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { optionsExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                DropdownMenu(
                    expanded = optionsExpanded,
                    onDismissRequest = { optionsExpanded = false }
                ) {
                    ToggleMenuItem(
                        text = "Case Insensitive",
                        checked = state.isCaseInsensitive,
                        onToggle = {
                            state.isCaseInsensitive = !state.isCaseInsensitive
                            state.updateSearchOptions()
                        }
                    )
                    ToggleMenuItem(
                        text = "Regex",
                        checked = state.isRegex,
                        onToggle = {
                            state.isRegex = !state.isRegex
                            state.updateSearchOptions()
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            FormTextField(
                value = state.searchQuery,
                onValueChange = {
                    state.searchQuery = it
                    state.tryCommitSearch()
                },
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.editor_text_to_search),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    state.gotoNext()
                    keyboardController?.hide()
                })
            )

            if (state.showReplace) {
                FormTextField(
                    value = state.replacementText,
                    onValueChange = { state.replacementText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    label = stringResource(R.string.editor_replacement)
                )
            }
        }
    }
}
