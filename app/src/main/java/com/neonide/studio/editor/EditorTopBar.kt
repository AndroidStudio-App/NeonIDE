package com.neonide.studio.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppIcon
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
    isGradleRunning: Boolean = false,
    buildVariant: String = "debug",
    onBuildVariantChange: (String) -> Unit = {},
    onBuildClick: () -> Unit,
    onSyncClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onSwitchColors: () -> Unit,
    onSwitchTypeface: () -> Unit
) {
    var panelExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var buildMenuExpanded by remember { mutableStateOf(false) }
    var variantBuildMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 20) {
                        panelExpanded = true
                    } else if (dragAmount < -20) {
                        panelExpanded = false
                        buildMenuExpanded = false
                        variantBuildMenuExpanded = false
                    }
                }
            }
    ) {
        // Build panel ABOVE toolbar
        AnimatedVisibility(
            visible = panelExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            BuildVariantPanel(
                buildVariant = buildVariant,
                onBuildVariantChange = onBuildVariantChange,
                buildMenuExpanded = buildMenuExpanded,
                onBuildMenuExpandedChange = { buildMenuExpanded = it },
                variantBuildMenuExpanded = variantBuildMenuExpanded,
                onVariantBuildMenuExpandedChange = { variantBuildMenuExpanded = it }
            )
        }

        horizontalDivider()

        // Toolbar
        TopAppBar(
            modifier = Modifier.height(40.dp),
            title = {},
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            navigationIcon = {
                IconButton(onClick = onNavigationClick) {
                    AppIcon(painterResource(R.drawable.ic_menu))
                }
            },
            actions = {
                IconButton(onClick = onUndoClick) { AppIcon(painterResource(R.drawable.ic_undo)) }
                IconButton(onClick = onRedoClick) { AppIcon(painterResource(R.drawable.ic_redo)) }
                IconButton(onClick = onSaveClick) { AppIcon(painterResource(R.drawable.ic_save)) }
                IconButton(onClick = onBuildClick) {
                    if (isGradleRunning) {
                        AppIcon(painterResource(R.drawable.ic_stop))
                    } else {
                        AppIcon(painterResource(R.drawable.ic_play))
                    }
                }
                IconButton(onClick = onSyncClick) {
                    AppIcon(painterResource(R.drawable.ic_refresh))
                }
                IconButton(onClick = onTerminalClick) {
                    AppIcon(painterResource(R.drawable.ic_terminal))
                }
                IconButton(onClick = { menuExpanded = true }) {
                    AppIcon(painterResource(R.drawable.ic_menu))
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    MenuCategoryTitle(stringResource(R.string.search))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_mode)) },
                        onClick = {
                            onSearchActionMode()
                            menuExpanded = false
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.search_panel),
                        checked = searchPanelVisible,
                        onToggle = {
                            onSearchPanelToggle()
                            menuExpanded = false
                        }
                    )

                    horizontalDivider(color = Color.Gray)

                    MenuCategoryTitle(stringResource(R.string.feature_switches))
                    ToggleMenuItem(
                        text = stringResource(R.string.symbol_bar),
                        checked = settings.isSymbolBarVisible,
                        onToggle = { settings.isSymbolBarVisible = !settings.isSymbolBarVisible }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.wordwrap),
                        checked = settings.isWordwrap,
                        onToggle = {
                            settings.isWordwrap = !settings.isWordwrap
                            editor?.isWordwrap = settings.isWordwrap
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.line_number),
                        checked = settings.isLineNumberVisible,
                        onToggle = {
                            settings.isLineNumberVisible = !settings.isLineNumberVisible
                            editor?.isLineNumberEnabled = settings.isLineNumberVisible
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.pin_line_number),
                        checked = settings.isLineNumberPinned,
                        onToggle = {
                            settings.isLineNumberPinned = !settings.isLineNumberPinned
                            editor?.setPinLineNumber(settings.isLineNumberPinned)
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.magnifier),
                        checked = settings.isMagnifierEnabled,
                        onToggle = {
                            settings.isMagnifierEnabled = !settings.isMagnifierEnabled
                            editor?.getComponent(Magnifier::class.java)?.isEnabled =
                                settings.isMagnifierEnabled
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.use_icu),
                        checked = settings.useIcu,
                        onToggle = {
                            settings.useIcu = !settings.useIcu
                            editor?.props?.useICULibToSelectWords = settings.useIcu
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.completion_animation),
                        checked = settings.completionAnim,
                        onToggle = {
                            settings.completionAnim = !settings.completionAnim
                            editor?.getComponent(
                                EditorAutoCompletion::class.java
                            )?.setEnabledAnimation(settings.completionAnim)
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.soft_keyboard),
                        checked = settings.softKbdEnabled,
                        onToggle = {
                            settings.softKbdEnabled = !settings.softKbdEnabled
                            editor?.isSoftKeyboardEnabled = settings.softKbdEnabled
                        }
                    )
                    ToggleMenuItem(
                        text = stringResource(R.string.disable_soft_kbd_hard_kbd),
                        checked = settings.hardKbdDisabled,
                        onToggle = {
                            settings.hardKbdDisabled = !settings.hardKbdDisabled
                            editor?.isDisableSoftKbdIfHardKbdAvailable = settings.hardKbdDisabled
                        }
                    )

                    horizontalDivider(color = Color.Gray)

                    MenuCategoryTitle(stringResource(R.string.configuration))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.switch_color_scheme)) },
                        onClick = {
                            onSwitchColors()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.switch_typeface)) },
                        onClick = {
                            onSwitchTypeface()
                            menuExpanded = false
                        }
                    )
                }
            }
        )

        horizontalDivider()
    }
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
