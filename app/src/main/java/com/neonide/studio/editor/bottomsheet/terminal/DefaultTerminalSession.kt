package com.neonide.studio.editor.bottomsheet.terminal

import android.content.Context
import com.termux.shared.interact.ShareUtils
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

internal class DefaultTerminalSession(private val context: Context) : TerminalSessionClient {
    override fun onTextChanged(session: TerminalSession) {}
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ShareUtils.copyTextToClipboard(context, text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val text = ShareUtils.getTextStringFromClipboardIfSet(context, true)
        if (text != null && session != null) {
            session.emulator.paste(text)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String, message: String) {}
    override fun logWarn(tag: String, message: String) {}
    override fun logInfo(tag: String, message: String) {}
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}
