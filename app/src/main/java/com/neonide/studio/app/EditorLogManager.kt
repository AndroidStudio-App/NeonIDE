package com.neonide.studio.app

import android.content.Context
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import io.github.rosemoe.sora.widget.CodeEditor

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
}
