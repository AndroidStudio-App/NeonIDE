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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neonide.studio.ui.components.ToggleMenuItem
import com.neonide.studio.utils.divider.horizontalDivider
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
                ToggleMenuItem(
                    text = "Search Panel",
                    checked = searchPanelVisible,
                    onToggle = {
                        onSearchPanelToggle()
                        expanded = false
                    }
                )

                horizontalDivider(color = Color.Gray)

                MenuCategoryTitle("Feature Switches")
                ToggleMenuItem(
                    text = "Symbol Bar",
                    checked = settings.isSymbolBarVisible,
                    onToggle = { settings.isSymbolBarVisible = !settings.isSymbolBarVisible }
                )
                ToggleMenuItem(
                    text = "Wordwrap",
                    checked = settings.isWordwrap,
                    onToggle = {
                        settings.isWordwrap = !settings.isWordwrap
                        editor?.isWordwrap = settings.isWordwrap
                    }
                )
                ToggleMenuItem(
                    text = "Line Number",
                    checked = settings.isLineNumberVisible,
                    onToggle = {
                        settings.isLineNumberVisible = !settings.isLineNumberVisible
                        editor?.isLineNumberEnabled = settings.isLineNumberVisible
                    }
                )
                ToggleMenuItem(
                    text = "Pin Line Number",
                    checked = settings.isLineNumberPinned,
                    onToggle = {
                        settings.isLineNumberPinned = !settings.isLineNumberPinned
                        editor?.setPinLineNumber(settings.isLineNumberPinned)
                    }
                )
                ToggleMenuItem(
                    text = "Magnifier",
                    checked = settings.isMagnifierEnabled,
                    onToggle = {
                        settings.isMagnifierEnabled = !settings.isMagnifierEnabled
                        editor?.getComponent(Magnifier::class.java)?.isEnabled =
                            settings.isMagnifierEnabled
                    }
                )
                ToggleMenuItem(
                    text = "Use ICU",
                    checked = settings.useIcu,
                    onToggle = {
                        settings.useIcu = !settings.useIcu
                        editor?.props?.useICULibToSelectWords = settings.useIcu
                    }
                )
                ToggleMenuItem(
                    text = "Completion Animation",
                    checked = settings.completionAnim,
                    onToggle = {
                        settings.completionAnim = !settings.completionAnim
                        editor?.getComponent(
                            EditorAutoCompletion::class.java
                        )?.setEnabledAnimation(settings.completionAnim)
                    }
                )
                ToggleMenuItem(
                    text = "Soft Keyboard",
                    checked = settings.softKbdEnabled,
                    onToggle = {
                        settings.softKbdEnabled = !settings.softKbdEnabled
                        editor?.isSoftKeyboardEnabled = settings.softKbdEnabled
                    }
                )
                ToggleMenuItem(
                    text = "Disable Soft Kbd on Hard Kbd",
                    checked = settings.hardKbdDisabled,
                    onToggle = {
                        settings.hardKbdDisabled = !settings.hardKbdDisabled
                        editor?.isDisableSoftKbdIfHardKbdAvailable = settings.hardKbdDisabled
                    }
                )

                horizontalDivider(color = Color.Gray)

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
