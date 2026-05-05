package com.neonide.studio.app

import android.content.Context
import android.view.MenuItem
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.EditorBottomSheetTabAdapter
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.Magnifier

class EditorUiManager(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val gradleManager: EditorGradleManager
) {

    fun setupAcsBottomSheet() {
        val sheet = activity.findViewById<View>(R.id.acs_bottom_sheet)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        behavior.isHideable = false
        sheet.findViewById<View>(R.id.acs_bottom_sheet_status).visibility = View.GONE

        val tabs = sheet.findViewById<TabLayout>(R.id.acs_bottom_sheet_tabs)
        val pager = sheet.findViewById<ViewPager2>(R.id.acs_bottom_sheet_pager)
        val adapter = EditorBottomSheetTabAdapter(activity)
        pager.adapter = adapter
        pager.isUserInputEnabled = false
        TabLayoutMediator(tabs, pager) { tab, position -> 
            tab.text = activity.getString(adapter.getTitleRes(position)) 
        }.attach()

        BuildOutputBuffer.clear()
    }

    fun collapseBottomSheet(): Boolean {
        val behavior = BottomSheetBehavior.from(
            activity.findViewById<View>(R.id.acs_bottom_sheet)
        )
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED || 
            behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return true
        }
        return false
    }

    fun updateBtnState(undoItem: MenuItem?, redoItem: MenuItem?) {
        undoItem?.isEnabled = editor.canUndo()
        redoItem?.isEnabled = editor.canRedo()
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        gradleManager.updateQuickRunBtn(toolbar)
    }

    fun toggleMagnifier(item: MenuItem) {
        item.isChecked = !item.isChecked
        editor.getComponent(Magnifier::class.java).isEnabled = item.isChecked
    }

    fun toggleSymbolBar(item: MenuItem) {
        item.isChecked = !item.isChecked
        activity.findViewById<View>(R.id.main_bottom_bar).visibility = if (item.isChecked) {
            View.VISIBLE 
        } else {
            View.GONE
        }
        activity.getPreferences(Context.MODE_PRIVATE).edit()
            .putBoolean("symbol_bar_visible", item.isChecked).apply()
    }
}
