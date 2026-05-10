package com.neonide.studio.app.bottomsheet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch

@Composable
fun EditorBottomSheetContent(
    viewModel: BottomSheetViewModel,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        R.string.acs_tab_build_output,
        R.string.acs_tab_app_logs,
        R.string.acs_tab_ide_logs,
        R.string.acs_tab_diagnostics,
        R.string.acs_tab_search,
        R.string.acs_tab_references
    )

    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val status by viewModel.status.observeAsState()
    val selectedTab by viewModel.selectedTab.observeAsState(0)

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setSelectedTab(pagerState.currentPage)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    color = Color.Gray,
                    shape = MaterialTheme.shapes.small
                ) {}
                
                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            tabs.forEachIndexed { index, titleRes ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = stringResource(titleRes),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> BuildOutputPage(viewModel)
                1 -> LogsPage(viewModel.appLogs.observeAsState("").value)
                2 -> LogsPage(viewModel.ideLogs.observeAsState("").value)
                3 -> SimpleListPage(viewModel.diagnostics.observeAsState(emptyList()).value)
                4 -> SimpleListPage(viewModel.searchResults.observeAsState(emptyList()).value)
                5 -> NavigationResultsPage(viewModel)
            }
        }
    }
}

@Composable
fun BuildOutputPage(viewModel: BottomSheetViewModel) {
    val output by viewModel.buildOutput.observeAsState("")
    var editor by remember { mutableStateOf<CodeEditor?>(null) }

    LaunchedEffect(output) {
        editor?.setText(output)
        // Auto-scroll to end
        editor?.let { ed ->
            val content = ed.text
            ed.setSelection(content.lineCount - 1, content.getColumnCount(content.lineCount - 1))
        }
    }

    AndroidView(
        factory = { context ->
            CodeEditor(context).apply {
                setEditorLanguage(EmptyLanguage())
                setEditable(false)
                isWordwrap = false
                typefaceText = android.graphics.Typeface.MONOSPACE
                setTextSize(12f)
                setScalable(true)
                setInterceptParentHorizontalScrollIfNeeded(true)
                editor = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun LogsPage(logs: String) {
    AndroidView(
        factory = { context ->
            CodeEditor(context).apply {
                setEditorLanguage(EmptyLanguage())
                setEditable(false)
                isWordwrap = false
                typefaceText = android.graphics.Typeface.MONOSPACE
                setTextSize(12f)
                setScalable(true)
                setInterceptParentHorizontalScrollIfNeeded(true)
                setText(logs)
            }
        },
        update = { view ->
            if (view.text.toString() != logs) {
                view.setText(logs)
                val content = view.text
                view.setSelection(content.lineCount - 1, content.getColumnCount(content.lineCount - 1))
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun SimpleListPage(items: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            Text(
                text = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun NavigationResultsPage(viewModel: BottomSheetViewModel) {
    val items by viewModel.navigationResults.observeAsState(emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            Text(
                text = item.displayText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                fontSize = 14.sp
            )
            // Add click listener if needed
        }
    }
}
