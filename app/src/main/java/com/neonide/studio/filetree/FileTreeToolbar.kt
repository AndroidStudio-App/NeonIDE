package com.neonide.studio.filetree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonide.studio.R

@Composable
internal fun FileTreeToolbar(
    onCollapseAll: () -> Unit,
    onExpandAll: () -> Unit,
    onToggleSearch: () -> Unit,
    isCompactMode: Boolean,
    onToggleCompact: () -> Unit,
    searchRegex: Boolean,
    onToggleRegex: () -> Unit,
    searchCaseSensitive: Boolean,
    onToggleCaseSensitive: () -> Unit,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    searchOpen: Boolean = false,
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapseAll) {
            Icon(
                painter = painterResource(R.drawable.ic_collapse_all),
                contentDescription = "Collapse All"
            )
        }
        IconButton(onClick = onExpandAll) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_all),
                contentDescription = "Expand All"
            )
        }
        IconButton(onClick = onToggleSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = "Search"
            )
        }

        if (searchOpen) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Filter...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(onClick = onToggleSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close search"
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Box {
            IconButton(onClick = onToggleMenu) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "Options"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu
            ) {
                DropdownMenuItem(
                    text = { Text("Compact Tree") },
                    trailingIcon = {
                        Checkbox(checked = isCompactMode, onCheckedChange = null)
                    },
                    onClick = {
                        onToggleCompact()
                        onDismissMenu()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Regex") },
                    trailingIcon = {
                        Checkbox(checked = searchRegex, onCheckedChange = null)
                    },
                    onClick = {
                        onToggleRegex()
                        onDismissMenu()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Case Sensitive") },
                    trailingIcon = {
                        Checkbox(checked = searchCaseSensitive, onCheckedChange = null)
                    },
                    onClick = {
                        onToggleCaseSensitive()
                        onDismissMenu()
                    }
                )
            }
        }
    }
}
