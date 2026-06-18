package com.neonide.studio.editor

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.app.EditorGradleController
import com.neonide.studio.app.bottomsheet.BottomSheetTab
import com.neonide.studio.app.bottomsheet.BottomSheetTabRow
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.EditorBottomSheetContent
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.utils.GradleBuildStatus
import com.neonide.studio.utils.OpenFile
import com.termux.app.TermuxActivity
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "EditorScreen"

@Composable
fun EditorScreen(
    positionTextState: MutableState<String>,
    bottomSheetVm: BottomSheetViewModel,
    settings: EditorSettingsState,
    projectPath: File,
    openFilesState: MutableState<List<OpenFile>>,
    activeFileState: MutableState<OpenFile?>,
    editorState: MutableState<CodeEditor?>,
    symbolInputView: SymbolInputView,
    gradleController: EditorGradleController,
    languageProvider: SoraLanguageProvider,
    lspController: com.neonide.studio.app.lsp.EditorLspController,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val tabs = BottomSheetTab.entries
    val pagerState = rememberPagerState { tabs.size }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = 15.dp + navBarHeight

    val gradleRunningState = remember { mutableStateOf(GradleBuildStatus.isRunning) }
    val buildVariant = remember { mutableStateOf("debug") }
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { gradleRunningState.value = it }
        GradleBuildStatus.addListener(listener)
        onDispose { GradleBuildStatus.removeListener(listener) }
    }

    // Pre-scan Gradle cache and build dirs so classPath is ready when a Java file is opened
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val properties = context.getSharedPreferences("global_properties", Context.MODE_PRIVATE)
            if (properties.getBoolean("enabled", true)) {
                val dir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".gradle").also { it.mkdirs() }
                val gradleText =
                    properties.getString("gradle_text", null)
                        ?: EditorGlobalProperties.DEFAULT_GRADLE
                val localText =
                    properties.getString("local_text", null) ?: EditorGlobalProperties.DEFAULT_LOCAL
                val gradleFile = File(dir, "gradle.properties")
                val localFile = File(dir, "local.properties")
                if (!gradleFile.exists() || gradleFile.readText() != gradleText) {
                    gradleFile.writeText(gradleText)
                }
                if (!localFile.exists() || localFile.readText() != localText) {
                    localFile.writeText(localText)
                }
            }
            lspController.prefetchClassPath(projectPath)
        }
    }

    BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    LaunchedEffect(editorState.value) {
        val editor = editorState.value ?: return@LaunchedEffect
        EditorDialogs.setupTextmate()
        EditorDialogs.restoreAppearance(context, editor)
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
            Logger.logInfo(
                TAG,
                "file=${file.name}, language=${language::class.simpleName}, colorScheme=${editor.colorScheme::class.simpleName}"
            )
            editor.setEditorLanguage(language)
            if (editor.text.toString() != activeFile.content) {
                editor.setText(activeFile.content)
            } else {
                editor.setHighlightTexts(null)
            }
            val ext = file.extension.lowercase()
            if (ext in
                listOf(
                    "java", "kt", "kts", "dart",
                    "xml", "json", "yaml", "yml",
                    "js", "ts", "jsx", "tsx",
                    "sh", "bash", "zsh"
                )
            ) {
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
            editor.setHighlightTexts(null)
        }
    }

    val searchState = remember(editorState.value) {
        editorState.value?.let { EditorSearchState(it) }
    }

    BackHandler(enabled = searchState?.isVisible == true) {
        searchState?.toggle()
    }

    BottomSheetScaffold(
        modifier = Modifier,
        scaffoldState = scaffoldState,
        sheetShape = RectangleShape,
        sheetSwipeEnabled = false,
        sheetDragHandle = {
            Column(
                modifier = Modifier.draggable(
                    state = rememberDraggableState { delta ->
                        if (delta > 0 &&
                            scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
                        ) {
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        } else if (delta < 0 &&
                            scaffoldState.bottomSheetState.currentValue ==
                            SheetValue.PartiallyExpanded
                        ) {
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        }
                    },
                    orientation = Orientation.Vertical
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.padding(bottom = 0.dp).offset(y = (-15).dp)) {
                    BottomSheetDefaults.DragHandle()
                }
                BottomSheetTabRow(pagerState = pagerState, tabs = tabs)
            }
        },
        sheetContent = {
            EditorBottomSheetContent(viewModel = bottomSheetVm, pagerState = pagerState)
        },
        sheetPeekHeight = peekHeight,
        topBar = {
            EditorTopBar(
                settings = settings,
                editor = editorState.value,
                searchPanelVisible = searchState?.isVisible == true,
                onSearchPanelToggle = { searchState?.toggle() },
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
                isGradleRunning = gradleRunningState.value,
                buildVariant = buildVariant.value,
                onBuildVariantChange = { buildVariant.value = it },
                onBuildClick = {
                    gradleController.onQuickRunOrCancel(projectPath, buildVariant.value)
                    scope.launch { scaffoldState.bottomSheetState.expand() }
                },
                onSyncClick = { gradleController.onSyncProject(projectPath) },
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
            if (searchState?.isVisible == true) {
                EditorSearchPanel(searchState)
            }

            EditorTabRow(
                openFilesState = openFilesState,
                activeFileState = activeFileState,
                editorState = editorState
            )

            SoraEditor(
                modifier = Modifier.weight(1f),
                onEditorCreated = { editor ->
                    editorState.value = editor
                    symbolInputView.bindEditor(editor)
                    editor.subscribeAlways(SelectionChangeEvent::class.java) {
                        updatePositionText(editor, positionTextState)
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
                        editor.setHighlightTexts(null)
                        // Only dismiss if showing to avoid unnecessary layout triggers
                        try {
                            val tooltip = editor.getComponent(
                                EditorDiagnosticTooltipWindow::class.java
                            )
                            if (tooltip.isShowing) {
                                tooltip.dismiss()
                            }
                        } catch (e: IllegalStateException) {
                            Logger.logDebug(TAG, "dismiss tooltip: ${e.message}")
                        }
                    }
                    updatePositionText(editor, positionTextState)
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
                    text = positionTextState.value,
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

private fun updatePositionText(editor: CodeEditor?, positionTextState: MutableState<String>) {
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
    positionTextState.value = text
}
