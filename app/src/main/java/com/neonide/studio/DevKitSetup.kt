package com.neonide.studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.system.Os
import android.widget.Toast

import com.neonide.studio.R
import com.neonide.studio.shared.shell.command.ExecutionCommand.Runner
import com.neonide.studio.shared.shell.command.ExecutionCommand.ShellCreateMode
import com.neonide.studio.shared.termux.TermuxConstants
import com.neonide.studio.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.neonide.studio.app.TermuxInstaller
import com.neonide.studio.app.TermuxService
import com.neonide.studio.app.TermuxActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object DevKitSetup {

    private const val SETUP_SCRIPT_ASSET_PATH = "setup.sh"
    private const val SETUP_SCRIPT_FILE_NAME = "setup.sh"
    
    @JvmStatic
    fun startSetup(activity: Activity) {
        TermuxInstaller.setupBootstrapIfNeeded(activity) {
            try {
                val homeDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
                homeDir.mkdirs()

                val setupScriptFile = File(homeDir, SETUP_SCRIPT_FILE_NAME)
                copyAssetToFile(activity, SETUP_SCRIPT_ASSET_PATH, setupScriptFile)

                try {
                    Os.chmod(setupScriptFile.absolutePath, 455)
                } catch (_: Throwable) {
                }

                runScriptInNewTerminalSession(activity, setupScriptFile.absolutePath)

                activity.startActivity(Intent(activity, TermuxActivity::class.java))

            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    "Failed to start setup: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun runScriptInNewTerminalSession(activity: Activity, scriptAbsolutePath: String) {
        val bashPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash"

        val execUri = Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(bashPath)
            .build()

        val execIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, execUri).apply {
            setClass(activity, TermuxService::class.java)
            putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arrayOf(scriptAbsolutePath))
            putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TermuxConstants.TERMUX_HOME_DIR_PATH)
            putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.TERMINAL_SESSION.getName())
            putExtra(
                TERMUX_SERVICE.EXTRA_SESSION_ACTION,
                TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY.toString()
            )
            putExtra(
                TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE,
                ShellCreateMode.ALWAYS.mode
            )
            putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, "setup-development-kit")
            putExtra(
                TERMUX_SERVICE.EXTRA_COMMAND_LABEL,
                activity.getString(R.string.acs_setup_development_kit)
            )
            putExtra(
                TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION,
                activity.getString(R.string.acs_setup_development_kit_summary)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(execIntent)
        } else {
            activity.startService(execIntent)
        }
    }

    @Throws(IOException::class)
    private fun copyAssetToFile(
        activity: Activity,
        assetPath: String,
        destinationFile: File
    ) {
        activity.assets.open(assetPath).use { input ->
            FileOutputStream(destinationFile, false).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }
}