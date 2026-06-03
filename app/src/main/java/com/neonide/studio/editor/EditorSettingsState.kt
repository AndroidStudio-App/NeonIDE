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
}
