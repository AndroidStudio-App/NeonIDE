package com.neonide.studio.app

import android.content.Context
import android.view.MenuItem
import android.view.View
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.android.material.appbar.MaterialToolbar
import com.neonide.studio.R
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.Magnifier
import kotlin.jvm.java
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EditorUiManager(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val gradleManager: EditorGradleManager,
    private val uiScope: CoroutineScope
) {

    var scaffoldState: BottomSheetScaffoldState? = null
    var symbolBarVisible by mutableStateOf(true)
    var toolbar: MaterialToolbar? = null

    fun setupAcsBottomSheet() {
        // Now handled by Compose
        BuildOutputBuffer.clear()
        
        val prefs = activity.getPreferences(Context.MODE_PRIVATE)
        symbolBarVisible = prefs.getBoolean("symbol_bar_visible", true)
    }

    fun collapseBottomSheet(): Boolean {
        val state = scaffoldState ?: return false
        if (state.bottomSheetState.currentValue == SheetValue.Expanded || 
            state.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
            uiScope.launch {
                state.bottomSheetState.partialExpand()
            }
            return true
        }
        return false
    }

    fun expandBottomSheet() {
        val state = scaffoldState ?: return
        uiScope.launch {
            state.bottomSheetState.expand()
        }
    }

    fun updateBtnState(undoItem: MenuItem?, redoItem: MenuItem?) {
        undoItem?.isEnabled = editor.canUndo()
        redoItem?.isEnabled = editor.canRedo()
        val tb = toolbar
        if (tb != null) {
            gradleManager.updateQuickRunBtn(tb)
        }
    }

    fun toggleMagnifier(item: MenuItem) {
        item.isChecked = !item.isChecked
        editor.getComponent(Magnifier::class.java).isEnabled = item.isChecked
    }

    fun toggleSymbolBar(item: MenuItem) {
        item.isChecked = !item.isChecked
        symbolBarVisible = item.isChecked
        activity.getPreferences(Context.MODE_PRIVATE).edit()
            .putBoolean("symbol_bar_visible", item.isChecked).apply()
    }
}
