package com.neonide.studio.app.lsp.server

import com.termux.shared.termux.TermuxConstants
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.languageServerDefinition
import java.io.File

/**
 * LSP server definitions using sora-editor's [languageServerDefinition] DSL.
 *
 * Each server is started as a process via [ProcessStreamConnectionProvider].
 * The library handles LSP protocol, diagnostics, completions, and document sync.
 */
object ServerDefinitions {

    private val termuxJava: String
        get() {
            val f = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "java")
            return if (f.exists()) f.absolutePath else "java"
        }

    /**
     * LemMinX XML language server.
     * JAR is extracted from assets on first use.
     */
    fun xml(lemminxJar: File) = languageServerDefinition {
        name("xml")
        ext("xml")
        connect { _ ->
            ProcessStreamConnectionProvider(
                listOf(termuxJava, "-jar", lemminxJar.absolutePath)
            )
        }
    }

    /**
     * Java language server (org.javacs).
     * JARs are extracted from assets on first use.
     */
    fun java(serverDir: File) = languageServerDefinition {
        name("java")
        ext("java")
        connect { _ ->
            val classpath = listOf(
                "gson-2.8.9.jar",
                "protobuf-java-3.19.6.jar",
                "java-language-server.jar"
            ).joinToString(File.pathSeparator) { File(serverDir, it).absolutePath }

            val vmOptions = listOf(
                "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
                "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )

            val command = mutableListOf(termuxJava)
            command.addAll(vmOptions)
            command.addAll(listOf("-cp", classpath, "org.javacs.Main"))

            ProcessStreamConnectionProvider(
                command,
                workingDir = serverDir,
                env = mapOf("HOME" to TermuxConstants.TERMUX_HOME_DIR_PATH)
            )
        }
    }

    /**
     * Kotlin language server.
     * JARs are extracted from assets on first use.
     */
    fun kotlin(serverDir: File) = languageServerDefinition {
        name("kotlin")
        ext("kt")
        exts("kts")
        connect { _ ->
            val libDir = File(serverDir, "lib")
            val klsJars = libDir.listFiles()
                ?.filter { it.extension == "jar" }
                ?.map { it.absolutePath }
                ?: emptyList()
            val rootJars = serverDir.listFiles()
                ?.filter { it.extension == "jar" }
                ?.map { it.absolutePath }
                ?: emptyList()
            val classpath = (rootJars + klsJars).joinToString(":")

            ProcessStreamConnectionProvider(
                listOf(
                    termuxJava,
                    "-Xms128m",
                    "-Xmx512m",
                    "-XX:+UseG1GC",
                    "-XX:TieredStopAtLevel=1",
                    "-XX:+UseStringDeduplication",
                    "-Djava.awt.headless=true",
                    "-cp",
                    classpath,
                    "org.javacs.kt.MainKt"
                ),
                workingDir = serverDir,
                env = mapOf("HOME" to TermuxConstants.TERMUX_HOME_DIR_PATH)
            )
        }
    }
}
