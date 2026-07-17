package com.neonide.studio.app.bottomsheet.preview.core

import android.graphics.Color
import android.util.Xml
import java.io.File
import java.io.FileReader
import org.xmlpull.v1.XmlPullParser

/**
 * Resolves project @color/name literals by scanning values XML under projectDir.
 * literal # colors and direct color tag bodies. No theme attrs or indirection.
 * @android:color/ references are handled upstream in XmlLayoutInflater.parseColor
 * via proper reflection + context.theme.
 */
class ProjectColorResolver(private val projectDir: File?) {
    private val cache: Map<String, Int> by lazy { loadColors() }

    fun resolve(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val value = value.trim()
        if (value.startsWith("?")) return null
        if (value.startsWith("#") || (!value.startsWith("@") && !value.contains('/'))) {
            return parseLiteral(value)
        }
        val name = colorName(value)
        if (name != null) {
            cache[name]?.let { return it }
        }
        // @android:color handled upstream in XmlLayoutInflater.parseColor
        // via proper reflection + context.theme — skip here to avoid stale values.
        return null
    }

    private fun colorName(raw: String): String? {
        val attribute = raw.trim()
        return when {
            attribute.startsWith("@color/") -> attribute.removePrefix("@color/")
            attribute.startsWith("@android:color/") -> null
            attribute.startsWith("color/") -> attribute.removePrefix("color/")
            attribute.startsWith("@+color/") -> attribute.removePrefix("@+color/")
            else -> null
        }?.substringBefore('/')?.takeIf { it.isNotBlank() }
    }

    private fun parseLiteral(value: String): Int? = try {
        Color.parseColor(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun loadColors(): Map<String, Int> {
        val dir = projectDir ?: return emptyMap()
        if (!dir.isDirectory) return emptyMap()

        val out = LinkedHashMap<String, Int>()
        val valuesDirs = findValuesDirs(dir)
        for (valuesDir in valuesDirs) {
            val files = valuesDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml") }
                .orEmpty()
            for (file in files) {
                parseColorFile(file, out)
            }
        }
        return out
    }

    private fun findValuesDirs(root: File): List<File> {
        val direct = listOf(
            File(root, "app/src/main/res"),
            File(root, "src/main/res"),
            File(root, "res")
        ).flatMap { res ->
            res.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("values") }
                .orEmpty()
        }
        if (direct.isNotEmpty()) return direct.distinct()

        return root.walkTopDown()
            .maxDepth(14)
            .filter { file ->
                file.isDirectory &&
                    file.name.startsWith("values") &&
                    file.parentFile?.name?.startsWith("res") == true
            }
            .toList()
    }

    private fun parseColorFile(file: File, out: MutableMap<String, Int>) {
        val parser = Xml.newPullParser()
        try {
            FileReader(file).use { reader ->
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(reader)
                var event = parser.eventType
                var inColor = false
                var colorName: String? = null
                val text = StringBuilder()
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name?.substringAfterLast(':').orEmpty()
                            if (tag == "color") {
                                inColor = true
                                colorName = parser.getAttributeValue(null, "name")
                                    ?: attrLocal(parser, "name")
                                text.clear()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inColor) text.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> {
                            val tag = parser.name?.substringAfterLast(':').orEmpty()
                            if (tag == "color" && inColor) {
                                val name = colorName
                                val body = text.toString().trim()
                                if (!name.isNullOrBlank() && body.isNotBlank()) {
                                    // Only store direct literals; skip @color and ?attr chains.
                                    parseLiteral(body)?.let { out.putIfAbsent(name, it) }
                                }
                                inColor = false
                                colorName = null
                                text.clear()
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        } catch (_: Exception) {
            // Ignore unreadable / malformed values files
        }
    }

    private fun attrLocal(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            val local = parser.getAttributeName(i)?.substringAfterLast(':')
            if (local == name) return parser.getAttributeValue(i)
        }
        return null
    }
}
