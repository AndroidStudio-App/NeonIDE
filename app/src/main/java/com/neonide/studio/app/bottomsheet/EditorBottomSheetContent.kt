package com.neonide.studio.app.bottomsheet

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.neonide.studio.utils.GradleBuildStatus
import com.termux.shared.logger.Logger
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class NavigationItem(
    val uri: String,
    val line: Int,
    val character: Int,
    val displayText: String
)

enum class BottomSheetTab(val title: String) {
    BUILD_OUTPUT("Build"),
    APP_LOGS("Logcat"),
    IDE_LOGS("IDE Logs"),
    DIAGNOSTICS("Diagnostics"),
    SEARCH("Search"),
    REFERENCES("References")
}

private const val BOTTOM_SHEET_TAG = "EditorBottomSheet"

class BottomSheetViewModel : ViewModel() {

    private val _buildOutput = MutableLiveData("")
    val buildOutput: LiveData<String> = _buildOutput

    private val _appLogs = MutableLiveData("")
    val appLogs: LiveData<String> = _appLogs

    private val _ideLogs = MutableLiveData("")
    val ideLogs: LiveData<String> = _ideLogs

    private val _diagnostics = MutableLiveData<List<String>>(emptyList())
    val diagnostics: LiveData<List<String>> = _diagnostics

    private val _searchResults = MutableLiveData<List<String>>(emptyList())
    val searchResults: LiveData<List<String>> = _searchResults

    private val _navigationResults = MutableLiveData<List<NavigationItem>>(emptyList())
    val navigationResults: LiveData<List<NavigationItem>> = _navigationResults

    private val _status = MutableLiveData<String?>(null)
    val status: LiveData<String?> = _status

    private val _isBuilding = MutableLiveData(false)
    val isBuilding: LiveData<Boolean> = _isBuilding

    private val _selectedTab = MutableLiveData(0)
    val selectedTab: LiveData<Int> = _selectedTab

    fun setBuildOutput(text: String) = _buildOutput.postValue(text)
    fun setAppLogs(text: String) = _appLogs.postValue(text)
    fun setIdeLogs(text: String) = _ideLogs.postValue(text)
    fun setDiagnostics(items: List<String>) = _diagnostics.postValue(items)
    fun setSearchResults(items: List<String>) = _searchResults.postValue(items)
    fun setNavigationResults(items: List<NavigationItem>) = _navigationResults.postValue(items)
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
    pagerState: PagerState,
    tabs: List<BottomSheetTab>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    TabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = Color.Transparent,
        divider = {},
        modifier = modifier
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = {
                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            )
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
    pagerState: PagerState,
    onNavigate: (NavigationItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabs = BottomSheetTab.entries
    val selectedTab by viewModel.selectedTab.observeAsState(0)

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.setSelectedTab(page) }
    }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        val gradleRunning = rememberGradleRunning()
        if (gradleRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            when (tabs[page]) {
                BottomSheetTab.BUILD_OUTPUT -> LogViewerPage(
                    viewModel.buildOutput.observeAsState("").value
                )

                BottomSheetTab.APP_LOGS -> LogViewerPage(viewModel.appLogs.observeAsState("").value)

                BottomSheetTab.IDE_LOGS -> LogViewerPage(viewModel.ideLogs.observeAsState("").value)

                BottomSheetTab.DIAGNOSTICS -> SimpleListPage(
                    viewModel.diagnostics.observeAsState(emptyList()).value
                )

                BottomSheetTab.SEARCH -> SimpleListPage(
                    viewModel.searchResults.observeAsState(emptyList()).value
                )

                BottomSheetTab.REFERENCES -> NavigationResultsPage(viewModel, onNavigate)
            }
        }
    }
}

private class LogState {
    var lastLen = 0
}

@Composable
private fun LogViewerPage(contentStream: String) {
    val state = remember { LogState() }

    AndroidView(
        factory = { context ->
            CodeEditor(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                setEditable(false)
                setTextSize(12f)
                setScalable(true)
                setText("", true, null)
                props.stickyScroll = true
                props.overScrollEnabled = true
                setInterceptParentHorizontalScrollIfNeeded(false)
            }
        },
        update = { ed ->
            val snapshot = contentStream

            if (snapshot.isEmpty()) {
                ed.setText("", true, null)
                state.lastLen = 0
                return@AndroidView
            }

            val delta = if (snapshot.length >= state.lastLen) {
                snapshot.substring(state.lastLen)
            } else {
                state.lastLen = 0
                ed.setText("", true, null)
                snapshot
            }

            if (delta.isEmpty()) {
                state.lastLen = snapshot.length
                return@AndroidView
            }

            val toInsert = delta.replace("\r\n", "\n")

            try {
                val content = ed.text
                val lastLine = content.lineCount - 1
                val lastCol = content.getColumnCount(lastLine)

                content.insert(lastLine, lastCol, toInsert)

                val newLine = content.lineCount - 1
                val newCol = content.getColumnCount(newLine)
                ed.setSelection(newLine, newCol)
            } catch (e: IllegalStateException) {
                Logger.logDebug(BOTTOM_SHEET_TAG, "insert failed: ${e.message}")
                runCatching {
                    ed.setText(snapshot)
                    val content = ed.text
                    val lastLine = content.lineCount - 1
                    ed.setSelection(lastLine, content.getColumnCount(lastLine))
                }
            }

            state.lastLen = snapshot.length
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun SimpleListPage(items: List<String>) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No items",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                Text(
                    text = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun NavigationResultsPage(
    viewModel: BottomSheetViewModel,
    onNavigate: (NavigationItem) -> Unit
) {
    val items by viewModel.navigationResults.observeAsState(emptyList())

    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                Text(
                    text = item.displayText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(item) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
