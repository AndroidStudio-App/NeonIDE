package com.neonide.studio.app.bottomsheet.terminal

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.app.TermuxService
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.extrakeys.ExtraKeysConstants
import com.termux.shared.termux.extrakeys.ExtraKeysInfo
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.terminal.io.TerminalExtraKeys
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import java.util.Properties

private const val TERMINAL_BG = 0xFF000000.toInt()
private const val KEYS_TEXT = 0xFFFFFFFF.toInt()
private const val KEYS_ACTIVE_TEXT = 0xFF80DEEA.toInt()
private const val KEYS_BG = 0xFF000000.toInt()
private const val KEYS_ACTIVE_BG = 0xFF7F7F7F.toInt()

private fun applyDarkTerminalColors(session: TerminalSession, terminalView: TerminalView) {
    val props = Properties().apply {
        setProperty("foreground", "#ffffff")
        setProperty("background", "#000000")
        setProperty("cursor", "#ffffff")
    }
    TerminalColors.COLOR_SCHEME.updateWith(props)
    session.emulator?.mColors?.reset()
    terminalView.setBackgroundColor(TERMINAL_BG)
}

private fun applyDarkExtraKeysColors(extraKeysView: ExtraKeysView) {
    extraKeysView.setButtonColors(
        KEYS_TEXT,
        KEYS_ACTIVE_TEXT,
        KEYS_BG,
        KEYS_ACTIVE_BG
    )
    extraKeysView.setBackgroundColor(KEYS_BG)
}

@Composable
fun TerminalTab(
    projectPath: String,
    sessionId: Int,
    sessionHolder: MutableState<TerminalSession?>,
    onSessionExit: () -> Unit
) {
    val context = LocalContext.current
    val serviceRef = remember { mutableStateOf<TermuxService?>(null) }

    DisposableEffect(Unit) {
        val intent = Intent(context, TermuxService::class.java)
        context.startForegroundService(intent)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val field = binder.javaClass.getField("service")
                serviceRef.value = field.get(binder) as TermuxService
            }
            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.value = null
            }
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(connection)
        }
    }

    val service = serviceRef.value

    LaunchedEffect(service, projectPath, sessionId) {
        if (service != null && sessionHolder.value == null) {
            val session = service.createTermuxSession(
                "${TermuxConstants.TERMUX_PREFIX_DIR_PATH}/bin/login",
                arrayOf(),
                null,
                projectPath,
                false,
                null
            )?.terminalSession
            sessionHolder.value = session
        }
    }

    val terminalSession = sessionHolder.value

    if (service == null || terminalSession == null) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            Text(text = "Initializing terminal...", modifier = Modifier.align(Alignment.Center))
        }
    } else {
        val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }
        AndroidView(
            factory = { context ->
                val activity = context as? Activity
                @Suppress("DEPRECATION")
                activity?.window?.setDecorFitsSystemWindows(false)

                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(TERMINAL_BG)
                }

                var extraKeysView: ExtraKeysView? = null

                val terminalView = TerminalView(context, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTextSize(30)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )

                    setTerminalViewClient(
                        DefaultTerminalView(context, { this }, { extraKeysView })
                    )

                    terminalSession.updateTerminalSessionClient(
                        object : TerminalSessionClient by DefaultTerminalSession(context) {
                            override fun onTextChanged(session: TerminalSession) {
                                post { onScreenUpdated() }
                            }

                            override fun onColorsChanged(session: TerminalSession) {
                                post {
                                    val emulator = session.emulator ?: return@post
                                    val colors = emulator.mColors.mCurrentColors
                                    val background =
                                        colors[TextStyle.COLOR_INDEX_BACKGROUND]
                                    setBackgroundColor(background)
                                    onScreenUpdated()
                                }
                            }

                            override fun onSessionFinished(session: TerminalSession) {
                                // Notify TermuxService that the session has exited
                                // so it can clean up the session and the notification.
                                val termuxSession = service.getTermuxSessionForTerminalSession(
                                    session
                                )
                                if (termuxSession != null) {
                                    service.onTermuxSessionExited(termuxSession)
                                }
                                sessionHolder.value = null
                                onSessionExit()
                            }
                        }
                    )

                    attachSession(terminalSession)
                    applyDarkTerminalColors(terminalSession, this)
                }

                terminalViewRef.value = terminalView

                extraKeysView = ExtraKeysView(context, null).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        200,
                        0f
                    )
                }

                val extraKeys = TerminalExtraKeys(terminalView)
                extraKeysView.setExtraKeysViewClient(extraKeys)
                applyDarkExtraKeysColors(extraKeysView)

                val extraKeysInfo = ExtraKeysInfo(
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
                    "default",
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES
                )
                extraKeysView.reload(extraKeysInfo, 0f)

                layout.addView(terminalView)
                layout.addView(extraKeysView)
                layout
            },
            modifier = Modifier.fillMaxSize().imePadding()
        )
        LaunchedEffect(terminalSession) {
            terminalViewRef.value?.requestFocus()
        }
    }
}
