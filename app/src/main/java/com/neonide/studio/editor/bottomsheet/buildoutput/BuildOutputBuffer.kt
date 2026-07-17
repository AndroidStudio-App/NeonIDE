package com.neonide.studio.editor.bottomsheet.buildoutput

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.neonide.studio.editor.bottomsheet.buildoutput.BuildTab
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BuildOutputBuffer {

    private const val FLUSH_DELAY_MS = 100L
    private const val MAX_CHARS = 700_000
    private const val TRIM_TO_CHARS = 550_000

    private val handler = Handler(Looper.getMainLooper())
    private val pending = StringBuilder(8_192)
    private val flushScheduled = AtomicBoolean(false)

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    fun getSnapshot(): String = _output.value

    fun clear() {
        synchronized(pending) { pending.clear() }
        _output.value = ""
    }

    fun appendLine(line: String) {
        val msg = if (line.endsWith("\n")) line else "$line\n"
        synchronized(pending) { pending.append(msg) }
        scheduleFlush()
    }

    fun appendRaw(text: String) {
        synchronized(pending) { pending.append(text) }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        handler.postDelayed({
            flushScheduled.set(false)
            flush()
        }, FLUSH_DELAY_MS)
    }

    private fun flush() {
        val chunk: String = synchronized(pending) {
            if (pending.isEmpty()) return
            val out = pending.toString()
            pending.clear()
            out
        }

        var newValue = _output.value + chunk
        if (newValue.length > MAX_CHARS) {
            val trimmed = newValue.length - TRIM_TO_CHARS
            newValue = "... [trimmed $trimmed chars] ...\n\n" +
                newValue.takeLast(TRIM_TO_CHARS)
        }
        _output.value = newValue
    }
}
