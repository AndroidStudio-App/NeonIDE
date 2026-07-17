package com.neonide.studio.editor.bottomsheet.preview.core

sealed class PreviewError {
    data class InflateFailed(val detail: String) : PreviewError()
    data class RenderFailed(val detail: String) : PreviewError()
    data class UnsupportedXml(val detail: String) : PreviewError()
    data object ParseError : PreviewError()
}
