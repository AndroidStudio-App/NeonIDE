package com.neonide.studio.editor.bottomsheet.terminal

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.extrakeys.SpecialButton
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

internal class DefaultTerminalView(
    private val context: Context,
    private val terminalViewProvider: () -> TerminalView?,
    private val extraKeysViewProvider: () -> ExtraKeysView?
) : TerminalViewClient {

    private var textSize = 30

    override fun onScale(scale: Float): Float {
        val view = terminalViewProvider() ?: return scale
        if (scale < 0.9f || scale > 1.1f) {
            textSize = if (scale > 1f) textSize + 5 else (textSize - 5).coerceAtLeast(10)
            view.setTextSize(textSize)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val view = terminalViewProvider() ?: return
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean =
        extraKeysViewProvider()?.readSpecialButton(SpecialButton.CTRL, true) ?: false
    override fun readAltKey(): Boolean =
        extraKeysViewProvider()?.readSpecialButton(SpecialButton.ALT, true) ?: false
    override fun readShiftKey(): Boolean =
        extraKeysViewProvider()?.readSpecialButton(SpecialButton.SHIFT, true) ?: false
    override fun readFnKey(): Boolean =
        extraKeysViewProvider()?.readSpecialButton(SpecialButton.FN, true) ?: false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean =
        false
    override fun onEmulatorSet() {}
    override fun logError(tag: String, message: String) {}
    override fun logWarn(tag: String, message: String) {}
    override fun logInfo(tag: String, message: String) {}
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}
