package com.neonide.studio.app.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EditorSettingsState {
    var isSymbolBarVisible by mutableStateOf(true)
    var isWordwrap by mutableStateOf(false)
    var isLineNumberVisible by mutableStateOf(true)
    var isLineNumberPinned by mutableStateOf(false)
    var isMagnifierEnabled by mutableStateOf(true)
    var useIcu by mutableStateOf(true)
    var completionAnim by mutableStateOf(true)
    var softKbdEnabled by mutableStateOf(true)
    var hardKbdDisabled by mutableStateOf(true)
}
