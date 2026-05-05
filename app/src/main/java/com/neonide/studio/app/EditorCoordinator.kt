package com.neonide.studio.app

import android.widget.Toast
import com.neonide.studio.R
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

class EditorCoordinator(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val fileManager: EditorFileManager,
    private val languageProvider: SoraLanguageProvider,
    private val lspManager: EditorLspManager,
    private val viewHelper: EditorViewHelper,
    private val uiManager: EditorUiManager,
    private val uiScope: CoroutineScope
) {

    fun openFileInEditor(file: File, title: String, projectRoot: File?) {
        activity.currentFile = file
        editor.setText(fileManager.readFileText(file.absolutePath))
        activity.supportActionBar?.title = title
        val languageForEditor = languageProvider.getLanguage(file)
        editor.setEditorLanguage(languageForEditor)
        val ext = file.extension.lowercase()
        
        if (ext in listOf("java", "kt", "kts", "xml")) {
            runCatching {
                lspManager.controller.attach(editor, file, languageForEditor, projectRoot)
            }
        } else {
            runCatching { lspManager.controller.detach() }
        }
        
        if (ext == "xml") {
            uiScope.launch(Dispatchers.IO) {
                if (AndroidFrameworkAttrIndex.ensureLoaded(activity)) {
                    val snapshot = AndroidFrameworkAttrIndex.allAttrs().toList()
                    AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider { snapshot }
                }
            }
        } else {
            AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider(null)
        }
        
        viewHelper.updatePositionText(activity.findViewById(R.id.position_display))
        uiManager.updateBtnState(activity.undoItem, activity.redoItem)
    }

    fun openAssetsFile(path: String) {
        activity.currentFile = null
        runCatching { lspManager.controller.detach() }
        editor.setEditorLanguage(languageProvider.getLanguage(File(path)))
        uiScope.launch(Dispatchers.Main) {
            val text = withContext(Dispatchers.IO) { 
                ContentIO.createFrom(activity.assets.open(path)) 
            }
            editor.setText(text, null)
            viewHelper.updatePositionText(activity.findViewById(R.id.position_display))
            uiManager.updateBtnState(activity.undoItem, activity.redoItem)
        }
    }

    fun navigateTo(uri: String, line: Int, column: Int, projectRoot: File?) {
        val file = if (uri.startsWith("file://")) File(URI.create(uri)) else File(uri)
        if (!file.exists()) {
            Toast.makeText(activity, "File does not exist: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            return
        }
        openFileInEditor(file, file.name, projectRoot)
        editor.post { 
            editor.setSelection(line, column)
            editor.ensurePositionVisible(line, column) 
        }
    }

    fun saveCurrentFile() {
        val f = activity.currentFile ?: return
        if (fileManager.saveFile(f, editor.text.toString())) {
            Toast.makeText(activity, activity.getString(R.string.acs_saved), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, activity.getString(R.string.acs_save_failed), Toast.LENGTH_LONG).show()
        }
    }
}
