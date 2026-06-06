package com.neonide.studio.app

import android.app.Activity
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import java.io.File

class EditorGradleManager(
    private val activity: Activity,
    private val bottomSheetVm: BottomSheetViewModel
) {
    private val gradleController = EditorGradleController(activity, bottomSheetVm)

    fun onQuickRunOrCancel(projectRoot: File, variant: String = "debug") {
        gradleController.onQuickRunOrCancel(projectRoot, variant)
    }

    fun onSyncProject(projectRoot: File) {
        gradleController.onSyncProject(projectRoot)
    }

    fun onDestroy() {
        gradleController.onDestroy()
    }
}
