package com.neonide.studio.app.bottomsheet.preview.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Xml
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class LayoutPreviewEngine(
    private val appContext: Context,
    private val projectDir: File,
    private val cacheDir: File
) {
    companion object {
        private val NESTED_DRAWABLE_TAGS = setOf("shape")

        // TO-DO
        private val UNSUPPORTED_ELEMENTS = setOf(
            "animated-vector",
            "animated-selector",
            "adaptive-icon",
            "transition",
            "clip",
            "scale",
            "selector",
            "rotate",
            "bitmap",
            "nine-patch",
            "color",
            "menu",
            "navigation",
            "preference-headers",
            "PreferenceScreen",
            "paths",
            "font-family",
            "animated-image",
            "motionScene",
            "GestureOverlayView"
        )

        private val WRAPPER_TAGS = setOf("ripple", "layer-list", "inset", "level-list")
    }

    val viewModel = LayoutPreviewViewModel()

    private var renderer: LayoutRenderer? = null
    private val config = RenderConfig(
        appContext.resources.displayMetrics.widthPixels,
        appContext.resources.displayMetrics.heightPixels
    )

    suspend fun onXmlEdited(xmlContent: String, filePath: String? = null) {
        viewModel.setState(PreviewState.Loading)
        val elementTag = extractFirstNestedDrawable(xmlContent)?.tag
            ?: extractRootTag(xmlContent)

        val isVector = elementTag == "vector"
        val isShape = elementTag == "shape"
        val isWrapper = elementTag in WRAPPER_TAGS

        if (!isVector && !isShape && !isWrapper) {
            val unsupported = validateElements(xmlContent, filePath, elementTag)
            unsupported?.let {
                viewModel.setState(PreviewState.Error(PreviewError.UnsupportedXml(it)))
                return
            }
        }

        try {
            val bitmap = when {
                isVector -> withContext(Dispatchers.Default) {
                    VectorDrawablePreview.render(appContext, xmlContent, projectDir)
                }
                isShape -> withContext(Dispatchers.Default) {
                    ShapeDrawablePreview.render(appContext, xmlContent, projectDir)
                }
                isWrapper -> withContext(Dispatchers.Default) {
                    renderWrappedDrawable(appContext, xmlContent, projectDir, elementTag!!)
                }
                else -> withContext(Dispatchers.Default) {
                    val inflater = XmlLayoutInflater(
                        context = appContext,
                        projectDir = projectDir
                    )
                    val root = inflater.inflate(xmlContent)

                    renderer?.dispose()
                    val rend = LayoutRenderer()
                    renderer = rend

                    val result = rend.render(root, config)
                    result.getOrElse { throw it }
                }
            }
            viewModel.setState(PreviewState.Rendered(bitmap.asImageBitmap()))
        } catch (e: Exception) {
            val detail = buildString {
                append(e.message ?: e.javaClass.simpleName)
                e.cause?.message?.let { append("\nCause: ").append(it) }
                append("\n\n").append(e.stackTraceToString())
            }
            viewModel.setState(PreviewState.Error(PreviewError.RenderFailed(detail)))
        }
    }

    fun dispose() {
        renderer?.dispose()
        renderer = null
    }

    /**
     * Unpacks wrapper drawables (ripple, layer-list, inset, level-list) by extracting
     * their first nested <shape> / <selector> and rendering it.
     *
     * Must capture the element at its START_TAG; a separate "peek" pass would advance
     * the parser past the drawable and produce empty / broken XML.
     */
    private fun renderWrappedDrawable(
        context: Context,
        xmlContent: String,
        projectDir: File,
        element: String
    ): Bitmap {
        val element = extractFirstNestedDrawable(xmlContent)
            ?: throw IllegalStateException("No drawable child found inside <$element>")

        return when (element.tag) {
            "shape" -> ShapeDrawablePreview.render(context, element.xml, projectDir)
            else -> throw IllegalStateException("Unsupported wrapper child: <${element.tag}>")
        }
    }

    private data class NestedDrawable(val tag: String, val xml: String)

    private fun extractFirstNestedDrawable(xmlContent: String): NestedDrawable? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xmlContent))

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name?.substringAfterLast(':').orEmpty()
                // Skip wrapper roots and item wrappers; take the first real drawable.
                if (tag in NESTED_DRAWABLE_TAGS) {
                    val xml = serializeCurrentElement(parser, tag)
                    return NestedDrawable(tag, xml)
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun extractRootTag(xmlContent: String): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xmlContent))
        return try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    return parser.name
                }
                event = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun validateElements(xmlContent: String, filePath: String?, element: String?): String? {
        if (element != null && element in UNSUPPORTED_ELEMENTS) {
            return "Unsupported element <$element>.\n" +
                "Preview supports layout files, <vector>, and <shape> drawables."
        }
        return null
    }

    /**
     * Serializes the element currently at START_TAG (including children) to a standalone
     * XML fragment with android namespace so Shape/Selector parsers can resolve attrs.
     */
    private fun serializeCurrentElement(parser: XmlPullParser, rootLocalName: String): String {
        require(parser.eventType == XmlPullParser.START_TAG) {
            "serializeCurrentElement requires START_TAG"
        }

        val sb = StringBuilder()
        appendStartTag(sb, parser, rootLocalName, declareAndroidNs = true)

        var depth = 1
        while (depth > 0) {
            val pt = parser.next()
            when (pt) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val local = parser.name?.substringAfterLast(':').orEmpty()
                    appendStartTag(sb, parser, local, declareAndroidNs = false)
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth > 0) {
                        val local = parser.name?.substringAfterLast(':').orEmpty()
                        sb.append("</").append(local).append('>')
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank()) {
                        sb.append(text.trim())
                    }
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        sb.append("</").append(rootLocalName).append('>')
        return sb.toString()
    }

    private fun appendStartTag(
        sb: StringBuilder,
        parser: XmlPullParser,
        localName: String,
        declareAndroidNs: Boolean
    ) {
        sb.append('<').append(localName)
        if (declareAndroidNs) {
            sb.append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        }
        for (i in 0 until parser.attributeCount) {
            val rawName = parser.getAttributeName(i).orEmpty()
            val prefix = parser.getAttributePrefix(i)
            val local = rawName.substringAfterLast(':')
            // Namespace decls already re-added on the root; skip raw xmlns attrs.
            if (local.startsWith("xmlns") || rawName.startsWith("xmlns")) continue
            val attrName = when {
                !prefix.isNullOrEmpty() -> "$prefix:$local"
                local == "name" || local == "id" -> local
                else -> "android:$local"
            }
            val value = parser.getAttributeValue(i)
                ?.replace("&", "&amp;")
                ?.replace("\"", "&quot;")
                .orEmpty()
            sb.append(' ').append(attrName).append("=\"").append(value).append('"')
        }
        sb.append('>')
    }
}
