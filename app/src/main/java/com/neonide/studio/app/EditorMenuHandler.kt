package com.neonide.studio.app

import android.view.MenuItem
import com.neonide.studio.R
import com.termux.app.TermuxActivity
import android.content.Intent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import java.io.File

class EditorMenuHandler(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val gradleManager: EditorGradleManager,
    private val logManager: EditorLogManager,
    private val dialogManager: EditorDialogManager,
    private val searchManager: EditorSearchManager,
    private val coordinator: EditorCoordinator,
    private val uiManager: EditorUiManager,
    private val lspManager: EditorLspManager
) {

    private val editorConfigIds = setOf(
        R.id.sora_magnifier, R.id.sora_symbol_bar_visibility, R.id.sora_text_wordwrap,
        R.id.sora_editor_line_number, R.id.sora_pin_line_number, R.id.sora_use_icu,
        R.id.sora_completion_anim, R.id.sora_soft_kbd_enabled, 
        R.id.sora_disable_soft_kbd_on_hard_kbd, R.id.sora_switch_language, 
        R.id.sora_search_panel_st, R.id.sora_search_am, R.id.sora_switch_colors, 
        R.id.sora_switch_typeface
    )

    private val fileAndLogIds = setOf(
        R.id.sora_text_undo, R.id.sora_text_redo, R.id.sora_open_terminal,
        R.id.sora_save_file, R.id.sora_open_build_log, R.id.sora_open_logs, 
        R.id.sora_clear_logs, R.id.sora_open_ide_file_log, R.id.sora_load_test_file, 
        R.id.sora_start_java_lsp
    )

    fun handleMenuItemSelection(item: MenuItem, projectRoot: File?): Boolean {
        return when (item.itemId) {
            in editorConfigIds -> handleEditorConfig(item)
            in fileAndLogIds -> handleFileAndLogActions(item)
            R.id.sora_quick_run -> { gradleManager.onQuickRunOrCancel(projectRoot); true }
            R.id.sora_sync_project -> { gradleManager.onSyncProject(projectRoot); true }
            R.id.sora_code_format -> { editor.formatCodeAsync(); true }
            else -> false
        }
    }

    private fun handleEditorConfig(item: MenuItem): Boolean {
        if (dialogManager.handleDialogAction(item.itemId)) return true
        val handled = when (item.itemId) {
            R.id.sora_magnifier -> { uiManager.toggleMagnifier(item); true }
            R.id.sora_symbol_bar_visibility -> { uiManager.toggleSymbolBar(item); true }
            R.id.sora_text_wordwrap -> { 
                item.isChecked = !item.isChecked
                editor.isWordwrap = item.isChecked
                true 
            }
            R.id.sora_editor_line_number -> {
                editor.isLineNumberEnabled = !editor.isLineNumberEnabled
                item.isChecked = editor.isLineNumberEnabled
                true
            }
            R.id.sora_pin_line_number -> {
                editor.setPinLineNumber(!editor.isLineNumberPinned)
                item.isChecked = editor.isLineNumberPinned
                true
            }
            R.id.sora_use_icu -> { 
                item.isChecked = !item.isChecked
                editor.props.useICULibToSelectWords = item.isChecked
                true 
            }
            R.id.sora_completion_anim -> { 
                item.isChecked = !item.isChecked
                editor.getComponent(EditorAutoCompletion::class.java)
                    .setEnabledAnimation(item.isChecked)
                true
            }
            R.id.sora_soft_kbd_enabled -> { 
                item.isChecked = !item.isChecked
                editor.isSoftKeyboardEnabled = item.isChecked
                true 
            }
            R.id.sora_disable_soft_kbd_on_hard_kbd -> {
                item.isChecked = !item.isChecked
                editor.isDisableSoftKbdIfHardKbdAvailable = item.isChecked
                true
            }
            R.id.sora_search_panel_st -> { searchManager.toggleSearchPanel(item); true }
            R.id.sora_search_am -> { 
                searchManager.toggleSearchPanel(item)
                searchManager.beginSearchMode()
                true 
            }
            else -> false
        }
        return handled
    }

    private fun handleFileAndLogActions(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sora_text_undo -> editor.undo()
            R.id.sora_text_redo -> editor.redo()
            R.id.sora_open_terminal -> runCatching { 
                activity.startActivity(Intent(activity, TermuxActivity::class.java)) 
            }
            R.id.sora_save_file -> coordinator.saveCurrentFile()
            R.id.sora_open_build_log -> logManager.openBuildLog("gradle-build.log")
            R.id.sora_open_logs -> logManager.openLogs("crash-journal.log")
            R.id.sora_clear_logs -> logManager.clearLogs("crash-journal.log")
            R.id.sora_open_ide_file_log -> logManager.openIdeFileLog()
            R.id.sora_load_test_file -> coordinator.openAssetsFile("samples/big_sample.txt")
            R.id.sora_start_java_lsp -> lspManager.startJavaLsp()
            else -> return false
        }
        return true
    }
}
