package com.neonide.studio.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.neonide.studio.app.lsp.LspStatus

/**
 * ViewModel for managing the state and lifecycle of editor components.
 */
class EditorViewModel : ViewModel() {

    var positionText by mutableStateOf("")

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
}
