package com.neonide.studio.utils

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

class PersistedString(prefs: SharedPreferences, private val key: String, default: String) :
    MutableState<String> {

    private val state = mutableStateOf(prefs.getString(key, default) ?: default)
    private val editor = prefs.edit()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == key) {
            state.value = prefs.getString(key, default) ?: default
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override var value: String
        get() = state.value
        set(value) {
            state.value = value
            editor.putString(key, value).apply()
        }

    override fun component1(): String = value
    override fun component2(): (String) -> Unit = { value = it }
}
