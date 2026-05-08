package com.neonide.studio.app

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.neonide.studio.R
import com.neonide.studio.FileTreeDrawer
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.CreateContextMenuEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import java.io.File

class EditorSetupManager(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val uiManager: EditorUiManager,
    private val lspManager: EditorLspManager,
    private val viewHelper: EditorViewHelper
) {

    fun setupUi(projectRoot: File?, openFile: (File, String) -> Unit) {
        val prefs = activity.getPreferences(Context.MODE_PRIVATE)
        val bar = activity.findViewById<View>(R.id.main_bottom_bar)
        bar.visibility = if (prefs.getBoolean("symbol_bar_visible", true)) View.VISIBLE else View.GONE

        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val drawer = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
        ViewCompat.setOnApplyWindowInsetsListener(drawer) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, ime.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity, 
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 
                1
            )
        }

        activity.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.file_tree_drawer_view)
            .setContent {
                FileTreeDrawer(
                    rootPath = projectRoot?.absolutePath ?: "", 
                    onFileClick = { path -> openFile(File(path), File(path).name) }
                )
            }

        val tb: MaterialToolbar = activity.findViewById(R.id.toolbar)
        activity.setSupportActionBar(tb)
        activity.supportActionBar?.title = "Editor"

        uiManager.setupAcsBottomSheet()
    }

    fun setupEditor(symbols: Array<String>, symbolsInsert: Array<String>) {
        editor.runCatching {
            typefaceText = Typeface.createFromAsset(activity.assets, "JetBrainsMono-Regular.ttf")
        }
        activity.findViewById<SymbolInputView>(R.id.symbol_input).apply {
            bindEditor(editor)
            addSymbols(symbols, symbolsInsert)
        }
        editor.setEditorLanguage(EmptyLanguage())
        editor.props.stickyScroll = true
        editor.nonPrintablePaintingFlags = 
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or 
            CodeEditor.FLAG_DRAW_LINE_SEPARATOR or 
            CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or 
            CodeEditor.FLAG_DRAW_SOFT_WRAP
    }

    fun setupEventListeners(
        xmlDiag: Runnable, 
        currentFile: () -> File?, 
        undoItem: () -> MenuItem?, 
        redoItem: () -> MenuItem?
    ) {
        editor.subscribeAlways(SelectionChangeEvent::class.java) { 
            viewHelper.updatePositionText(activity.findViewById(R.id.position_display)) 
        }
        editor.subscribeAlways(PublishSearchResultEvent::class.java) { 
            viewHelper.updatePositionText(activity.findViewById(R.id.position_display)) 
        }
        editor.subscribeAlways(ContentChangeEvent::class.java) { ev ->
            editor.postDelayed({ uiManager.updateBtnState(undoItem(), redoItem()) }, 50L)
            val f = currentFile()
            if (f != null && f.extension.equals("xml", ignoreCase = true)) {
                runCatching { AndroidXmlLanguageEnhancer.applyAdvancedSlashEditIfNeeded(f, editor, ev) }
                editor.removeCallbacks(xmlDiag)
                editor.postDelayed(xmlDiag, 180L)
            }
        }
        editor.subscribeAlways(CreateContextMenuEvent::class.java) { 
            lspManager.handler.onContextMenuCreated(it) 
        }
        editor.subscribeAlways(LongPressEvent::class.java) { e ->
            val isJava = currentFile()?.extension?.lowercase() == "java"
            if (isJava && lspManager.controller.currentEditor()?.isConnected == true) {
                editor.setSelection(e.line, e.column)
                lspManager.handler.handleShowHover(e.line, e.column)
            }
        }
        editor.subscribeAlways(SideIconClickEvent::class.java) {
            editor.setSelection(it.clickedIcon.line, 0)
            editor.getComponent(EditorDiagnosticTooltipWindow::class.java).show()
        }
    }

    fun initializeProject(
        savedInstanceState: Bundle?, 
        themeManager: EditorThemeAndLanguageManager, 
        coordinator: EditorCoordinator,
        projectRoot: File?
    ) {
        themeManager.setupTextmate()
        themeManager.setupMonarch()
        if (editor.colorScheme !is TextMateColorScheme) {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }
        val restoredFile = savedInstanceState?.getString("current_file_path")?.let { File(it) }
        if (restoredFile?.exists() == true) {
            coordinator.openFileInEditor(restoredFile, restoredFile.name, projectRoot)
            editor.setSelection(
                savedInstanceState!!.getInt("cursor_line", 0), 
                savedInstanceState.getInt("cursor_column", 0)
            )
        } else {
            val readme = projectRoot?.let { File(it, "README.md") }
            if (readme?.exists() == true) {
                coordinator.openFileInEditor(readme, readme.name, projectRoot)
            } else {
                editor.setText("")
            }
        }
    }
}
