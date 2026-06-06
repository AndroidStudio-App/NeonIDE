package com.neonide.studio.utils

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

class PersistedBoolean(prefs: SharedPreferences, private val key: String, default: Boolean) :
    MutableState<Boolean> {

    private val state = mutableStateOf(prefs.getBoolean(key, default))
    private val editor = prefs.edit()

    override var value: Boolean
        get() = state.value
        set(value) {
            state.value = value
            editor.putBoolean(key, value).apply()
        }

    override fun component1(): Boolean = value
    override fun component2(): (Boolean) -> Unit = { value = it }
}
