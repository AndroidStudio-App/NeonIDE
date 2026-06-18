package com.neonide.studio.app.lsp

import android.content.Context
import android.os.Build
import com.termux.shared.logger.Logger

object EditorLspControllerFactory {

    private const val IMPL_CLASS = "com.neonide.studio.app.lsp.impl.SoraEditorLspController"
    private const val TAG = "LspControllerFactory"

    /**
     * Create the real LSP controller on API 26+.
     *
     * Uses reflection to avoid class loading/verifier issues on lower API levels.
     */
    fun createOrNoop(context: Context): EditorLspController {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return NoopEditorLspController

        return try {
            val cls = Class.forName(IMPL_CLASS)
            val ctor = cls.getDeclaredConstructor(Context::class.java)
            ctor.isAccessible = true
            ctor.newInstance(context.applicationContext) as EditorLspController
        } catch (t: ReflectiveOperationException) {
            // If anything goes wrong (missing class, verifier error, etc.), fall back.
            Logger.logDebug(TAG, "Lsp controller init failed, using noop: ${t.message}")
            NoopEditorLspController
        } catch (t: SecurityException) {
            Logger.logDebug(TAG, "Lsp controller init failed, using noop: ${t.message}")
            NoopEditorLspController
        }
    }
}
