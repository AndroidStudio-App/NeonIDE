package com.neonide.studio.editor

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.neonide.studio.utils.HexColorScanner
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier

class NeonCodeEditor(context: Context) : CodeEditor(context) {
    override fun setHighlightTexts(highlights: HighlightTextContainer?) {
        val container = highlights ?: HighlightTextContainer()
        HexColorScanner.appendHighlights(text, container)
        super.setHighlightTexts(container)
    }
}

@Composable
fun SoraEditor(
    settings: EditorSettingsState,
    modifier: Modifier = Modifier,
    onEditorCreated: (CodeEditor) -> Unit
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            NeonCodeEditor(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                typefaceText = Typeface.MONOSPACE
                props.cancelCompletionNs = 150 * 1_000_000L // 150ms
                isCursorAnimationEnabled = true
                nonPrintablePaintingFlags =
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                    CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                    CodeEditor.FLAG_DRAW_SOFT_WRAP

                isWordwrap = settings.isWordwrap
                isLineNumberEnabled = settings.isLineNumberVisible
                setPinLineNumber(settings.isLineNumberPinned)
                isSoftKeyboardEnabled = settings.softKbdEnabled
                isDisableSoftKbdIfHardKbdAvailable = settings.hardKbdDisabled

                props.showMinimap = settings.isMinimapEnabled
                props.useICULibToSelectWords = settings.useIcu
                props.autoIndent = settings.isAutoIndentEnabled
                props.highlightMatchingDelimiters = settings.isBracketHighlightEnabled
                props.boldMatchingDelimiters = settings.isBoldMatchingBrackets
                props.symbolPairAutoCompletion = settings.isSymbolPairCompletionEnabled
                props.formatPastedText = settings.isFormatPastedTextEnabled
                props.stickyScroll = settings.isStickyScrollEnabled
                props.enhancedHomeAndEnd = settings.isEnhancedHomeEndEnabled
                props.scrollFling = settings.isScrollFlingEnabled
                props.overScrollEnabled = settings.isOverScrollEnabled
                props.deleteEmptyLineFast = settings.isDeleteEmptyLineFast
                setBlockLineEnabled(settings.isSideBlockLineEnabled)
                props.drawSideBlockLine = settings.isSideBlockLineEnabled
                props.enableRoundTextBackground = settings.isRoundTextBackgroundEnabled

                getComponent(Magnifier::class.java).isEnabled = settings.isMagnifierEnabled
                getComponent(EditorAutoCompletion::class.java)
                    .setEnabledAnimation(settings.completionAnim)

                onEditorCreated(this)
            }
        },
        onRelease = { it.release() }
    )
}
