package com.neonide.studio.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultIconSize = 24.dp

@Composable
fun AppIcon(
    painter: Painter,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = DefaultIconSize,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
