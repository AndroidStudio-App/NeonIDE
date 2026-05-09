package com.neonide.studio

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.ui.theme.AppTheme
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import android.view.Gravity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.compose.ui.platform.ComposeView
import com.neonide.studio.R

class EditorActivity : ComponentActivity() {
    private val filePathState =
        mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        val projectPath = intent.getStringExtra("extra_project_dir") ?: ""
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val mainContent = findViewById<ComposeView>(R.id.main_content)
        val drawerView = findViewById<ComposeView>(R.id.file_tree_drawer_view)
        
        mainContent.setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        AppTopBar(onNavigationClick = { drawerLayout.openDrawer(Gravity.START) })
                    }
                ) { padding ->
                    Editor(
                        modifier = Modifier.padding(padding),
                        filePath = filePathState.value
                    )
                }
            }
        }

        drawerView.setContent {
            AppTheme {
                FileTreeDrawer(
                    rootPath = projectPath,
                    onFileClick = { path ->
                        if (!path.endsWith(".apk", ignoreCase = true)) {
                            filePathState.value = path
                        }
                        drawerLayout.closeDrawer(Gravity.START)
                    }
                )
            }
        }
    }
}

@Composable
fun AppTopBar(onNavigationClick: () -> Unit) {
    TopAppBar(
        modifier = Modifier.height(50.dp),
        title = {},
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }
    )
}

@Composable
fun Editor(
    modifier: Modifier = Modifier,
    filePath: String?
) {
    var editor by remember {
        mutableStateOf<CodeEditor?>(null)
    }
    LaunchedEffect(filePath) {

        filePath?.let { path ->

            val content = withContext(Dispatchers.IO) {

                runCatching {
                    java.io.File(path).readText()
                }.getOrNull()
            }

            content?.let {
                editor?.setText(it)
            }
        }
    }
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                editor = this
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE
                setEditorLanguage(JavaLanguage())
                props.stickyScroll = true
                props.overScrollEnabled = true
                isCursorAnimationEnabled = true
                nonPrintablePaintingFlags = 
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or 
                CodeEditor.FLAG_DRAW_LINE_SEPARATOR or 
                CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or 
                CodeEditor.FLAG_DRAW_SOFT_WRAP
            }
        },
        onRelease = { editor ->
            editor.release()
        }
    )
}