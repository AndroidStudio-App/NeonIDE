package com.neonide.studio.app.bottomsheet

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.buildoutput.BuildTab
import com.neonide.studio.app.bottomsheet.preview.PreviewTab
import com.neonide.studio.app.bottomsheet.terminal.TerminalTab
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.GradleBuildStatus
import com.termux.terminal.TerminalSession

enum class BottomSheetTab(val title: String) {
    BUILD_OUTPUT("Build Output"),
    TERMINAL("Terminal"),
    PREVIEW("Preview")
}

private const val BOTTOM_SHEET_TAG = "EditorBottomSheet"

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
    activeFilePath: String?,
    markdownContent: String,
    modifier: Modifier = Modifier
) {
    val tabs = remember { mutableStateListOf(BottomSheetTab.BUILD_OUTPUT) }
    val selectedTab by viewModel.selectedTab.observeAsState(0)

    val coroutineScope = rememberCoroutineScope()

    val buildOutput by viewModel.buildOutput.observeAsState("")
    val buildOutputPage = remember {
        movableContentOf { content: String ->
            BuildTab(content)
        }
    }

    val activeTerminalSession = remember { mutableStateOf<TerminalSession?>(null) }
    var terminalSessionId by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            activeTerminalSession.value?.finishIfRunning()
            activeTerminalSession.value = null
        }
    }

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

    LaunchedEffect(activeFilePath) {
        val isMd = activeFilePath?.endsWith(".md", ignoreCase = true) == true
        if (isMd && !tabs.contains(BottomSheetTab.PREVIEW)) {
            tabs.add(BottomSheetTab.PREVIEW)
        } else if (!isMd && tabs.contains(BottomSheetTab.PREVIEW)) {
            tabs.remove(BottomSheetTab.PREVIEW)
            if (tabs.getOrNull(selectedTab) == null) {
                viewModel.setSelectedTab(0)
            }
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

        val current = tabs.getOrNull(selectedTab)
        val terminalExists = tabs.contains(BottomSheetTab.TERMINAL)
        val previewExists = tabs.contains(BottomSheetTab.PREVIEW)

        AppBox(modifier = Modifier.fillMaxSize()) {
            AppBox(
                modifier = if (current == BottomSheetTab.BUILD_OUTPUT) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.clipToBounds().size(0.dp)
                }
            ) {
                buildOutputPage(buildOutput)
            }

            if (terminalExists) {
                AppBox(
                    modifier = if (current == BottomSheetTab.TERMINAL) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.clipToBounds().size(0.dp)
                    }
                ) {
                    terminalPage()
                }
            }

            if (previewExists && current == BottomSheetTab.PREVIEW) {
                AppBox(modifier = Modifier.fillMaxSize()) {
                    PreviewTab(markdownContent, activeFilePath)
                }
            }
        }
    }
}
