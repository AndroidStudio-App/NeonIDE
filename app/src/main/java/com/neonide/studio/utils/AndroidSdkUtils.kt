package com.neonide.studio.utils

import com.termux.shared.termux.TermuxConstants
import java.io.File

object AndroidSdkUtils {

    data class SdkConfig(val sdkDir: File, val env: Map<String, String>)

    private const val NDK_VERSION = "29.0.14206865"
    private const val BUILD_TOOLS_VERSION = "36.0.0"

    private val homeDir: File
        get() = File(TermuxConstants.TERMUX_HOME_DIR_PATH)

    val sdkDir: File get() = File(homeDir, "android-sdk")
    val ndkDir: File get() = File(sdkDir, "ndk/$NDK_VERSION")
    val aapt2Path: File get() = File(sdkDir, "build-tools/$BUILD_TOOLS_VERSION/aapt2")

    /**
     * Build environment overrides for running gradlew.
     */
    fun buildEnvOverrides(baseEnv: Map<String, String>): Map<String, String> {
        val sdk = sdkDir.absolutePath
        val path = listOf(
            "$sdk/cmdline-tools/latest/bin",
            "$sdk/platform-tools",
            "$sdk/build-tools/$BUILD_TOOLS_VERSION",
            baseEnv["PATH"].orEmpty()
        ).filter { it.isNotBlank() }.joinToString(":")

        return mapOf(
            "ANDROID_HOME" to sdk,
            "ANDROID_SDK_ROOT" to sdk,
            "PATH" to path
        )
    }

    private fun ensureNdkDir(projectDir: File) {
        val lp = File(projectDir, "local.properties")
        val line = "ndk.dir=${ndkDir.absolutePath}"

        if (lp.exists()) {
            val lines = lp.readLines().toMutableList()
            val idx = lines.indexOfFirst { it.trim().startsWith("ndk.dir=") }
            if (idx >= 0) {
                if (lines[idx].trim() == line) return
                lines[idx] = line
            } else {
                lines.add(line)
            }
            lp.writeText(lines.joinToString("\n") + "\n")
        } else {
            lp.writeText(line + "\n")
        }
    }

    /**
     * Write ndk.dir to local.properties and return env overrides for Gradle.
     */
    fun configureForProject(projectDir: File, baseEnv: Map<String, String>): SdkConfig? {
        if (!sdkDir.exists()) return null

        ensureNdkDir(projectDir)

        val env = buildEnvOverrides(baseEnv)
        return SdkConfig(sdkDir = sdkDir, env = env)
    }
}
