package com.neonide.studio.app.lsp.impl

import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.neonide.studio.app.lsp.EditorLspController
import com.neonide.studio.app.lsp.LspUtils
import com.neonide.studio.app.lsp.server.JavaLanguageServer
import com.neonide.studio.app.lsp.server.JavaLanguageServerService
import com.neonide.studio.app.lsp.server.KotlinLanguageServer
import com.neonide.studio.app.lsp.server.KotlinLanguageServerService
import com.neonide.studio.app.lsp.server.XMLLanguageServer
import com.neonide.studio.app.lsp.server.XmlLanguageServerService
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lsp.client.connection.CustomConnectProvider
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspLanguage
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeConfigurationParams

/**
 * LSP controller built on top of `io.github.rosemoe:editor-lsp`.
 *
 * NOTE: This class must only be loaded on API 26+.
 */
class SoraEditorLspController(private val context: android.content.Context) : EditorLspController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        private const val TAG = "SoraEditorLsp"

        /**
         * Socket names are shared in the "abstract" namespace.
         *
         * The corresponding language server service/binary must be listening on the same name.
         */
        private const val JAVA_SOCKET = "java-lang-server"
        private const val KOTLIN_SOCKET = "kotlin-lang-server"
        private const val XML_SOCKET = "xml-lang-server"
    }

    private var project: LspProject? = null
    private var current: LspEditor? = null
    private var currentFile: File? = null
    private val connectedEditors = mutableSetOf<String>()

    /**
     * Files where diagnostics are suppressed after initial open.
     * Diagnostics are suppressed until the user types, so stale diagnostics
     * from the initial didOpen (before classPath is configured) don't show.
     */
    private val suppressDiagnosticsFor = mutableSetOf<String>()

    /** Cached classpath JARs from [prefetchClassPath], keyed by project path. */
    @Volatile
    private var cachedClassPath: List<String>? = null
    private var cachedClassPathProject: String? = null

    override fun attach(
        editor: CodeEditor,
        file: File,
        wrapperLanguage: Language,
        projectRoot: File?
    ): Boolean {
        // Debug-friendly: fail fast if server doesn't initialize
        io.github.rosemoe.sora.lsp.requests.Timeout[io.github.rosemoe.sora.lsp.requests.Timeouts.INIT] =
            10000

        val serverId = LspUtils.getServerId(file) ?: run {
            detach()
            return false
        }

        startServer(serverId)

        val desiredRoot = (projectRoot ?: file.parentFile ?: file).let {
            if (it.isFile) {
                it.parentFile
                    ?: it
            } else {
                it
            }
        }

        // If project root changed (user opened a file from a different project), recreate the LSP project
        // so servers get the correct workspace root.
        val p = project?.takeIf { it.projectUri.path == desiredRoot.absolutePath }
            ?: run {
                project?.let { old ->
                    runCatching { old.dispose() }
                }
                createProject(desiredRoot).also { project = it }
            }

        // Ensure server definitions are registered
        ensureServerDefinitions(p)

        // Reuse editor for same file ONLY if it is still valid and connected
        if (currentFile?.absolutePath == file.absolutePath && current != null) {
            Logger.logDebug(TAG, "Re-attaching to existing LSP editor for ${file.name}")
            current!!.editor = editor
            current!!.wrapperLanguage = wrapperLanguage
            return true
        }

        Logger.logDebug(TAG, "Switching LSP editor from ${currentFile?.name} to ${file.name}")

        val lspEditor = try {
            p.getOrCreateEditor(file.absolutePath)
        } catch (t: Throwable) {
            Logger.logStackTraceWithMessage(
                TAG,
                "Failed to create LSP editor for ${file.absolutePath}",
                t
            )
            return false
        }

        lspEditor.isEnableSignatureHelp = true
        lspEditor.editor = editor
        lspEditor.wrapperLanguage = wrapperLanguage

        val lspLang = editor.editorLanguage
        if (lspLang is LspLanguage) {
            editor.setEditorLanguage(
                com.neonide.studio.app.editor.completion.NeonLspLanguageWrapper(
                    lspLang,
                    editor,
                    lspEditor,
                    wrapperLanguage
                )
            )
        }

        val filePath = file.absolutePath
        if (filePath in connectedEditors) {
            Logger.logDebug(TAG, "Reusing existing LSP connection for ${file.name}")
            current = lspEditor
            currentFile = file
            return true
        }

        // Launch connection in background to avoid blocking main thread
        scope.launch {
            try {
                // Async connect
                val ok = withContext(Dispatchers.IO) {
                    Logger.logDebug(TAG, "Connecting LSP for ${file.name}...")
                    lspEditor.connect(false)
                }
                if (ok) {
                    Logger.logInfo(
                        TAG,
                        "LSP connected for ${file.absolutePath} (serverId=$serverId)"
                    )
                    connectedEditors.add(filePath)
                    current = lspEditor
                    currentFile = file

                    // Suppress diagnostics until the user types.
                    // The initial didOpen happens before classPath is configured,
                    // so diagnostics would show false errors.
                    suppressDiagnosticsFor.add(filePath)
                    val codeEditor = editor
                    codeEditor.setDiagnostics(null)

                    // Periodically clear diagnostics while suppressed.
                    // Stops once the user types (removes from suppressDiagnosticsFor).
                    val clearRunnable = object : Runnable {
                        override fun run() {
                            if (filePath in suppressDiagnosticsFor) {
                                codeEditor.setDiagnostics(null)
                                codeEditor.postDelayed(this, 150)
                            }
                        }
                    }
                    codeEditor.postDelayed(clearRunnable, 150)

                    // Re-enable diagnostics on first user edit
                    val listener = object : ContentListener {
                        override fun beforeReplace(
                            content: io.github.rosemoe.sora.text.Content
                        ) {
                        }
                        override fun afterInsert(
                            content: io.github.rosemoe.sora.text.Content,
                            startLine: Int,
                            startColumn: Int,
                            endLine: Int,
                            endColumn: Int,
                            inserted: CharSequence
                        ) {
                            if (suppressDiagnosticsFor.remove(filePath)) {
                                Logger.logDebug(
                                    TAG,
                                    "User typed, re-enabling diagnostics for $filePath"
                                )
                            }
                        }
                        override fun afterDelete(
                            content: io.github.rosemoe.sora.text.Content,
                            startLine: Int,
                            startColumn: Int,
                            endLine: Int,
                            endColumn: Int,
                            deleted: CharSequence
                        ) {
                            if (suppressDiagnosticsFor.remove(filePath)) {
                                Logger.logDebug(
                                    TAG,
                                    "User typed, re-enabling diagnostics for $filePath"
                                )
                            }
                        }
                    }
                    codeEditor.text.addContentListener(listener)

                    // Configure server in background so completions work immediately
                    // without waiting for the expensive Gradle cache scan
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { configureServer(serverId, lspEditor) }
                                .onFailure { t ->
                                    Logger.logStackTraceWithMessage(
                                        TAG,
                                        "Failed to configure server settings",
                                        t
                                    )
                                }
                        }
                    }
                } else {
                    Logger.logWarn(TAG, "LSP connect returned false for ${file.absolutePath}")
                    connectedEditors.remove(filePath)
                    runCatching { lspEditor.dispose() }
                }
            } catch (t: Throwable) {
                Logger.logStackTraceWithMessage(
                    TAG,
                    "LSP connect failed for ${file.absolutePath}",
                    t
                )
                connectedEditors.remove(filePath)
                runCatching { lspEditor.dispose() }
            }
        }

        return true
    }

    override fun detach() {
        val prev = current
        val prevFile = currentFile
        current = null
        currentFile = null
        if (prev != null) {
            connectedEditors.remove(prevFile?.absolutePath)
            Logger.logDebug(TAG, "Disposing previous LSP editor for ${prevFile?.name}")
            scope.launch(Dispatchers.IO) {
                runCatching { prev.dispose() }
            }
        }
    }

    override fun dispose() {
        detach()
        connectedEditors.clear()
        val p = project
        project = null
        scope.launch(Dispatchers.IO) {
            if (p != null) {
                runCatching { p.dispose() }
            }
            stopServers()
            runCatching { scope.coroutineContext.cancelChildren() }
        }
    }

    override fun currentEditor(): LspEditor? = current

    private fun createProject(base: File): LspProject {
        // LspProject expects a filesystem path (it internally converts it to a file:// uri).
        return LspProject(base.absolutePath).also { it.init() }
    }

    /**
     * Pre-scan Gradle cache and build directories so [configureServer] can use the
     * cached classpath instantly when a Java file is opened.
     *
     * Call this when the project is opened, before any Java file is attached.
     */
    override fun prefetchClassPath(projectPath: File) {
        val path = projectPath.absolutePath
        if (cachedClassPath != null && cachedClassPathProject == path) {
            Logger.logDebug(TAG, "ClassPath already cached for $path, skipping prefetch")
            return
        }
        scope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val jars = scanAllClassPathJars(path)
            cachedClassPath = jars
            cachedClassPathProject = path
            val elapsed = System.currentTimeMillis() - start
            Logger.logDebug(
                TAG,
                "Prefetched ${jars.size} classpath entries in ${elapsed}ms"
            )
        }
    }

    /**
     * Scan all classpath sources (android.jar, termux lib, Gradle cache, build dirs)
     * and return the list of paths. This runs on the caller's thread.
     */
    private fun scanAllClassPathJars(projectPath: String): List<String> {
        val entries = mutableListOf<String>()

        // android.jar
        resolveAndroidJar()?.let {
            entries.add(it.absolutePath)
        }

        // Termux runtime libs
        val termuxLib = File(context.filesDir, "usr/lib")
        if (termuxLib.isDirectory) {
            entries.add(termuxLib.absolutePath)
        }

        // Gradle cache dependency JARs
        val gradleCacheDir = File(
            TermuxConstants.TERMUX_HOME_DIR_PATH,
            ".gradle/caches"
        )
        addGradleCacheJars(gradleCacheDir, projectPath, entries)

        // Project build dirs
        val projectDir = File(projectPath)
        for (variant in listOf("debug", "release")) {
            val classes = File(projectDir, "build/intermediates/javac/$variant/classes")
            if (classes.isDirectory) entries.add(classes.absolutePath)
            val appClasses = File(projectDir, "app/build/intermediates/javac/$variant/classes")
            if (appClasses.isDirectory) entries.add(appClasses.absolutePath)
        }

        return entries
    }

    /**
     * Send best-effort settings to language server.
     *
     * For Java (org.javacs), this enables richer hover/Javadoc if JDK sources are present.
     * Uses [cachedClassPath] if available (from [prefetchClassPath]), otherwise scans on the spot.
     */
    private fun configureServer(serverId: String, lspEditor: LspEditor) {
        val rm = lspEditor.requestManager

        when (serverId) {
            JavaLanguageServer.SERVER_ID -> {
                val root = JsonObject()
                val java = JsonObject()

                val docPaths = detectJdkSourceZips()
                Logger.logDebug(TAG, "Detected JDK src.zip candidates: ${docPaths.joinToString()}")

                val projectPath = lspEditor.project.projectUri.path
                val classPathArr = JsonArray()

                // Use cached classpath if available for this project
                val cached = cachedClassPath
                if (cached != null && cachedClassPathProject == projectPath) {
                    Logger.logDebug(TAG, "Using cached classPath with ${cached.size} entries")
                    cached.forEach { classPathArr.add(it) }
                } else {
                    Logger.logDebug(TAG, "No classPath cache, scanning on the spot")
                    val scanStart = System.currentTimeMillis()
                    val jars = scanAllClassPathJars(projectPath)
                    jars.forEach { classPathArr.add(it) }
                    val elapsed = System.currentTimeMillis() - scanStart
                    Logger.logDebug(
                        TAG,
                        "Scanned ${jars.size} classPath entries in ${elapsed}ms"
                    )
                }

                java.add("classPath", classPathArr)
                Logger.logDebug(TAG, "Sending java.classPath with ${classPathArr.size()} entries")
                if (docPaths.isNotEmpty()) {
                    val arr = JsonArray()
                    docPaths.forEach { arr.add(it) }
                    java.add("docPath", arr)
                }

                root.add("java", java)

                val params = DidChangeConfigurationParams().apply {
                    settings = root
                }

                Logger.logDebug(
                    TAG,
                    "Sending didChangeConfiguration(java.docPath count=${docPaths.size})"
                )
                rm.didChangeConfiguration(params)
            }

            XMLLanguageServer.SERVER_ID -> {
                // LemMinX supports settings under the "xml" key.
                // We keep this minimal; users can extend later.
                val root = JsonObject()
                val xml = JsonObject()

                // Enable formatter by default (editor-lsp will call textDocument/formatting).
                val format = JsonObject()
                format.addProperty("enabled", true)
                xml.add("format", format)

                // Optional: enable client logs (LemMinX uses this for troubleshooting)
                // NOTE: This may create log files inside HOME depending on LemMinX settings.
                // val logs = JsonObject().apply { addProperty("client", true) }
                // xml.add("logs", logs)

                root.add("xml", xml)

                val params = DidChangeConfigurationParams().apply { settings = root }
                Logger.logDebug(TAG, "Sending didChangeConfiguration(xml)")
                rm.didChangeConfiguration(params)
            }

            else -> {
                // no-op
            }
        }
    }

    private fun detectJdkSourceZips(): List<String> {
        // Typical layout: $PREFIX/lib/jvm/<jdk>/lib/src.zip
        //
        // IMPORTANT: TermuxConstants.TERMUX_PREFIX_DIR_PATH may point to /data/data/com.neonide.studio/...,
        // but this app runs in a different sandbox (e.g. com.tom.rv2ide). So probe a few likely prefixes.
        // Prefer the runtime app sandbox prefix. This will be:
        //   /data/data/<applicationId>/files/usr
        // for the installed app (e.g. com.neonide.studio).
        val runtimePrefix = File(context.filesDir, "usr").absolutePath

        val prefixesToTry = linkedSetOf(
            runtimePrefix,
            // Kept for compatibility with environments still using the default TermuxConstants package name
            TermuxConstants.TERMUX_PREFIX_DIR_PATH
        )

        val candidates = ArrayList<String>()

        fun addIfExists(path: String) {
            if (File(path).exists()) candidates.add(path)
        }

        for (prefix in prefixesToTry) {
            val jvmDir = File(prefix, "lib/jvm")
            if (jvmDir.isDirectory) {
                jvmDir.listFiles()?.forEach { child ->
                    addIfExists(File(child, "lib/src.zip").absolutePath)
                    addIfExists(File(child, "src.zip").absolutePath)
                }
            }

            // Common explicit locations
            addIfExists("$prefix/lib/jvm/java-17-openjdk/lib/src.zip")
            addIfExists("$prefix/lib/jvm/java-21-openjdk/lib/src.zip")
            addIfExists("$prefix/lib/jvm/java-11-openjdk/lib/src.zip")
        }

        return candidates.distinct()
    }

    /**
     * Resolve the highest-version android.jar from the local Android SDK.
     */
    private fun resolveAndroidJar(): File? {
        val sdkDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "android-sdk")
        val platforms = File(sdkDir, "platforms")
        if (!platforms.isDirectory) return null

        val best = platforms.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("android-") }
            ?.maxByOrNull {
                it.name.removePrefix("android-").toIntOrNull() ?: 0
            }
            ?: return null

        val jar = File(best, "android.jar")
        return if (jar.exists()) jar else null
    }

    /**
     * Read the Gradle version from the project's gradle-wrapper.properties.
     * Returns e.g. "9.0.0" from distributionUrl=...gradle-9.0.0-bin.zip
     */
    private fun readGradleVersionFromWrapper(projectPath: String): String? {
        val wrapperFile = File(projectPath, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperFile.isFile) {
            Logger.logDebug(
                TAG,
                "gradle-wrapper.properties not found at ${wrapperFile.absolutePath}"
            )
            return null
        }
        val line = wrapperFile.readLines().firstOrNull { it.startsWith("distributionUrl=") }
            ?: return null
        // Extract version: ...gradle-9.0.0-bin.zip → 9.0.0
        val match = Regex("""gradle-(\d+\.\d+(?:\.\d+)?)-""").find(line)
        return match?.groupValues?.get(1).also {
            Logger.logDebug(TAG, "Detected Gradle version from wrapper: $it")
        }
    }

    /**
     * Scan the Gradle cache for commonly-needed dependency JARs (appcompat, material, etc.)
     * so the language server can resolve classes like AppCompatActivity.
     *
     * Reads gradle-wrapper.properties to determine the exact Gradle version and only scans
     * that version's transforms/ directory, avoiding duplicate scans across versions.
     */
    private fun addGradleCacheJars(
        gradleCacheDir: File,
        projectPath: String,
        out: MutableList<String>
    ) {
        if (!gradleCacheDir.isDirectory) {
            Logger.logDebug(TAG, "Gradle cache dir does not exist: ${gradleCacheDir.absolutePath}")
            return
        }

        val needed = setOf(
            "appcompat",
            "material",
            "core",
            "activity",
            "fragment",
            "lifecycle",
            "annotation",
            "constraintlayout",
            "recyclerview",
            "cardview",
            "coordinatorlayout",
            "drawerlayout",
            "viewpager",
            "swiperefreshlayout"
        )

        val seen = HashSet<String>()

        fun scanTransformsDir(transformsDir: File) {
            if (!transformsDir.isDirectory) return
            transformsDir.listFiles()?.forEach { hashDir ->
                if (!hashDir.isDirectory) return@forEach
                val transformedDir = File(hashDir, "transformed")
                if (!transformedDir.isDirectory) return@forEach
                transformedDir.listFiles()?.forEach { artifactDir ->
                    if (!artifactDir.isDirectory) return@forEach
                    val name = artifactDir.name.lowercase()
                    if (!needed.any { name.contains(it) }) return@forEach

                    val jarsDir = File(artifactDir, "jars")
                    val jar = File(jarsDir, "classes.jar")
                    if (jar.exists() && seen.add(jar.absolutePath)) {
                        out.add(jar.absolutePath)
                    }
                }
            }
        }

        // Read the exact Gradle version from the project's wrapper properties
        val gradleVersion = readGradleVersionFromWrapper(projectPath)
        if (gradleVersion != null) {
            // Scan only the matching version directory (e.g. 9.0.0/transforms/)
            val versionDir = File(gradleCacheDir, gradleVersion)
            Logger.logDebug(
                TAG,
                "Scanning Gradle cache for version $gradleVersion: ${versionDir.absolutePath}"
            )
            scanTransformsDir(File(versionDir, "transforms"))
        } else {
            // Fallback: scan all versioned subdirs if wrapper properties not found
            Logger.logDebug(TAG, "Gradle version unknown, scanning all cache subdirs")
            scanTransformsDir(File(gradleCacheDir, "transforms"))
            gradleCacheDir.listFiles()?.forEach { subdir ->
                if (subdir.isDirectory) {
                    scanTransformsDir(File(subdir, "transforms"))
                }
            }
        }

        Logger.logDebug(TAG, "Gradle cache scan found ${seen.size} dependency JARs")
    }

    private fun ensureServerDefinitions(project: LspProject) {
        // LspEditor requires project.getServerDefinition(ext) to exist.
        // Use extension-only keys ("java", "kt", "kts", "xml").
        if (project.getServerDefinition("java") == null) {
            project.addServerDefinition(javaDefinition())
        }
        if (project.getServerDefinition("kt") == null) {
            project.addServerDefinition(kotlinDefinitionFor("kt"))
        }
        if (project.getServerDefinition("kts") == null) {
            project.addServerDefinition(kotlinDefinitionFor("kts"))
        }
        if (project.getServerDefinition("xml") == null) {
            project.addServerDefinition(xmlDefinition())
        }
    }

    private fun startServer(serverId: String) {
        val intent = when (serverId) {
            JavaLanguageServer.SERVER_ID -> Intent(context, JavaLanguageServerService::class.java)

            KotlinLanguageServer.SERVER_ID -> Intent(
                context,
                KotlinLanguageServerService::class.java
            )

            XMLLanguageServer.SERVER_ID -> Intent(context, XmlLanguageServerService::class.java)

            else -> return
        }
        context.startService(intent)
    }

    private fun stopServers() {
        context.stopService(Intent(context, JavaLanguageServerService::class.java))
        context.stopService(Intent(context, KotlinLanguageServerService::class.java))
        context.stopService(Intent(context, XmlLanguageServerService::class.java))
    }

    private fun javaDefinition(): LanguageServerDefinition = CustomLanguageServerDefinition(
        "java",
        CustomLanguageServerDefinition.ServerConnectProvider {
            retryingLocalSocketProvider(JAVA_SOCKET)
        }
    )

    private fun kotlinDefinitionFor(ext: String): LanguageServerDefinition {
        // Kotlin language server uses the "kotlin" language id but we register per extension.
        return CustomLanguageServerDefinition(
            ext,
            CustomLanguageServerDefinition.ServerConnectProvider {
                retryingLocalSocketProvider(KOTLIN_SOCKET)
            }
        )
    }

    private fun xmlDefinition(): LanguageServerDefinition = CustomLanguageServerDefinition(
        "xml",
        CustomLanguageServerDefinition.ServerConnectProvider {
            retryingLocalSocketProvider(XML_SOCKET)
        }
    )

    /**
     * Sora's built-in [io.github.rosemoe.sora.lsp.client.connection.LocalSocketStreamConnectionProvider]
     * does not retry. In our case, the Android Service may not have created the
     * [android.net.LocalServerSocket] yet, so we need a retry loop.
     */
    private fun retryingLocalSocketProvider(socketName: String): StreamConnectionProvider {
        return object : StreamConnectionProvider {
            private lateinit var socket: LocalSocket
            private lateinit var _inputStream: java.io.InputStream
            private lateinit var _outputStream: java.io.OutputStream
            private var _isClosed = false

            override fun start() {
                val deadlineMs = System.currentTimeMillis() + 30000L
                var lastErr: IOException? = null
                _isClosed = false

                while (System.currentTimeMillis() < deadlineMs) {
                    try {
                        socket = LocalSocket()
                        socket.connect(
                            LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT)
                        )
                        _inputStream = socket.inputStream
                        _outputStream = socket.outputStream
                        return
                    } catch (e: IOException) {
                        lastErr = e
                        try {
                            Thread.sleep(100)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }

                throw lastErr ?: IOException("Failed to connect to local socket: $socketName")
            }

            override val inputStream: java.io.InputStream
                get() = _inputStream

            override val outputStream: java.io.OutputStream
                get() = _outputStream

            override val isClosed: Boolean
                get() = _isClosed

            override fun close() {
                _isClosed = true
                runCatching { socket.close() }
            }
        }
    }

    @Suppress("unused")
    private fun customProvider(socketName: String): StreamConnectionProvider {
        // Alternative: use CustomConnectProvider. Kept for reference.
        return CustomConnectProvider {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            android.util.Pair(s.inputStream, s.outputStream)
        }
    }
}
