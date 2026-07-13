package com.neonide.studio.app

import android.app.Activity
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.buildoutput.BuildOutputBuffer
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
        GradleService.startBuild(
            context = activity,
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = activity.getString(R.string.sync_started),
            installOnSuccess = false
        )
    }

    fun onQuickRunOrCancel(projectRoot: File, variant: String = "debug") {
        if (GradleBuildStatus.isRunning) {
            GradleService.stopBuild(activity)
            return
        }

        val isFlutter = GradleProjectActions.isFlutterProject(projectRoot)
        val plan = if (isFlutter) {
            GradleProjectActions.createFlutterBuildPlan(projectRoot, variant)
        } else {
            GradleProjectActions.createQuickRunPlan(projectRoot, variant)
        }
        val actionLabel = activity.getString(R.string.build_started)
        val executable = if (isFlutter) "flutter" else null

        BuildOutputBuffer.clear()
        GradleService.startBuild(
            context = activity,
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = actionLabel,
            installOnSuccess = true,
            variant = variant,
            executable = executable
        )
    }
}
