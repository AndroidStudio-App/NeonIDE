package com.neonide.studio.app

import android.app.Activity
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.BuildOutputBuffer
import com.neonide.studio.utils.GradleBuildStatus
import com.neonide.studio.utils.GradleProjectActions
import com.neonide.studio.utils.GradleService
import java.io.File

/**
 * Controller for handling Gradle build and sync operations.
 */
class EditorGradleController(
    private val activity: Activity,
    private val bottomSheetVm: BottomSheetViewModel
) {
    fun onSyncProject(projectRoot: File) {
        if (GradleBuildStatus.isRunning) {
            GradleService.stopBuild(activity)
            return
        }
        val plan = GradleProjectActions.createSyncPlan()
        BuildOutputBuffer.clear()
        bottomSheetVm.setDiagnostics(emptyList())
        GradleService.startBuild(
            context = activity,
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = activity.getString(R.string.sync_project),
            installOnSuccess = false,
            logFilePath = File(activity.filesDir, "gradle-build.log").absolutePath
        )
    }

    fun onQuickRunOrCancel(projectRoot: File, variant: String = "debug") {
        if (GradleBuildStatus.isRunning) {
            GradleService.stopBuild(activity)
            return
        }
        val plan = GradleProjectActions.createQuickRunPlan(projectRoot, variant)
        val actionLabel = activity.getString(R.string.quick_run)
        BuildOutputBuffer.clear()
        bottomSheetVm.setDiagnostics(emptyList())
        GradleService.startBuild(
            context = activity,
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = actionLabel,
            installOnSuccess = true,
            variant = variant,
            logFilePath = File(activity.filesDir, "gradle-build.log").absolutePath
        )
    }
}
