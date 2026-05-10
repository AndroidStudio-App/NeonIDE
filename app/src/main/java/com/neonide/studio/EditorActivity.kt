package com.neonide.studio

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.neonide.studio.R
import com.neonide.studio.app.EditorGradleManager
import com.neonide.studio.app.bottomsheet.EditorBottomSheetContent
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import com.neonide.studio.filetree.FileTreeDrawer
import com.neonide.studio.ui.theme.AppTheme
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.content.Intent
import com.termux.app.TermuxActivity

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.view.WindowCompat
import com.neonide.studio.app.EditorSearchController
import com.neonide.studio.app.EditorSearchPanel
import com.neonide.studio.app.EditorViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import com.neonide.studio.app.editor.SoraLanguageProvider
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.IOException

import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import io.github.rosemoe.sora.widget.component.Magnifier

import androidx.compose.ui.text.style.TextAlign
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.SymbolInputView

class EditorActivity : ComponentActivity() {
    companion object {
        private val SYMBOLS = arrayOf(
            "->", "{", "}", "(", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<", ">", "[", "]", ":"
        )

        private val SYMBOL_INSERT_TEXT = arrayOf(
            "\t", "{}", "}", "()", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<>", ">", "[]", "]", ":"
        )
    }

    private val filePathState = mutableStateOf<String?>(null)
    private val editorState = mutableStateOf<CodeEditor?>(null)
    private val editorVm: EditorViewModel by viewModels()
    private val bottomSheetVm: BottomSheetViewModel by viewModels()
    private val languageProvider: SoraLanguageProvider by lazy { SoraLanguageProvider(this) }

    private val symbolInputView by lazy {
        SymbolInputView(this).apply {
            addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT)
        }
    }
    
    // Feature Switch States
    private var isSymbolBarVisible by mutableStateOf(true)
    private var isWordwrap by mutableStateOf(false)
    private var isLineNumberVisible by mutableStateOf(true)
    private var isLineNumberPinned by mutableStateOf(false)
    private var isMagnifierEnabled by mutableStateOf(true)
    private var useIcu by mutableStateOf(true)
    private var completionAnim by mutableStateOf(true)
    private var softKbdEnabled by mutableStateOf(true)
    private var hardKbdDisabled by mutableStateOf(true)

    private val gradleManager: EditorGradleManager by lazy {
        EditorGradleManager(this, bottomSheetVm)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val projectPath = File(intent.getStringExtra("extra_project_dir") ?: return)

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_editor)

        // Add observer for build output
        lifecycleScope.launch {
            BuildOutputBuffer.output.collectLatest { output ->
                bottomSheetVm.setBuildOutput(output)
            }
        }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val mainContent = findViewById<ComposeView>(R.id.main_content)
        val drawerView = findViewById<ComposeView>(R.id.file_tree_drawer_view)

        mainContent.setContent {
            AppTheme {
                @OptIn(ExperimentalMaterial3Api::class)
                val scaffoldState = rememberBottomSheetScaffoldState()
                val scope = rememberCoroutineScope()
                val searchController = remember(editorState.value) {
                    editorState.value?.let { EditorSearchController(this@EditorActivity, it, editorVm) }
                }

                @OptIn(ExperimentalMaterial3Api::class)
                BottomSheetScaffold(
                    modifier = Modifier.navigationBarsPadding(),
                    scaffoldState = scaffoldState,
                    sheetContent = {
                        EditorBottomSheetContent(
                            viewModel = bottomSheetVm
                        )
                    },
                    sheetPeekHeight = 72.dp,
                    topBar = {
                        AppTopBar(
                            onNavigationClick = { drawerLayout.openDrawer(Gravity.START) },
                            onUndoClick = { editorState.value?.undo() },
                            onRedoClick = { editorState.value?.redo() },
                            onSaveClick = {
                                val currentEditor = editorState.value
                                val currentPath = filePathState.value
                                if (currentEditor != null && currentPath != null) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            File(currentPath).writeText(currentEditor.text.toString())
                                        }
                                    }
                                }
                            },
                            onBuildClick = {
                                gradleManager.onQuickRunOrCancel(projectPath)
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            },
                            onSyncClick = { gradleManager.onSyncProject(projectPath) },
                            onTerminalClick = {
                                runCatching {
                                    startActivity(Intent(this, TermuxActivity::class.java))
                                }
                            },
                            // Search Actions
                            onSearchActionMode = { searchController?.tryCommitSearch() },
                            onSearchPanelToggle = { editorVm.searchPanelVisible = !editorVm.searchPanelVisible },
                            searchPanelVisible = editorVm.searchPanelVisible,
                            // Feature Switches
                            onSymbolBarToggle = { isSymbolBarVisible = !isSymbolBarVisible },
                            isSymbolBarVisible = isSymbolBarVisible,
                            onWordwrapToggle = {
                                isWordwrap = !isWordwrap
                                editorState.value?.isWordwrap = isWordwrap
                            },
                            isWordwrap = isWordwrap,
                            onLineNumberToggle = {
                                isLineNumberVisible = !isLineNumberVisible
                                editorState.value?.isLineNumberEnabled = isLineNumberVisible
                            },
                            isLineNumberVisible = isLineNumberVisible,
                            onPinLineNumberToggle = {
                                isLineNumberPinned = !isLineNumberPinned
                                editorState.value?.setPinLineNumber(isLineNumberPinned)
                            },
                            isLineNumberPinned = isLineNumberPinned,
                            onMagnifierToggle = {
                                isMagnifierEnabled = !isMagnifierEnabled
                                editorState.value?.getComponent(Magnifier::class.java)?.isEnabled = isMagnifierEnabled
                            },
                            isMagnifierEnabled = isMagnifierEnabled,
                            onIcuToggle = {
                                useIcu = !useIcu
                                editorState.value?.props?.useICULibToSelectWords = useIcu
                            },
                            useIcu = useIcu,
                            onCompletionAnimToggle = {
                                completionAnim = !completionAnim
                                editorState.value?.getComponent(EditorAutoCompletion::class.java)
                                    ?.setEnabledAnimation(completionAnim)
                            },
                            completionAnim = completionAnim,
                            onSoftKbdToggle = {
                                softKbdEnabled = !softKbdEnabled
                                editorState.value?.isSoftKeyboardEnabled = softKbdEnabled
                            },
                            softKbdEnabled = softKbdEnabled,
                            onHardKbdToggle = {
                                hardKbdDisabled = !hardKbdDisabled
                                editorState.value?.isDisableSoftKbdIfHardKbdAvailable = hardKbdDisabled
                            },
                            hardKbdDisabled = hardKbdDisabled,
                            // Configuration
                            onSwitchLanguage = { showLanguageChoice() },
                            onSwitchColors = { showThemeChoice() },
                            onSwitchTypeface = { showTypefaceChoice() }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        if (editorVm.searchPanelVisible && searchController != null) {
                            EditorSearchPanel(editorVm, searchController)
                        }
                        
                        Editor(
                            modifier = Modifier.weight(1f),
                            filePath = filePathState.value,
                            onEditorCreated = { editor ->
                                editorState.value = editor
                                symbolInputView.bindEditor(editor)
                                editor.subscribeAlways(SelectionChangeEvent::class.java) {
                                    updatePositionText()
                                }
                                updatePositionText()
                            }
                        )

                        Column(modifier = Modifier.imePadding()) {
                            if (isSymbolBarVisible) {
                                AndroidView(
                                    factory = { symbolInputView },
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
        }

        drawerView.setContent {
            AppTheme {
                FileTreeDrawer(
                    rootPath = projectPath.path,
                    onFileClick = { path ->
                        if (!path.endsWith(".apk", ignoreCase = true)) {
                            filePathState.value = path
                        }
                        drawerLayout.closeDrawer(Gravity.START)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gradleManager.onDestroy()
    }

    fun navigateTo(uri: String, line: Int, column: Int) {
        // coordinator.navigateTo(uri, line, column, projectRoot)
    }

    private fun updatePositionText() {
        val editor = editorState.value ?: return
        val cursor = editor.cursor
        var text = "${cursor.leftLine + 1}:${cursor.leftColumn};${cursor.left} "
        text += if (cursor.isSelected) {
            "(${cursor.right - cursor.left} chars)"
        } else {
            "(${editor.text.getLine(
                cursor.leftLine
            ).toString().getOrNull(cursor.leftColumn) ?: ' '})"
        }

        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            val idx = searcher.currentMatchedPositionIndex
            val count = searcher.matchedPositionCount
            text += if (idx == -1) "(no match)" else "(${idx + 1} of $count matches)"
        }
        editorVm.positionText = text
    }

    private fun showTypefaceChoice() {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu Mono", "Google/Roboto Mono")
        val assetsPaths =
            arrayOf("JetBrainsMono-Regular.ttf", "UbuntuMono-Regular.ttf", "RobotoMono-Regular.ttf")
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Typeface")
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in assetsPaths.indices) {
                    runCatching {
                        editorState.value?.typefaceText =
                            android.graphics.Typeface.createFromAsset(assets, assetsPaths[which])
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguageChoice() {
        val languages = arrayOf("Java", "Kotlin", "Python", "C", "C++", "HTML", "JavaScript", "Markdown", "Text")
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { dialog, which ->
                val editor = editorState.value ?: return@setItems
                when (languages[which]) {
                    "Java" -> editor.setEditorLanguage(io.github.rosemoe.sora.langs.java.JavaLanguage())
                    "Text" -> editor.setEditorLanguage(io.github.rosemoe.sora.lang.EmptyLanguage())
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showThemeChoice() {
        val themes = arrayOf("Default", "GitHub", "Eclipse", "Darcula", "VS2019", "NotepadXX")
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Color Scheme")
            .setItems(themes) { dialog, which ->
                val editor = editorState.value ?: return@setItems
                when (which) {
                    0 -> editor.colorScheme = io.github.rosemoe.sora.widget.schemes.EditorColorScheme()
                    1 -> editor.colorScheme = io.github.rosemoe.sora.widget.schemes.SchemeGitHub()
                    2 -> editor.colorScheme = io.github.rosemoe.sora.widget.schemes.SchemeEclipse()
                    3 -> editor.colorScheme = io.github.rosemoe.sora.widget.schemes.SchemeDarcula()
                    4 -> editor.colorScheme = io.github.rosemoe.sora.widget.schemes.SchemeVS2019()
                    5 -> editor.colorScheme = io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    onNavigationClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBuildClick: () -> Unit,
    onSyncClick: () -> Unit,
    onTerminalClick: () -> Unit,
    // Search Actions
    onSearchActionMode: () -> Unit,
    onSearchPanelToggle: () -> Unit,
    searchPanelVisible: Boolean,
    // Feature Switches
    onSymbolBarToggle: () -> Unit,
    isSymbolBarVisible: Boolean,
    onWordwrapToggle: () -> Unit,
    isWordwrap: Boolean,
    onLineNumberToggle: () -> Unit,
    isLineNumberVisible: Boolean,
    onPinLineNumberToggle: () -> Unit,
    isLineNumberPinned: Boolean,
    onMagnifierToggle: () -> Unit,
    isMagnifierEnabled: Boolean,
    onIcuToggle: () -> Unit,
    useIcu: Boolean,
    onCompletionAnimToggle: () -> Unit,
    completionAnim: Boolean,
    onSoftKbdToggle: () -> Unit,
    softKbdEnabled: Boolean,
    onHardKbdToggle: () -> Unit,
    hardKbdDisabled: Boolean,
    // Configuration
    onSwitchLanguage: () -> Unit,
    onSwitchColors: () -> Unit,
    onSwitchTypeface: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.statusBarsPadding().height(50.dp),
        title = {},
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onUndoClick) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedoClick) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onSaveClick) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }
            IconButton(onClick = onBuildClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Build/Run")
            }
            IconButton(onClick = onSyncClick) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync")
            }
            IconButton(onClick = onTerminalClick) {
                Icon(Icons.Filled.Terminal, contentDescription = "Terminal")
            }
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Search
                Text(
                    "Search",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenuItem(
                    text = { Text("Action Mode") },
                    onClick = { onSearchActionMode(); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Search Panel") },
                    onClick = { onSearchPanelToggle(); expanded = false },
                    trailingIcon = { Checkbox(checked = searchPanelVisible, onCheckedChange = null) }
                )

                HorizontalDivider()

                // Feature Switches
                Text(
                    "Feature Switches",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenuItem(
                    text = { Text("Symbol Bar") },
                    onClick = { onSymbolBarToggle() },
                    trailingIcon = { Checkbox(checked = isSymbolBarVisible, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Wordwrap") },
                    onClick = { onWordwrapToggle() },
                    trailingIcon = { Checkbox(checked = isWordwrap, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Line Number") },
                    onClick = { onLineNumberToggle() },
                    trailingIcon = { Checkbox(checked = isLineNumberVisible, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Pin Line Number") },
                    onClick = { onPinLineNumberToggle() },
                    trailingIcon = { Checkbox(checked = isLineNumberPinned, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Magnifier") },
                    onClick = { onMagnifierToggle() },
                    trailingIcon = { Checkbox(checked = isMagnifierEnabled, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Use ICU") },
                    onClick = { onIcuToggle() },
                    trailingIcon = { Checkbox(checked = useIcu, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Completion Animation") },
                    onClick = { onCompletionAnimToggle() },
                    trailingIcon = { Checkbox(checked = completionAnim, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Soft Keyboard") },
                    onClick = { onSoftKbdToggle() },
                    trailingIcon = { Checkbox(checked = softKbdEnabled, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Disable Soft Kbd on Hard Kbd") },
                    onClick = { onHardKbdToggle() },
                    trailingIcon = { Checkbox(checked = hardKbdDisabled, onCheckedChange = null) }
                )

                HorizontalDivider()

                // Configuration
                Text(
                    "Configuration",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenuItem(
                    text = { Text("Switch Language") },
                    onClick = { onSwitchLanguage(); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Switch Color Scheme") },
                    onClick = { onSwitchColors(); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Switch Typeface") },
                    onClick = { onSwitchTypeface(); expanded = false }
                )
            }
        }
    )
}

@Composable
fun Editor(
    modifier: Modifier = Modifier,
    filePath: String?,
    onEditorCreated: (CodeEditor) -> Unit
) {
    var editor by remember {
        mutableStateOf<CodeEditor?>(null)
    }
    LaunchedEffect(filePath) {
        filePath?.let { path ->

            val content = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(path).readText()
                }.getOrNull()
            }

            content?.let {
                editor?.setText(it)
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                editor = this
                onEditorCreated(this)
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE
                setEditorLanguage(JavaLanguage())
                props.stickyScroll = true
                props.overScrollEnabled = true
                isCursorAnimationEnabled = true
                nonPrintablePaintingFlags =
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                    CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                    CodeEditor.FLAG_DRAW_SOFT_WRAP
            }
        },
        onRelease = { editor ->
            editor.release()
        }
    )
}
