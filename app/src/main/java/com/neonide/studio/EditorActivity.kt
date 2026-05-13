package com.neonide.studio

import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.neonide.studio.app.EditorGradleManager
import com.neonide.studio.app.EditorViewModel
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.BuildOutputBuffer
import com.neonide.studio.app.editor.EditorScreen
import com.neonide.studio.app.editor.EditorSettingsState
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.filetree.FileTreeDrawer
import com.neonide.studio.ui.theme.AppTheme
import com.neonide.studio.utils.OpenFile
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

    private val openFilesState = mutableStateOf<List<OpenFile>>(emptyList())
    private val activeFileState = mutableStateOf<OpenFile?>(null)
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
        editorVm.projectPath = projectPath
        editorVm.initializeLsp(this)

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (drawerLayout.isDrawerOpen(Gravity.START)) {
                        drawerLayout.closeDrawer(Gravity.START)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, insets ->
            ViewCompat.dispatchApplyWindowInsets(mainContent, insets)
            ViewCompat.dispatchApplyWindowInsets(drawerView, insets)
            insets
        }

        mainContent.setContent {
            AppTheme {
                EditorScreen(
                    editorVm = editorVm,
                    bottomSheetVm = bottomSheetVm,
                    settings = settingsState,
                    projectPath = projectPath,
                    openFilesState = openFilesState,
                    activeFileState = activeFileState,
                    editorState = editorState,
                    symbolInputView = symbolInputView,
                    gradleManager = gradleManager,
                    onOpenDrawer = { drawerLayout.openDrawer(Gravity.START) }
                )
            }
        }

        drawerView.setContent {
            AppTheme {
                Box(modifier = Modifier.systemBarsPadding()) {
                    FileTreeDrawer(
                        rootPath = projectPath.path,
                        onFileClick = { path ->
                            if (!path.endsWith(".apk", ignoreCase = true)) {
                                val file = File(path)
                                val existingFile = openFilesState.value.find { it.path == path }

                                // Save current text before switching
                                activeFileState.value?.let { active ->
                                    editorState.value?.text?.toString()?.let { currentText ->
                                        val updated = active.copy(content = currentText)
                                        openFilesState.value = openFilesState.value.map {
                                            if (it.path == updated.path) updated else it
                                        }
                                    }
                                }

                                if (existingFile != null) {
                                    activeFileState.value = existingFile
                                } else {
                                    if (file.exists() && file.isFile) {
                                        val content = runCatching {
                                            file.readText()
                                        }.getOrDefault("")
                                        val newOpenFile = OpenFile(path, file.name, content)
                                        openFilesState.value = openFilesState.value + newOpenFile
                                        activeFileState.value = newOpenFile
                                    }
                                }
                            }
                            drawerLayout.closeDrawer(Gravity.START)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gradleManager.onDestroy()
    }
}
