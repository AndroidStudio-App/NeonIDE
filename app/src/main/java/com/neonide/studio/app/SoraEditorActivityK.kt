package com.neonide.studio.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import com.neonide.studio.app.editor.xml.inline.XmlColorHighlighter
import com.neonide.studio.app.lsp.EditorLspControllerFactory
import com.neonide.studio.app.lsp.server.JavaLanguageServerService
import com.google.android.material.appbar.MaterialToolbar
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.CreateContextMenuEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.io.IOException

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
    private val uiManager: EditorUiManager by lazy {
        EditorUiManager(this, editor, gradleManager)
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
        EditorViewHelper(this, editor)
    }
    private val setupManager: EditorSetupManager by lazy {
        EditorSetupManager(this, editor, uiManager, lspManager, viewHelper)
    }
    
    private val coordinator: EditorCoordinator by lazy {
        EditorCoordinator(
            this, editor, fileManager, languageProvider, 
            lspManager, viewHelper, uiManager, uiScope
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
            "\t", "{}", "}", "(", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<", ">", "[", "]", ":"
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
                ThemeRegistry.getInstance().loadTheme(IThemeSource.fromInputStream(stream, result.path, null))
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
                    editorLanguage.updateLanguage(DefaultGrammarDefinition.withGrammarSource(grammarSource))
                    editorLanguage
                } else {
                    TextMateLanguage.create(DefaultGrammarDefinition.withGrammarSource(grammarSource), true)
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
        setContentView(R.layout.activity_sora_editor)
        editor = findViewById(R.id.editor)
        languageProvider = SoraLanguageProvider(this)
        
        val searchController = EditorSearchController(
            this, editor, findViewById(R.id.search_panel), 
            findViewById(R.id.search_editor), findViewById(R.id.replace_editor), 
            findViewById(R.id.search_options)
        )
        searchManager = EditorSearchManager(searchController, editor)
        
        dialogHelper = EditorDialogHelper(this, editor, languageProvider, loadTMTLauncher, loadTMLLauncher)
        
        projectRoot = savedInstanceState?.getString("project_root_path")?.let { File(it) } ?: 
            intent.getStringExtra(EXTRA_PROJECT_DIR)?.let { File(it) }
            
        setupManager.setupUi(projectRoot) { f, t -> coordinator.openFileInEditor(f, t, projectRoot) }
        setupManager.setupEditor(SYMBOLS, SYMBOL_INSERT_TEXT)
        setupManager.setupEventListeners(xmlDiagnosticsRunnable, { currentFile }, { undoItem }, { redoItem })
        
        setupManager.initializeProject(savedInstanceState, themeManager, coordinator, projectRoot)
        logManager.refreshAppLogs(LOG_BUFFER_SIZE)
        viewHelper.updatePositionText(findViewById(R.id.position_display))
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
        val barVisible = findViewById<View>(R.id.main_bottom_bar).visibility == View.VISIBLE
        menu.findItem(R.id.sora_symbol_bar_visibility).isChecked = barVisible
        uiManager.updateBtnState(undoItem, redoItem)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return menuHandler.handleMenuItemSelection(item, projectRoot) || super.onOptionsItemSelected(item)
    }

    fun updateBtnState() {
        uiManager.updateBtnState(undoItem, redoItem)
    }

    fun navigateTo(uri: String, line: Int, column: Int) {
        coordinator.navigateTo(uri, line, column, projectRoot)
    }
}
