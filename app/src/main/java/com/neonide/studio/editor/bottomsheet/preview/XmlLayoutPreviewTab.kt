package com.neonide.studio.editor.bottomsheet.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.neonide.studio.editor.bottomsheet.preview.core.LayoutPreviewEngine
import com.neonide.studio.editor.bottomsheet.preview.core.PreviewError
import com.neonide.studio.editor.bottomsheet.preview.core.PreviewState
import com.neonide.studio.ui.layout.AppBox

@Composable
fun XmlLayoutPreviewTab(engine: LayoutPreviewEngine) {
    val state by engine.viewModel.previewState.collectAsState()
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val colorBackground = if (darkTheme) Color.Black else Color.White

    AppBox(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        when (val s = state) {
            is PreviewState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is PreviewState.Rendered -> {
                val bitmapWidthDp = with(density) { s.bitmap.width.toDp() }
                val bitmapHeightDp = with(density) { s.bitmap.height.toDp() }
                AppBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .wrapContentSize(Alignment.Center)
                ) {
                    Image(
                        bitmap = s.bitmap,
                        contentDescription = "XML Preview",
                        modifier = Modifier
                            .requiredWidth(bitmapWidthDp)
                            .requiredHeight(bitmapHeightDp),
                        contentScale = ContentScale.None
                    )
                }
            }
            is PreviewState.Error -> PreviewErrorDisplay(s.error)
        }
    }
}

@Composable
fun PreviewErrorDisplay(error: PreviewError) {
    val message = when (error) {
        is PreviewError.InflateFailed -> "Inflation failed:\n${error.detail}"
        is PreviewError.RenderFailed -> "Rendering failed:\n${error.detail}"
        is PreviewError.UnsupportedXml -> "Preview unavailable:\n${error.detail}"
        is PreviewError.ParseError -> "Could not parse XML layout."
    }

    SelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
