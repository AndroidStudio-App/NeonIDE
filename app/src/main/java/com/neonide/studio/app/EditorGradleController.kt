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

    fun onSyncProject(projectRoot: File) {
        if (gradleRunning) {
            GradleService.stopBuild(activity)
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
            installApkOnSuccess = false
        )
    }

    fun onQuickRunOrCancel(projectRoot: File, variant: String = "debug") {
        if (gradleRunning) {
            GradleService.stopBuild(activity)
            gradleRunning = false
            return
        }

        Toast.makeText(
            activity,
            activity.getString(R.string.build_started),
            Toast.LENGTH_SHORT
        ).show()

        val plan = GradleProjectActions.createQuickRunPlan(projectRoot, variant)
        runGradle(
            projectDir = projectRoot,
            args = plan.args,
            actionLabel = activity.getString(R.string.quick_run),
            installApkOnSuccess = true,
            variant = variant
        )
    }

    private fun runGradle(
        projectDir: File,
        args: List<String>,
        actionLabel: String,
        installApkOnSuccess: Boolean,
        variant: String = "debug"
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
            variant = variant,
            logFilePath = File(activity.filesDir, "gradle-build.log").absolutePath
        )
    }
}
