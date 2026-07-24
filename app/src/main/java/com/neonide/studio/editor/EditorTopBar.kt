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
import com.neonide.studio.app.lsp.EditorLspController
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.ToggleMenuItem
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.utils.Divider.horizontalDivider
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier

@Composable
fun EditorTopBar(
    settings: EditorSettingsState,
    editor: CodeEditor?,
    lspController: EditorLspController? = null,
    searchPanelVisible: Boolean,
    onSearchPanelToggle: () -> Unit,
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
    var propertiesDialogEnabled by remember { mutableStateOf(false) }

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
                onVariantBuildMenuExpandedChange = { variantBuildMenuExpanded = it },
                propertiesDialogEnabled = propertiesDialogEnabled,
                onPropertiesDialogChange = { propertiesDialogEnabled = it }
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

                AppBox {
                    IconButton(onClick = { menuExpanded = true }) {
                        AppIcon(painterResource(R.drawable.ic_menu_kebab))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        MenuCategoryTitle(stringResource(R.string.search))
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
                            onToggle = { settings.isSymbolBarVisible = it }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.wordwrap),
                            checked = settings.isWordwrap,
                            onToggle = {
                                settings.isWordwrap = it
                                editor?.isWordwrap = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.line_number),
                            checked = settings.isLineNumberVisible,
                            onToggle = {
                                settings.isLineNumberVisible = it
                                editor?.isLineNumberEnabled = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.pin_line_number),
                            checked = settings.isLineNumberPinned,
                            onToggle = {
                                settings.isLineNumberPinned = it
                                editor?.setPinLineNumber(it)
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.minimap),
                            checked = settings.isMinimapEnabled,
                            onToggle = {
                                settings.isMinimapEnabled = it
                                editor?.props?.showMinimap = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.magnifier),
                            checked = settings.isMagnifierEnabled,
                            onToggle = {
                                settings.isMagnifierEnabled = it
                                editor?.getComponent(Magnifier::class.java)?.isEnabled = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.use_icu),
                            checked = settings.useIcu,
                            onToggle = {
                                settings.useIcu = it
                                editor?.props?.useICULibToSelectWords = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.completion_animation),
                            checked = settings.completionAnim,
                            onToggle = {
                                settings.completionAnim = it
                                editor?.getComponent(
                                    EditorAutoCompletion::class.java
                                )?.setEnabledAnimation(it)
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.soft_keyboard),
                            checked = settings.softKbdEnabled,
                            onToggle = {
                                settings.softKbdEnabled = it
                                editor?.isSoftKeyboardEnabled = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.disable_soft_kbd_hard_kbd),
                            checked = settings.hardKbdDisabled,
                            onToggle = {
                                settings.hardKbdDisabled = it
                                editor?.isDisableSoftKbdIfHardKbdAvailable = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.auto_indent),
                            checked = settings.isAutoIndentEnabled,
                            onToggle = {
                                settings.isAutoIndentEnabled = it
                                editor?.props?.autoIndent = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.bracket_highlight),
                            checked = settings.isBracketHighlightEnabled,
                            onToggle = {
                                settings.isBracketHighlightEnabled = it
                                editor?.props?.highlightMatchingDelimiters = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.bold_matching_brackets),
                            checked = settings.isBoldMatchingBrackets,
                            onToggle = {
                                settings.isBoldMatchingBrackets = it
                                editor?.props?.boldMatchingDelimiters = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.symbol_pair_completion),
                            checked = settings.isSymbolPairCompletionEnabled,
                            onToggle = {
                                settings.isSymbolPairCompletionEnabled = it
                                editor?.props?.symbolPairAutoCompletion = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.format_pasted_text),
                            checked = settings.isFormatPastedTextEnabled,
                            onToggle = {
                                settings.isFormatPastedTextEnabled = it
                                editor?.props?.formatPastedText = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.sticky_scroll),
                            checked = settings.isStickyScrollEnabled,
                            onToggle = {
                                settings.isStickyScrollEnabled = it
                                editor?.props?.stickyScroll = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.enhanced_home_end),
                            checked = settings.isEnhancedHomeEndEnabled,
                            onToggle = {
                                settings.isEnhancedHomeEndEnabled = it
                                editor?.props?.enhancedHomeAndEnd = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.scroll_fling),
                            checked = settings.isScrollFlingEnabled,
                            onToggle = {
                                settings.isScrollFlingEnabled = it
                                editor?.props?.scrollFling = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.overscroll),
                            checked = settings.isOverScrollEnabled,
                            onToggle = {
                                settings.isOverScrollEnabled = it
                                editor?.props?.overScrollEnabled = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.delete_empty_line_fast),
                            checked = settings.isDeleteEmptyLineFast,
                            onToggle = {
                                settings.isDeleteEmptyLineFast = it
                                editor?.props?.deleteEmptyLineFast = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.side_block_line),
                            checked = settings.isSideBlockLineEnabled,
                            onToggle = {
                                settings.isSideBlockLineEnabled = it
                                editor?.let { editor ->
                                    editor.setBlockLineEnabled(it)
                                    editor.props.drawSideBlockLine = it
                                    editor.invalidate()
                                }
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.round_text_bg),
                            checked = settings.isRoundTextBackgroundEnabled,
                            onToggle = {
                                settings.isRoundTextBackgroundEnabled = it
                                editor?.props?.enableRoundTextBackground = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.inlay_hints),
                            checked = settings.isInlayHintsEnabled,
                            onToggle = {
                                settings.isInlayHintsEnabled = it
                                lspController?.currentEditor()?.isEnableInlayHint = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.signature_help),
                            checked = settings.isSignatureHelpEnabled,
                            onToggle = {
                                settings.isSignatureHelpEnabled = it
                                lspController?.currentEditor()?.isEnableSignatureHelp = it
                            }
                        )
                        ToggleMenuItem(
                            text = stringResource(R.string.hover_info),
                            checked = settings.isHoverInfoEnabled,
                            onToggle = {
                                settings.isHoverInfoEnabled = it
                                lspController?.currentEditor()?.isEnableHover = it
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
