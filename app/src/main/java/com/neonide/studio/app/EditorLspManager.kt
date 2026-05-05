package com.neonide.studio.app

import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.lsp.EditorLspControllerFactory
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import android.content.Intent
import android.widget.Toast
import com.neonide.studio.app.lsp.server.JavaLanguageServerService

class EditorLspManager(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val bottomSheetVm: BottomSheetViewModel,
    private val uiScope: CoroutineScope
) {
    val controller by lazy { EditorLspControllerFactory.createOrNoop(activity) }
    val handler by lazy { EditorLspHandler(activity, editor, controller, bottomSheetVm, uiScope) }

    fun dispose() {
        runCatching { controller.dispose() }
    }

    fun startJavaLsp() {
        runCatching {
            activity.startService(Intent(activity, JavaLanguageServerService::class.java))
            Toast.makeText(activity, "Java LSP server service started", Toast.LENGTH_SHORT).show()
        }
    }
}
