package com.neonide.studio.app.bottomsheet

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.buildoutput.BuildTab
import com.neonide.studio.app.bottomsheet.terminal.TerminalTab
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.GradleBuildStatus
import com.termux.terminal.TerminalSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BottomSheetTab(val title: String) {
    BUILD_OUTPUT("Build Output"),
    TERMINAL("Terminal")
}

private const val BOTTOM_SHEET_TAG = "EditorBottomSheet"

class BottomSheetViewModel : ViewModel() {

    private val _buildOutput = MutableLiveData("")
    val buildOutput: LiveData<String> = _buildOutput

    private val _status = MutableLiveData<String?>(null)
    val status: LiveData<String?> = _status

    private val _isBuilding = MutableLiveData(false)
    val isBuilding: LiveData<Boolean> = _isBuilding

    private val _selectedTab = MutableLiveData(0)
    val selectedTab: LiveData<Int> = _selectedTab

    fun setBuildOutput(text: String) = _buildOutput.postValue(text)
    fun setStatus(text: String?) = _status.postValue(text)
    fun setIsBuilding(value: Boolean) = _isBuilding.postValue(value)
    fun setSelectedTab(index: Int) = _selectedTab.postValue(index)
}

object BuildOutputBuffer {

    private const val FLUSH_DELAY_MS = 150L
    private const val MAX_CHARS = 700_000
    private const val TRIM_TO_CHARS = 550_000

    private val handler = Handler(Looper.getMainLooper())
    private val pending = StringBuilder(8_192)
    private val flushScheduled = AtomicBoolean(false)

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    fun getSnapshot(): String = _output.value

    fun clear() {
        synchronized(pending) { pending.clear() }
        _output.value = ""
    }

    fun appendLine(line: String) {
        val msg = if (line.endsWith("\n")) line else "$line\n"
        synchronized(pending) { pending.append(msg) }
        scheduleFlush()
    }

    fun appendRaw(text: String) {
        synchronized(pending) { pending.append(text) }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        handler.postDelayed({
            flushScheduled.set(false)
            flush()
        }, FLUSH_DELAY_MS)
    }

    private fun flush() {
        val chunk: String = synchronized(pending) {
            if (pending.isEmpty()) return
            val out = pending.toString()
            pending.clear()
            out
        }

        var newValue = _output.value + chunk
        if (newValue.length > MAX_CHARS) {
            val trimmed = newValue.length - TRIM_TO_CHARS
            newValue = "... [trimmed $trimmed chars] ...\n\n" +
                newValue.takeLast(TRIM_TO_CHARS)
        }
        _output.value = newValue
    }
}

@Composable
fun BottomSheetTabRow(
    selectedTab: Int,
    tabs: List<BottomSheetTab>,
    onTabSelected: (Int) -> Unit,
    onOpenTerminal: () -> Unit,
    onCloseTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showCloseMenu by remember { mutableStateOf(false) }

    AppRow(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = {},
            modifier = Modifier.weight(1f)
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        AppRow(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            if (tab == BottomSheetTab.TERMINAL) {
                                IconButton(
                                    onClick = { showCloseMenu = true },
                                    modifier = Modifier.size(18.dp).padding(start = 4.dp)
                                ) {
                                    Text("×")
                                }
                            }
                        }
                    }
                )
            }
        }
        DropdownMenu(
            expanded = showCloseMenu,
            onDismissRequest = { showCloseMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.close)) },
                onClick = {
                    onCloseTerminal()
                    showCloseMenu = false
                }
            )
        }
        IconButton(onClick = { showMenu = true }) {
            AppIcon(
                painter = painterResource(R.drawable.ic_menu_kebab),
                tint = LocalContentColor.current
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_terminal)) },
                    onClick = {
                        onOpenTerminal()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun rememberGradleRunning(): Boolean {
    var running by remember { mutableStateOf(GradleBuildStatus.isRunning) }
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { running = it }
        GradleBuildStatus.addListener(listener)
        onDispose { GradleBuildStatus.removeListener(listener) }
    }
    return running
}

@Composable
fun EditorBottomSheetContent(
    viewModel: BottomSheetViewModel,
    projectPath: String,
    modifier: Modifier = Modifier
) {
    val tabs = remember { mutableStateListOf(BottomSheetTab.BUILD_OUTPUT) }
    val selectedTab by viewModel.selectedTab.observeAsState(0)

    val coroutineScope = rememberCoroutineScope()

    val buildOutputPage = remember {
        movableContentOf {
            BuildTab(
                viewModel.buildOutput.observeAsState("").value
            )
        }
    }

    val activeTerminalSession = remember { mutableStateOf<TerminalSession?>(null) }
    var terminalSessionId by remember { mutableStateOf(0) }

    val terminalPage = remember {
        movableContentOf {
            TerminalTab(
                projectPath = projectPath,
                sessionId = terminalSessionId,
                sessionHolder = activeTerminalSession,
                onSessionExit = {
                    tabs.remove(BottomSheetTab.TERMINAL)
                    viewModel.setSelectedTab(0)
                }
            )
        }
    }

    AppColumn(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        val gradleRunning = rememberGradleRunning()
        if (gradleRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        BottomSheetTabRow(
            selectedTab = selectedTab,
            tabs = tabs,
            onTabSelected = { viewModel.setSelectedTab(it) },
            onOpenTerminal = {
                if (!tabs.contains(BottomSheetTab.TERMINAL)) {
                    terminalSessionId++
                    tabs.add(BottomSheetTab.TERMINAL)
                    viewModel.setSelectedTab(tabs.size - 1)
                }
            },
            onCloseTerminal = {
                activeTerminalSession.value?.let { session ->
                    // Attempt to bypass "[Process completed - press Enter]"
                    try {
                        session.emulator.paste("\r")
                    } catch (e: Exception) {
                        // Ignore
                    }
                    session.finishIfRunning()
                }
                activeTerminalSession.value = null
                tabs.remove(BottomSheetTab.TERMINAL)
                viewModel.setSelectedTab(0)
            }
        )

        val isTerminalSelected = tabs.getOrNull(selectedTab) == BottomSheetTab.TERMINAL

        AppBox(modifier = Modifier.fillMaxSize()) {
            AppBox(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha =
                        if (isTerminalSelected) 0f else 1f
                }
            ) {
                buildOutputPage()
            }
            if (tabs.contains(BottomSheetTab.TERMINAL)) {
                AppBox(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha =
                            if (isTerminalSelected) 1f else 0f
                    }
                ) {
                    terminalPage()
                }
            }
        }
    }
}
