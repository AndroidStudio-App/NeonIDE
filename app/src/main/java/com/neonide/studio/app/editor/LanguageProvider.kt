package com.neonide.studio.app.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import java.io.File

class LanguageProvider(
    // TextMate Grammar
    private val tmFactory: (String) -> Language?
) {
    fun getLanguage(file: File): Language {
        val ext = file.extension.lowercase()
        return when (ext) {
            "java" -> tmFactory("java") ?: EmptyLanguage()
            "kt", "kts" -> tmFactory("kotlin") ?: EmptyLanguage()
            "xml" -> tmFactory("xml") ?: EmptyLanguage()
            "json" -> tmFactory("json") ?: EmptyLanguage()
            "py" -> tmFactory("python") ?: EmptyLanguage()
            "c" -> tmFactory("c") ?: EmptyLanguage()
            "h" -> tmFactory("c") ?: EmptyLanguage()
            "cpp", "cc", "cxx" -> tmFactory("cpp") ?: EmptyLanguage()
            "hpp", "hh", "hxx" -> tmFactory("cpp") ?: EmptyLanguage()
            "properties" -> tmFactory("properties") ?: EmptyLanguage()
            "log" -> EmptyLanguage()
            "aidl" -> tmFactory("aidl") ?: EmptyLanguage()
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
