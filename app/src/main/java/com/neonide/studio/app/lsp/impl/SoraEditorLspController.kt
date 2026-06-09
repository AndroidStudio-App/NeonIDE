package com.neonide.studio.app.lsp.impl

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.neonide.studio.app.lsp.EditorLspController
import com.neonide.studio.app.lsp.LspUtils
import com.neonide.studio.app.lsp.server.LspServerIds
import com.neonide.studio.app.lsp.server.ServerDefinitions
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeConfigurationParams

/**
 * LSP controller using sora-editor's [LspProject] for connection management.
 *
 * Server processes are started directly via [ProcessStreamConnectionProvider] —
 * no Android Services or LocalSocket needed.
 * Servers are expected to be installed via the Extensions system (downloaded from extensions.json).
 */
class SoraEditorLspController(private val context: Context) : EditorLspController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        private const val TAG = "SoraEditorLsp"
    }

    private var project: LspProject? = null
    private var current: LspEditor? = null
    private var currentFile: File? = null
    private var connectionJob: kotlinx.coroutines.Job? = null

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
        val isSameFile = currentFile?.absolutePath == file.absolutePath
        val isSameEditor = current?.editor == editor
        val isConnected = current?.isConnected == true

        if (isSameFile && isSameEditor && isConnected) {
            return true
        }

        detach()

        val serverId = LspUtils.getServerId(file) ?: run {
            return false
        }

        // Android resource XML: skip LSP — AndroidXmlLanguageEnhancer handles it.
        if (file.extension.equals("xml", ignoreCase = true) &&
            (file.path.contains("/res/") || file.name == "AndroidManifest.xml")
        ) {
            detach()
            return false
        }

        val desiredRoot = (projectRoot ?: file.parentFile ?: file).let {
            if (it.isFile) it.parentFile ?: it else it
        }

        val p = project?.takeIf { it.projectUri.path == desiredRoot.absolutePath }
            ?: run {
                project?.let { old -> runCatching { old.dispose() } }
                createProject(desiredRoot).also { project = it }
            }

        ensureServerDefinitions(p)

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

        lspEditor.wrapperLanguage = wrapperLanguage
        lspEditor.editor = editor

        current = lspEditor
        currentFile = file

        connectionJob = scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    Logger.logDebug(TAG, "Connecting LSP for ${file.name}...")
                    lspEditor.connectWithTimeout()
                }
                Logger.logInfo(TAG, "LSP connected for ${file.absolutePath} (serverId=$serverId)")

                // Configure server in background (classpath, settings)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { configureServer(serverId, lspEditor) }
                            .onFailure { t ->
                                Logger.logStackTraceWithMessage(
                                    TAG,
                                    "Failed to configure server",
                                    t
                                )
                            }
                    }
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    Logger.logStackTraceWithMessage(
                        TAG,
                        "LSP connect failed for ${file.absolutePath}",
                        t
                    )
                    if (current == lspEditor) {
                        detach()
                    } else {
                        runCatching { lspEditor.dispose() }
                    }
                }
            }
        }

        return true
    }

    override fun detach() {
        connectionJob?.cancel()
        connectionJob = null

        val prev = current
        current = null
        currentFile = null
        if (prev != null) {
            runCatching { prev.editor = null }
            scope.launch(Dispatchers.IO) {
                runCatching { prev.dispose() }
            }
        }
    }

    override fun dispose() {
        detach()
        val p = project
        project = null
        scope.launch(Dispatchers.IO) {
            runCatching { p?.dispose() }
        }
    }

    override fun currentEditor(): LspEditor? = current

    override fun prefetchClassPath(projectPath: File) {
        val path = projectPath.absolutePath
        if (cachedClassPath != null && cachedClassPathProject == path) return
        scope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val jars = scanAllClassPathJars(path)
            cachedClassPath = jars
            cachedClassPathProject = path
            Logger.logDebug(
                TAG,
                "Prefetched ${jars.size} classpath entries in ${System.currentTimeMillis() - start}ms"
            )
        }
    }

    private fun createProject(base: File): LspProject = LspProject(base.absolutePath).also {
        it.init()
    }

    private fun ensureServerDefinitions(project: LspProject) {
        fun addIfAbsent(ext: String, name: String, factory: () -> LanguageServerDefinition) {
            runCatching {
                project.addServerDefinition(factory())
            }.onFailure { e ->
                if (e.message?.contains("already exists") != true) {
                    // Silently ignore - server may already be registered
                }
            }
        }

        addIfAbsent("java", "java") { ServerDefinitions.java(getServerDir("java-language-server")) }
        addIfAbsent("kt", "kotlin") {
            ServerDefinitions.`kotlin`(getServerDir("kotlin-language-server"))
        }
        addIfAbsent("xml", "xml") {
            ServerDefinitions.xml(File(getServerDir("xml-language-server"), "lemminx-uber.jar"))
        }
        val jsonDef = { ServerDefinitions.json(getServerDir("json-language-server")) }
        addIfAbsent("json", "json", jsonDef)
        addIfAbsent("js", "json", jsonDef)
        val bashDef = { ServerDefinitions.bash(getServerDir("bash-language-server")) }
        addIfAbsent("bash", "bash", bashDef)
        addIfAbsent("sh", "bash", bashDef)
        addIfAbsent("zsh", "bash", bashDef)
        val yamlDef = { ServerDefinitions.yaml(getServerDir("yaml-language-server")) }
        addIfAbsent("yaml", "yaml", yamlDef)
        addIfAbsent("yml", "yaml", yamlDef)
    }

    private fun getServerDir(id: String): File = File(context.filesDir, id)

    private fun configureServer(serverId: String, lspEditor: LspEditor) {
        val rm = lspEditor.requestManager

        when (serverId) {
            LspServerIds.JAVA -> {
                val root = JsonObject()
                val java = JsonObject()
                val projectPath = lspEditor.project.projectUri.path
                val classPathArr = JsonArray()

                val cached = cachedClassPath
                if (cached != null && cachedClassPathProject == projectPath) {
                    cached.forEach { classPathArr.add(it) }
                } else {
                    val jars = scanAllClassPathJars(projectPath)
                    jars.forEach { classPathArr.add(it) }
                }

                java.add("classPath", classPathArr)
                root.add("java", java)
                rm.didChangeConfiguration(DidChangeConfigurationParams().apply { settings = root })
            }

            LspServerIds.XML -> {
                val root = JsonObject()
                val xml = JsonObject()
                val format = JsonObject()
                format.addProperty("enabled", true)
                xml.add("format", format)
                root.add("xml", xml)
                rm.didChangeConfiguration(DidChangeConfigurationParams().apply { settings = root })
            }

            LspServerIds.JSON -> {
                val root = JsonObject()
                val json = JsonObject()
                json.addProperty("validate", true)
                json.addProperty("allowComments", true)
                root.add("json", json)
                rm.didChangeConfiguration(DidChangeConfigurationParams().apply { settings = root })
            }

            LspServerIds.YAML -> {
                val root = JsonObject()
                val yaml = JsonObject()
                val format = JsonObject()
                format.addProperty("enable", true)
                yaml.add("format", format)
                root.add("yaml", yaml)
                rm.didChangeConfiguration(DidChangeConfigurationParams().apply { settings = root })
            }

            LspServerIds.KOTLIN -> {
                val projectPath = lspEditor.project.projectUri.path
                val classPathArr = JsonArray()

                val klsLibDir = File(getServerDir("kotlin-language-server"), "lib")
                if (klsLibDir.isDirectory) {
                    klsLibDir.listFiles()?.filter { it.extension == "jar" }?.forEach {
                        classPathArr.add(it.absolutePath)
                    }
                }

                val cached = cachedClassPath
                if (cached != null && cachedClassPathProject == projectPath) {
                    cached.forEach { classPathArr.add(it) }
                } else {
                    val jars = scanAllClassPathJars(projectPath)
                    jars.forEach { classPathArr.add(it) }
                }

                val root = JsonObject()
                root.addProperty("usePredefinedClasspath", true)
                root.addProperty("disableDependencyResolution", true)
                root.add("classpath", classPathArr)
                rm.didChangeConfiguration(DidChangeConfigurationParams().apply { settings = root })
            }

            LspServerIds.BASH -> {
                // bash-language-server requires no special configuration
            }
        }
    }

    private fun scanAllClassPathJars(projectPath: String): List<String> {
        val entries = mutableListOf<String>()

        resolveAndroidJar()?.let { entries.add(it.absolutePath) }

        val termuxLib = File(context.filesDir, "usr/lib")
        if (termuxLib.isDirectory) entries.add(termuxLib.absolutePath)

        val gradleCacheDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".gradle/caches")
        addGradleCacheJars(gradleCacheDir, projectPath, entries)

        val projectDir = File(projectPath)
        for (variant in listOf("debug", "release")) {
            val classes = File(projectDir, "build/intermediates/javac/$variant/classes")
            if (classes.isDirectory) entries.add(classes.absolutePath)
            val appClasses = File(projectDir, "app/build/intermediates/javac/$variant/classes")
            if (appClasses.isDirectory) entries.add(appClasses.absolutePath)
        }

        return entries
    }

    private fun resolveAndroidJar(): File? {
        val sdkDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "android-sdk")
        val platforms = File(sdkDir, "platforms")
        if (!platforms.isDirectory) return null
        val best = platforms.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("android-") }
            ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
            ?: return null
        val jar = File(best, "android.jar")
        return if (jar.exists()) jar else null
    }

    private fun addGradleCacheJars(
        gradleCacheDir: File,
        projectPath: String,
        out: MutableList<String>
    ) {
        if (!gradleCacheDir.isDirectory) return

        val needed = setOf(
            "appcompat", "material", "core", "activity", "fragment", "lifecycle",
            "annotation", "constraintlayout", "recyclerview", "cardview",
            "coordinatorlayout", "drawerlayout", "viewpager", "swiperefreshlayout",
            "kotlin-stdlib", "kotlin-reflect", "kotlinx-coroutines", "kotlinx-serialization"
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
                    val jar = File(artifactDir, "jars/classes.jar")
                    if (jar.exists() && seen.add(jar.absolutePath)) out.add(jar.absolutePath)
                }
            }
        }

        val gradleVersion = readGradleVersionFromWrapper(projectPath)
        if (gradleVersion != null) {
            scanTransformsDir(File(gradleCacheDir, "$gradleVersion/transforms"))
        } else {
            gradleCacheDir.listFiles()?.forEach { subdir ->
                if (subdir.isDirectory) scanTransformsDir(File(subdir, "transforms"))
            }
        }
    }

    private fun readGradleVersionFromWrapper(projectPath: String): String? {
        val wrapperFile = File(projectPath, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperFile.isFile) return null
        val line =
            wrapperFile.readLines().firstOrNull { it.startsWith("distributionUrl=") } ?: return null
        return Regex("""gradle-(\d+\.\d+(?:\.\d+)?)-""").find(line)?.groupValues?.get(1)
    }
}
