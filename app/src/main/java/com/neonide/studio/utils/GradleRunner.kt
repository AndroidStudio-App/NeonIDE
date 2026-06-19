package com.neonide.studio.utils

import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal Gradle wrapper runner.
 *
 * It executes ./gradlew in a project directory and streams stdout/stderr.
 */
object GradleRunner {

    /**
     * Run a gradle wrapper command like: ./gradlew :app:assembleDebug
     *
     * @param args Gradle arguments (tasks + flags). Do not include ./gradlew.
     */
    @Throws(IOException::class)
    fun start(
        projectDir: File,
        args: List<String>,
        envOverrides: Map<String, String> = emptyMap(),
        onOutputLine: (String) -> Unit
    ): Handle {
        val gradlew = File(projectDir, "gradlew")

        // Explicitly source .bashrc to load ANDROID_HOME and other env vars
        val bashrcPath = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".bashrc").absolutePath
        val cmd = listOf(
            File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash").absolutePath,
            "-c",
            "source $bashrcPath && ${gradlew.absolutePath} ${args.joinToString(" ")}"
        )

        val pb = ProcessBuilder(cmd)
        pb.directory(projectDir)
        pb.redirectErrorStream(true)

        // bash -lc already loads .bashrc, just apply custom overrides
        if (envOverrides.isNotEmpty()) {
            val env = pb.environment()
            env.putAll(envOverrides)
        }

        val process = pb.start()
        return Handle(process, onOutputLine)
    }

    /**
     * Wraps a running Gradle process, streaming output line-by-line.
     */
    class Handle internal constructor(
        private val process: Process,
        private val onOutputLine: (String) -> Unit
    ) {
        private val cancelled = AtomicBoolean(false)

        companion object {
            private const val BUFFER_SIZE = 4096
        }

        /** Terminates the underlying Gradle process. */
        fun cancel() {
            cancelled.set(true)
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
        }

        /** Blocks until the process finishes and all output has been streamed. */
        fun waitFor(): Result {
            val buf = ByteArray(BUFFER_SIZE)
            val partial = StringBuilder()

            try {
                readOutput(buf, partial)
            } catch (e: IOException) {
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

    /** Outcome of a Gradle build invocation. */
    data class Result(val exitCode: Int, val wasCancelled: Boolean) {
        val isSuccessful: Boolean get() = exitCode == 0 && !wasCancelled
    }
}
