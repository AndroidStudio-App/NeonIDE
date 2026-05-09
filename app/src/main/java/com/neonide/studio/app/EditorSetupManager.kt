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

    fun setupUi(projectRoot: File?) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity, 
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 
                1
            )
        }

        uiManager.setupAcsBottomSheet()
    }

    fun setupEditor(symbolInput: SymbolInputView, symbols: Array<String>, symbolsInsert: Array<String>) {
        editor.runCatching {
            typefaceText = Typeface.createFromAsset(activity.assets, "JetBrainsMono-Regular.ttf")
        }
        symbolInput.apply {
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
            // Position display update is now handled by viewHelper or directly in Compose
        }
        editor.subscribeAlways(PublishSearchResultEvent::class.java) { 
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
