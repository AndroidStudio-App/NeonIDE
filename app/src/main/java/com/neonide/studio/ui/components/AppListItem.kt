package com.neonide.studio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppListItem(
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit = {},
    headlineText: String? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    leadingText: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val resolvedHeadline = if (headlineText != null) {
        { Text(text = headlineText, style = MaterialTheme.typography.bodyLarge) }
    } else {
        headlineContent
    }
    val resolvedLeading = leadingContent ?: if (leadingText != null) {
        { Text(text = leadingText, style = MaterialTheme.typography.bodyMedium) }
    } else {
        null
    }
    val resolvedTrailing = trailingContent ?: if (trailingText != null) {
        { Text(text = trailingText, style = MaterialTheme.typography.bodyMedium) }
    } else {
        null
    }
    val clickModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ListItem(
        headlineContent = resolvedHeadline,
        modifier = clickModifier,
        supportingContent = supportingContent,
        leadingContent = resolvedLeading,
        trailingContent = resolvedTrailing
    )
}
