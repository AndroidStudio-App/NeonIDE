package com.neonide.studio.app.editor

import android.content.Context
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import java.io.File

class SoraLanguageProvider(val context: Context) {

    init {
        AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider {
            AndroidFrameworkAttrIndex.allAttrs().toList()
        }
        Thread { AndroidFrameworkAttrIndex.ensureLoaded() }.start()
    }

    fun getLanguage(file: File): Language {
        val scope = extensions[file.extension.lowercase()] ?: return EmptyLanguage()
        val base = runCatching {
            TextMateLanguage.create(scope, true)
        }.getOrDefault(EmptyLanguage())
        return if (isAndroidResourceXml(file)) AndroidXmlLanguageEnhancer(base, file) else base
    }

    fun isAndroidResourceXml(file: File): Boolean {
        val path = file.path
        return file.extension.equals("xml", ignoreCase = true) &&
            (path.contains("/res/") || path.endsWith("AndroidManifest.xml"))
    }

    companion object {
        val extensions: Map<String, String> = mapOf(
            "aidl" to "source.aidl",
            "c" to "source.c", "h" to "source.c",
            "cpp" to "source.cpp", "cc" to "source.cpp", "cxx" to "source.cpp",
            "dart" to "source.dart",
            "hpp" to "source.cpp", "hh" to "source.cpp", "hxx" to "source.cpp",
            "html" to "text.html.basic", "htm" to "text.html.basic",
            "java" to "source.java",
            "js" to "source.js", "jsx" to "source.js.jsx",
            "json" to "source.json",
            "kt" to "source.kotlin", "kts" to "source.kotlin",
            "lua" to "source.lua",
            "md" to "text.html.markdown", "markdown" to "text.html.markdown",
            "properties" to "source.properties",
            "py" to "source.python",
            "sh" to "source.shell", "bash" to "source.shell", "zsh" to "source.shell",
            "ts" to "source.ts", "tsx" to "source.tsx",
            "xml" to "text.xml",
            "yaml" to "source.yaml", "yml" to "source.yaml"
        )
    }
}
