package com.neonide.studio.utils

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal Gradle wrapper runner.
 *
 * This is NOT the full AndroidCodeStudio Tooling API integration.
 * It executes ./gradlew in a project directory and streams stdout/stderr.
 */
object GradleRunner {

    data class Result(
        val exitCode: Int,
        val wasCancelled: Boolean,
    ) {
        val isSuccessful: Boolean get() = exitCode == 0 && !wasCancelled
    }

    fun hasGradleWrapper(projectDir: File): Boolean {
        val gradlew = File(projectDir, "gradlew")
        val wrapperProps = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")

        // gradle-wrapper.jar may be missing in some projects (it can be generated/downloaded).
        // Don't block execution just because the jar isn't present.
        return gradlew.isFile && wrapperProps.isFile
    }

    /**
     * Run a gradle wrapper command like: ./gradlew :app:assembleDebug
     *
     * @param args Gradle arguments (tasks + flags). Do not include ./gradlew.
     */
    @Throws(IOException::class)
    fun start(
        context: Context,
        projectDir: File,
        args: List<String>,
        envOverrides: Map<String, String> = emptyMap(),
        onOutputLine: (String) -> Unit,
    ): Handle {
        val gradlew = File(projectDir, "gradlew")
        if (!gradlew.exists()) {
            throw IOException("gradlew not found at: ${gradlew.absolutePath}")
        }

        // IMPORTANT:
        // Many gradlew scripts are POSIX sh, but some projects contain bash-isms.
        // If we execute them with a strict /bin/sh-compatible shell (dash/toybox), they can fail with:
        //   "Syntax error: ( unexpected"
        // So we prefer Termux's bash when available, and fall back to sh.
        // Also, executing the script directly can fail on Android because '#!/bin/sh' usually doesn't exist.
        val prefix = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
        val bash = File(prefix, "bash").takeIf { it.exists() }?.absolutePath
        val sh = File(prefix, "sh").takeIf { it.exists() }?.absolutePath
        val shell = bash ?: sh ?: "sh"

        val cmd: List<String> = if (bash != null) {
            // Execute through a login shell so user's ~/.profile, ~/.bashrc etc are applied.
            // This makes IDE builds behave closer to running in terminal.
            //
            // We still keep ProcessBuilder env overrides (ANDROID_HOME, etc.) as the base env.
            val command = buildString {
                append("cd ")
                append(shellQuote(projectDir.absolutePath))
                append(" && ")
                append(shellQuote(gradlew.absolutePath))
                for (a in args) {
                    append(' ')
                    append(shellQuote(a))
                }
            }
            listOf(bash, "-lc", command)
        } else {
            // Fallback: execute with sh/bash without login semantics.
            mutableListOf(shell, gradlew.absolutePath).apply { addAll(args) }
        }

        val pb = ProcessBuilder(cmd)
        pb.directory(projectDir)
        pb.redirectErrorStream(true)

        // Use TermuxShellEnvironment so PATH/TMPDIR etc match runtime.
        val env = pb.environment()
        val termuxEnv = com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment()
            .getEnvironment(context, false)
        env.putAll(termuxEnv)

        // Ensure HOME is always set.
        env["HOME"] = env["HOME"] ?: TermuxConstants.TERMUX_HOME_DIR_PATH

        // Make gradle less chatty unless needed
        env["GRADLE_OPTS"] = env["GRADLE_OPTS"] ?: ""

        // Apply custom overrides last.
        env.putAll(envOverrides)

        val process = pb.start()
        return Handle(process, onOutputLine)
    }

    private fun shellQuote(arg: String): String {
        // Safe-ish single-quote quoting for POSIX shells.
        // abc'def -> 'abc'\''def'
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    class Handle internal constructor(
        private val process: Process,
        private val onOutputLine: (String) -> Unit,
    ) {
        private val cancelled = AtomicBoolean(false)
        
        companion object {
            private const val BUFFER_SIZE = 4096
        }

        fun cancel() {
            cancelled.set(true)
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
        }

        fun waitFor(): Result {
            val buf = ByteArray(BUFFER_SIZE)
            val partial = StringBuilder()

            try {
                readOutput(buf, partial)
            } catch (e: java.io.IOException) {
                if (!cancelled.get()) {
                    onOutputLine("[GradleRunner] Output stream error: ${e.message}")
                }
            }

            val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
            return Result(exitCode = exitCode, wasCancelled = cancelled.get())
        }

        private fun readOutput(buf: ByteArray, partial: StringBuilder) {
            val input = process.inputStream
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                partial.append(String(buf, 0, n))
                flushPartial(partial, force = false)
            }
            flushPartial(partial, force = true)
        }

        private fun flushPartial(partial: StringBuilder, force: Boolean) {
            if (partial.isEmpty()) return
            val text = partial.toString()
            if (!force && !text.contains('\n') && !text.contains('\r')) return

            partial.setLength(0)
            text.split('\n', '\r').forEach { if (it.isNotBlank()) onOutputLine(it) }
        }
    }
}
