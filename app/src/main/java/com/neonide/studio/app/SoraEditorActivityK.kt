package com.neonide.studio.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.appbar.MaterialToolbar
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import com.neonide.studio.app.editor.xml.inline.XmlColorHighlighter
import com.neonide.studio.app.lsp.EditorLspControllerFactory
import com.neonide.studio.app.lsp.server.JavaLanguageServerService
import com.neonide.studio.filetree.FileTreeDrawer
import com.neonide.studio.ui.theme.AppTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.CreateContextMenuEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import java.io.File
import java.io.IOException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Termux editor activity with sora-editor demo feature set.
 */
class SoraEditorActivityK : AppCompatActivity() {

    private val uiScope = MainScope()

    internal var currentFile: File? = null
    private var projectRoot: File? = null

    private val bottomSheetVm: BottomSheetViewModel by viewModels()
    private val editorVm: EditorViewModel by viewModels()

    private val fileManager: EditorFileManager by lazy { EditorFileManager(this) }
    private val themeManager: EditorThemeAndLanguageManager by lazy {
        EditorThemeAndLanguageManager(editor)
    }
    private val gradleManager: EditorGradleManager by lazy {
        EditorGradleManager(this, bottomSheetVm)
    }
    internal val uiManager: EditorUiManager by lazy {
        EditorUiManager(this, editor, gradleManager, uiScope)
    }
    private val logManager: EditorLogManager by lazy {
        EditorLogManager(this, editor, bottomSheetVm, uiScope)
    }
    private val lspManager: EditorLspManager by lazy {
        EditorLspManager(this, editor, bottomSheetVm, uiScope)
    }
    private val dialogManager: EditorDialogManager by lazy {
        EditorDialogManager(dialogHelper)
    }
    private val viewHelper: EditorViewHelper by lazy {
        EditorViewHelper(this, editor, editorVm)
    }
    private val setupManager: EditorSetupManager by lazy {
        EditorSetupManager(this, editor, uiManager, lspManager, viewHelper)
    }

    private val coordinator: EditorCoordinator by lazy {
        EditorCoordinator(
            this,
            editor,
            fileManager,
            languageProvider,
            lspManager,
            viewHelper,
            uiManager,
            uiScope
        )
    }
    private val menuHandler: EditorMenuHandler by lazy {
        EditorMenuHandler(
            this, editor, gradleManager, logManager,
            dialogManager, searchManager, coordinator, uiManager, lspManager
        )
    }

    private lateinit var searchManager: EditorSearchManager
    private lateinit var dialogHelper: EditorDialogHelper

    internal var undoItem: MenuItem? = null
    internal var redoItem: MenuItem? = null

    fun getProjectRootDir(): File? = projectRoot

    companion object {
        const val EXTRA_PROJECT_DIR = "extra_project_dir"
        private const val LOG_BUFFER_SIZE = 200
        private const val CONTENT_CHANGE_DELAY_MS = 50L
        private const val XML_DIAGNOSTIC_DELAY_MS = 180L

        private val SYMBOLS = arrayOf(
            "->", "{", "}", "(", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<", ">", "[", "]", ":"
        )

        private val SYMBOL_INSERT_TEXT = arrayOf(
            "\t", "{}", "}", "()", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<>", ">", "[]", "]", ":"
        )
    }

    private lateinit var editor: CodeEditor

    private val xmlDiagnosticsRunnable: Runnable = Runnable {
        val f = currentFile
        if (f == null || !f.extension.equals("xml", ignoreCase = true)) return@Runnable
        val lspConnected = lspManager.controller.currentEditor()?.isConnected == true
        if (!lspConnected) {
            runCatching {
                val diags = AndroidXmlLanguageEnhancer.computeXmlDiagnostics(editor.text)
                editor.setDiagnostics(diags)
            }
        }
        runCatching {
            val version = editor.text.documentVersion
            val highlights = XmlColorHighlighter.computeHighlights(editor.text)
            if (version == editor.text.documentVersion) editor.highlightTexts = highlights
        }
    }

    private lateinit var languageProvider: SoraLanguageProvider

    private val loadTMTLauncher = registerForActivityResult(GetContent()) { result: Uri? ->
        try {
            if (result == null) return@registerForActivityResult
            themeManager.setupTextmate()
            contentResolver.openInputStream(result)?.use { stream ->
                ThemeRegistry.getInstance().loadTheme(
                    IThemeSource.fromInputStream(stream, result.path, null)
                )
            }
            editor.colorScheme = editor.colorScheme
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val loadTMLLauncher = registerForActivityResult(GetContent()) { result: Uri? ->
        try {
            if (result == null) return@registerForActivityResult
            contentResolver.openInputStream(result)?.use { stream ->
                val editorLanguage = editor.editorLanguage
                val grammarSource = IGrammarSource.fromInputStream(stream, result.path, null)
                val language = if (editorLanguage is TextMateLanguage) {
                    editorLanguage.updateLanguage(
                        DefaultGrammarDefinition.withGrammarSource(grammarSource)
                    )
                    editorLanguage
                } else {
                    TextMateLanguage.create(
                        DefaultGrammarDefinition.withGrammarSource(grammarSource),
                        true
                    )
                }
                editor.setEditorLanguage(language)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        editor = CodeEditor(this)
        val symbolInput = SymbolInputView(this)

        languageProvider = SoraLanguageProvider(this)

        val searchController = EditorSearchController(this, editor, editorVm)
        searchManager = EditorSearchManager(searchController, editor)

        dialogHelper = EditorDialogHelper(
            this,
            editor,
            languageProvider,
            loadTMTLauncher,
            loadTMLLauncher
        )

        projectRoot = savedInstanceState?.getString("project_root_path")?.let { File(it) }
            ?: intent.getStringExtra(EXTRA_PROJECT_DIR)?.let { File(it) }

        val toolbar = MaterialToolbar(this).apply {
            val tv = TypedValue()
            val height = if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        setSupportActionBar(toolbar)
        uiManager.toolbar = toolbar
        uiManager.setupAcsBottomSheet() // Initialize state

        setContent {
            AppTheme {
                val scaffoldState = rememberBottomSheetScaffoldState()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    uiManager.scaffoldState = scaffoldState
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            FileTreeDrawer(
                                rootPath = projectRoot?.absolutePath ?: "",
                                onFileClick = { path ->
                                    if (!path.endsWith(".apk", ignoreCase = true)) {
                                        coordinator.openFileInEditor(
                                            File(path),
                                            File(path).name,
                                            projectRoot
                                        )
                                    }
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    SoraEditorScreen(
                        editor = editor,
                        symbolInput = symbolInput,
                        editorVm = editorVm,
                        bottomSheetVm = bottomSheetVm,
                        searchController = searchController,
                        scaffoldState = scaffoldState,
                        topBar = {
                            AndroidView(
                                factory = { toolbar },
                                update = { view ->
                                    view.setNavigationIcon(R.drawable.ic_menu)
                                    view.setNavigationOnClickListener {
                                        scope.launch { drawerState.open() }
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            Column {
                                if (uiManager.symbolBarVisible) {
                                    AndroidView(factory = { ctx ->
                                        android.widget.HorizontalScrollView(ctx).apply {
                                            isHorizontalScrollBarEnabled = false
                                            addView(
                                                symbolInput,
                                                android.view.ViewGroup.LayoutParams(
                                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            )
                                        }
                                    })
                                }
                                Text(
                                    text = editorVm.positionText,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    )
                }
            }
        }

        setupManager.setupUi(projectRoot)
        setupManager.setupEditor(symbolInput, SYMBOLS, SYMBOL_INSERT_TEXT)
        setupManager.setupEventListeners(xmlDiagnosticsRunnable, {
            currentFile
        }, { undoItem }, { redoItem })

        setupManager.initializeProject(savedInstanceState, themeManager, coordinator, projectRoot)
        logManager.refreshAppLogs(LOG_BUFFER_SIZE)
        viewHelper.updatePositionText()
        uiManager.updateBtnState(undoItem, redoItem)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        themeManager.switchTheme(isNight)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentFile?.let { outState.putString("current_file_path", it.absolutePath) }
        projectRoot?.let { outState.putString("project_root_path", it.absolutePath) }
        if (this::editor.isInitialized) {
            outState.putInt("cursor_line", editor.cursor.leftLine)
            outState.putInt("cursor_column", editor.cursor.leftColumn)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (uiManager.collapseBottomSheet()) return
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { editor.removeCallbacks(xmlDiagnosticsRunnable) }
        gradleManager.onDestroy()
        runCatching { uiScope.cancel() }
        runCatching { lspManager.dispose() }
        editor.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        themeManager.switchTheme(isNight)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sora_main, menu)
        undoItem = menu.findItem(R.id.sora_text_undo)
        redoItem = menu.findItem(R.id.sora_text_redo)
        menu.findItem(R.id.sora_symbol_bar_visibility).isChecked = uiManager.symbolBarVisible
        uiManager.updateBtnState(undoItem, redoItem)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        menuHandler.handleMenuItemSelection(item, projectRoot) ||
            super.onOptionsItemSelected(item)

    fun updateBtnState() {
        uiManager.updateBtnState(undoItem, redoItem)
    }

    fun navigateTo(uri: String, line: Int, column: Int) {
        coordinator.navigateTo(uri, line, column, projectRoot)
    }
}
