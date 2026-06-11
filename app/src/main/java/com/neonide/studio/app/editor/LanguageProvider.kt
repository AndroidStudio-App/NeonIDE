package com.neonide.studio.app.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import java.io.File

class LanguageProvider(
    private val tsFactory: (String) -> Language?,
    private val tmFactory: (String) -> Language?
) {
    fun getLanguage(file: File): Language {
        val ext = file.extension.lowercase()
        return when (ext) {
            "java" -> tsFactory("java") ?: tmFactory("java") ?: EmptyLanguage()
            "kt", "kts" -> tsFactory("kotlin") ?: tmFactory("kotlin") ?: EmptyLanguage()
            "xml" -> tsFactory("xml") ?: tmFactory("xml") ?: EmptyLanguage()
            "json" -> tmFactory("json") ?: tsFactory("json") ?: EmptyLanguage()
            "py" -> tsFactory("python") ?: tmFactory("python") ?: EmptyLanguage()
            "c" -> tsFactory("c") ?: EmptyLanguage()
            "h" -> tsFactory("c") ?: EmptyLanguage()
            "cpp", "cc", "cxx" -> tsFactory("cpp") ?: EmptyLanguage()
            "hpp", "hh", "hxx" -> tsFactory("cpp") ?: EmptyLanguage()
            "properties" -> tsFactory("properties") ?: EmptyLanguage()
            "log" -> tsFactory("log") ?: EmptyLanguage()
            "aidl" -> tsFactory("aidl") ?: EmptyLanguage()
            "js" -> tmFactory("javascript") ?: EmptyLanguage()
            "jsx" -> tmFactory("javascriptreact") ?: EmptyLanguage()
            "html", "htm" -> tmFactory("html") ?: EmptyLanguage()
            "md", "markdown" -> tmFactory("markdown") ?: EmptyLanguage()
            "ts" -> tmFactory("typescript") ?: EmptyLanguage()
            "tsx" -> tmFactory("typescriptreact") ?: EmptyLanguage()
            "yaml", "yml" -> tmFactory("yaml") ?: EmptyLanguage()
            "sh" -> tmFactory("sh") ?: EmptyLanguage()
            "bash" -> tmFactory("bash") ?: EmptyLanguage()
            "zsh" -> tmFactory("zsh") ?: EmptyLanguage()
            "dart" -> tmFactory("dart") ?: EmptyLanguage()
            else -> EmptyLanguage()
        }
    }
}
