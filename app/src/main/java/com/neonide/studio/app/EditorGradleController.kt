package com.neonide.studio.app

import android.app.Activity
import android.widget.Toast
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
    @Volatile var gradleRunning: Boolean = false
        private set

    private val gradleStatusListener: (Boolean) -> Unit = { isRunning ->
        gradleRunning = isRunning
    }

    init {
        GradleBuildStatus.addListener(gradleStatusListener)
        gradleRunning = GradleBuildStatus.isRunning
    }

    fun onDestroy() {
        GradleBuildStatus.removeListener(gradleStatusListener)
    }

    fun onSyncProject(projectRoot: File?) {
        if (projectRoot == null || !projectRoot.exists()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.project_dir_missing),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (gradleRunning) {
            GradleService.stopBuild(activity)
            return
        }

        val wrapperStatus = GradleProjectActions.ensureWrapperPresent(activity, projectRoot)
        GradleProjectActions.wrapperStatusMessage(activity, wrapperStatus)?.let { msg ->
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        }
        if (wrapperStatus == GradleProjectActions.WrapperStatus.MissingScriptOrProps ||
            wrapperStatus == GradleProjectActions.WrapperStatus.RepairFailed
        ) {
            return
        }

        Toast.makeText(
            activity,
            activity.getString(R.string.sync_started),
            Toast.LENGTH_SHORT
        ).show()
        val plan = GradleProjectActions.createSyncPlan()
        runGradle(
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = activity.getString(R.string.sync_project),
            kind = GradleActionKind.SYNC,
            installApkOnSuccess = false
        )
    }

    fun onQuickRunOrCancel(projectRoot: File?) {
        if (projectRoot == null || !projectRoot.exists()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.project_dir_missing),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (gradleRunning) {
            GradleService.stopBuild(activity)
            gradleRunning = false
            return
        }

        val wrapperStatus = GradleProjectActions.ensureWrapperPresent(activity, projectRoot)
        GradleProjectActions.wrapperStatusMessage(activity, wrapperStatus)?.let { msg ->
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        }
        if (wrapperStatus == GradleProjectActions.WrapperStatus.MissingScriptOrProps ||
            wrapperStatus == GradleProjectActions.WrapperStatus.RepairFailed
        ) {
            return
        }

        Toast.makeText(
            activity,
            activity.getString(R.string.build_started),
            Toast.LENGTH_SHORT
        ).show()

        val plan = GradleProjectActions.createQuickRunPlan(projectRoot)
        runGradle(
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = activity.getString(R.string.quick_run),
            kind = GradleActionKind.BUILD,
            installApkOnSuccess = true
        )
    }

    private enum class GradleActionKind { BUILD, SYNC }

    private fun runGradle(
        projectDir: File,
        args: List<String>,
        actionLabel: String,
        kind: GradleActionKind,
        installApkOnSuccess: Boolean
    ) {
        gradleRunning = true

        bottomSheetVm.setStatus("$actionLabel: ${activity.getString(R.string.status_building)}")

        BuildOutputBuffer.clear()
        bottomSheetVm.setDiagnostics(emptyList())

        GradleService.startBuild(
            context = activity,
            projectDir = projectDir,
            args = args,
            actionLabel = actionLabel,
            installOnSuccess = installApkOnSuccess,
            logFilePath = File(activity.filesDir, "gradle-build.log").absolutePath
        )
    }
}
