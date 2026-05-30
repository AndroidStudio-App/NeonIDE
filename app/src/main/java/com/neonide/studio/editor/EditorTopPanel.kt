package com.neonide.studio.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonide.studio.R

@Composable
fun BuildVariantPanel(
    buildVariant: String,
    onBuildVariantChange: (String) -> Unit,
    buildMenuExpanded: Boolean,
    onBuildMenuExpandedChange: (Boolean) -> Unit,
    variantBuildMenuExpanded: Boolean,
    onVariantBuildMenuExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.build),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable { onBuildMenuExpandedChange(!buildMenuExpanded) }
                .padding(end = 4.dp)
        )

        DropdownMenu(
            expanded = buildMenuExpanded,
            onDismissRequest = { onBuildMenuExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.variant_build)) },
                onClick = {
                    onVariantBuildMenuExpandedChange(true)
                }
            )

            DropdownMenu(
                expanded = variantBuildMenuExpanded,
                onDismissRequest = { onVariantBuildMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.debug),
                            color = if (buildVariant == "debug") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onBuildVariantChange("debug")
                        onVariantBuildMenuExpandedChange(false)
                        onBuildMenuExpandedChange(false)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.release),
                            color = if (buildVariant == "release") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onBuildVariantChange("release")
                        onVariantBuildMenuExpandedChange(false)
                        onBuildMenuExpandedChange(false)
                    }
                )
            }
        }
    }
}
