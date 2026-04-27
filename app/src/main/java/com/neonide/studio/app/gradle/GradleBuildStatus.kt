package com.neonide.studio.app.gradle

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global state for Gradle builds.
 */
object GradleBuildStatus {

    @Volatile
    var isRunning: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                listeners.forEach { it.invoke(value) }
            }
        }

    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addListener(l: (Boolean) -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: (Boolean) -> Unit) {
        listeners.remove(l)
    }
}
