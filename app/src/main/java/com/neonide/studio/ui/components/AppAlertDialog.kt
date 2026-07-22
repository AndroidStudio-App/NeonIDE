package com.neonide.studio.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        title = title,
        text = text,
        confirmButton = if (confirmText != null) {
            {
                TextButton(onClick = onConfirm ?: onDismissRequest) {
                    Text(confirmText)
                }
            }
        } else {
            {}
        },
        dismissButton = if (dismissText != null) {
            {
                TextButton(onClick = onDismiss ?: onDismissRequest) {
                    Text(dismissText)
                }
            }
        } else {
            {}
        }
    )
}

@Composable
fun AppAlertDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        shape = shape,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text = text, style = MaterialTheme.typography.bodyMedium) },
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        onDismiss = onDismiss
    )
}
