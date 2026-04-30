package com.neonide.studio.app.buildoutput

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Buffered build output broadcaster using StateFlow.
 *
 * Why:
 * - Build output can be extremely chatty.
 * - Updating UI (TextView/Editor) per-line causes lag.
 * - StateFlow provides a modern, reactive way to observe changes.
 * - Still utilizes batching to prevent UI thread saturation.
 */
object BuildOutputBuffer {

    private const val FLUSH_DELAY_MS = 150L
    private const val MAX_CHARS = 700_000        // ~0.7MB text cap
    private const val TRIM_TO_CHARS = 550_000

    private val handler = Handler(Looper.getMainLooper())
    private val pending = StringBuilder(8_192)
    private val flushScheduled = AtomicBoolean(false)

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    fun getSnapshot(): String = _output.value

    fun clear() {
        synchronized(pending) {
            pending.clear()
        }
        _output.value = ""
    }

    fun appendLine(line: String) {
        val msg = if (line.endsWith("\n")) line else "$line\n"
        synchronized(pending) {
            pending.append(msg)
        }
        scheduleFlush()
    }

    fun appendRaw(text: String) {
        synchronized(pending) {
            pending.append(text)
        }
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

        var newCache = _output.value + chunk
        if (newCache.length > MAX_CHARS) {
            val trimmedCount = newCache.length - TRIM_TO_CHARS
            newCache = "... [trimmed $trimmedCount chars for performance] ...\n\n" + newCache.takeLast(TRIM_TO_CHARS)
        }
        
        _output.value = newCache
    }
}
