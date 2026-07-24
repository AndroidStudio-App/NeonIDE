package com.neonide.studio.preference

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.app.lsp.EditorLspController
import com.neonide.studio.editor.EditorSettingsState
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppListItem
import com.neonide.studio.ui.components.AppSwitch
import com.neonide.studio.ui.components.AppTopBar
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppLazyColumn
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier

@Composable
fun EditorSettingsScreen(
    title: String,
    onBack: () -> Unit,
    settings: EditorSettingsState,
    editor: CodeEditor? = null,
    lspController: EditorLspController? = null
) {
    AppColumn(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = title,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    AppIcon(painterResource(R.drawable.ic_chevron_left))
                }
            }
        )
        AppLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.enhanced_home_end),
                    supportingText = stringResource(R.string.enhanced_home_end_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isEnhancedHomeEndEnabled,
                            onCheckedChange = {
                                settings.isEnhancedHomeEndEnabled = it
                                editor?.props?.enhancedHomeAndEnd = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.disable_soft_kbd_hard_kbd),
                    supportingText = stringResource(R.string.disable_soft_kbd_hard_kbd_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.hardKbdDisabled,
                            onCheckedChange = {
                                settings.hardKbdDisabled = it
                                editor?.isDisableSoftKbdIfHardKbdAvailable = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.hover_info),
                    supportingText = stringResource(R.string.hover_info_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isHoverInfoEnabled,
                            onCheckedChange = {
                                settings.isHoverInfoEnabled = it
                                lspController?.currentEditor()?.isEnableHover = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.inlay_hints),
                    supportingText = stringResource(R.string.inlay_hints_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isInlayHintsEnabled,
                            onCheckedChange = {
                                settings.isInlayHintsEnabled = it
                                lspController?.currentEditor()?.isEnableInlayHint = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.magnifier),
                    supportingText = stringResource(R.string.magnifier_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isMagnifierEnabled,
                            onCheckedChange = {
                                settings.isMagnifierEnabled = it
                                editor?.getComponent(Magnifier::class.java)?.isEnabled = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.soft_keyboard),
                    supportingText = stringResource(R.string.soft_keyboard_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.softKbdEnabled,
                            onCheckedChange = {
                                settings.softKbdEnabled = it
                                editor?.isSoftKeyboardEnabled = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.signature_help),
                    supportingText = stringResource(R.string.signature_help_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isSignatureHelpEnabled,
                            onCheckedChange = {
                                settings.isSignatureHelpEnabled = it
                                lspController?.currentEditor()?.isEnableSignatureHelp = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.symbol_bar),
                    supportingText = stringResource(R.string.symbol_bar_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isSymbolBarVisible,
                            onCheckedChange = { settings.isSymbolBarVisible = it }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.wordwrap),
                    supportingText = stringResource(R.string.wordwrap_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isWordwrap,
                            onCheckedChange = {
                                settings.isWordwrap = it
                                editor?.isWordwrap = it
                            }
                        )
                    }
                )
            }
            item {
                Text(
                    text = "Display",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.completion_animation),
                    supportingText = stringResource(R.string.completion_animation_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.completionAnim,
                            onCheckedChange = {
                                settings.completionAnim = it
                                editor?.getComponent(EditorAutoCompletion::class.java)
                                    ?.setEnabledAnimation(it)
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.line_number),
                    supportingText = stringResource(R.string.line_number_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isLineNumberVisible,
                            onCheckedChange = {
                                settings.isLineNumberVisible = it
                                editor?.isLineNumberEnabled = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.pin_line_number),
                    supportingText = stringResource(R.string.pin_line_number_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isLineNumberPinned,
                            onCheckedChange = {
                                settings.isLineNumberPinned = it
                                editor?.setPinLineNumber(it)
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.minimap),
                    supportingText = stringResource(R.string.minimap_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isMinimapEnabled,
                            onCheckedChange = {
                                settings.isMinimapEnabled = it
                                editor?.props?.showMinimap = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.round_text_bg),
                    supportingText = stringResource(R.string.round_text_bg_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isRoundTextBackgroundEnabled,
                            onCheckedChange = {
                                settings.isRoundTextBackgroundEnabled = it
                                editor?.props?.enableRoundTextBackground = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.side_block_line),
                    supportingText = stringResource(R.string.side_block_line_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isSideBlockLineEnabled,
                            onCheckedChange = {
                                settings.isSideBlockLineEnabled = it
                                editor?.let { editor ->
                                    editor.setBlockLineEnabled(it)
                                    editor.props.drawSideBlockLine = it
                                    editor.invalidate()
                                }
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.sticky_scroll),
                    supportingText = stringResource(R.string.sticky_scroll_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isStickyScrollEnabled,
                            onCheckedChange = {
                                settings.isStickyScrollEnabled = it
                                editor?.props?.stickyScroll = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.scroll_fling),
                    supportingText = stringResource(R.string.scroll_fling_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isScrollFlingEnabled,
                            onCheckedChange = {
                                settings.isScrollFlingEnabled = it
                                editor?.props?.scrollFling = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.overscroll),
                    supportingText = stringResource(R.string.overscroll_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isOverScrollEnabled,
                            onCheckedChange = {
                                settings.isOverScrollEnabled = it
                                editor?.props?.overScrollEnabled = it
                            }
                        )
                    }
                )
            }
            item {
                Text(
                    text = "CodeEditing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.auto_indent),
                    supportingText = stringResource(R.string.auto_indent_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isAutoIndentEnabled,
                            onCheckedChange = {
                                settings.isAutoIndentEnabled = it
                                editor?.props?.autoIndent = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.bracket_highlight),
                    supportingText = stringResource(R.string.bracket_highlight_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isBracketHighlightEnabled,
                            onCheckedChange = {
                                settings.isBracketHighlightEnabled = it
                                editor?.props?.highlightMatchingDelimiters = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.bold_matching_brackets),
                    supportingText = stringResource(R.string.bold_matching_brackets_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isBoldMatchingBrackets,
                            onCheckedChange = {
                                settings.isBoldMatchingBrackets = it
                                editor?.props?.boldMatchingDelimiters = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.delete_empty_line_fast),
                    supportingText = stringResource(R.string.delete_empty_line_fast_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isDeleteEmptyLineFast,
                            onCheckedChange = {
                                settings.isDeleteEmptyLineFast = it
                                editor?.props?.deleteEmptyLineFast = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.format_pasted_text),
                    supportingText = stringResource(R.string.format_pasted_text_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isFormatPastedTextEnabled,
                            onCheckedChange = {
                                settings.isFormatPastedTextEnabled = it
                                editor?.props?.formatPastedText = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.symbol_pair_completion),
                    supportingText = stringResource(R.string.symbol_pair_completion_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.isSymbolPairCompletionEnabled,
                            onCheckedChange = {
                                settings.isSymbolPairCompletionEnabled = it
                                editor?.props?.symbolPairAutoCompletion = it
                            }
                        )
                    }
                )
            }
            item {
                AppListItem(
                    headlineText = stringResource(R.string.use_icu),
                    supportingText = stringResource(R.string.use_icu_desc),
                    trailingContent = {
                        AppSwitch(
                            checked = settings.useIcu,
                            onCheckedChange = {
                                settings.useIcu = it
                                editor?.props?.useICULibToSelectWords = it
                            }
                        )
                    }
                )
            }
        }
    }
}
