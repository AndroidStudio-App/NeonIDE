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
    headlineContent: @Composable (() -> Unit)? = null,
    headlineText: String? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    leadingText: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    onClick: ((String) -> Unit)? = null
) {
    val resolvedHeadline: @Composable () -> Unit = if (headlineText != null) {
        { Text(text = headlineText, style = MaterialTheme.typography.bodyLarge) }
    } else if (headlineContent != null) {
        headlineContent
    } else {
        {}
    }
    val text = headlineText
    val clickModifier = if (onClick != null && text != null) {
        modifier.clickable { onClick(text) }
    } else if (onClick != null && headlineContent != null) {
        modifier.clickable { onClick("") }
    } else {
        modifier
    }
    val resolvedSupporting = supportingContent ?: if (supportingText != null) {
        { Text(text = supportingText, style = MaterialTheme.typography.bodyMedium) }
    } else {
        null
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
    ListItem(
        headlineContent = resolvedHeadline,
        modifier = clickModifier,
        supportingContent = resolvedSupporting,
        leadingContent = resolvedLeading,
        trailingContent = resolvedTrailing
    )
}
