package com.neonide.studio.editor.bottomsheet.preview.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * Renders editor/project `<shape>` XML to a Bitmap using [GradientDrawable].
 *
 * rectangle/oval/line/ring, solid, gradient (linear/radial/sweep),
 * corners, stroke, size.
 */
object ShapeDrawablePreview {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val MAX_PX = 1024
    private const val DEFAULT_SIZE_DP = 120f
    private const val PAD_PX = 24

    /**
     * Parses <shape> XML into a stretchable [GradientDrawable] for view backgrounds.
     * Unlike [render] this does NOT produce a fixed-size Bitmap — the drawable fills
     * whatever bounds the view assigns it.
     */
    fun buildDrawable(
        xmlContent: String,
        density: Float,
        projectDir: java.io.File? = null
    ): GradientDrawable? = try {
        val colorResolver = ProjectColorResolver(projectDir)
        val model = parseShape(xmlContent, colorResolver)
        model.buildGradientDrawable(density)
    } catch (_: Exception) {
        null
    }

    fun render(context: Context, xmlContent: String, projectDir: java.io.File? = null): Bitmap {
        val density = context.resources.displayMetrics.density

        val colorResolver = ProjectColorResolver(projectDir)
        val result = parseShape(xmlContent, colorResolver)
        val (widthPx, heightPx) = result.sizePx(density)

        // Bail out early if size is zero
        val w = widthPx.coerceIn(1, MAX_PX)
        val h = heightPx.coerceIn(1, MAX_PX)

        val outW = (w + PAD_PX * 2).coerceAtMost(MAX_PX + PAD_PX * 2)
        val outH = (h + PAD_PX * 2).coerceAtMost(MAX_PX + PAD_PX * 2)

        val drawable = result.buildGradientDrawable(density)

        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Checkerboard so white / light solids stay visible (not white-on-white).
        drawCheckerboard(canvas, outW, outH)

        val left = (outW - w) / 2
        val top = (outH - h) / 2
        drawable.bounds = Rect(left, top, left + w, top + h)
        drawable.draw(canvas)

        return bitmap
    }

    private fun drawCheckerboard(canvas: Canvas, width: Int, height: Int) {
        val light = 0xFFD0D0D0.toInt()
        val dark = 0xFFB8B8B8.toInt()
        val cell = 16
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        var y = 0
        while (y < height) {
            var x = 0
            val row = y / cell
            while (x < width) {
                val col = x / cell
                paint.color = if ((row + col) % 2 == 0) light else dark
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    minOf(x + cell, width).toFloat(),
                    minOf(y + cell, height).toFloat(),
                    paint
                )
                x += cell
            }
            y += cell
        }
    }

    private const val TAG_SHAPE = "shape"
    private const val TAG_SOLID = "solid"
    private const val TAG_CORNERS = "corners"
    private const val TAG_STROKE = "stroke"
    private const val TAG_SIZE = "size"
    private const val TAG_PADDING = "padding"
    private const val TAG_GRADIENT = "gradient"

    private data class GradientModel(
        val type: Int = GradientDrawable.LINEAR_GRADIENT,
        val colors: IntArray,
        val angle: Float = 0f,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        /** Radial gradient radius in dp (null = half of min side). */
        val gradientRadiusDp: Float? = null
    )

    private data class ShapeModel(
        val shape: Int = GradientDrawable.RECTANGLE,
        val solidColor: Int? = null,
        val gradient: GradientModel? = null,
        val strokeWidthPx: Float = 0f,
        val strokeColor: Int? = null,
        val cornerRadii: FloatArray? = null,
        val widthDp: Float? = null,
        val heightDp: Float? = null,
        val innerRadiusPx: Float? = null,
        val thicknessPx: Float? = null
    ) {
        fun sizePx(density: Float): Pair<Int, Int> {
            val defaultPx = (DEFAULT_SIZE_DP * density).toInt().coerceAtLeast(1)
            val w = (widthDp?.times(density))?.toInt() ?: defaultPx
            val h = (heightDp?.times(density))?.toInt() ?: defaultPx
            return w to h
        }

        fun buildGradientDrawable(density: Float): GradientDrawable = GradientDrawable().apply {
            shape = this@ShapeModel.shape

            if (shape == GradientDrawable.LINE) {
                // LINE is invisible without a stroke — fallback to solid as stroke
                val sw = if (strokeWidthPx > 0f) {
                    (strokeWidthPx * density).toInt().coerceAtLeast(1)
                } else {
                    (3f * density).toInt().coerceAtLeast(1)
                }
                val sc = strokeColor ?: solidColor ?: Color.BLACK
                setStroke(sw, sc)
            } else if (shape == GradientDrawable.RING) {
                applyFill(this, density)
                // GradientDrawable defaults mUseLevelForShape=true with level=0 → 0° sweep.
                // Full ring in preview requires level at max (10000) regardless of useLevel.
                useLevel = false
                level = 10000
                // Parsed dimension values are in dp-like numbers; GradientDrawable wants px.
                if (innerRadiusPx != null) {
                    innerRadius = (innerRadiusPx * density).toInt().coerceAtLeast(0)
                }
                if (thicknessPx != null) {
                    thickness = (thicknessPx * density).toInt().coerceAtLeast(1)
                }
            } else {
                applyFill(this, density)
                val modelRadii = this@ShapeModel.cornerRadii
                if (modelRadii != null) {
                    // Corner radii from XML are in dp; convert to px.
                    cornerRadii = FloatArray(modelRadii.size) { i ->
                        modelRadii[i] * density
                    }
                }
                if (strokeWidthPx > 0f && strokeColor != null) {
                    setStroke(
                        (strokeWidthPx * density).toInt().coerceAtLeast(1),
                        strokeColor
                    )
                }
            }
        }

        private fun applyFill(drawable: GradientDrawable, density: Float) {
            val g = gradient
            if (g != null && g.colors.isNotEmpty()) {
                drawable.gradientType = g.type
                drawable.colors = g.colors
                drawable.setGradientCenter(g.centerX, g.centerY)
                when (g.type) {
                    GradientDrawable.LINEAR_GRADIENT -> {
                        drawable.orientation = angleToOrientation(g.angle)
                    }
                    GradientDrawable.RADIAL_GRADIENT -> {
                        val radiusDp = g.gradientRadiusDp
                        if (radiusDp != null && radiusDp > 0f) {
                            drawable.gradientRadius = radiusDp * density
                        } else {
                            // Platform default is half the shorter side once bounds exist.
                            val (w, h) = sizePx(density)
                            drawable.gradientRadius = minOf(w, h) / 2f
                        }
                    }
                    // SWEEP_GRADIENT: colors + center only
                }
            } else if (solidColor != null) {
                drawable.setColor(solidColor)
            } else if (strokeColor != null && shape == GradientDrawable.RING) {
                drawable.setColor(strokeColor)
            }
        }
    }

    private fun resolveShapeType(xmlContent: String): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xmlContent))
        try {
            var event = parser.eventType
            while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }
            return if (event == XmlPullParser.START_TAG) localName(parser.name) else null
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseShape(xmlContent: String, colorResolver: ProjectColorResolver): ShapeModel {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xmlContent))

        var event = parser.eventType
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
        if (event != XmlPullParser.START_TAG) {
            throw IllegalStateException("No start tag found in shape XML")
        }

        val root = localName(parser.name)
        if (root != "shape") {
            throw IllegalStateException("Expected root <shape>, found <$root>")
        }

        val shapeVal = attr(parser, "shape") ?: "rectangle"
        val shapeInt = mapShape(shapeVal)

        var solidColor: Int? = null
        var gradient: GradientModel? = null
        var strokeWidth: Float? = null
        var strokeColor: Int? = null
        var topLeftRadius: Float? = null
        var topRightRadius: Float? = null
        var bottomLeftRadius: Float? = null
        var bottomRightRadius: Float? = null
        var uniformRadius: Float? = null
        var widthDp: Float? = null
        var heightDp: Float? = null
        // ring attrs live on the root <shape> tag
        val innerRadius = parseDimensionF(attr(parser, "innerRadius"))
        val thickness = parseDimensionF(attr(parser, "thickness"))

        var depth = 1
        while (depth > 0) {
            event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (localName(parser.name)) {
                        TAG_SOLID -> solidColor = parseColor(attr(parser, "color"), colorResolver)
                        TAG_STROKE -> {
                            strokeWidth = parseDimensionF(attr(parser, "width"))
                            strokeColor = parseColor(attr(parser, "color"), colorResolver)
                        }
                        TAG_CORNERS -> {
                            val r = attr(parser, "radius")
                            if (r != null) uniformRadius = parseDimensionF(r)
                            topLeftRadius = parseDimensionF(attr(parser, "topLeftRadius"))
                            topRightRadius = parseDimensionF(attr(parser, "topRightRadius"))
                            bottomLeftRadius = parseDimensionF(attr(parser, "bottomLeftRadius"))
                            bottomRightRadius = parseDimensionF(attr(parser, "bottomRightRadius"))
                        }
                        TAG_SIZE -> {
                            widthDp = parseDimensionDp(attr(parser, "width"))
                            heightDp = parseDimensionDp(attr(parser, "height"))
                        }
                        TAG_PADDING -> { /* V1: skip padding */ }
                        TAG_GRADIENT -> gradient = parseGradientAttrs(parser, colorResolver)
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        val cornerRadii = buildCornerRadii(
            uniformRadius = uniformRadius,
            topLeftRadius = topLeftRadius,
            topRightRadius = topRightRadius,
            bottomLeftRadius = bottomLeftRadius,
            bottomRightRadius = bottomRightRadius
        )

        return ShapeModel(
            shape = shapeInt,
            solidColor = solidColor,
            gradient = gradient,
            strokeWidthPx = strokeWidth ?: 0f,
            strokeColor = strokeColor,
            cornerRadii = cornerRadii,
            widthDp = widthDp,
            heightDp = heightDp,
            innerRadiusPx = innerRadius,
            thicknessPx = thickness
        )
    }

    private fun parseGradientAttrs(
        parser: XmlPullParser,
        resolver: ProjectColorResolver
    ): GradientModel? {
        val start = parseColor(attr(parser, "startColor"), resolver)
        val center = parseColor(attr(parser, "centerColor"), resolver)
        val end = parseColor(attr(parser, "endColor"), resolver)
        val colors = when {
            start != null && center != null && end != null -> intArrayOf(start, center, end)
            start != null && end != null -> intArrayOf(start, end)
            start != null && center != null -> intArrayOf(start, center)
            end != null && center != null -> intArrayOf(center, end)
            start != null -> intArrayOf(start, start)
            end != null -> intArrayOf(end, end)
            center != null -> intArrayOf(center, center)
            else -> return null
        }

        val type = when (attr(parser, "type")?.lowercase()) {
            "radial" -> GradientDrawable.RADIAL_GRADIENT
            "sweep" -> GradientDrawable.SWEEP_GRADIENT
            else -> GradientDrawable.LINEAR_GRADIENT
        }
        val angle = attr(parser, "angle")?.toFloatOrNull() ?: 0f
        val centerX = parseFractionOrFloat(attr(parser, "centerX")) ?: 0.5f
        val centerY = parseFractionOrFloat(attr(parser, "centerY")) ?: 0.5f
        val gradientRadiusDp = parseDimensionF(attr(parser, "gradientRadius"))

        return GradientModel(
            type = type,
            colors = colors,
            angle = angle,
            centerX = centerX,
            centerY = centerY,
            gradientRadiusDp = gradientRadiusDp
        )
    }

    /**
     * Android gradient angle: 0 = left→right, increases counter-clockwise in 45° steps.
     * Maps to [GradientDrawable.Orientation].
     */
    private fun angleToOrientation(angle: Float): GradientDrawable.Orientation {
        var a = angle % 360f
        if (a < 0f) a += 360f
        // Snap to nearest 45° like platform ShapeDrawable inflater.
        val snapped = ((a / 45f).toInt() * 45) % 360
        return when (snapped) {
            0 -> GradientDrawable.Orientation.LEFT_RIGHT
            45 -> GradientDrawable.Orientation.BL_TR
            90 -> GradientDrawable.Orientation.BOTTOM_TOP
            135 -> GradientDrawable.Orientation.BR_TL
            180 -> GradientDrawable.Orientation.RIGHT_LEFT
            225 -> GradientDrawable.Orientation.TR_BL
            270 -> GradientDrawable.Orientation.TOP_BOTTOM
            315 -> GradientDrawable.Orientation.TL_BR
            else -> GradientDrawable.Orientation.LEFT_RIGHT
        }
    }

    private fun mapShape(value: String): Int = when (value.lowercase()) {
        "rectangle" -> GradientDrawable.RECTANGLE
        "oval" -> GradientDrawable.OVAL
        "line" -> GradientDrawable.LINE
        "ring" -> GradientDrawable.RING
        else -> GradientDrawable.RECTANGLE
    }

    /** Fraction (0–1), percent ("50%"), or plain float for gradient center. */
    private fun parseFractionOrFloat(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim()
        return when {
            v.endsWith("%") -> v.dropLast(1).toFloatOrNull()?.div(100f)
            else -> v.toFloatOrNull()
        }
    }

    /**
     * Build corner radius array for [GradientDrawable.cornerRadii].
     * Order: top-left-x, top-left-y, top-right-x, top-right-y,
     *        bottom-right-x, bottom-right-y, bottom-left-x, bottom-left-y
     */
    private fun buildCornerRadii(
        uniformRadius: Float?,
        topLeftRadius: Float?,
        topRightRadius: Float?,
        bottomLeftRadius: Float?,
        bottomRightRadius: Float?
    ): FloatArray? {
        if (uniformRadius != null && topLeftRadius == null) {
            // Uniform radius for all corners
            return floatArrayOf(
                uniformRadius,
                uniformRadius,
                uniformRadius,
                uniformRadius,
                uniformRadius,
                uniformRadius,
                uniformRadius,
                uniformRadius
            )
        }
        val tl = topLeftRadius ?: 0f
        val tr = topRightRadius ?: 0f
        val br = bottomRightRadius ?: 0f
        val bl = bottomLeftRadius ?: 0f
        if (tl == 0f && tr == 0f && br == 0f && bl == 0f) return null
        return floatArrayOf(
            tl,
            tl,
            tr,
            tr,
            br,
            br,
            bl,
            bl
        )
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        parser.getAttributeValue(ANDROID_NS, name)?.let { return it }
        for (i in 0 until parser.attributeCount) {
            val local = parser.getAttributeName(i)?.substringAfterLast(':')
            if (local == name) parser.getAttributeValue(i)?.let { return it }
        }
        return null
    }

    private fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()

    private fun parseDimensionDp(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim()
        return when {
            v.endsWith("dp", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            v.endsWith("dip", ignoreCase = true) -> v.dropLast(3).toFloatOrNull()
            v.endsWith("px", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            v.endsWith("sp", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            else -> v.toFloatOrNull()
        }
    }

    /** Parse a raw dimension value (may be dp or just float). */
    private fun parseDimensionF(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim()
        return when {
            v.endsWith("dp", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            v.endsWith("dip", ignoreCase = true) -> v.dropLast(3).toFloatOrNull()
            v.endsWith("px", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            v.endsWith("mm", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            v.endsWith("pt", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            v.endsWith("sp", ignoreCase = true) -> v.dropLast(2).toFloatOrNull()
            else -> v.toFloatOrNull()
        }
    }

    private fun parseColor(raw: String?, resolver: ProjectColorResolver): Int? =
        resolver.resolve(raw)
}
