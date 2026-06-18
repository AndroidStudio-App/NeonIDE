package com.neonide.studio.app.editor.xml.framework

import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * Lazy, cached index of Android framework XML attributes.
 *
 * Parses <sdk>/platforms/android-<api>/data/res/values/attrs.xml
 * and extracts <attr name="..."> for autocompletion.
 */
object AndroidFrameworkAttrIndex {

    @Volatile private var cached: Set<String>? = null

    fun ensureLoaded(): Boolean {
        if (cached != null) return true

        synchronized(this) {
            if (cached != null) return true

            val sdkDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "android-sdk")
            if (!sdkDir.exists()) return false

            val attrsFile = resolveBestAttrsXml(sdkDir) ?: return false
            val names = parseAttrNames(attrsFile)
            if (names.isEmpty()) return false

            cached = names
            return true
        }
    }

    fun isLoaded(): Boolean = cached != null

    fun allAttrs(): Set<String> = cached ?: emptySet()

    fun suggestions(prefix: String, limit: Int = 400): List<String> {
        val set = cached ?: return emptyList()
        if (prefix.isBlank()) {
            return set.asSequence().sorted().take(limit).toList()
        }

        val p = prefix.lowercase()
        return set.asSequence()
            .filter { it.contains(p) }
            .sorted()
            .take(limit)
            .toList()
    }

    private fun resolveBestAttrsXml(sdkDir: File): File? {
        val platforms = File(sdkDir, "platforms")
        if (!platforms.exists() || !platforms.isDirectory) return null

        val dirs =
            platforms.listFiles()?.filter { it.isDirectory && it.name.startsWith("android-") }
                ?.sortedByDescending { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
                ?: emptyList()

        for (d in dirs) {
            val f = File(d, "data/res/values/attrs.xml")
            if (f.exists() && f.isFile) return f
        }
        return null
    }

    private fun parseAttrNames(attrsXml: File): Set<String> {
        val text = attrsXml.readText(Charsets.UTF_8)
        val regex = Regex("""<attr name="([^"]+)"""")
        return regex.findAll(text)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toHashSet()
    }
}
