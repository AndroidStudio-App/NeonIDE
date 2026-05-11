package com.neonide.studio.app

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.EditorBottomSheetContent
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView

@Composable
fun SoraEditorScreen(
    editor: CodeEditor,
    symbolInput: SymbolInputView,
    editorVm: EditorViewModel,
    bottomSheetVm: BottomSheetViewModel,
    searchController: EditorSearchController,
    scaffoldState: BottomSheetScaffoldState,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            EditorBottomSheetContent(
                viewModel = bottomSheetVm,
                modifier = Modifier.fillMaxHeight(0.6f)
            )
        },
        sheetPeekHeight = 72.dp,
        topBar = topBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (editorVm.searchPanelVisible) {
                EditorSearchPanel(editorVm, searchController)
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = {
                        editor.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            bottomBar()
        }
    }
}

@Composable
fun EditorSearchPanel(viewModel: EditorViewModel, controller: EditorSearchController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.gotoPrev() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
            }
            IconButton(onClick = { controller.gotoNext() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
            IconButton(onClick = { controller.replaceCurrent() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = "Replace")
            }
            IconButton(onClick = { controller.replaceAll() }, modifier = Modifier.weight(1f)) {
                Text("ALL")
            }
            IconButton(onClick = { /* Show options menu */ }, modifier = Modifier.weight(0.5f)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        }

        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = {
                viewModel.searchQuery = it
                controller.tryCommitSearch()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sora_text_to_search)) },
            singleLine = true
        )

        OutlinedTextField(
            value = viewModel.replacementText,
            onValueChange = { viewModel.replacementText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sora_replacement)) },
            singleLine = true
        )
    }
}
