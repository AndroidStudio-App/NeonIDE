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
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

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
