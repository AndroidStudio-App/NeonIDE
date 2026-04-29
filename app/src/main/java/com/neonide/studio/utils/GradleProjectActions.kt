package com.neonide.studio.utils

import android.content.Context
import com.neonide.studio.R
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File

/**
 * Core logic for "Sync project" and "Quick run" actions.
 *
 * Notes:
 * - This is a lightweight implementation that works by invoking the project's Gradle Wrapper.
 * - It does not use the Android Studio/ACS Tooling API project model.
 * - We still aim to provide the *expected* UX: ensure wrapper exists, run reasonable tasks,
 *   surface build output, and provide basic diagnostics.
 */
object GradleProjectActions {

    /**
     * Ensure that required Gradle wrapper pieces exist.
     *
     * Some imported projects have gradlew + wrapper properties but miss the wrapper jar.
     * Our templates also generate gradlew scripts that execute `java -jar gradle-wrapper.jar`.
     */
    fun ensureWrapperPresent(context: Context, projectDir: File): WrapperStatus {
        val gradlew = File(projectDir, "gradlew")
        val wrapperProps = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        val wrapperJar = File(projectDir, "gradle/wrapper/gradle-wrapper.jar")

        var status = if (!gradlew.exists() || !wrapperProps.exists()) {
            WrapperStatus.MissingScriptOrProps
        } else {
            runCatching { gradlew.setExecutable(true) }
            if (wrapperJar.exists()) WrapperStatus.Ok else repairWrapper(context, wrapperJar, wrapperProps)
        }
        return status
    }

    private fun repairWrapper(context: Context, wrapperJar: File, wrapperProps: File): WrapperStatus {
        return runCatching {
            wrapperJar.parentFile?.mkdirs()
            val jarAsset = findAsset(
                context,
                listOf("gradle/wrapper/gradle-wrapper.jar", "gradle-wrapper/gradle-wrapper.jar"),
                "gradle-wrapper/gradle-wrapper.jar"
            )
            
            context.assets.open(jarAsset).use { input ->
                wrapperJar.outputStream().use { out -> input.copyTo(out) }
            }

            if (!wrapperProps.exists()) {
                wrapperProps.parentFile?.mkdirs()
                val propsAsset = findAsset(
                    context,
                    listOf("gradle/wrapper/gradle-wrapper.properties", "gradle-wrapper/gradle-wrapper.properties"),
                    "gradle-wrapper/gradle-wrapper.properties"
                )
                context.assets.open(propsAsset).use { input ->
                    wrapperProps.outputStream().use { out -> input.copyTo(out) }
                }
            }
            WrapperStatus.Repaired
        }.getOrElse { WrapperStatus.RepairFailed }
    }

    private fun findAsset(context: Context, paths: List<String>, fallback: String): String {
        return paths.firstOrNull { p ->
            runCatching { context.assets.open(p).close(); true }.getOrDefault(false)
        } ?: fallback
    }

    enum class WrapperStatus {
        Ok,
        Repaired,
        MissingScriptOrProps,
        RepairFailed,
    }

    /** A structured sync plan. */
    data class SyncPlan(
        val args: List<String>,
        val description: String,
    )

    /** A structured build/run plan. */
    data class QuickRunPlan(
        val args: List<String>,
        val description: String,
        val expectedApkSearchDir: File?,
    )

    /**
     * Determine a reasonable "sync" command.
     *
     * We use `help` and `projects`/`tasks` to force Gradle to resolve settings and plugins.
     * `dependencies` can be too heavy on large projects.
     */
    fun createSyncPlan(aapt2Path: String? = null): SyncPlan {
        // `projects` is a good cheap proxy for "sync": it resolves settings.gradle and includes.
        // `tasks --all` tends to trigger plugin configuration and is useful for diagnosing.
        val args = baseArgs(aapt2Path) + listOf("projects")
        return SyncPlan(args = args, description = "Gradle projects")
    }

    /**
     * Determine a reasonable quick-run build based on common Android app tasks.
     *
     * Strategy:
     * - Prefer :app:assembleDebug
     * - Fall back to assembleDebug (single-module)
     */
    fun createQuickRunPlan(projectDir: File, aapt2Path: String? = null): QuickRunPlan {
        // Most templates are single app module called :app.
        // If user opened a different structure, Gradle will fail, but output will show.
        val tasks = listOf("assembleDebug")

        val args = baseArgs(aapt2Path) + tasks
        val apkSearchDir = File(projectDir, "app/build/outputs/apk")

        return QuickRunPlan(
            args = args,
            description = "Assemble debug",
            expectedApkSearchDir = apkSearchDir,
        )
    }

    fun baseArgs(aapt2Path: String? = null): List<String> {
        // NOTE: don't use --no-daemon: wrapper itself downloads gradle distributions and
        // running without daemon can be slower but safer memory-wise. We still keep it off
        // by default for constrained Android env.
        val args = mutableListOf(
            "--no-daemon",
            "--stacktrace",
            "--console=plain",
        )
        if (aapt2Path != null) {
            args.add("-Pandroid.aapt2FromMavenOverride=$aapt2Path")
        }
        return args
    }

    /**
     * Build an environment map suitable for running gradlew.
     *
     * Uses TermuxShellEnvironment so binaries, TMPDIR etc match the Termux runtime.
     */
    fun getGradleEnvironment(context: Context): Map<String, String> {
        return TermuxShellEnvironment().getEnvironment(context, false)
    }

    fun wrapperStatusMessage(context: Context, status: WrapperStatus): String? {
        return when (status) {
            WrapperStatus.Ok -> null
            WrapperStatus.Repaired -> context.getString(R.string.acs_gradle_wrapper_repaired)
            WrapperStatus.MissingScriptOrProps -> context.getString(R.string.acs_gradle_wrapper_missing)
            WrapperStatus.RepairFailed -> context.getString(R.string.acs_gradle_wrapper_repair_failed)
        }
    }
}
