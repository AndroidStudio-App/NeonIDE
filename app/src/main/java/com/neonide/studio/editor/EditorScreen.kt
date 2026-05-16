package com.neonide.studio.app.editor

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.R
import com.neonide.studio.app.EditorGradleManager
import com.neonide.studio.app.EditorSearchController
import com.neonide.studio.app.EditorThemeAndLanguageManager
import com.neonide.studio.app.EditorViewModel
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.EditorBottomSheetContent
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.editor.completion.UnifiedCompletionProvider
import com.neonide.studio.utils.OpenFile
import com.termux.app.TermuxActivity
import com.termux.shared.logger.Logger
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "EditorScreen"

@Composable
fun EditorScreen(
    editorVm: EditorViewModel,
    bottomSheetVm: BottomSheetViewModel,
    settings: EditorSettingsState,
    projectPath: File,
    openFilesState: MutableState<List<OpenFile>>,
    activeFileState: MutableState<OpenFile?>,
    editorState: MutableState<CodeEditor?>,
    symbolInputView: SymbolInputView,
    gradleManager: EditorGradleManager,
    languageProvider: SoraLanguageProvider,
    lspController: com.neonide.studio.app.lsp.EditorLspController,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = 30.dp + navBarHeight

    BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    LaunchedEffect(editorState.value) {
        val editor = editorState.value ?: return@LaunchedEffect
        val tm = EditorThemeAndLanguageManager(editor)
        tm.setupTextmate()
        tm.setupMonarch()
        if (editor.colorScheme !is TextMateColorScheme) {
            editor.colorScheme = TextMateColorScheme.create(
                ThemeRegistry.getInstance()
            )
        }
        Logger.logInfo(
            TAG,
            "theme initialized, colorScheme=${editor.colorScheme::class.simpleName}"
        )
    }

    LaunchedEffect(activeFileState.value?.path) {
        val activeFile = activeFileState.value
        val editor = editorState.value
        if (activeFile != null && editor != null) {
            val file = java.io.File(activeFile.path)
            val language = languageProvider.getLanguage(file)
            val innerName = (language as? UnifiedCompletionProvider)?.baseLanguageClassName
            Logger.logInfo(
                TAG,
                "file=${file.name}, language=${language::class.simpleName}, inner=${innerName ?: "none"}, colorScheme=${editor.colorScheme::class.simpleName}"
            )
            editor.setEditorLanguage(language)
            if (editor.text.toString() != activeFile.content) {
                editor.setText(activeFile.content)
            }
            val ext = file.extension.lowercase()
            if (ext in listOf("java", "kt", "kts", "xml")) {
                runCatching {
                    lspController.attach(editor, file, language, projectPath)
                }.onFailure {
                    Logger.logWarn(TAG, "LSP attach failed: ${it.message}")
                }
            } else {
                runCatching { lspController.detach() }
            }
        } else if (activeFile == null && editor != null) {
            editor.setText("")
        }
    }

    val searchController = remember(editorState.value) {
        editorState.value?.let { EditorSearchController(context, it, editorVm) }
    }

    BottomSheetScaffold(
        modifier = Modifier,
        scaffoldState = scaffoldState,
        sheetShape = RectangleShape,
        sheetContent = { EditorBottomSheetContent(viewModel = bottomSheetVm) },
        sheetPeekHeight = peekHeight,
        topBar = {
            EditorTopBar(
                settings = settings,
                editor = editorState.value,
                searchPanelVisible = editorVm.searchPanelVisible,
                onSearchPanelToggle = {
                    editorVm.searchPanelVisible = !editorVm.searchPanelVisible
                },
                onSearchActionMode = { searchController?.tryCommitSearch() },
                onNavigationClick = onOpenDrawer,
                onUndoClick = { editorState.value?.undo() },
                onRedoClick = { editorState.value?.redo() },
                onSaveClick = {
                    saveAllModifiedFiles(
                        scope,
                        openFilesState,
                        activeFileState,
                        editorState.value,
                        activeFileState.value
                    )
                },
                onBuildClick = {
                    gradleManager.onQuickRunOrCancel(projectPath)
                    scope.launch { scaffoldState.bottomSheetState.expand() }
                },
                onSyncClick = { gradleManager.onSyncProject(projectPath) },
                onTerminalClick = {
                    runCatching {
                        context.startActivity(Intent(context, TermuxActivity::class.java))
                    }
                },
                onSwitchColors = { EditorDialogs.showThemeChoice(context, editorState.value) },
                onSwitchTypeface = { EditorDialogs.showTypefaceChoice(context, editorState.value) }
            )
        }
    ) { padding ->
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val scaffoldBottom = padding.calculateBottomPadding()
        val bottomPadding = max(imeBottom, scaffoldBottom)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = bottomPadding)
        ) {
            if (editorVm.searchPanelVisible && searchController != null) {
                EditorSearchPanel(editorVm, searchController)
            }

            EditorTabRow(
                openFilesState = openFilesState,
                activeFileState = activeFileState,
                editorState = editorState
            )

            SoraEditor(
                modifier = Modifier.weight(1f),
                filePath = activeFileState.value?.path,
                onEditorCreated = { editor ->
                    editorState.value = editor
                    symbolInputView.bindEditor(editor)
                    editor.subscribeAlways(SelectionChangeEvent::class.java) {
                        updatePositionText(editor, editorVm)
                    }
                    editor.subscribeAlways(ContentChangeEvent::class.java) {
                        val active = activeFileState.value
                        if (active != null && !active.isModified) {
                            if (editor.text.toString() != active.content) {
                                val updated = active.copy(isModified = true)
                                activeFileState.value = updated
                                openFilesState.value = openFilesState.value.map {
                                    if (it.path == updated.path) updated else it
                                }
                            }
                        }
                    }
                    updatePositionText(editor, editorVm)
                }
            )

            Column {
                if (settings.isSymbolBarVisible) {
                    AndroidView(
                        factory = { ctx ->
                            HorizontalScrollView(ctx).apply {
                                isHorizontalScrollBarEnabled = false
                                (symbolInputView.parent as? ViewGroup)?.removeView(symbolInputView)
                                addView(symbolInputView)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
                Text(
                    text = editorVm.positionText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun saveAllModifiedFiles(
    scope: kotlinx.coroutines.CoroutineScope,
    openFilesState: MutableState<List<OpenFile>>,
    activeFileState: MutableState<OpenFile?>,
    editor: CodeEditor?,
    activeFile: OpenFile?
) {
    scope.launch(Dispatchers.IO) {
        val currentText = withContext(Dispatchers.Main) {
            editor?.text?.toString()
        }

        val currentOpenFiles = openFilesState.value
        val updatedFiles = currentOpenFiles.map { file ->
            if (file.isModified) {
                runCatching {
                    val contentToSave = if (file.path == activeFile?.path && currentText != null) {
                        currentText
                    } else {
                        file.content
                    }
                    File(file.path).writeText(contentToSave)
                    file.copy(content = contentToSave, isModified = false)
                }.getOrDefault(file)
            } else {
                file
            }
        }

        withContext(Dispatchers.Main) {
            openFilesState.value = updatedFiles
            // Update active file reference to the new instance if it was saved
            activeFile?.let { active ->
                updatedFiles.find { it.path == active.path }?.let {
                    activeFileState.value = it
                }
            }
        }
    }
}

@Composable
fun EditorSearchPanel(viewModel: EditorViewModel, controller: EditorSearchController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.gotoPrev() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
            }
            IconButton(onClick = { controller.gotoNext() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
            IconButton(onClick = { controller.replaceCurrent() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = "Replace")
            }
            IconButton(onClick = { controller.replaceAll() }, modifier = Modifier.weight(1f)) {
                Text("ALL")
            }
            IconButton(onClick = { }, modifier = Modifier.weight(0.5f)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        }

        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = {
                viewModel.searchQuery = it
                controller.tryCommitSearch()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_text_to_search)) },
            singleLine = true
        )

        OutlinedTextField(
            value = viewModel.replacementText,
            onValueChange = { viewModel.replacementText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_replacement)) },
            singleLine = true
        )
    }
}

private fun updatePositionText(editor: CodeEditor?, editorVm: EditorViewModel) {
    if (editor == null) return
    val cursor = editor.cursor
    var text = "${cursor.leftLine + 1}:${cursor.leftColumn};${cursor.left} "

    text += if (cursor.isSelected) {
        "(${cursor.right - cursor.left} chars)"
    } else {
        "(${editor.text.getLine(cursor.leftLine).toString().getOrNull(cursor.leftColumn) ?: ' '})"
    }

    val searcher = editor.searcher
    if (searcher.hasQuery()) {
        val idx = searcher.currentMatchedPositionIndex
        val count = searcher.matchedPositionCount
        text += if (idx == -1) "(no match)" else "(${idx + 1} of $count matches)"
    }
    editorVm.positionText = text
}
