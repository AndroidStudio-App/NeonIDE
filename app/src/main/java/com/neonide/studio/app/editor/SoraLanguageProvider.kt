package com.neonide.studio.app.editor

import android.content.Context
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import java.io.File

class SoraLanguageProvider(private val context: Context) {

    init {
        AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider {
            AndroidFrameworkAttrIndex.allAttrs().toList()
        }
        Thread {
            AndroidFrameworkAttrIndex.ensureLoaded()
        }.start()
    }

    private val baseProvider = LanguageProvider(
        tmFactory = { type -> createTextMateLanguage(type) }
    )

    fun getLanguage(file: File): Language {
        val base = baseProvider.getLanguage(file)

        return if (isAndroidResourceXml(file)) {
            AndroidXmlLanguageEnhancer(base, file)
        } else {
            base
        }
    }

    private fun isAndroidResourceXml(file: File): Boolean {
        if (!file.extension.equals("xml", ignoreCase = true)) return false
        val path = file.path
        return path.contains("/res/") ||
            path.endsWith("AndroidManifest.xml")
    }

    private fun createTextMateLanguage(type: String): Language? = runCatching {
        when (type) {
            "java" -> TextMateLanguage.create("source.java", true)
            "kotlin" -> TextMateLanguage.create("source.kotlin", true)
            "python" -> TextMateLanguage.create("source.python", true)
            "html" -> TextMateLanguage.create("text.html.basic", true)
            "javascript" -> TextMateLanguage.create("source.js", true)
            "javascriptreact" -> TextMateLanguage.create("source.js.jsx", true)
            "markdown" -> TextMateLanguage.create("text.html.markdown", true)
            "typescript" -> TextMateLanguage.create("source.ts", true)
            "typescriptreact" -> TextMateLanguage.create("source.tsx", true)
            "xml" -> TextMateLanguage.create("text.xml", true)
            "json" -> TextMateLanguage.create("source.json", true)
            "yaml" -> TextMateLanguage.create("source.yaml", true)
            "sh", "bash", "zsh" -> TextMateLanguage.create("source.shell", true)
            "dart" -> TextMateLanguage.create("source.dart", true)
            "properties" -> TextMateLanguage.create("source.properties", true)
            "c" -> TextMateLanguage.create("source.c", true)
            "cpp" -> TextMateLanguage.create("source.cpp", true)
            "aidl" -> TextMateLanguage.create("source.aidl", true)
            else -> null
        }
    }.getOrNull()
}
