package com.neonide.studio.app.bottomsheet.preview.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Xml
import androidx.core.graphics.PathParser
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * Renders editor/project `<vector>` XML to a Bitmap.
 *
 * Platform [android.graphics.drawable.VectorDrawable] only inflates compiled resource XML
 * (`XmlBlock$Parser`), so free-form editor XML must be parsed and drawn manually.
 * root vector + path children (fill/stroke/pathData). Groups/clip-path not expanded.
 */
object VectorDrawablePreview {
    private const val MAX_PX = 1024
    private const val DEFAULT_DP = 80f
    private const val DEFAULT_VIEWPORT = 24f
    private const val PAD_PX = 24
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun render(context: Context, xmlContent: String, projectDir: java.io.File? = null): Bitmap {
        val colorResolver = ProjectColorResolver(projectDir)
        val model = parseVector(xmlContent, colorResolver)

        val density = context.resources.displayMetrics.density
        val defaultPx = (DEFAULT_DP * density).toInt().coerceAtLeast(1)

        val intrinsicW = model.widthPx(density).takeIf { it > 0 } ?: defaultPx
        val intrinsicH = model.heightPx(density).takeIf { it > 0 } ?: defaultPx

        val w = intrinsicW.coerceIn(1, MAX_PX)
        val h = intrinsicH.coerceIn(1, MAX_PX)

        val outW = (w + PAD_PX * 2).coerceAtMost(MAX_PX + PAD_PX * 2)
        val outH = (h + PAD_PX * 2).coerceAtMost(MAX_PX + PAD_PX * 2)

        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val left = (outW - w) / 2f
        val top = (outH - h) / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(
            w / model.viewportWidth.coerceAtLeast(0.001f),
            h / model.viewportHeight.coerceAtLeast(0.001f)
        )
        drawPaths(canvas, model.paths)
        canvas.restore()

        return bitmap
    }

    private fun drawPaths(canvas: Canvas, paths: List<VectorPath>) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeJoin = Paint.Join.MITER
        }

        for (vp in paths) {
            val path = try {
                PathParser.createPathFromPathData(vp.pathData)
            } catch (e: Exception) {
                null
            } ?: continue

            if (vp.fillAlpha > 0f && vp.fillColor != Color.TRANSPARENT) {
                fillPaint.color = vp.fillColor
                fillPaint.alpha = (vp.fillAlpha * 255f).toInt().coerceIn(0, 255)
                path.fillType = if (vp.fillTypeEvenOdd) {
                    Path.FillType.EVEN_ODD
                } else {
                    Path.FillType.WINDING
                }
                canvas.drawPath(path, fillPaint)
            }

            if (vp.strokeWidth > 0f &&
                vp.strokeAlpha > 0f &&
                vp.strokeColor != Color.TRANSPARENT
            ) {
                strokePaint.color = vp.strokeColor
                strokePaint.alpha = (vp.strokeAlpha * 255f).toInt().coerceIn(0, 255)
                strokePaint.strokeWidth = vp.strokeWidth
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun parseVector(xmlContent: String, colorResolver: ProjectColorResolver): VectorModel {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xmlContent))

        var event = parser.eventType
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
        if (event != XmlPullParser.START_TAG) {
            throw IllegalStateException("No start tag found in vector XML")
        }

        val root = localName(parser.name)
        if (root != "vector") {
            throw IllegalStateException("Expected root <vector>, found <$root>")
        }

        val widthDp = parseDimensionDp(attr(parser, "width")) ?: DEFAULT_DP
        val heightDp = parseDimensionDp(attr(parser, "height")) ?: DEFAULT_DP
        val viewportW = attr(parser, "viewportWidth")?.toFloatOrNull()
            ?: DEFAULT_VIEWPORT
        val viewportH = attr(parser, "viewportHeight")?.toFloatOrNull()
            ?: DEFAULT_VIEWPORT
        val rootAlpha = attr(parser, "alpha")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

        val paths = mutableListOf<VectorPath>()
        var depth = 1
        while (depth > 0) {
            event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (localName(parser.name) == "path") {
                        parsePath(parser, rootAlpha, colorResolver)
                            ?.let { paths.add(it) }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (paths.isEmpty()) {
            throw IllegalStateException("Vector has no drawable <path> elements")
        }

        return VectorModel(
            widthDp = widthDp,
            heightDp = heightDp,
            viewportWidth = viewportW.coerceAtLeast(0.001f),
            viewportHeight = viewportH.coerceAtLeast(0.001f),
            paths = paths
        )
    }

    private fun parsePath(
        parser: XmlPullParser,
        rootAlpha: Float,
        resolver: ProjectColorResolver
    ): VectorPath? {
        val pathData = attr(parser, "pathData")?.trim().orEmpty()
        if (pathData.isEmpty()) {
            return null
        }

        val fillColor = parseColor(attr(parser, "fillColor"), resolver) ?: Color.TRANSPARENT
        val strokeColor = parseColor(attr(parser, "strokeColor"), resolver) ?: Color.TRANSPARENT
        val fillAlpha =
            (attr(parser, "fillAlpha")?.toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * rootAlpha
        val strokeAlpha =
            (attr(parser, "strokeAlpha")?.toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * rootAlpha
        val strokeWidth = attr(parser, "strokeWidth")?.toFloatOrNull() ?: 0f
        val fillTypeEvenOdd =
            attr(parser, "fillType")?.equals("evenOdd", ignoreCase = true) == true

        return VectorPath(
            pathData = pathData,
            fillColor = fillColor,
            fillAlpha = fillAlpha,
            strokeColor = strokeColor,
            strokeAlpha = strokeAlpha,
            strokeWidth = strokeWidth,
            fillTypeEvenOdd = fillTypeEvenOdd
        )
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        parser.getAttributeValue(ANDROID_NS, name)?.let { return it }
        for (i in 0 until parser.attributeCount) {
            val local = parser.getAttributeName(i)?.substringAfterLast(':')
            if (local == name) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()

    private fun parseDimensionDp(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim()
        return when {
            v.endsWith("dp", ignoreCase = true) ->
                v.dropLast(2).toFloatOrNull()
            v.endsWith("dip", ignoreCase = true) ->
                v.dropLast(3).toFloatOrNull()
            v.endsWith("px", ignoreCase = true) ->
                v.dropLast(2).toFloatOrNull()
            v.endsWith("sp", ignoreCase = true) ->
                v.dropLast(2).toFloatOrNull()
            else -> v.toFloatOrNull()
        }
    }

    /**
     * Parses #RGB / #ARGB / #RRGGBB / #AARRGGBB and rejects resource refs for V1.
     */
    private fun parseColor(raw: String?, resolver: ProjectColorResolver): Int? =
        resolver.resolve(raw)

    private data class VectorModel(
        val widthDp: Float,
        val heightDp: Float,
        val viewportWidth: Float,
        val viewportHeight: Float,
        val paths: List<VectorPath>
    ) {
        fun widthPx(density: Float): Int = (widthDp * density).toInt().coerceAtLeast(1)

        fun heightPx(density: Float): Int = (heightDp * density).toInt().coerceAtLeast(1)
    }

    private data class VectorPath(
        val pathData: String,
        val fillColor: Int,
        val fillAlpha: Float,
        val strokeColor: Int,
        val strokeAlpha: Float,
        val strokeWidth: Float,
        val fillTypeEvenOdd: Boolean
    )
}
