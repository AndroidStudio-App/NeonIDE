package com.neonide.studio.app.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neonide.studio.utils.OpenFile
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

@Composable
fun EditorTabRow(
    openFilesState: MutableState<List<OpenFile>>,
    activeFileState: MutableState<OpenFile?>,
    editorState: MutableState<CodeEditor?>
) {
    val openFiles = openFilesState.value
    val activeFile = activeFileState.value
    val selectedIndex = openFiles.indexOf(activeFile).coerceAtLeast(0)

    if (openFiles.isNotEmpty()) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                if (tabPositions.isNotEmpty()) {
                    val indicatorIndex = selectedIndex.coerceIn(0, tabPositions.lastIndex)
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[indicatorIndex])
                    )
                }
            },
            divider = {}
        ) {
            openFiles.forEachIndexed { index, file ->
                key(file.path) {
                    var isMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        Tab(
                            selected = activeFile == file,
                            onClick = {
                                if (activeFile != file) {
                                    activeFile?.let { active ->
                                        val currentText =
                                            editorState.value?.text?.toString() ?: active.content
                                        val updated = active.copy(content = currentText)
                                        openFilesState.value = openFilesState.value.map {
                                            if (it.path == updated.path) updated else it
                                        }
                                    }
                                    activeFileState.value = file
                                } else {
                                    isMenuExpanded = true
                                }
                            },
                            text = {
                                val displayText = if (file.isModified) "${file.name}*" else file.name
                                Text(displayText)
                            }
                        )

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Close this") },
                                onClick = {
                                    isMenuExpanded = false

                                    val currentText = if (activeFile == file) {
                                        editorState.value?.text?.toString() ?: file.content
                                    } else {
                                        file.content
                                    }
                                    // testing only
                                    if (file.isModified ||
                                        (activeFile == file && currentText != file.content)
                                    ) {
                                        runCatching {
                                            File(file.path).writeText(currentText)
                                        }
                                    }

                                    val newList = openFilesState.value.filter {
                                        it.path != file.path
                                    }
                                    openFilesState.value = newList
                                    if (activeFile == file) {
                                        activeFileState.value = newList.lastOrNull()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close others") },
                                onClick = {
                                    isMenuExpanded = false
                                    // TODO File(file.path).writeText(currentText)
                                    val updatedActive = activeFile?.let { active ->
                                        val currentText =
                                            editorState.value?.text?.toString() ?: active.content
                                        active.copy(content = currentText)
                                    }

                                    val targetFile = if (updatedActive?.path ==
                                        file.path
                                    ) {
                                        updatedActive
                                    } else {
                                        file
                                    }
                                    openFilesState.value = listOf(targetFile)
                                    activeFileState.value = targetFile
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close all") },
                                onClick = {
                                    isMenuExpanded = false
                                    // TODO File(file.path).writeText(currentText)
                                    openFilesState.value = emptyList()
                                    activeFileState.value = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
