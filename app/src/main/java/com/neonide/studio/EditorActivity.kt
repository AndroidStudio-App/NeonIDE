package com.neonide.studio

import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.neonide.studio.app.EditorGradleManager
import com.neonide.studio.app.EditorViewModel
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import com.neonide.studio.app.editor.EditorScreen
import com.neonide.studio.app.editor.EditorSettingsState
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.filetree.FileTreeDrawer
import com.neonide.studio.ui.theme.AppTheme
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EditorActivity : ComponentActivity() {
    companion object {
        private val SYMBOLS =
            arrayOf("->", "{", "}", "(", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<", ">", "[", "]", ":")
        private val SYMBOL_INSERT_TEXT =
            arrayOf("\t", "{}", "}", "()", ")", ",", ".", ";", "\"", "?", "+", "-", "*", "/", "<>", ">", "[]", "]", ":")
    }

    private val filePathState = mutableStateOf<String?>(null)
    private val editorState = mutableStateOf<CodeEditor?>(null)
    private val editorVm: EditorViewModel by viewModels()
    private val bottomSheetVm: BottomSheetViewModel by viewModels()
    private val settingsState = EditorSettingsState()

    private val languageProvider: SoraLanguageProvider by lazy { SoraLanguageProvider(this) }
    private val gradleManager: EditorGradleManager by lazy {
        EditorGradleManager(this, bottomSheetVm)
    }

    private val symbolInputView by lazy {
        SymbolInputView(this).apply { addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val projectPath = File(intent.getStringExtra("extra_project_dir") ?: return)

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_editor)

        lifecycleScope.launch {
            BuildOutputBuffer.output.collectLatest { output ->
                bottomSheetVm.setBuildOutput(output)
            }
        }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val mainContent = findViewById<ComposeView>(R.id.main_content)
        val drawerView = findViewById<ComposeView>(R.id.file_tree_drawer_view)

        mainContent.setContent {
            AppTheme {
                EditorScreen(
                    editorVm = editorVm,
                    bottomSheetVm = bottomSheetVm,
                    settings = settingsState,
                    projectPath = projectPath,
                    filePathState = filePathState,
                    editorState = editorState,
                    symbolInputView = symbolInputView,
                    gradleManager = gradleManager,
                    onOpenDrawer = { drawerLayout.openDrawer(Gravity.START) }
                )
            }
        }

        drawerView.setContent {
            AppTheme {
                FileTreeDrawer(
                    rootPath = projectPath.path,
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

    override fun onDestroy() {
        super.onDestroy()
        gradleManager.onDestroy()
    }
}
