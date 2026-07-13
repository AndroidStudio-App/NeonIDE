package com.neonide.studio.utils

import android.content.Context
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File

/**
 * Core logic for "Sync project" and "Quick run" actions.
 */
object GradleProjectActions {

    data class SyncPlan(val args: List<String>, val description: String)

    data class QuickRunPlan(
        val args: List<String>,
        val description: String,
        val expectedApkSearchDir: File?
    )

    fun createSyncPlan(): SyncPlan {
        val args = baseArgs() + listOf("projects")
        return SyncPlan(args = args, description = "Gradle projects")
    }

    fun createQuickRunPlan(projectDir: File, variant: String = "debug"): QuickRunPlan {
        val taskName = "assemble${variant.replaceFirstChar { it.uppercase() }}"
        val args = baseArgs() + listOf(taskName)
        val apkSearchDir = File(projectDir, "app/build/outputs/apk")
        return QuickRunPlan(
            args = args,
            description = "Assemble $variant",
            expectedApkSearchDir = apkSearchDir
        )
    }

    fun baseArgs(): List<String> = mutableListOf(
        "--no-daemon",
        "--stacktrace",
        "--console=plain"
    )

    fun isFlutterProject(projectDir: File): Boolean = File(projectDir, "pubspec.yaml").exists() &&
        File(projectDir, "lib/main.dart").exists()

    fun createFlutterBuildPlan(projectDir: File, variant: String = "debug"): QuickRunPlan {
        val args = listOf(
            "build",
            "apk",
            "--$variant",
            "--target-platform",
            "android-arm64"
        )
        val apkSearchDir = File(projectDir, "build/app/outputs/flutter-apk")
        return QuickRunPlan(
            args = args,
            description = "Flutter build $variant",
            expectedApkSearchDir = apkSearchDir
        )
    }

    fun getGradleEnvironment(context: Context): Map<String, String> =
        TermuxShellEnvironment().getEnvironment(context, false)
}
