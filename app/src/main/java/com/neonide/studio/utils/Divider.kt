package com.neonide.studio.utils

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Divider {

    @Composable
    fun horizontalDivider(
        modifier: Modifier = Modifier,
        color: Color = Color.Black,
        thickness: Dp = 1.dp
    ) {
        HorizontalDivider(
            modifier = modifier,
            color = color,
            thickness = thickness
        )
    }

    @Composable
    fun verticalDivider(
        modifier: Modifier = Modifier,
        color: Color = Color.Black,
        thickness: Dp = 1.dp
    ) {
        VerticalDivider(
            modifier = modifier,
            color = color,
            thickness = thickness
        )
    }
}
