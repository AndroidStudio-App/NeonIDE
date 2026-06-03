package com.neonide.studio.app.lsp.server

import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ProcessStreamConnectionProvider(
    private val command: List<String>,
    private val workingDir: File? = null,
    private val env: Map<String, String> = emptyMap()
) : StreamConnectionProvider {

    private var process: Process? = null

    override fun start() {
        val pb = ProcessBuilder(command)
        workingDir?.let { pb.directory(it) }
        pb.environment().putAll(env)
        pb.redirectErrorStream(false)
        process = pb.start()
    }

    override val inputStream: InputStream
        get() = process!!.inputStream

    override val outputStream: OutputStream
        get() = process!!.outputStream

    override val isClosed: Boolean
        get() = process == null

    override fun close() {
        process?.destroyForcibly()
        process = null
    }
}
