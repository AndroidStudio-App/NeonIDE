package com.neonide.studio.app

import android.content.Context
import android.widget.Toast
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditorLogManager(
    private val context: Context,
    private val editor: CodeEditor,
    private val bottomSheetVm: BottomSheetViewModel,
    private val uiScope: CoroutineScope
) {
    private val filesDir = context.filesDir

    fun refreshAppLogs(bufferSize: Int) {
        uiScope.launch(Dispatchers.IO) {
            val lines = runCatching { 
                ProcessBuilder("logcat", "-d", "-t", bufferSize.toString())
                    .redirectErrorStream(true).start().inputStream.bufferedReader().readText() 
            }.getOrDefault("")
            bottomSheetVm.setAppLogs(lines)
        }
    }

    fun openBuildLog(logFileName: String) {
        val logFile = File(filesDir, logFileName)
        if (!logFile.exists()) {
            Toast.makeText(context, context.getString(R.string.sora_not_supported), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { logFile.readText() }.onSuccess {
            editor.setText(it)
            bottomSheetVm.setBuildOutput(it)
        }
    }

    fun openLogs(logFileName: String) {
        runCatching { context.openFileInput(logFileName).reader().readText() }.onSuccess {
            editor.setText(it)
            bottomSheetVm.setIdeLogs(it)
        }
    }

    fun clearLogs(logFileName: String) {
        runCatching { context.openFileOutput(logFileName, Context.MODE_PRIVATE).use { } }
        bottomSheetVm.setIdeLogs("")
    }

    fun openIdeFileLog() {
        val logFile = com.neonide.studio.logger.IDEFileLogger.getLogFile()
        if (logFile?.exists() != true) {
            Toast.makeText(context, "IDE file log not found.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { logFile.readText() }.onSuccess {
            editor.setText(it)
            bottomSheetVm.setIdeLogs(it)
        }
    }
}
