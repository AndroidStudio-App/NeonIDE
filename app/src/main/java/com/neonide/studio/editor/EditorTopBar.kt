package com.neonide.studio.editor

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier

@Composable
fun EditorTopBar(
    settings: EditorSettingsState,
    editor: CodeEditor?,
    searchPanelVisible: Boolean,
    onSearchPanelToggle: () -> Unit,
    onSearchActionMode: () -> Unit,
    onNavigationClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBuildClick: () -> Unit,
    onSyncClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onSwitchColors: () -> Unit,
    onSwitchTypeface: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.statusBarsPadding().height(40.dp),
        title = {},
        navigationIcon = {
            IconButton(onClick = onNavigationClick) { Icon(Icons.Default.Menu, "Menu") }
        },
        actions = {
            IconButton(onClick = onUndoClick) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton(onClick = onRedoClick) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            IconButton(onClick = onSaveClick) { Icon(Icons.Filled.Save, "Save") }
            IconButton(onClick = onBuildClick) { Icon(Icons.Filled.PlayArrow, "Build/Run") }
            IconButton(onClick = onSyncClick) { Icon(Icons.Filled.Refresh, "Sync") }
            IconButton(onClick = onTerminalClick) { Icon(Icons.Filled.Terminal, "Terminal") }
            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "More") }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MenuCategoryTitle("Search")
                DropdownMenuItem(
                    text = { Text("Action Mode") },
                    onClick = {
                        onSearchActionMode()
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Search Panel") },
                    onClick = {
                        onSearchPanelToggle()
                        expanded = false
                    },
                    trailingIcon = {
                        Checkbox(checked = searchPanelVisible, onCheckedChange = null)
                    }
                )

                HorizontalDivider()

                MenuCategoryTitle("Feature Switches")
                DropdownMenuItem(
                    text = { Text("Symbol Bar") },
                    onClick = { settings.isSymbolBarVisible = !settings.isSymbolBarVisible },
                    trailingIcon = {
                        Checkbox(checked = settings.isSymbolBarVisible, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Wordwrap") },
                    onClick = {
                        settings.isWordwrap = !settings.isWordwrap
                        editor?.isWordwrap = settings.isWordwrap
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.isWordwrap, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Line Number") },
                    onClick = {
                        settings.isLineNumberVisible = !settings.isLineNumberVisible
                        editor?.isLineNumberEnabled = settings.isLineNumberVisible
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.isLineNumberVisible, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pin Line Number") },
                    onClick = {
                        settings.isLineNumberPinned = !settings.isLineNumberPinned
                        editor?.setPinLineNumber(settings.isLineNumberPinned)
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.isLineNumberPinned, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Magnifier") },
                    onClick = {
                        settings.isMagnifierEnabled = !settings.isMagnifierEnabled
                        editor?.getComponent(Magnifier::class.java)?.isEnabled =
                            settings.isMagnifierEnabled
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.isMagnifierEnabled, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Use ICU") },
                    onClick = {
                        settings.useIcu = !settings.useIcu
                        editor?.props?.useICULibToSelectWords = settings.useIcu
                    },
                    trailingIcon = { Checkbox(checked = settings.useIcu, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Completion Animation") },
                    onClick = {
                        settings.completionAnim = !settings.completionAnim
                        editor?.getComponent(
                            EditorAutoCompletion::class.java
                        )?.setEnabledAnimation(settings.completionAnim)
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.completionAnim, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Soft Keyboard") },
                    onClick = {
                        settings.softKbdEnabled = !settings.softKbdEnabled
                        editor?.isSoftKeyboardEnabled = settings.softKbdEnabled
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.softKbdEnabled, onCheckedChange = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Disable Soft Kbd on Hard Kbd") },
                    onClick = {
                        settings.hardKbdDisabled = !settings.hardKbdDisabled
                        editor?.isDisableSoftKbdIfHardKbdAvailable = settings.hardKbdDisabled
                    },
                    trailingIcon = {
                        Checkbox(checked = settings.hardKbdDisabled, onCheckedChange = null)
                    }
                )

                HorizontalDivider()

                MenuCategoryTitle("Configuration")
                DropdownMenuItem(
                    text = { Text("Switch Color Scheme") },
                    onClick = {
                        onSwitchColors()
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Switch Typeface") },
                    onClick = {
                        onSwitchTypeface()
                        expanded = false
                    }
                )
            }
        }
    )
}

@Composable
private fun MenuCategoryTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
