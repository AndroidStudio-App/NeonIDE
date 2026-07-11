package com.neonide.studio

import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.neonide.studio.app.EditorGradleController
import com.neonide.studio.app.bottomsheet.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.buildoutput.BuildOutputBuffer
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.lsp.EditorLspControllerFactory
import com.neonide.studio.editor.EditorScreen
import com.neonide.studio.editor.EditorSettingsState
import com.neonide.studio.filetree.FileTreeDrawer
import com.neonide.studio.ui.theme.AppTheme
import com.neonide.studio.utils.GradleService
import com.neonide.studio.utils.OpenFile
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROJECT_DIR = "extra_project_dir"
        private val symbolMap = mapOf(
            "->" to "\t",
            "{" to "{}",
            "}" to "}",
            "(" to "()",
            ")" to ")",
            "," to ",",
            "." to ".",
            ";" to ";",
            "\"" to "\"",
            "?" to "?",
            "+" to "+",
            "-" to "-",
            "*" to "*",
            "/" to "/",
            "<" to "<>",
            ">" to ">",
            "[" to "[]",
            "]" to "]",
            ":" to ":",
            "::" to "::"
        )
        private val SYMBOLS = symbolMap.keys.toTypedArray()
        private val SYMBOL_INSERT_TEXT = symbolMap.values.toTypedArray()
    }

    private val openFilesState = mutableStateOf<List<OpenFile>>(emptyList())
    private val activeFileState = mutableStateOf<OpenFile?>(null)
    private val editorState = mutableStateOf<CodeEditor?>(null)
    private val positionTextState = mutableStateOf("")
    private val bottomSheetVm: BottomSheetViewModel by viewModels()
    private val settingsState by lazy { EditorSettingsState(this) }

    private val languageProvider: SoraLanguageProvider by lazy { SoraLanguageProvider(this) }
    private val lspController by lazy { EditorLspControllerFactory.createOrNoop(this) }
    private val gradleController: EditorGradleController by lazy {
        EditorGradleController(this, bottomSheetVm)
    }

    private val symbolInputView by lazy {
        SymbolInputView(this).apply { addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val projectPath = File(intent.getStringExtra(EXTRA_PROJECT_DIR) ?: return)

        super.onCreate(savedInstanceState)
        BuildOutputBuffer.clear()
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_editor)

        savedInstanceState?.let { bundle ->
            val paths = bundle.getStringArrayList("open_paths") ?: return@let
            openFilesState.value = paths.mapNotNull { path ->
                val file = File(path)
                if (file.exists() && file.isFile) {
                    val content = runCatching { file.readText() }.getOrDefault("")
                    OpenFile(path, file.name, content)
                } else {
                    null
                }
            }
            activeFileState.value =
                openFilesState.value.find { it.path == bundle.getString("active_path") }
            positionTextState.value = bundle.getString("position_text", "")
        }

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
                    positionTextState = positionTextState,
                    bottomSheetVm = bottomSheetVm,
                    settings = settingsState,
                    projectPath = projectPath,
                    openFilesState = openFilesState,
                    activeFileState = activeFileState,
                    editorState = editorState,
                    symbolInputView = symbolInputView,
                    gradleController = gradleController,
                    languageProvider = languageProvider,
                    lspController = lspController,
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("open_paths", ArrayList(openFilesState.value.map { it.path }))
        outState.putString("active_path", activeFileState.value?.path)
        outState.putString("position_text", positionTextState.value)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { lspController.dispose() }
        runCatching { GradleService.stopBuild(this) }
    }
}
