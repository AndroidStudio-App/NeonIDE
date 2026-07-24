package com.neonide.studio.ui.components

import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Dropdown menu item with a trailing checkbox for toggle/boolean settings.
 */
@Composable
fun ToggleMenuItem(text: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = { onToggle(!checked) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = null) }
    )
}
