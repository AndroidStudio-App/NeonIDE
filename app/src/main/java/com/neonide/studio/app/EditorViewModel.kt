package com.neonide.studio.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.neonide.studio.app.lsp.EditorLspController
import com.neonide.studio.app.lsp.EditorLspControllerFactory
import com.neonide.studio.app.lsp.LspManager
import com.neonide.studio.app.lsp.LspStatus
import java.io.File

/**
 * ViewModel for managing the state and lifecycle of components within SoraEditorActivityK.
 */
class EditorViewModel : ViewModel() {

    var positionText by mutableStateOf("")
    var searchQuery by mutableStateOf("")
    var replacementText by mutableStateOf("")
    var searchPanelVisible by mutableStateOf(false)
    var projectPath by mutableStateOf<File?>(null)

    /**
     * Manager for Language Server Protocol integration.
     */
    val lspManager = LspManager().apply {
        setStatusListener { status ->
            setLspStatus(status)
        }
    }

    /**
     * The actual controller that manages LSP attachment to the editor.
     */
    lateinit var lspController: EditorLspController

    fun initializeLsp(context: android.content.Context) {
        if (!::lspController.isInitialized) {
            lspController = EditorLspControllerFactory.createOrNoop(context)
        }
    }

    private val _connectionStatus = MutableLiveData<LspStatus>(LspStatus.Disconnected)

    /**
     * Observable status of the LSP connection.
     */
    val connectionStatus: LiveData<LspStatus> = _connectionStatus

    /**
     * Update the current LSP connection status.
     */
    fun setLspStatus(status: LspStatus) {
        _connectionStatus.postValue(status)
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure the LSP manager is shut down when the ViewModel is cleared.
        lspManager.shutdown()
    }
}
