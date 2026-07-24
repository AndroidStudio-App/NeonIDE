package com.neonide.studio.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.neonide.studio.utils.PersistedBoolean

class EditorSettingsState(context: Context) {

    private val prefs =
        context.getSharedPreferences("editor_settings", Context.MODE_PRIVATE)

    var isSymbolBarVisible by PersistedBoolean(prefs, "symbol_bar", true)
    var isWordwrap by PersistedBoolean(prefs, "wordwrap", false)
    var isLineNumberVisible by PersistedBoolean(prefs, "line_number", true)
    var isLineNumberPinned by PersistedBoolean(prefs, "pin_line_number", false)
    var isMagnifierEnabled by PersistedBoolean(prefs, "magnifier", true)
    var useIcu by PersistedBoolean(prefs, "use_icu", true)
    var completionAnim by PersistedBoolean(prefs, "completion_anim", true)
    var softKbdEnabled by PersistedBoolean(prefs, "soft_kbd", true)
    var hardKbdDisabled by PersistedBoolean(prefs, "hard_kbd_disabled", true)

    var isMinimapEnabled by PersistedBoolean(prefs, "minimap", false)
    var isAutoIndentEnabled by PersistedBoolean(prefs, "auto_indent", true)
    var isBracketHighlightEnabled by PersistedBoolean(prefs, "bracket_highlight", true)
    var isBoldMatchingBrackets by PersistedBoolean(prefs, "bold_bracket", false)
    var isSymbolPairCompletionEnabled by PersistedBoolean(prefs, "symbol_pair", true)
    var isFormatPastedTextEnabled by PersistedBoolean(prefs, "format_paste", false)
    var isStickyScrollEnabled by PersistedBoolean(prefs, "sticky_scroll", true)
    var isEnhancedHomeEndEnabled by PersistedBoolean(prefs, "enhanced_home_end", true)
    var isScrollFlingEnabled by PersistedBoolean(prefs, "scroll_fling", true)
    var isOverScrollEnabled by PersistedBoolean(prefs, "overscroll", true)
    var isDeleteEmptyLineFast by PersistedBoolean(prefs, "delete_empty_fast", true)
    var isSideBlockLineEnabled by PersistedBoolean(prefs, "side_block", true)
    var isRoundTextBackgroundEnabled by PersistedBoolean(prefs, "round_text_bg", true)

    var isInlayHintsEnabled by PersistedBoolean(prefs, "inlay_hints", false)
    var isSignatureHelpEnabled by PersistedBoolean(prefs, "signature_help", false)
    var isHoverInfoEnabled by PersistedBoolean(prefs, "hover_info", false)
}
