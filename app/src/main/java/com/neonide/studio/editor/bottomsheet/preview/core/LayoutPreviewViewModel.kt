package com.neonide.studio.editor.bottomsheet.preview.core

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PreviewState {
    data object Loading : PreviewState()
    data class Rendered(val bitmap: ImageBitmap) : PreviewState()
    data class Error(val error: PreviewError) : PreviewState()
}

class LayoutPreviewViewModel {
    private val _previewState = MutableStateFlow<PreviewState>(
        PreviewState.Error(PreviewError.ParseError)
    )
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    fun setState(state: PreviewState) {
        _previewState.value = state
    }
}
