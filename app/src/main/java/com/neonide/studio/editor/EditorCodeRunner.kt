package com.neonide.studio.editor

import android.app.Activity
import com.neonide.studio.R
import com.neonide.studio.editor.bottomsheet.BottomSheetViewModel
import com.neonide.studio.editor.bottomsheet.buildoutput.BuildOutputBuffer
import com.neonide.studio.utils.GradleBuildStatus
import com.neonide.studio.utils.GradleProjectActions
import com.neonide.studio.utils.GradleService
import java.io.File

class EditorCodeRunner(
    private val activity: Activity,
    private val bottomSheetVm: BottomSheetViewModel
) {
    private fun runCommandForFile(filePath: String): String? {
        val file = File(filePath)
        return when (file.extension.lowercase()) {
            "java" -> "java $file"
            "kt", "kts" -> "kotlin $file"
            "py" -> "python $file"
            "js" -> "node $file"
            "sh", "bash" -> "bash $file"
            "rb" -> "ruby $file"
            "php" -> "php $file"
            "go" -> "go run $file"
            "rs" -> "cargo run"
            else -> null
        }.let { cmd -> if (cmd == null) null else "$cmd" }
    }

    /**
     * Runs the currently opened file in the terminal (when it is a runnable
     * script), or falls back to the normal Gradle build otherwise
     */
    fun onRunOpenedFileOrCancel(projectRoot: File, activeFilePath: String?) {
        if (GradleBuildStatus.isRunning) {
            GradleService.stopBuild(activity)
            return
        }
        // validate gradle project ,skip running java file inside root
        val isGradleProject = File(projectRoot, "build.gradle.kts").exists() ||
            File(projectRoot, "settings.gradle.kts").exists() ||
            File(projectRoot, "build.gradle").exists()
        val command = if (!isGradleProject) {
            activeFilePath?.let { runCommandForFile(it) }
        } else {
            null
        }
        if (command != null) {
            val workingDir = File(activeFilePath).parent ?: projectRoot.absolutePath
            bottomSheetVm.setPendingRunCommand(command, workingDir)
            bottomSheetVm.requestOpenTerminal()
        } else {
            onQuickRunOrCancel(projectRoot)
        }
    }
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
