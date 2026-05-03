package com.neonide.studio.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.neonide.studio.R
import com.termux.app.TermuxActivity
import com.termux.app.TermuxService
import com.termux.shared.termux.TermuxConstants
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.EditorBottomSheetTabAdapter
import com.neonide.studio.app.lsp.LspClient
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.bottomsheet.model.NavigationItem
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.CreateContextMenuEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.completion.snippetUpComparator
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import com.itsaky.androidide.treesitter.TreeSitter
import com.itsaky.androidide.treesitter.java.TSLanguageJava
import com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin
import com.itsaky.androidide.treesitter.xml.TSLanguageXml
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry as MonarchThemeRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import io.github.rosemoe.sora.widget.style.LineInfoPanelPosition
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import com.neonide.studio.utils.GradleService
import com.neonide.studio.utils.GradleBuildStatus
import com.neonide.studio.utils.GradleProjectActions
import com.neonide.studio.utils.ApkInstallUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Termux editor activity with sora-editor demo feature set.
 */
class SoraEditorActivityK : AppCompatActivity() {

    private val uiScope = MainScope()

    private val gradleStatusListener: (Boolean) -> Unit = { isRunning ->
        gradleRunning = isRunning
        runOnUiThread {
            updateBtnState()
        }
    }
    @Volatile private var gradleRunning: Boolean = false

    private val lspController by lazy { com.neonide.studio.app.lsp.EditorLspControllerFactory.createOrNoop(this) }
    private var currentFile: File? = null
    private var projectRoot: File? = null

    private val bottomSheetVm: BottomSheetViewModel by viewModels()
    private val editorVm: EditorViewModel by viewModels()

    fun getProjectRootDir(): File? = projectRoot

    companion object {
        const val EXTRA_PROJECT_DIR = "extra_project_dir"

        private const val CRASH_LOG_FILE = "crash-journal.log"
        private const val GRADLE_LOG_FILE = "gradle-build.log"
        private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1

        private const val MENU_ID_DEFINITION = 1001
        private const val MENU_ID_REFERENCES = 1002
        private const val MENU_ID_HOVER = 1003

        private const val TAB_INDEX_REFERENCES = 5

        // Same symbols as sora-editor demo
        private val SYMBOLS = arrayOf(
            "->", "{", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
        )

        private val SYMBOL_INSERT_TEXT = arrayOf(
            "\t", "{}", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
        )
    }

    private lateinit var editor: CodeEditor
    private lateinit var drawerLayout: DrawerLayout

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetView: View? = null

    // --- XML diagnostics (tree-sitter based) ---
    private val xmlDiagnosticsRunnable: Runnable = Runnable {
        val f = currentFile
        if (f == null || !f.extension.equals("xml", ignoreCase = true)) return@Runnable

        // If LSP is connected for XML, prefer its diagnostics.
        val lspEditor = lspController.currentEditor()
        val lspConnected = (lspEditor != null && lspEditor.isConnected)

        // 1) Syntax diagnostics (tree-sitter) when LSP isn't available
        if (!lspConnected) {
            runCatching {
                val diags = com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.computeXmlDiagnostics(editor.text)
                editor.setDiagnostics(diags)
            }
        }

        // 2) Inline color highlights (#RRGGBB/#AARRGGBB, etc.)
        runCatching {
            val version = editor.text.documentVersion
            val highlights = com.neonide.studio.app.editor.xml.inline.XmlColorHighlighter.computeHighlights(editor.text)
            // Discard if document changed while we were computing
            if (version == editor.text.documentVersion) {
                editor.highlightTexts = highlights
            }
        }
    }

    private lateinit var languageProvider: SoraLanguageProvider

    private lateinit var searchMenu: PopupMenu
    private var searchOptions: EditorSearcher.SearchOptions =
        EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true, RegexBackrefGrammar.DEFAULT)

    private var undoItem: MenuItem? = null
    private var redoItem: MenuItem? = null

    private val loadTMTLauncher = registerForActivityResult(GetContent()) { result: Uri? ->
        try {
            if (result == null) return@registerForActivityResult

            ensureTextmateTheme()

            ThemeRegistry.getInstance().loadTheme(
                IThemeSource.fromInputStream(
                    contentResolver.openInputStream(result),
                    result.path,
                    null
                )
            )

            // Re-apply to refresh editor colors
            val cs = editor.colorScheme
            editor.colorScheme = cs

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val loadTMLLauncher = registerForActivityResult(GetContent()) { result: Uri? ->
        try {
            if (result == null) return@registerForActivityResult

            val editorLanguage = editor.editorLanguage

            val grammarSource = IGrammarSource.fromInputStream(
                contentResolver.openInputStream(result),
                result.path,
                null
            )

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

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sora_editor)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        val isBarVisible = prefs.getBoolean("symbol_bar_visible", true)
        val bar = findViewById<View>(R.id.main_bottom_bar)
        
        bar.visibility = if (isBarVisible) View.VISIBLE else View.GONE

        // Sync layout with keyboard animation for smooth opening
        val drawer = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(drawer) { v, insets ->
            val imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val systemInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Apply bottom padding for IME and top padding for status bar
            v.setPadding(0, systemInsets.top, 0, imeInsets.bottom)
            insets
        }

        drawer.setWindowInsetsAnimationCallback(object : android.view.WindowInsetsAnimation.Callback(android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
            override fun onProgress(insets: android.view.WindowInsets, runningAnimations: MutableList<android.view.WindowInsetsAnimation>): android.view.WindowInsets {
                val imeInsets = insets.getInsets(android.view.WindowInsets.Type.ime())
                val systemInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                drawer.setPadding(0, systemInsets.top, 0, imeInsets.bottom)
                return insets
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
            }
        }

        drawerLayout = findViewById(R.id.drawer_layout)

        val tb: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.title = "Editor"

        setupAcsBottomSheet()

        languageProvider = SoraLanguageProvider(this)
        editor = findViewById(R.id.editor)

        // Typeface
        runCatching {
            editor.typefaceText = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")
        }

        // Configure symbol input
        findViewById<io.github.rosemoe.sora.widget.SymbolInputView>(R.id.symbol_input).apply {
            bindEditor(editor)
            addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT)
        }

        // Configure search options popup
        searchMenu = PopupMenu(this, findViewById(R.id.search_options)).apply {
            menuInflater.inflate(R.menu.menu_sora_search_options, menu)
            setOnMenuItemClickListener { item ->
                item.isChecked = !item.isChecked
                // Regex and whole word are mutually exclusive
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

        // Search text change listener
        findViewById<android.widget.EditText>(R.id.search_editor).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { tryCommitSearch() }
        })

        // Buttons
        findViewById<View>(R.id.btn_goto_prev).setOnClickListener { gotoPrev() }
        findViewById<View>(R.id.btn_goto_next).setOnClickListener { gotoNext() }
        findViewById<View>(R.id.btn_replace).setOnClickListener { replaceCurrent() }
        findViewById<View>(R.id.btn_replace_all).setOnClickListener { replaceAll() }
        findViewById<View>(R.id.search_options).setOnClickListener { searchMenu.show() }

        // Base editor config
        // Set a default empty language. The actual language will be set when a file is opened.
        editor.setEditorLanguage(EmptyLanguage())
        editor.props.stickyScroll = true
        editor.nonPrintablePaintingFlags =
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                CodeEditor.FLAG_DRAW_SOFT_WRAP

        // Events for position display + undo/redo
        editor.subscribeAlways(SelectionChangeEvent::class.java) { updatePositionText() }
        editor.subscribeAlways(PublishSearchResultEvent::class.java) { updatePositionText() }
        editor.subscribeAlways(ContentChangeEvent::class.java) { ev ->
            editor.postDelayed({ updateBtnState() }, 50)

            // --- Android XML editing enhancements (ACS-like) ---
            val f = currentFile
            if (f != null && f.extension.equals("xml", ignoreCase = true)) {
                // 1) Advanced edit: typing '/' inside XML auto-completes tags (e.g., '</' -> '</TagName>')
                runCatching {
                    com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.applyAdvancedSlashEditIfNeeded(f, editor, ev)
                }

                // 2) Tree-sitter XML diagnostics (syntax errors) -> squiggles
                // Debounce: avoid reparsing on every keystroke burst
                editor.removeCallbacks(xmlDiagnosticsRunnable)
                editor.postDelayed(xmlDiagnosticsRunnable, 180)
            }
        }
        editor.subscribeAlways(CreateContextMenuEvent::class.java) { onContextMenuCreated(it) }

        // Touch devices typically show EditorTextActionWindow (copy/cut/paste) instead of Android ContextMenu.
        // Provide JavaDoc/hover on long-press by intercepting the long-press and requesting LSP hover.
        editor.subscribeAlways(io.github.rosemoe.sora.event.LongPressEvent::class.java) { e ->
            val file = currentFile
            if (file != null && file.extension.lowercase() == "java") {
                val lspEditor = lspController.currentEditor()
                // Only show when LSP is connected/available
                if (lspEditor != null && lspEditor.isConnected) {
                    // Anchor hover window position at the pressed location
                    editor.setSelection(e.line, e.column)
                    handleShowHover(e.line, e.column)
                    // Do NOT intercept: keep default long-press selection/tool actions working
                }
            }
        }

        editor.subscribeAlways(SideIconClickEvent::class.java) {
            editor.setSelection(it.clickedIcon.line, 0)
            editor.getComponent(EditorDiagnosticTooltipWindow::class.java).show()
        }

        // Allow launching the editor with a specific project directory
        projectRoot = savedInstanceState?.getString("project_root_path")?.let { File(it) }
            ?: intent.getStringExtra(EXTRA_PROJECT_DIR)?.let { File(it) }

        // Load themes/grammars
        setupTextmate()
        setupMonarch()
        ensureTextmateTheme()

        // Restore last file or load sample
        val restoredFilePath = savedInstanceState?.getString("current_file_path")
        val restoredFile = restoredFilePath?.let { File(it) }
        if (restoredFile != null && restoredFile.exists()) {
            openFileInEditor(restoredFile, restoredFile.name)
            val line = savedInstanceState.getInt("cursor_line", 0)
            val column = savedInstanceState.getInt("cursor_column", 0)
            editor.setSelection(line, column)
        } else {
            // Load sample (no LSP) - use Java sample to demonstrate Tree-sitter integration
            openAssetsFile("samples/sample.java")
        }

        // Prefill IDE logs tab with crash log (if any)
        runCatching {
            val f = File(filesDir, CRASH_LOG_FILE)
            if (f.exists()) bottomSheetVm.setIdeLogs(f.readText())
        }

        updatePositionText()
        updateBtnState()
        switchThemeIfRequired()

        // LSP diagnostics UI is handled by editor-lsp (see PublishDiagnosticsEvent)
        // and shown via CodeEditor diagnostics tooltip.

        // LSP tree-sitter libs are heavy; do NOT load tree-sitter native libs automatically.
        GradleBuildStatus.addListener(gradleStatusListener)
        gradleRunning = GradleBuildStatus.isRunning
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

    override fun onBackPressed() {
        // If bottom sheet is expanded, collapse it first (ACS-like behavior)
        runCatching {
            val sheet = findViewById<View>(R.id.acs_bottom_sheet)
            val behavior = BottomSheetBehavior.from(sheet)
            if (behavior.state == BottomSheetBehavior.STATE_EXPANDED || behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return
            }
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop pending XML diagnostics update
        runCatching { editor.removeCallbacks(xmlDiagnosticsRunnable) }

        GradleBuildStatus.removeListener(gradleStatusListener)
        runCatching { uiScope.cancel() }
        runCatching { lspController.dispose() }
        editor.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        switchThemeIfRequired()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sora_main, menu)
        undoItem = menu.findItem(R.id.sora_text_undo)
        redoItem = menu.findItem(R.id.sora_text_redo)
        menu.findItem(R.id.sora_symbol_bar_visibility).isChecked = findViewById<View>(R.id.main_bottom_bar).visibility == View.VISIBLE
        updateBtnState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sora_quick_run -> {
                onQuickRunOrCancel()
                return true
            }
            R.id.sora_sync_project -> {
                onSyncProject()
                return true
            }
            R.id.sora_text_undo -> editor.undo()
            R.id.sora_text_redo -> editor.redo()
            R.id.sora_open_terminal -> {
                runCatching {
                    startActivity(Intent(this, TermuxActivity::class.java))
                }
            }

            R.id.sora_goto_end -> editor.setSelection(editor.text.lineCount - 1, editor.text.getColumnCount(editor.text.lineCount - 1))
            R.id.sora_move_up -> editor.moveSelection(SelectionMovement.UP)
            R.id.sora_move_down -> editor.moveSelection(SelectionMovement.DOWN)
            R.id.sora_move_left -> editor.moveSelection(SelectionMovement.LEFT)
            R.id.sora_move_right -> editor.moveSelection(SelectionMovement.RIGHT)
            R.id.sora_home -> editor.moveSelection(SelectionMovement.LINE_START)
            R.id.sora_end -> editor.moveSelection(SelectionMovement.LINE_END)

            R.id.sora_magnifier -> {
                item.isChecked = !item.isChecked
                editor.getComponent(Magnifier::class.java).isEnabled = item.isChecked
            }

            R.id.sora_symbol_bar_visibility -> {
                item.isChecked = !item.isChecked
                val isVisible = item.isChecked
                findViewById<View>(R.id.main_bottom_bar).visibility = if (isVisible) View.VISIBLE else View.GONE
                getPreferences(Context.MODE_PRIVATE).edit().putBoolean("symbol_bar_visible", isVisible).apply()
            }

            R.id.sora_text_wordwrap -> {
                item.isChecked = !item.isChecked
                editor.isWordwrap = item.isChecked
            }

            R.id.sora_editor_line_number -> {
                editor.isLineNumberEnabled = !editor.isLineNumberEnabled
                item.isChecked = editor.isLineNumberEnabled
            }

            R.id.sora_pin_line_number -> {
                editor.setPinLineNumber(!editor.isLineNumberPinned)
                item.isChecked = editor.isLineNumberPinned
            }

            R.id.sora_use_icu -> {
                item.isChecked = !item.isChecked
                editor.props.useICULibToSelectWords = item.isChecked
            }

            R.id.sora_completion_anim -> {
                item.isChecked = !item.isChecked
                editor.getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(item.isChecked)
            }

            R.id.sora_soft_kbd_enabled -> {
                editor.isSoftKeyboardEnabled = !editor.isSoftKeyboardEnabled
                item.isChecked = editor.isSoftKeyboardEnabled
            }

            R.id.sora_disable_soft_kbd_on_hard_kbd -> {
                editor.isDisableSoftKbdIfHardKbdAvailable = !editor.isDisableSoftKbdIfHardKbdAvailable
                item.isChecked = editor.isDisableSoftKbdIfHardKbdAvailable
            }

            R.id.sora_ln_panel_fixed -> chooseLineNumberPanelPosition()

            R.id.sora_ln_panel_follow -> chooseLineNumberPanelFollow()

            R.id.sora_code_format -> editor.formatCodeAsync()

            R.id.sora_switch_language -> chooseLanguage()

            R.id.sora_search_panel_st -> toggleSearchPanel(item)

            R.id.sora_search_am -> {
                findViewById<android.widget.EditText>(R.id.replace_editor).setText("")
                findViewById<android.widget.EditText>(R.id.search_editor).setText("")
                editor.searcher.stopSearch()
                editor.beginSearchMode()
            }

            R.id.sora_switch_colors -> chooseTheme()
            R.id.sora_switch_typeface -> chooseTypeface()

            R.id.sora_save_file -> saveCurrentFile()
            R.id.sora_open_build_log -> openBuildLog()
            R.id.sora_open_logs -> openLogs()
            R.id.sora_clear_logs -> clearLogs()
            R.id.sora_open_ide_file_log -> openIdeFileLog()

            R.id.sora_load_test_file -> openAssetsFile("samples/big_sample.txt")

            R.id.sora_start_java_lsp -> {
                runCatching {
                    startService(Intent(this, com.neonide.studio.app.lsp.server.JavaLanguageServerService::class.java))
                    android.widget.Toast.makeText(this, "Java LSP server service started", android.widget.Toast.LENGTH_SHORT).show()
                }.onFailure {
                    android.widget.Toast.makeText(this, "Failed to start Java LSP: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }

        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupAcsBottomSheet() {
        val sheet = findViewById<View>(R.id.acs_bottom_sheet)
        bottomSheetView = sheet
        val behavior = BottomSheetBehavior.from(sheet)
        bottomSheetBehavior = behavior
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        behavior.isHideable = false

        // Hide the status label by default (we only show it while building)
        runCatching {
            val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
            status.visibility = View.GONE
        }

        val tabs = sheet.findViewById<TabLayout>(R.id.acs_bottom_sheet_tabs)
        val pager = sheet.findViewById<ViewPager2>(R.id.acs_bottom_sheet_pager)

        val adapter = EditorBottomSheetTabAdapter(this)
        pager.adapter = adapter
        // Match AndroidCodeStudio behavior: don't allow horizontal swipe to change tabs.
        // Otherwise, horizontal scrolling in build output/log views accidentally switches tabs.
        pager.isUserInputEnabled = false

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = getString(adapter.getTitleRes(position))
        }.attach()

        // Load current log files into tabs on startup.
        // Default behavior (closer to ACS): start with empty build output for each IDE session.
        // Users can still open the saved build log from menu.
        BuildOutputBuffer.clear()
        runCatching {
            val buildLog = File(filesDir, GRADLE_LOG_FILE)
            // Don't auto-load old log into the live buffer.
            // (It makes output feel "stale" and prevents seeing fresh output clearly.)
            if (!buildLog.exists()) return@runCatching
        }
        runCatching {
            val crash = File(filesDir, CRASH_LOG_FILE)
            if (crash.exists()) bottomSheetVm.setIdeLogs(crash.readText())
        }

        // Basic App Logs snapshot from logcat
        refreshAppLogs()
    }

    private fun refreshAppLogs() {
        // Best-effort: read a small logcat snapshot. Streaming logcat continuously is expensive.
        uiScope.launch(Dispatchers.IO) {
            val lines = runCatching {
                val p = ProcessBuilder("logcat", "-d", "-t", "200")
                    .redirectErrorStream(true)
                    .start()
                p.inputStream.bufferedReader().readText()
            }.getOrDefault("")

            bottomSheetVm.setAppLogs(lines)
        }
    }

    private fun updateBtnState() {
        undoItem?.isEnabled = editor.canUndo()
        redoItem?.isEnabled = editor.canRedo()

        // Update quick run icon/title based on gradle state
        // (menu item exists only after onCreateOptionsMenu)
        runCatching {
            val m = findViewById<MaterialToolbar>(R.id.toolbar).menu
            val quick = m.findItem(R.id.sora_quick_run)
            if (quick != null) {
                if (gradleRunning) {
                    quick.title = getString(R.string.acs_cancel_build)
                    quick.setIcon(R.drawable.ic_stop_daemons)
                } else {
                    quick.title = getString(R.string.acs_quick_run)
                    quick.setIcon(R.drawable.ic_run_outline)
                }
            }
        }
    }

    private fun onSyncProject() {
        val root = projectRoot
        if (root == null || !root.exists()) {
            android.widget.Toast.makeText(this, getString(R.string.acs_project_dir_missing), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Cancel if already running
        if (gradleRunning) {
            GradleService.stopBuild(this)
            return
        }

        // Ensure wrapper is present (repair missing wrapper jar if possible)
        val wrapperStatus = GradleProjectActions.ensureWrapperPresent(this, root)
        GradleProjectActions.wrapperStatusMessage(this, wrapperStatus)?.let { msg ->
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        if (wrapperStatus == GradleProjectActions.WrapperStatus.MissingScriptOrProps ||
            wrapperStatus == GradleProjectActions.WrapperStatus.RepairFailed
        ) {
            return
        }

        // Run "sync" plan
        android.widget.Toast.makeText(this, getString(R.string.acs_sync_started), android.widget.Toast.LENGTH_SHORT).show()
        val plan = GradleProjectActions.createSyncPlan()
        runGradle(
            projectDir = root,
            args = plan.args,
            actionLabel = getString(R.string.acs_sync_project),
            kind = GradleActionKind.SYNC,
            installApkOnSuccess = false,
        )
    }

    private fun onQuickRunOrCancel() {
        val root = projectRoot
        if (root == null || !root.exists()) {
            android.widget.Toast.makeText(this, getString(R.string.acs_project_dir_missing), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        if (gradleRunning) {
            GradleService.stopBuild(this)
            gradleRunning = false
            updateBtnState()
            return
        }

        // Ensure wrapper is present (repair missing wrapper jar if possible)
        val wrapperStatus = GradleProjectActions.ensureWrapperPresent(this, root)
        GradleProjectActions.wrapperStatusMessage(this, wrapperStatus)?.let { msg ->
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        if (wrapperStatus == GradleProjectActions.WrapperStatus.MissingScriptOrProps ||
            wrapperStatus == GradleProjectActions.WrapperStatus.RepairFailed
        ) {
            return
        }

        android.widget.Toast.makeText(this, getString(R.string.acs_build_started), android.widget.Toast.LENGTH_SHORT).show()

        val plan = GradleProjectActions.createQuickRunPlan(root)
        runGradle(
            projectDir = root,
            args = plan.args,
            actionLabel = getString(R.string.acs_quick_run),
            kind = GradleActionKind.BUILD,
            installApkOnSuccess = true,
        )
    }

    private enum class GradleActionKind { BUILD, SYNC }

    private fun runGradle(
        projectDir: File,
        args: List<String>,
        actionLabel: String,
        kind: GradleActionKind,
        installApkOnSuccess: Boolean,
    ) {
        gradleRunning = true
        invalidateOptionsMenu()
        updateBtnState()

        // Expand bottom sheet and select Build Output tab, ACS-like.
        runCatching {
            val sheet = findViewById<View>(R.id.acs_bottom_sheet)
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

            val pager = sheet.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.acs_bottom_sheet_pager)
            pager.setCurrentItem(0, false)

            val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
            status.text = "$actionLabel: ${getString(R.string.acs_status_building)}"
            status.visibility = View.VISIBLE
        }

        // IMPORTANT: clear live buffer so the Build Output tab shows only the new run.
        BuildOutputBuffer.clear()
        // Also clear diagnostics on new run.
        bottomSheetVm.setDiagnostics(emptyList())

        // Start build in foreground service
        GradleService.startBuild(
            context = this,
            projectDir = projectDir,
            args = args,
            actionLabel = actionLabel,
            installOnSuccess = installApkOnSuccess,
            logFilePath = File(filesDir, GRADLE_LOG_FILE).absolutePath
        )
    }

    private fun updatePositionText() {
        val cursor = editor.cursor
        var text = "${cursor.leftLine + 1}:${cursor.leftColumn};${cursor.left} "

        text += if (cursor.isSelected) {
            "(${cursor.right - cursor.left} chars)"
        } else {
            val content = editor.text
            if (content.getColumnCount(cursor.leftLine) == cursor.leftColumn) {
                val sep = content.getLine(cursor.leftLine).lineSeparator
                "(<${if (sep == LineSeparator.NONE) "EOF" else sep.name}>)"
            } else {
                // best effort
                "(${content.getLine(cursor.leftLine).toString().getOrNull(cursor.leftColumn) ?: ' '})"
            }
        }

        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            val idx = searcher.currentMatchedPositionIndex
            val count = searcher.matchedPositionCount
            val matchText = when (count) {
                0 -> "no match"
                1 -> "1 match"
                else -> "$count matches"
            }
            text += if (idx == -1) {
                "($matchText)"
            } else {
                "(${idx + 1} of $matchText)"
            }
        }

        findViewById<android.widget.TextView>(R.id.position_display).text = text
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

    private fun tryCommitSearch() {
        val query = findViewById<android.widget.EditText>(R.id.search_editor).text
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
        val replacement = findViewById<android.widget.EditText>(R.id.replace_editor).text.toString()
        runCatching { editor.searcher.replaceCurrentMatch(replacement) }
    }

    private fun replaceAll() {
        val replacement = findViewById<android.widget.EditText>(R.id.replace_editor).text.toString()
        runCatching { editor.searcher.replaceAll(replacement) }
    }

    private fun toggleSearchPanel(item: MenuItem) {
        val panel = findViewById<View>(R.id.search_panel)
        if (panel.visibility == View.GONE) {
            findViewById<android.widget.EditText>(R.id.replace_editor).setText("")
            findViewById<android.widget.EditText>(R.id.search_editor).setText("")
            editor.searcher.stopSearch()
            panel.visibility = View.VISIBLE
            item.isChecked = true
        } else {
            panel.visibility = View.GONE
            editor.searcher.stopSearch()
            item.isChecked = false
        }
    }

    private fun openAssetsFile(path: String) {
        // Assets are not part of a real project; disable LSP.
        currentFile = null
        runCatching { lspController.detach() }

        // Set language based on asset extension
        val languageForEditor = languageProvider.getLanguage(File(path))
        editor.setEditorLanguage(languageForEditor)

        uiScope.launch(Dispatchers.Main) {
            val text = withContext(Dispatchers.IO) {
                ContentIO.createFrom(assets.open(path))
            }
            editor.setText(text, null)
            updatePositionText()
            updateBtnState()
        }
    }

    private fun openBuildLog() {
        val logFile = File(filesDir, GRADLE_LOG_FILE)
        if (!logFile.exists()) {
            android.widget.Toast.makeText(this, getString(R.string.sora_not_supported), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { logFile.readText() }
            .onSuccess {
                editor.setText(it)
                bottomSheetVm.setBuildOutput(it)
            }
    }

    private fun openLogs() {
        runCatching { openFileInput(CRASH_LOG_FILE).reader().readText() }
            .onSuccess {
                editor.setText(it)
                bottomSheetVm.setIdeLogs(it)
            }
    }

    private fun clearLogs() {
        runCatching { openFileOutput(CRASH_LOG_FILE, MODE_PRIVATE).use { } }
        bottomSheetVm.setIdeLogs("")
    }

    private fun openIdeFileLog() {
        val logFile = com.neonide.studio.logger.IDEFileLogger.getLogFile()
        if (logFile == null || !logFile.exists()) {
            android.widget.Toast.makeText(this, "IDE file log not found. Enable it in IDE Configurations first.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        runCatching { logFile.readText() }
            .onSuccess {
                editor.setText(it)
                bottomSheetVm.setIdeLogs(it)
            }
            .onFailure {
                android.widget.Toast.makeText(this, "Failed to read IDE log: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    private fun saveCurrentFile() {
        val f = currentFile
        if (f == null) {
            android.widget.Toast.makeText(this, getString(R.string.sora_not_supported), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val text = editor.text.toString()
        val ok = runCatching {
            f.parentFile?.mkdirs()
            f.writeText(text)
            true
        }.getOrDefault(false)

        if (ok) {
            android.widget.Toast.makeText(this, getString(R.string.acs_saved), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(this, getString(R.string.acs_save_failed), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTextmate() {
        loadDefaultTextMateLanguages()
        loadDefaultTextMateThemes()
    }

    private fun loadDefaultTextMateThemes() {
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        val themeRegistry = ThemeRegistry.getInstance()
        themes.forEach { name ->
            val path = "textmate/$name.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(path),
                        path,
                        null
                    ),
                    name
                ).apply { if (name != "quietlight") isDark = true }
            )
        }
        themeRegistry.setTheme("quietlight")
    }

    private fun loadDefaultTextMateLanguages() {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    private fun ensureTextmateTheme() {
        val cs = editor.colorScheme
        if (cs !is TextMateColorScheme) {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }
    }

    private fun setupMonarch() {
        // Themes
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        themes.forEach { name ->
            val path = "textmate/$name.json"
            MonarchThemeRegistry.loadTheme(
                io.github.rosemoe.sora.langs.monarch.registry.model.ThemeModel(
                    ThemeSource(path, name)
                ).apply { if (name != "quietlight") isDark = true },
                false
            )
        }
        MonarchThemeRegistry.setTheme("quietlight")

        // Grammars
        MonarchGrammarRegistry.INSTANCE.loadGrammars(
            monarchLanguages {
                // Use monarch-language-pack definitions
                language("java") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.JavaLanguage
                    defaultScopeName()
                    languageConfiguration = "textmate/java/language-configuration.json"
                }
                language("kotlin") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.KotlinLanguage
                    defaultScopeName()
                    languageConfiguration = "textmate/kotlin/language-configuration.json"
                }
                language("python") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.PythonLanguage
                    defaultScopeName()
                    languageConfiguration = "textmate/python/language-configuration.json"
                }
                language("typescript") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.TypescriptLanguage
                    defaultScopeName()
                    // No bundled language-configuration for TypeScript in assets; keep null
                }
            }
        )
    }

    private fun ensureMonarchTheme() {
        if (editor.colorScheme !is MonarchColorScheme) {
            editor.colorScheme = MonarchColorScheme.create(MonarchThemeRegistry.currentTheme)
            switchThemeIfRequired()
        }
    }

    private fun switchThemeIfRequired() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        when (val scheme = editor.colorScheme) {
            is TextMateColorScheme -> ThemeRegistry.getInstance().setTheme(if (night) "darcula" else "quietlight")
            is MonarchColorScheme -> MonarchThemeRegistry.setTheme(if (night) "darcula" else "quietlight")
            else -> editor.colorScheme = if (night) SchemeDarcula() else EditorColorScheme()
        }
        editor.invalidate()
    }

    private fun chooseTypeface() {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu", "Roboto")
        val assetsPaths = arrayOf("JetBrainsMono-Regular.ttf", "Ubuntu-Regular.ttf", "Roboto-Regular.ttf")
        AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in assetsPaths.indices) {
                    runCatching {
                        editor.typefaceText = Typeface.createFromAsset(assets, assetsPaths[which])
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseLineNumberPanelPosition() {
        val items = arrayOf(
            getString(R.string.sora_top),
            getString(R.string.sora_bottom),
            getString(R.string.sora_left),
            getString(R.string.sora_right),
            getString(R.string.sora_center),
            getString(R.string.sora_top_left),
            getString(R.string.sora_top_right),
            getString(R.string.sora_bottom_left),
            getString(R.string.sora_bottom_right)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.sora_fixed)
            .setSingleChoiceItems(items, -1) { dialog, which ->
                editor.lnPanelPositionMode = LineInfoPanelPositionMode.FIXED
                editor.lnPanelPosition = when (which) {
                    0 -> LineInfoPanelPosition.TOP
                    1 -> LineInfoPanelPosition.BOTTOM
                    2 -> LineInfoPanelPosition.LEFT
                    3 -> LineInfoPanelPosition.RIGHT
                    4 -> LineInfoPanelPosition.CENTER
                    5 -> LineInfoPanelPosition.TOP or LineInfoPanelPosition.LEFT
                    6 -> LineInfoPanelPosition.TOP or LineInfoPanelPosition.RIGHT
                    7 -> LineInfoPanelPosition.BOTTOM or LineInfoPanelPosition.LEFT
                    8 -> LineInfoPanelPosition.BOTTOM or LineInfoPanelPosition.RIGHT
                    else -> LineInfoPanelPosition.CENTER
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseLineNumberPanelFollow() {
        val items = arrayOf(getString(R.string.sora_top), getString(R.string.sora_center), getString(R.string.sora_bottom))
        AlertDialog.Builder(this)
            .setTitle(R.string.sora_follow_scrollbar)
            .setSingleChoiceItems(items, -1) { dialog, which ->
                editor.lnPanelPositionMode = LineInfoPanelPositionMode.FOLLOW
                editor.lnPanelPosition = when (which) {
                    0 -> LineInfoPanelPosition.TOP
                    1 -> LineInfoPanelPosition.CENTER
                    2 -> LineInfoPanelPosition.BOTTOM
                    else -> LineInfoPanelPosition.CENTER
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseLanguage() {
        val languageOptions = arrayOf(
            "Java",
            "TextMate Java",
            "TextMate Kotlin",
            "TextMate Python",
            "TextMate Html",
            "TextMate JavaScript",
            "TextMate MarkDown",
            "TM Language from file",
            "Tree-sitter Java",
            "Monarch Java",
            "Monarch Kotlin",
            "Monarch Python",
            "Monarch TypeScript",
            "Text"
        )

        val tmLanguages = mapOf(
            "TextMate Java" to Pair("source.java", "source.java"),
            "TextMate Kotlin" to Pair("source.kotlin", "source.kotlin"),
            "TextMate Python" to Pair("source.python", "source.python"),
            "TextMate Html" to Pair("text.html.basic", "text.html.basic"),
            "TextMate JavaScript" to Pair("source.js", "source.js"),
            "TextMate MarkDown" to Pair("text.html.markdown", "text.html.markdown")
        )

        val monarchLanguages = mapOf(
            "Monarch Java" to "source.java",
            "Monarch Kotlin" to "source.kotlin",
            "Monarch Python" to "source.python",
            "Monarch TypeScript" to "source.typescript"
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.sora_switch_language)
            .setSingleChoiceItems(languageOptions, -1) { dialog, which ->
                when (val selected = languageOptions[which]) {
                    in tmLanguages -> {
                        val info = tmLanguages[selected]!!
                        try {
                            ensureTextmateTheme()
                            val editorLanguage = editor.editorLanguage
                            val language = if (editorLanguage is TextMateLanguage) {
                                editorLanguage.updateLanguage(info.first)
                                editorLanguage
                            } else {
                                TextMateLanguage.create(info.second, true)
                            }
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    in monarchLanguages -> {
                        val info = monarchLanguages[selected]!!
                        try {
                            ensureMonarchTheme()
                            val editorLanguage = editor.editorLanguage
                            val language = if (editorLanguage is MonarchLanguage) {
                                editorLanguage.updateLanguage(info)
                                editorLanguage
                            } else {
                                MonarchLanguage.create(info, true)
                            }
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    else -> {
                        when (selected) {
                            "Java" -> editor.setEditorLanguage(JavaLanguage())
                            "Text" -> editor.setEditorLanguage(EmptyLanguage())
                            "TM Language from file" -> loadTMLLauncher.launch("*/*")
                            "Tree-sitter Java" -> {
                                // Use existing provider (keeps completion wrapping consistent)
                                editor.setEditorLanguage(languageProvider.getLanguage(File("dummy.java")))
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    private fun chooseTheme() {
        val themes = arrayOf(
            "Default",
            "GitHub",
            "Eclipse",
            "Darcula",
            "VS2019",
            "NotepadXX",
            "QuietLight for TM(VSCode)",
            "Darcula for TM",
            "Ayu Dark for VSCode",
            "Solarized(Dark) for TM(VSCode)",
            "TM theme from file"
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.sora_color_scheme)
            .setSingleChoiceItems(themes, -1) { dialog, which ->
                when (which) {
                    0 -> editor.colorScheme = EditorColorScheme()
                    1 -> editor.colorScheme = SchemeGitHub()
                    2 -> editor.colorScheme = SchemeEclipse()
                    3 -> editor.colorScheme = SchemeDarcula()
                    4 -> editor.colorScheme = SchemeVS2019()
                    5 -> editor.colorScheme = SchemeNotepadXX()

                    6 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("quietlight")
                    }

                    7 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("darcula")
                    }

                    8 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("ayu-dark")
                    }

                    9 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("solarized_dark")
                    }

                    10 -> {
                        // Load any TextMate/VSCode theme JSON file from storage
                        loadTMTLauncher.launch("*/*")
                    }
                }

                // Re-apply to refresh editor colors
                val cs = editor.colorScheme
                editor.colorScheme = cs

                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun navigateTo(uri: String, line: Int, column: Int) {
        val file = if (uri.startsWith("file://")) {
            File(java.net.URI.create(uri))
        } else {
            File(uri)
        }

        if (!file.exists()) {
            android.widget.Toast.makeText(this, "File does not exist: ${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        openFileInEditor(file, file.name)
        editor.post {
            editor.setSelection(line, column)
            editor.ensurePositionVisible(line, column)
        }
    }

    private fun onContextMenuCreated(event: CreateContextMenuEvent) {
        // For editor-lsp we don't gate on a separate status; menu actions will no-op if not connected.

        event.menu.add(0, MENU_ID_DEFINITION, 0, getString(R.string.acs_menu_go_to_definition))
            .setOnMenuItemClickListener {
                handleGoToDefinition(event.position.line, event.position.column)
                true
            }
        event.menu.add(0, MENU_ID_REFERENCES, 0, getString(R.string.acs_menu_find_references))
            .setOnMenuItemClickListener {
                handleFindReferences(event.position.line, event.position.column)
                true
            }

        event.menu.add(0, MENU_ID_HOVER, 0, "Show documentation")
            .setOnMenuItemClickListener {
                handleShowHover(event.position.line, event.position.column)
                true
            }
    }

    private fun handleGoToDefinition(line: Int, column: Int) {
        val f = currentFile ?: return
        // Use editor-lsp aggregated request manager
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return
        val params = org.eclipse.lsp4j.DefinitionParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(f.toURI().toString())
            position = org.eclipse.lsp4j.Position(line, column)
        }
        rm.definition(params)?.thenAccept { result ->
            uiScope.launch(Dispatchers.Main) {
                val locations = if (result.isLeft) {
                    result.left
                } else {
                    result.right.map { org.eclipse.lsp4j.Location(it.targetUri, it.targetRange) }
                }

                if (locations.isEmpty()) {
                    android.widget.Toast.makeText(this@SoraEditorActivityK, "No definition found", android.widget.Toast.LENGTH_SHORT).show()
                } else if (locations.size == 1) {
                    val loc = locations[0]
                    navigateTo(loc.uri, loc.range.start.line, loc.range.start.character)
                } else {
                    val items = locations.map { loc ->
                        NavigationItem(loc.uri, loc.range.start.line, loc.range.start.character, "${File(java.net.URI.create(loc.uri)).name}:${loc.range.start.line + 1}")
                    }
                    bottomSheetVm.setNavigationResults(items)
                    showNavigationTab()
                }
            }
        }
    }

    private fun handleShowHover(line: Int, column: Int) {
        val f = currentFile ?: return
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return

        android.util.Log.d(
            "SoraEditorHover",
            "Request hover for ${f.name} at ${line + 1}:${column + 1} (connected=${lspEditor.isConnected})"
        )

        val params = org.eclipse.lsp4j.HoverParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(f.toURI().toString())
            position = org.eclipse.lsp4j.Position(line, column)
        }

        // Make hover appear immediately when explicitly requested
        lspEditor.hoverWindow?.HOVER_TOOLTIP_SHOW_TIMEOUT = 0L

        rm.hover(params)?.thenAccept { hover ->
            android.util.Log.d(
                "SoraEditorHover",
                "Hover response: ${if (hover == null) "<null>" else "hasContents=" + (hover.contents != null) + ", range=" + (hover.range)}"
            )
            // Use editor-lsp built-in hover window rendering
            lspEditor.showHover(hover)
        }
    }

    private fun handleFindReferences(line: Int, column: Int) {
        val f = currentFile ?: return
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return
        val params = org.eclipse.lsp4j.ReferenceParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(f.toURI().toString())
            position = org.eclipse.lsp4j.Position(line, column)
            context = org.eclipse.lsp4j.ReferenceContext(true)
        }
        rm.references(params)?.thenAccept { locationsNullable ->
            val locations = locationsNullable?.filterNotNull() ?: emptyList()
            uiScope.launch(Dispatchers.Main) {
                if (locations.isEmpty()) {
                    android.widget.Toast.makeText(this@SoraEditorActivityK, "No references found", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val items = locations.map { loc ->
                        val fileName = File(java.net.URI.create(loc.uri)).name
                        val lineContent = if (loc.uri == f.toURI().toString()) {
                            editor.text.getLineString(loc.range.start.line).trim()
                        } else {
                            ""
                        }
                        NavigationItem(loc.uri, loc.range.start.line, loc.range.start.character, "$fileName:${loc.range.start.line + 1} $lineContent")
                    }
                    bottomSheetVm.setNavigationResults(items)
                    showNavigationTab()
                }
            }
        }
    }

    private fun showNavigationTab() {
        val sheet = findViewById<View>(R.id.acs_bottom_sheet)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        val viewPager = findViewById<ViewPager2>(R.id.acs_bottom_sheet_pager)
        viewPager.currentItem = TAB_INDEX_REFERENCES
    }

    private fun openFileInEditor(file: File, title: String) {
        currentFile = file

        // Load file content
        editor.setText(readFileText(file.absolutePath))
        supportActionBar?.title = title

        val ext = file.extension.lowercase()

        val languageForEditor = languageProvider.getLanguage(file)

        editor.setEditorLanguage(languageForEditor)

        // Attach LSP by default for java/kotlin/xml
        if (ext == "java" || ext == "kt" || ext == "kts" || ext == "xml") {
            runCatching {
                // For Java: attach with TsLanguage (or TextMate fallback)
                lspController.attach(editor, file, languageForEditor, projectRoot)
            }
        } else {
            runCatching { lspController.detach() }
        }

        // XML: load framework attribute index for ACS-like android:* completions
        if (ext == "xml") {
            // Ensure index is loaded asynchronously (heavy I/O)
            uiScope.launch(Dispatchers.IO) {
                val ok = com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex.ensureLoaded(this@SoraEditorActivityK)
                if (ok) {
                    // Inject provider into XML enhancer (raw names without "android:")
                    // Snapshot to avoid allocating a new List on every completion request
                    val snapshot = com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex.allAttrs().toList()
                    com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider {
                        snapshot
                    }
                }
            }
        } else {
            // Not XML: clear provider to avoid unnecessary memory use
            com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider(null)
        }

        updatePositionText()
        updateBtnState()
    }

    private fun readFileText(absolutePath: String): String {
        return try {
            BufferedReader(InputStreamReader(FileInputStream(absolutePath), StandardCharsets.UTF_8)).use { br ->
                buildString {
                    var line: String?
                    while (true) {
                        line = br.readLine() ?: break
                        append(line).append('\n')
                    }
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
