package com.neonide.studio.app

import com.google.android.material.appbar.MaterialToolbar
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import java.io.File

class EditorGradleManager(
    private val activity: SoraEditorActivityK,
    private val bottomSheetVm: BottomSheetViewModel
) {
    private val gradleController = EditorGradleController(activity, bottomSheetVm)

    fun onQuickRunOrCancel(projectRoot: File?) {
        gradleController.onQuickRunOrCancel(projectRoot)
    }

    fun onSyncProject(projectRoot: File?) {
        gradleController.onSyncProject(projectRoot)
    }

    fun updateQuickRunBtn(toolbar: MaterialToolbar) {
        gradleController.updateQuickRunBtn(toolbar)
    }

    fun onDestroy() {
        gradleController.onDestroy()
    }
}
