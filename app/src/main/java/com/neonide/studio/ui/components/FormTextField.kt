package com.neonide.studio.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Reusable form field for the common pattern of label + leading icon + error state.
 * Used in GitLayout, CreateProjectBottomSheet, and similar forms.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    leadingIcon: Painter? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { painter -> { AppIcon(painter) } },
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        shape = shape,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}
