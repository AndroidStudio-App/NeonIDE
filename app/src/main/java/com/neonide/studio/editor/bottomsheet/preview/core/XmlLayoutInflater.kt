package com.neonide.studio.editor.bottomsheet.preview.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Direct XML → real View tree inflater for on-device layout preview.
 */
class XmlLayoutInflater(
    private val context: Context,
    private val projectDir: File? = null,
    private val includeDepth: Int = 0
) {
    companion object {
        private const val MAX_EVENTS = 50_000
        private const val MAX_INCLUDE_DEPTH = 5

        // Stable view tags for card surface state (avoid reserved android ids).
        private val CARD_FILL_TAG = 0x7E01_0001
        private val CARD_STROKE_COLOR_TAG = 0x7E01_0002
        private val CARD_STROKE_WIDTH_TAG = 0x7E01_0003
        private val CARD_RADIUS_TAG = 0x7E01_0004
    }

    private var lastTag: String = "(none)"
    private val trace = StringBuilder()
    private val colorResolver by lazy { ProjectColorResolver(projectDir) }
    private val styleCache by lazy { loadProjectStyles() }

    // Tracks corner radius from cornerSize/cornerFamily attrs for backgroundTint to use
    private var pendingCornerRadius: Float = -1f

    /** Maps XML @+id / @id names to generated View ids for free-form ConstraintLayout. */
    private val idRegistry = HashMap<String, Int>()
    private val nextPreviewId = AtomicInteger(0x7000_0000)

    fun lastDebugInfo(): String = "lastTag=$lastTag\n$trace"

    private val viewClassMap: Map<String, Class<out View>> = mapOf(
        "LinearLayout" to LinearLayout::class.java,
        "FrameLayout" to FrameLayout::class.java,
        "ConstraintLayout" to ConstraintLayout::class.java,
        "RelativeLayout" to RelativeLayout::class.java,
        "ScrollView" to ScrollView::class.java,
        "HorizontalScrollView" to android.widget.HorizontalScrollView::class.java,
        "ListView" to android.widget.ListView::class.java,
        "GridView" to android.widget.GridView::class.java,
        "TextView" to TextView::class.java,
        "Button" to android.widget.Button::class.java,
        "EditText" to android.widget.EditText::class.java,
        "ImageView" to ImageView::class.java,
        "CheckBox" to android.widget.CheckBox::class.java,
        "RadioButton" to android.widget.RadioButton::class.java,
        "Switch" to android.widget.Switch::class.java,
        "ProgressBar" to android.widget.ProgressBar::class.java,
        "SeekBar" to android.widget.SeekBar::class.java,
        "Space" to android.widget.Space::class.java,
        "View" to View::class.java,
        "ImageButton" to ImageButton::class.java,
        "RatingBar" to android.widget.RatingBar::class.java,
        "Spinner" to android.widget.Spinner::class.java,
        "TableLayout" to android.widget.TableLayout::class.java,
        "TableRow" to android.widget.TableRow::class.java,
        "Chronometer" to android.widget.Chronometer::class.java,
        "GridLayout" to GridLayout::class.java
    )

    private data class PendingView(val view: View, val layoutAttrs: Map<String, String>)

    fun inflate(xmlContent: String): View {
        trace.clear()
        lastTag = "(none)"
        if (includeDepth == 0) {
            idRegistry.clear()
        }
        // Touch resolvers early so trace shows project resource resolution for debugging.
        val colorCount = try {
            colorResolver.resolve("@color/__probe__")
            // force lazy init via a private walk: resolve any; count via known common name
            if (colorResolver.resolve("@color/colorPrimaryText") != null ||
                colorResolver.resolve("@color/colorPrimary") != null
            ) {
                "hit"
            } else {
                "miss"
            }
        } catch (_: Exception) {
            "err"
        }
        log(
            "inflate start, length=${xmlContent.length} includeDepth=$includeDepth " +
                "projectDir=${projectDir?.absolutePath ?: "(null)"} colors=$colorCount"
        )

        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xmlContent))
        }

        val stack = ArrayDeque<PendingView>()
        var root: View? = null
        var events = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            events++
            if (events > MAX_EVENTS) {
                throw IllegalStateException(
                    "Parser exceeded $MAX_EVENTS events (possible loop). ${lastDebugInfo()}"
                )
            }

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name ?: "unknown"
                    lastTag = tagName
                    val parent = stack.peekLast()?.view as? ViewGroup

                    if (tagName == "include" || tagName.endsWith(".include")) {
                        val includeAttrs = collectAttrs(parser)
                        val included = inflateInclude(includeAttrs)
                        if (included != null) {
                            includeAttrs["id"]?.let { assignViewId(included, it) }
                            if (parent != null) {
                                val lp = buildLayoutParams(parent, includeAttrs, forInclude = true)
                                attachChild(parent, included, lp)
                            } else if (root == null) {
                                included.layoutParams = ViewGroup.LayoutParams(
                                    parseSize(includeAttrs["layout_width"] ?: "match_parent"),
                                    parseSize(includeAttrs["layout_height"] ?: "match_parent")
                                )
                                root = included
                            }
                            // include is a leaf — still push a dummy so END_TAG pops cleanly
                            stack.addLast(PendingView(included, includeAttrs))
                        } else {
                            val placeholder = createMissingIncludePlaceholder(includeAttrs)
                            includeAttrs["id"]?.let { assignViewId(placeholder, it) }
                            if (parent != null) {
                                parent.addView(
                                    placeholder,
                                    buildLayoutParams(parent, includeAttrs, forInclude = true)
                                )
                            } else if (root == null) {
                                root = placeholder
                            }
                            stack.addLast(PendingView(placeholder, includeAttrs))
                        }
                    } else if (tagName == "merge") {
                        // merge only valid as include root; treat as FrameLayout host for preview
                        val layoutAttrs = collectAttrs(parser)
                        val mergeHost = FrameLayout(context).also { host ->
                            if (parent == null) {
                                host.layoutParams = ViewGroup.LayoutParams(
                                    parseSize(layoutAttrs["layout_width"] ?: "match_parent"),
                                    parseSize(layoutAttrs["layout_height"] ?: "match_parent")
                                )
                                root = host
                            }
                        }
                        if (parent != null) {
                            val lp = buildLayoutParams(parent, layoutAttrs)
                            attachChild(parent, mergeHost, lp)
                        }
                        log("START merge -> temporary host")
                        stack.addLast(PendingView(mergeHost, layoutAttrs))
                    } else {
                        val (view, layoutAttrs) = createAndConfigure(parser, tagName)
                        log("START $tagName -> ${view.javaClass.simpleName}")

                        if (parent != null) {
                            val lp = buildLayoutParams(parent, layoutAttrs)
                            attachChild(parent, view, lp)
                        } else {
                            view.layoutParams = ViewGroup.LayoutParams(
                                parseSize(layoutAttrs["layout_width"] ?: "match_parent"),
                                parseSize(layoutAttrs["layout_height"] ?: "match_parent")
                            )
                            root = view
                        }
                        stack.addLast(PendingView(view, layoutAttrs))
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (stack.isNotEmpty()) {
                        val closed = stack.removeLast()
                        log("END ${closed.view.javaClass.simpleName} stack=${stack.size}")
                    }
                }
            }
            eventType = parser.next()
        }

        log("inflate done root=${root?.javaClass?.simpleName} events=$events")
        return root ?: TextView(context).apply { text = "(empty layout)" }
    }

    private fun attachChild(parent: ViewGroup, child: View, lp: ViewGroup.LayoutParams) {
        // If child came from include and already has a parent, reparent safely
        (child.parent as? ViewGroup)?.removeView(child)
        parent.addView(child, lp)
    }

    private fun collectAttrs(parser: XmlPullParser): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        for (i in 0 until parser.attributeCount) {
            val rawName = parser.getAttributeName(i) ?: continue
            val name = rawName.substringAfterLast(':')
            val raw = parser.getAttributeValue(i) ?: continue
            attrs[name] = raw
        }
        // also capture unqualified "layout" attribute used by <include layout="..."/>
        for (i in 0 until parser.attributeCount) {
            val rawName = parser.getAttributeName(i) ?: continue
            if (rawName == "layout" || rawName.endsWith(":layout")) {
                attrs["layout"] = parser.getAttributeValue(i) ?: continue
            }
        }
        return attrs
    }

    private fun inflateInclude(attrs: Map<String, String>): View? {
        val ref = attrs["layout"] ?: return null
        if (includeDepth >= MAX_INCLUDE_DEPTH) {
            log("include depth exceeded for $ref")
            return null
        }
        val name = ref.substringAfterLast('/').removePrefix("@layout/")
            .removePrefix("layout/")
        if (name.isBlank()) return null

        val file = resolveLayoutFile(name) ?: run {
            log("include not found: $ref")
            return null
        }
        return try {
            val xml = file.readText()
            log("include resolving $ref -> ${file.absolutePath}")
            XmlLayoutInflater(context, projectDir, includeDepth + 1).inflate(xml)
        } catch (e: Exception) {
            log("include failed $ref: ${e.message}")
            null
        }
    }

    private fun resolveLayoutFile(layoutName: String): File? {
        val dir = projectDir ?: return null
        val candidates = listOf(
            File(dir, "app/src/main/res/layout/$layoutName.xml"),
            File(dir, "src/main/res/layout/$layoutName.xml"),
            File(dir, "res/layout/$layoutName.xml")
        )
        candidates.firstOrNull { it.isFile }?.let { return it }

        // Walk a shallow search under project for res/layout/<name>.xml
        val target = "$layoutName.xml"
        return dir.walkTopDown()
            .maxDepth(12)
            .firstOrNull { file ->
                file.isFile &&
                    file.name == target &&
                    file.parentFile?.name == "layout" &&
                    file.parentFile?.parentFile?.name?.startsWith("res") == true
            }
    }

    private fun createMissingIncludePlaceholder(attrs: Map<String, String>): View {
        val ref = attrs["layout"] ?: "@layout/?"
        return TextView(context).apply {
            text = "[include $ref]"
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFE8E8E8.toInt())
        }
    }

    private fun resolveImageDrawable(view: ImageView, ref: String) {
        // Handle @android:drawable/ via framework resources
        if (ref.startsWith("@android:drawable/")) {
            val name = ref.removePrefix("@android:drawable/")
            val id = resolveAndroidDrawableId(name)
            if (id != null) {
                try {
                    val drawable = context.resources.getDrawable(id, context.theme)
                    view.setImageDrawable(drawable)
                } catch (_: Exception) {
                    view.setImageResource(id)
                }
            }
            return
        }
        val bitmap = resolveDrawableBitmap(ref) ?: return
        view.setImageBitmap(bitmap)
    }

    private fun resolveAndroidDrawableId(name: String): Int? = try {
        val fields = android.R.drawable::class.java.fields
        val field = fields.firstOrNull { it.name == name }
        field?.getInt(null)
    } catch (_: Exception) {
        null
    }

    private fun resolveDrawableBackground(view: View, ref: String) {
        val dir = projectDir ?: return
        val name = when {
            ref.startsWith("@drawable/") -> ref.removePrefix("@drawable/")
            ref.startsWith("@+drawable/") -> ref.removePrefix("@+drawable/")
            else -> return
        }.substringBefore('/')
        if (name.isBlank()) return

        // For <shape> drawables use a stretchable GradientDrawable, not a fixed Bitmap
        val xmlFile = resolveDrawableFile(name, "$name.xml", dir)
        if (xmlFile != null) {
            try {
                val xml = xmlFile.readText()
                val rootTag = detectRootTag(xml)?.substringAfterLast(':').orEmpty()
                if (rootTag == "shape") {
                    val gd = ShapeDrawablePreview.buildDrawable(
                        xml,
                        context.resources.displayMetrics.density,
                        projectDir
                    )
                    if (gd != null) {
                        view.background = gd
                        return
                    }
                }
            } catch (_: Exception) {
            }
        }

        val bitmap = resolveDrawableBitmap(ref)
        if (bitmap != null) {
            view.background = BitmapDrawable(context.resources, bitmap)
        }
    }

    private fun resolveDrawableBitmap(ref: String): Bitmap? {
        val dir = projectDir ?: return null
        val name = when {
            ref.startsWith("@drawable/") -> ref.removePrefix("@drawable/")
            ref.startsWith("@+drawable/") -> ref.removePrefix("@+drawable/")
            else -> return null
        }.substringBefore('/')
        if (name.isBlank()) return null
        val fileName = "$name.xml"
        val xmlFile = resolveDrawableFile(name, fileName, dir)
        if (xmlFile != null) {
            try {
                val xml = xmlFile.readText()
                val rootTag = detectRootTag(xml)?.substringAfterLast(':').orEmpty()
                return when (rootTag) {
                    "shape" -> ShapeDrawablePreview.render(context, xml, projectDir)
                    "vector" -> VectorDrawablePreview.render(context, xml, projectDir)
                    else -> null
                }
            } catch (_: Exception) {
                return null
            }
        }
        // Try PNG / JPG / WebP bitmap files
        val imageExtensions = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")
        for (ext in imageExtensions) {
            val imgFile = resolveDrawableFile(name, "$name$ext", dir)
            if (imgFile != null) {
                try {
                    val input = java.io.FileInputStream(imgFile)
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    return bmp
                } catch (_: Exception) {
                    return null
                }
            }
        }
        return null
    }

    private fun resolveDrawableFile(name: String, fileName: String, dir: File): File? {
        val candidates = listOf(
            File(dir, "app/src/main/res/drawable/$fileName"),
            File(dir, "src/main/res/drawable/$fileName"),
            File(dir, "res/drawable/$fileName")
        )
        candidates.firstOrNull { it.isFile }?.let { return it }
        for (resDir in listOf(
            File(dir, "app/src/main/res"),
            File(dir, "src/main/res"),
            File(dir, "res")
        )) {
            if (!resDir.isDirectory) continue
            resDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("drawable") }
                .orEmpty()
                .forEach { d ->
                    val f = File(d, fileName)
                    if (f.isFile) return f
                }
        }
        return dir.walkTopDown()
            .maxDepth(14)
            .firstOrNull { file ->
                file.isFile &&
                    file.name == fileName &&
                    file.parentFile?.name?.startsWith("drawable") == true &&
                    file.parentFile?.parentFile?.name?.startsWith("res") == true
            }
    }

    /** Cheap non-IO pull-parser root tag detection. */
    private fun detectRootTag(xmlContent: String): String? {
        var i = 0
        val s = xmlContent
        val n = s.length
        while (i < n) {
            val lt = s.indexOf('<', i)
            if (lt < 0) return null
            if (lt + 1 < n && (s[lt + 1] == '?' || s[lt + 1] == '!')) {
                i = lt + 1
                continue
            }
            var end = lt + 1
            while (end < n) {
                val ch = s[end]
                if (ch == ' ' ||
                    ch == '>' ||
                    ch == '/' ||
                    ch == '\n' ||
                    ch == '\r' ||
                    ch == '\t'
                ) {
                    break
                }
                end++
            }
            return s.substring(lt + 1, end)
        }
        return null
    }

    private fun createAndConfigure(
        parser: XmlPullParser,
        tagName: String
    ): Pair<View, Map<String, String>> {
        val view = createView(tagName)
        val layoutAttrs = mutableMapOf<String, String>()
        val viewAttrs = mutableMapOf<String, String>()
        var styleRef: String? = null

        for (i in 0 until parser.attributeCount) {
            val rawName = parser.getAttributeName(i) ?: continue
            val name = rawName.substringAfterLast(':')
            val raw = parser.getAttributeValue(i) ?: continue
            if (name == "id") {
                assignViewId(view, raw)
                continue
            }
            if (name == "style") {
                styleRef = raw
                continue
            }
            if (name.startsWith("layout_") || name == "minWidth" || name == "minHeight") {
                layoutAttrs[name] = raw
            } else {
                viewAttrs[name] = raw
            }
        }

        // Merge style attrs (lower priority — inline attrs override)
        if (styleRef != null) {
            val styleAttrs = resolveStyle(styleRef)
            if (styleAttrs != null) {
                log("style resolved $styleRef -> ${styleAttrs.size} attrs")
                // Apply style attrs first, then inline overrides
                applyViewAttributes(view, styleAttrs)
            }
        }

        applyViewAttributes(view, viewAttrs)
        layoutAttrs["minWidth"]?.let {
            view.minimumWidth = parseDimension(it, 0f).toInt()
        }
        layoutAttrs["minHeight"]?.let {
            view.minimumHeight = parseDimension(it, 0f).toInt()
        }
        return view to layoutAttrs
    }

    private fun createView(tagName: String): View {
        val simpleName = tagName.substringAfterLast('.')

        aliasView(tagName, simpleName)?.let { clazz ->
            log("alias $tagName -> ${clazz.simpleName}")
            return clazz.getConstructor(Context::class.java).newInstance(context).also {
                configureAliasedView(it, simpleName)
            }
        }

        if (tagName == "fragment" || simpleName == "fragment") {
            log("placeholder tag: fragment")
            return TextView(context).apply {
                text = "[fragment]"
                setTextColor(0xFF666666.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                setBackgroundColor(0xFFF5F5F5.toInt())
                setPadding(24, 48, 24, 48)
                minimumHeight = (120 * context.resources.displayMetrics.density).toInt()
            }
        }

        if (tagName == "include" ||
            simpleName == "include" ||
            tagName == "merge" ||
            simpleName == "merge"
        ) {
            return View(context)
        }

        // Skip resource-only tags as inert views
        if (simpleName in setOf(
                "path", "item", "aapt:attr", "gradient", "group", "clip-path",
                "background", "foreground", "vector"
            )
        ) {
            return View(context).apply {
                visibility = View.GONE
            }
        }

        viewClassMap[simpleName]?.let { clazz ->
            return clazz.getConstructor(Context::class.java).newInstance(context)
        }

        if (tagName.startsWith("android.")) {
            try {
                val fullClass = Class.forName(tagName).asSubclass(View::class.java)
                return fullClass.getConstructor(Context::class.java).newInstance(context)
            } catch (_: Exception) {
            }
        }

        log("unknown tag placeholder: $tagName")
        return TextView(context).apply {
            text = "[$simpleName]"
            setTextColor(0xFF888888.toInt())
            textSize = 10f
            setPadding(8, 8, 8, 8)
        }
    }

    private fun configureAliasedView(view: View, simpleName: String) {
        when (simpleName) {
            "CoordinatorLayout" -> {
                view.setBackgroundColor(0x00000000.toInt())
            }
            "AppBarLayout" -> {
                (view as? LinearLayout)?.orientation = LinearLayout.VERTICAL
                view.setBackgroundColor(0xFF6200EE.toInt())
            }
            "Toolbar", "MaterialToolbar" -> {
                if (view is TextView) {
                    view.gravity = Gravity.CENTER_VERTICAL
                    view.setTextColor(0xFFFFFFFF.toInt())
                    view.textSize = 18f
                    view.setPadding(32, 24, 32, 24)
                    view.minimumHeight =
                        (56 * context.resources.displayMetrics.density).toInt()
                    // Title is often set in code / manifest, not XML — leave empty.
                }
            }
            "CardView", "MaterialCardView" -> {
                // Default fill (surface + outline). Transparent FrameLayout + white text
                // on white canvas renders as empty. Material surfaces vary by theme —
                // use a mid surface so both light/dark text stay legible in preview.
                // Override via cardBackgroundColor / background in XML.
                val density = context.resources.displayMetrics.density
                val defaultFill = if (simpleName == "MaterialCardView") {
                    0xFF455A64.toInt()
                } else {
                    0xFFFFFFFF.toInt()
                }
                applyCardBackground(
                    view,
                    color = defaultFill,
                    cornerRadiusPx = 4f * density,
                    strokeColor = 0xFFBDBDBD.toInt(),
                    strokeWidthPx = (1f * density).toInt().coerceAtLeast(1)
                )
                view.elevation = 2f * density
                view.clipToOutline = true
            }
            "FloatingActionButton" -> {
                if (view is ImageButton) {
                    view.setImageResource(android.R.drawable.ic_input_add)
                    view.setBackgroundColor(0xFF03DAC5.toInt())
                    val size = (56 * context.resources.displayMetrics.density).toInt()
                    view.minimumWidth = size
                    view.minimumHeight = size
                }
            }
            "BottomNavigationView", "NavigationView" -> {
                if (view is LinearLayout) {
                    view.orientation = LinearLayout.HORIZONTAL
                    view.gravity = Gravity.CENTER
                    view.setBackgroundColor(0xFFFAFAFA.toInt())
                    view.minimumHeight =
                        (56 * context.resources.displayMetrics.density).toInt()
                    val label = TextView(context).apply {
                        text = if (simpleName == "NavigationView") "Navigation" else "BottomNav"
                        setTextColor(0xFF444444.toInt())
                        gravity = Gravity.CENTER
                    }
                    view.addView(
                        label,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }
    }

    private fun aliasView(tagName: String, simpleName: String): Class<out View>? = when {
        tagName.startsWith("androidx.coordinatorlayout") ||
            simpleName == "CoordinatorLayout" -> FrameLayout::class.java

        // Real ConstraintLayout (androidx) for free-form constraint solving
        tagName.startsWith("androidx.constraintlayout.widget.ConstraintLayout") ||
            (
                tagName.startsWith("androidx.constraintlayout") &&
                    simpleName == "ConstraintLayout"
                ) ||
            simpleName == "ConstraintLayout" -> ConstraintLayout::class.java

        tagName.startsWith("androidx.drawerlayout") ||
            simpleName == "DrawerLayout" -> FrameLayout::class.java

        // MaterialCardView / CardView: FrameLayout + GradientDrawable card look.
        // Instantiating real MaterialCardView without a Material theme context crashes.
        tagName.startsWith("androidx.cardview") ||
            simpleName == "CardView" ||
            simpleName == "MaterialCardView" ||
            tagName.contains("MaterialCardView") -> FrameLayout::class.java

        tagName.startsWith("androidx.recyclerview") ||
            simpleName == "RecyclerView" -> FrameLayout::class.java

        tagName.startsWith("androidx.swiperefreshlayout") -> FrameLayout::class.java

        tagName.startsWith("androidx.viewpager") -> FrameLayout::class.java

        tagName.startsWith("androidx.appcompat.widget.Toolbar") ||
            simpleName == "Toolbar" ||
            simpleName == "MaterialToolbar" -> TextView::class.java

        tagName.contains("AppBarLayout") || simpleName == "AppBarLayout" ->
            LinearLayout::class.java

        tagName.contains("FloatingActionButton") || simpleName == "FloatingActionButton" ->
            ImageButton::class.java

        tagName.contains("MaterialButton") || simpleName == "MaterialButton" ->
            android.widget.Button::class.java

        tagName.contains("TextInputEditText") -> android.widget.EditText::class.java

        tagName.contains("TextInputLayout") -> LinearLayout::class.java

        tagName.contains("BottomNavigationView") ||
            tagName.contains("NavigationView") ||
            simpleName == "BottomNavigationView" ||
            simpleName == "NavigationView" -> LinearLayout::class.java

        tagName.contains("TabLayout") || simpleName == "TabLayout" -> LinearLayout::class.java

        tagName.startsWith("com.google.android.material") -> {
            when (simpleName) {
                "ShapeableImageView" -> ImageView::class.java
                "MaterialButton" -> android.widget.Button::class.java
                "TextInputEditText" -> android.widget.EditText::class.java
                "TextInputLayout" -> LinearLayout::class.java
                "BottomNavigationView", "NavigationView" -> LinearLayout::class.java
                "TabLayout" -> LinearLayout::class.java
                "FloatingActionButton" -> ImageButton::class.java
                "AppBarLayout" -> LinearLayout::class.java
                "Toolbar", "MaterialToolbar" -> TextView::class.java
                else -> FrameLayout::class.java
            }
        }

        tagName.startsWith("androidx.appcompat") -> {
            when (simpleName) {
                "AppCompatTextView" -> TextView::class.java
                "AppCompatButton" -> android.widget.Button::class.java
                "AppCompatEditText", "AppCompatAutoCompleteTextView" ->
                    android.widget.EditText::class.java
                "AppCompatImageView" -> ImageView::class.java
                "AppCompatImageButton" -> ImageButton::class.java
                "AppCompatCheckBox" -> android.widget.CheckBox::class.java
                "AppCompatRadioButton" -> android.widget.RadioButton::class.java
                else -> FrameLayout::class.java
            }
        }

        else -> null
    }

    private fun applyViewAttributes(view: View, attrs: Map<String, String>) {
        // Corner attrs must be known before backgroundTint regardless of XML/map order.
        pendingCornerRadius = -1f
        attrs["cornerSize"]?.let { raw ->
            pendingCornerRadius = parseCornerRadius(raw)
        }
        if (pendingCornerRadius < 0f) {
            val family = attrs["cornerFamily"]
            if (family != null && family.contains("rounded", ignoreCase = true)) {
                pendingCornerRadius =
                    20f * context.resources.displayMetrics.density
            }
        }

        attrs.forEach { (name, raw) ->
            try {
                when (name) {
                    "text" -> if (view is TextView) view.text = resolveString(raw)
                    "textSize" -> if (view is TextView) {
                        view.textSize = parseDimension(raw, 14f) /
                            context.resources.displayMetrics.scaledDensity
                    }
                    "textColor" -> if (view is TextView) {
                        val color = parseColor(raw)
                        view.setTextColor(color)
                        log("textColor $raw -> #${Integer.toHexString(color)}")
                    }
                    "textStyle" -> if (view is TextView && raw.contains("bold")) {
                        view.setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    "textAllCaps" -> if (view is TextView) {
                        view.isAllCaps = raw == "true"
                    }
                    "backgroundTint" -> applyBackgroundTint(view, raw)
                    // pre-read above; still apply after tint if only corners set
                    "cornerSize", "cornerFamily" -> applyViewCorners(view)
                    "hint" -> if (view is android.widget.EditText) {
                        view.hint = resolveString(raw)
                    }
                    "src", "srcCompat" -> if (view is ImageView) {
                        if (raw.startsWith("@drawable/") ||
                            raw.startsWith("@+drawable/") ||
                            raw.startsWith("@android:drawable/")
                        ) {
                            resolveImageDrawable(view, raw)
                        } else {
                            view.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                    "visibility" -> view.visibility = parseVisibility(raw)
                    "enabled" -> view.isEnabled = raw != "false"
                    "alpha" -> view.alpha = raw.toFloatOrNull() ?: 1f
                    "elevation", "cardElevation" -> {
                        view.elevation = parseDimension(raw, 0f)
                    }
                    "cardCornerRadius" -> {
                        val radius = parseDimension(raw, 0f)
                        pendingCornerRadius = radius
                        applyCardBackground(
                            view,
                            color = cardFillColor(view),
                            cornerRadiusPx = radius
                        )
                    }
                    "cardBackgroundColor" -> {
                        val color = parseColor(raw)
                        val density = context.resources.displayMetrics.density
                        val storedRadius = view.getTag(CARD_RADIUS_TAG) as? Float
                        val radius = when {
                            pendingCornerRadius > 0f -> pendingCornerRadius
                            storedRadius != null && storedRadius > 0f -> storedRadius
                            else -> 4f * density
                        }
                        applyCardBackground(view, color = color, cornerRadiusPx = radius)
                    }
                    "cardUseCompatPadding" -> {
                        // No-op visual approx for preview measure
                    }
                    "contentPadding", "cardContentPadding" -> {
                        val p = parseDimension(raw, 0f).toInt()
                        view.setPadding(p, p, p, p)
                    }
                    "contentPaddingLeft" -> view.setPadding(
                        parseDimension(raw, 0f).toInt(),
                        view.paddingTop,
                        view.paddingRight,
                        view.paddingBottom
                    )
                    "contentPaddingTop" -> view.setPadding(
                        view.paddingLeft,
                        parseDimension(raw, 0f).toInt(),
                        view.paddingRight,
                        view.paddingBottom
                    )
                    "contentPaddingRight" -> view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        parseDimension(raw, 0f).toInt(),
                        view.paddingBottom
                    )
                    "contentPaddingBottom" -> view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        parseDimension(raw, 0f).toInt()
                    )
                    "rotation" -> view.rotation = raw.toFloatOrNull() ?: 0f
                    "padding" -> {
                        val p = parseDimension(raw, 0f).toInt()
                        view.setPadding(p, p, p, p)
                    }
                    "paddingLeft", "paddingStart" -> view.setPadding(
                        parseDimension(raw, 0f).toInt(),
                        view.paddingTop,
                        view.paddingRight,
                        view.paddingBottom
                    )
                    "paddingRight", "paddingEnd" -> view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        parseDimension(raw, 0f).toInt(),
                        view.paddingBottom
                    )
                    "paddingTop" -> view.setPadding(
                        view.paddingLeft,
                        parseDimension(raw, 0f).toInt(),
                        view.paddingRight,
                        view.paddingBottom
                    )
                    "paddingBottom" -> view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        parseDimension(raw, 0f).toInt()
                    )
                    "gravity" -> {
                        val g = parseGravity(raw)
                        if (view is TextView) {
                            view.gravity = g
                        } else if (view is LinearLayout) {
                            view.gravity = g
                        } else {
                            try {
                                view.javaClass.getMethod("setGravity", Int::class.java)
                                    .invoke(view, g)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    "orientation" -> if (view is LinearLayout) {
                        view.orientation = if (raw == "horizontal") {
                            LinearLayout.HORIZONTAL
                        } else {
                            LinearLayout.VERTICAL
                        }
                    }
                    "background" -> {
                        if (raw.startsWith("#") ||
                            raw.contains("color", true) ||
                            raw.contains("white", true) ||
                            raw.contains("black", true) ||
                            raw.contains("transparent", true)
                        ) {
                            try {
                                view.setBackgroundColor(parseColor(raw))
                            } catch (_: Exception) {
                            }
                        } else if (raw.startsWith("@drawable/") ||
                            raw.startsWith("@+drawable/")
                        ) {
                            resolveDrawableBackground(view, raw)
                        }
                    }
                    "maxLines" -> if (view is TextView) {
                        view.maxLines = raw.toIntOrNull() ?: Int.MAX_VALUE
                    }
                    "singleLine" -> if (view is TextView) {
                        view.maxLines = if (raw == "true") 1 else Int.MAX_VALUE
                    }
                    "lines" -> if (view is TextView) {
                        val lines = raw.toIntOrNull() ?: 1
                        view.minLines = lines
                        view.maxLines = lines
                    }
                    "scaleType" -> if (view is ImageView) {
                        try {
                            view.scaleType = ImageView.ScaleType.valueOf(
                                raw.uppercase().replace("-", "_")
                            )
                        } catch (_: Exception) {
                        }
                    }
                    "contentDescription" -> view.contentDescription = raw
                    "title" -> if (view is TextView && view.text.isNullOrEmpty()) {
                        view.text = resolveString(raw)
                    }
                    "columnCount" -> if (view is GridLayout) {
                        view.columnCount = raw.toIntOrNull() ?: 1
                    }
                    "rowCount" -> if (view is GridLayout) {
                        view.rowCount = raw.toIntOrNull() ?: 1
                    }
                    "useDefaultMargins" -> if (view is GridLayout) {
                        view.useDefaultMargins = raw == "true"
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun buildLayoutParams(
        parent: ViewGroup,
        attrs: Map<String, String>,
        forInclude: Boolean = false
    ): ViewGroup.LayoutParams {
        val rawW = attrs["layout_width"] ?: if (forInclude) "match_parent" else "wrap_content"
        val rawH = attrs["layout_height"] ?: if (forInclude) "match_parent" else "wrap_content"
        val w = parseSize(rawW, parent)
        val h = parseSize(rawH, parent)

        val newLp: ViewGroup.MarginLayoutParams = when (parent) {
            is ConstraintLayout -> ConstraintLayout.LayoutParams(w, h)
            is LinearLayout -> LinearLayout.LayoutParams(w, h)
            is FrameLayout -> FrameLayout.LayoutParams(w, h)
            is RelativeLayout -> RelativeLayout.LayoutParams(w, h)
            else -> ViewGroup.MarginLayoutParams(w, h)
        }

        attrs.forEach { (name, raw) ->
            try {
                when (name) {
                    "layout_margin" -> {
                        val m = parseDimension(raw, 0f).toInt()
                        newLp.setMargins(m, m, m, m)
                    }
                    "layout_marginLeft", "layout_marginStart" ->
                        newLp.leftMargin = parseDimension(raw, 0f).toInt()
                    "layout_marginRight", "layout_marginEnd" ->
                        newLp.rightMargin = parseDimension(raw, 0f).toInt()
                    "layout_marginTop" ->
                        newLp.topMargin = parseDimension(raw, 0f).toInt()
                    "layout_marginBottom" ->
                        newLp.bottomMargin = parseDimension(raw, 0f).toInt()
                    "layout_weight" -> if (newLp is LinearLayout.LayoutParams) {
                        newLp.weight = raw.toFloatOrNull() ?: 0f
                    }
                    "layout_gravity" -> applyGravity(newLp, parseGravity(raw))
                }
            } catch (_: Exception) {
            }
        }

        if (newLp is ConstraintLayout.LayoutParams) {
            applyConstraintAttrs(newLp, attrs)
        }
        if (newLp is RelativeLayout.LayoutParams) {
            applyRelativeLayoutAttrs(newLp, attrs)
        }

        return newLp
    }

    private fun applyRelativeLayoutAttrs(
        lp: RelativeLayout.LayoutParams,
        attrs: Map<String, String>
    ) {
        fun ruleToId(raw: String?): Int {
            if (raw.isNullOrBlank()) return 0
            val name = parseIdName(raw) ?: return 0
            return obtainId(name)
        }
        fun boolRule(key: String, rule: Int) {
            if (attrs[key] == "true") {
                lp.addRule(rule)
            }
        }
        attrs["layout_below"]?.let { lp.addRule(RelativeLayout.BELOW, ruleToId(it)) }
        attrs["layout_above"]?.let { lp.addRule(RelativeLayout.ABOVE, ruleToId(it)) }
        attrs["layout_toLeftOf"]?.let { lp.addRule(RelativeLayout.LEFT_OF, ruleToId(it)) }
        attrs["layout_toStartOf"]?.let { lp.addRule(RelativeLayout.START_OF, ruleToId(it)) }
        attrs["layout_toRightOf"]?.let { lp.addRule(RelativeLayout.RIGHT_OF, ruleToId(it)) }
        attrs["layout_toEndOf"]?.let { lp.addRule(RelativeLayout.END_OF, ruleToId(it)) }
        attrs["layout_alignTop"]?.let { lp.addRule(RelativeLayout.ALIGN_TOP, ruleToId(it)) }
        attrs["layout_alignBottom"]?.let { lp.addRule(RelativeLayout.ALIGN_BOTTOM, ruleToId(it)) }
        attrs["layout_alignLeft"]?.let { lp.addRule(RelativeLayout.ALIGN_LEFT, ruleToId(it)) }
        attrs["layout_alignStart"]?.let { lp.addRule(RelativeLayout.ALIGN_START, ruleToId(it)) }
        attrs["layout_alignRight"]?.let { lp.addRule(RelativeLayout.ALIGN_RIGHT, ruleToId(it)) }
        attrs["layout_alignEnd"]?.let { lp.addRule(RelativeLayout.ALIGN_END, ruleToId(it)) }
        attrs["layout_alignBaseline"]?.let {
            lp.addRule(RelativeLayout.ALIGN_BASELINE, ruleToId(it))
        }
        boolRule("layout_alignParentTop", RelativeLayout.ALIGN_PARENT_TOP)
        boolRule("layout_alignParentBottom", RelativeLayout.ALIGN_PARENT_BOTTOM)
        boolRule("layout_alignParentLeft", RelativeLayout.ALIGN_PARENT_LEFT)
        boolRule("layout_alignParentStart", RelativeLayout.ALIGN_PARENT_START)
        boolRule("layout_alignParentRight", RelativeLayout.ALIGN_PARENT_RIGHT)
        boolRule("layout_alignParentEnd", RelativeLayout.ALIGN_PARENT_END)
        boolRule("layout_centerInParent", RelativeLayout.CENTER_IN_PARENT)
        boolRule("layout_centerHorizontal", RelativeLayout.CENTER_HORIZONTAL)
        boolRule("layout_centerVertical", RelativeLayout.CENTER_VERTICAL)
    }

    private fun assignViewId(view: View, rawId: String) {
        val name = parseIdName(rawId) ?: return
        val id = obtainId(name)
        view.id = id
    }

    private fun parseIdName(raw: String): String? {
        val trimmed = raw.trim()
        return when {
            trimmed == "parent" || trimmed == "0" -> null
            trimmed.startsWith("@+id/") -> trimmed.removePrefix("@+id/")
            trimmed.startsWith("@id/") -> trimmed.removePrefix("@id/")
            trimmed.startsWith("@android:id/") -> trimmed.removePrefix("@android:id/")
            trimmed.startsWith("@") -> trimmed.substringAfterLast('/')
            else -> trimmed.takeIf { it.isNotBlank() }
        }
    }

    private fun obtainId(name: String): Int = idRegistry.getOrPut(name) {
        nextPreviewId.getAndIncrement()
    }

    private fun resolveConstraintTarget(raw: String?): Int {
        if (raw.isNullOrBlank()) return ConstraintLayout.LayoutParams.UNSET
        val trimmed = raw.trim()
        if (trimmed == "parent" ||
            trimmed == "0" ||
            trimmed == "@id/parent" ||
            trimmed.endsWith("/parent")
        ) {
            return ConstraintLayout.LayoutParams.PARENT_ID
        }
        val name = parseIdName(trimmed) ?: return ConstraintLayout.LayoutParams.UNSET
        return obtainId(name)
    }

    private fun applyConstraintAttrs(
        lp: ConstraintLayout.LayoutParams,
        attrs: Map<String, String>
    ) {
        attrs["layout_constraintLeft_toLeftOf"]?.let {
            lp.leftToLeft = resolveConstraintTarget(it)
        }
        attrs["layout_constraintLeft_toRightOf"]?.let {
            lp.leftToRight = resolveConstraintTarget(it)
        }
        attrs["layout_constraintRight_toLeftOf"]?.let {
            lp.rightToLeft = resolveConstraintTarget(it)
        }
        attrs["layout_constraintRight_toRightOf"]?.let {
            lp.rightToRight = resolveConstraintTarget(it)
        }
        attrs["layout_constraintTop_toTopOf"]?.let {
            lp.topToTop = resolveConstraintTarget(it)
        }
        attrs["layout_constraintTop_toBottomOf"]?.let {
            lp.topToBottom = resolveConstraintTarget(it)
        }
        attrs["layout_constraintBottom_toTopOf"]?.let {
            lp.bottomToTop = resolveConstraintTarget(it)
        }
        attrs["layout_constraintBottom_toBottomOf"]?.let {
            lp.bottomToBottom = resolveConstraintTarget(it)
        }
        attrs["layout_constraintStart_toStartOf"]?.let {
            lp.startToStart = resolveConstraintTarget(it)
        }
        attrs["layout_constraintStart_toEndOf"]?.let {
            lp.startToEnd = resolveConstraintTarget(it)
        }
        attrs["layout_constraintEnd_toStartOf"]?.let {
            lp.endToStart = resolveConstraintTarget(it)
        }
        attrs["layout_constraintEnd_toEndOf"]?.let {
            lp.endToEnd = resolveConstraintTarget(it)
        }
        attrs["layout_constraintBaseline_toBaselineOf"]?.let {
            lp.baselineToBaseline = resolveConstraintTarget(it)
        }
        attrs["layout_constraintBaseline_toTopOf"]?.let {
            lp.baselineToTop = resolveConstraintTarget(it)
        }
        attrs["layout_constraintBaseline_toBottomOf"]?.let {
            lp.baselineToBottom = resolveConstraintTarget(it)
        }
        attrs["layout_constraintCircle"]?.let {
            lp.circleConstraint = resolveConstraintTarget(it)
        }
        attrs["layout_constraintCircleRadius"]?.let {
            lp.circleRadius = parseDimension(it, 0f).toInt()
        }
        attrs["layout_constraintCircleAngle"]?.let {
            lp.circleAngle = it.toFloatOrNull() ?: 0f
        }
        attrs["layout_constraintHorizontal_bias"]?.let {
            lp.horizontalBias = it.toFloatOrNull() ?: 0.5f
        }
        attrs["layout_constraintVertical_bias"]?.let {
            lp.verticalBias = it.toFloatOrNull() ?: 0.5f
        }
        attrs["layout_constraintDimensionRatio"]?.let {
            lp.dimensionRatio = it
        }
        attrs["layout_constraintHorizontal_weight"]?.let {
            lp.horizontalWeight = it.toFloatOrNull() ?: 0f
        }
        attrs["layout_constraintVertical_weight"]?.let {
            lp.verticalWeight = it.toFloatOrNull() ?: 0f
        }
        attrs["layout_constraintHorizontal_chainStyle"]?.let {
            lp.horizontalChainStyle = parseChainStyle(it)
        }
        attrs["layout_constraintVertical_chainStyle"]?.let {
            lp.verticalChainStyle = parseChainStyle(it)
        }
        attrs["layout_constrainedWidth"]?.let {
            lp.constrainedWidth = it == "true"
        }
        attrs["layout_constrainedHeight"]?.let {
            lp.constrainedHeight = it == "true"
        }
        attrs["layout_goneMarginLeft"]?.let {
            lp.goneLeftMargin = parseDimension(it, 0f).toInt()
        }
        attrs["layout_goneMarginTop"]?.let {
            lp.goneTopMargin = parseDimension(it, 0f).toInt()
        }
        attrs["layout_goneMarginRight"]?.let {
            lp.goneRightMargin = parseDimension(it, 0f).toInt()
        }
        attrs["layout_goneMarginBottom"]?.let {
            lp.goneBottomMargin = parseDimension(it, 0f).toInt()
        }
        attrs["layout_goneMarginStart"]?.let {
            lp.goneStartMargin = parseDimension(it, 0f).toInt()
        }
        attrs["layout_goneMarginEnd"]?.let {
            lp.goneEndMargin = parseDimension(it, 0f).toInt()
        }
    }

    private fun parseChainStyle(raw: String): Int = when (raw.trim().lowercase()) {
        "spread_inside" -> ConstraintLayout.LayoutParams.CHAIN_SPREAD_INSIDE
        "packed" -> ConstraintLayout.LayoutParams.CHAIN_PACKED
        else -> ConstraintLayout.LayoutParams.CHAIN_SPREAD
    }

    private fun applyGravity(lp: ViewGroup.MarginLayoutParams, grav: Int) {
        when (lp) {
            is LinearLayout.LayoutParams -> lp.gravity = grav
            is FrameLayout.LayoutParams -> lp.gravity = grav
            else -> {
                try {
                    lp.javaClass.getMethod("setGravity", Int::class.java).invoke(lp, grav)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun resolveString(raw: String): String {
        if (raw.startsWith("@")) {
            return raw.substringAfterLast('/')
        }
        return raw
    }

    private fun parseSize(raw: String, parent: ViewGroup? = null): Int = when {
        raw == "match_parent" || raw == "fill_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
        raw == "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
        // ConstraintLayout match_constraint (0dp). Outside CL still expand.
        raw == "0dp" || raw == "0dip" -> {
            if (parent is ConstraintLayout) {
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            } else {
                ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        raw.startsWith("?") -> {
            // Common theme size: actionBarSize ≈ 56dp
            if (raw.contains("actionBarSize", ignoreCase = true)) {
                (56f * context.resources.displayMetrics.density).toInt()
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        raw.startsWith("@") -> ViewGroup.LayoutParams.WRAP_CONTENT
        else -> parseDimension(raw, ViewGroup.LayoutParams.WRAP_CONTENT.toFloat()).toInt()
    }

    private fun parseDimension(raw: String, default: Float): Float {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("@") || trimmed.startsWith("?") -> default
            trimmed.endsWith("dp") || trimmed.endsWith("dip") -> {
                val drop = if (trimmed.endsWith("dip")) 3 else 2
                val v = trimmed.dropLast(drop).trim().toFloatOrNull() ?: return default
                v * context.resources.displayMetrics.density
            }
            trimmed.endsWith("sp") -> {
                val v = trimmed.dropLast(2).trim().toFloatOrNull() ?: return default
                v * context.resources.displayMetrics.scaledDensity
            }
            trimmed.endsWith("px") -> {
                trimmed.dropLast(2).trim().toFloatOrNull() ?: default
            }
            trimmed.toFloatOrNull() != null -> {
                trimmed.toFloat() * context.resources.displayMetrics.density
            }
            else -> default
        }
    }

    private fun parseGravity(raw: String): Int {
        var g = Gravity.NO_GRAVITY
        raw.split("|").forEach { part ->
            g = g or when (part.trim().lowercase()) {
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "left" -> Gravity.LEFT
                "right" -> Gravity.RIGHT
                "start" -> Gravity.START
                "end" -> Gravity.END
                "center" -> Gravity.CENTER
                "center_horizontal" -> Gravity.CENTER_HORIZONTAL
                "center_vertical" -> Gravity.CENTER_VERTICAL
                "fill" -> Gravity.FILL
                "fill_horizontal" -> Gravity.FILL_HORIZONTAL
                "fill_vertical" -> Gravity.FILL_VERTICAL
                "clip_horizontal" -> Gravity.CLIP_HORIZONTAL
                "clip_vertical" -> Gravity.CLIP_VERTICAL
                else -> 0
            }
        }
        return g
    }

    private fun applyBackgroundTint(view: View, raw: String) {
        val color = parseColor(raw)
        val density = context.resources.displayMetrics.density
        // Material3 filled buttons are heavily rounded (≈20–24dp / near-pill on 48dp height).
        // Host framework Button stays rectangular; approximate M3 when no corner attr given.
        val isButton = view is android.widget.Button
        val radius = when {
            pendingCornerRadius > 0f -> pendingCornerRadius
            isButton -> 20f * density
            else -> 12f * density
        }
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        // Framework / AppCompat Button keeps theme backgroundTintList; that multiplies over any
        // background we set and washes out / flattens preview colors. Clear it first.
        view.backgroundTintList = null
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            view.foregroundTintList = null
        }
        if (isButton) {
            view.stateListAnimator = null
        }
        view.background = drawable
        log(
            "backgroundTint $raw -> #${Integer.toHexString(color)} " +
                "radius=${radius / density}dp on ${view.javaClass.simpleName}"
        )
        if (view is TextView && view !is android.widget.EditText) {
            val luminance = (
                0.299 * android.graphics.Color.red(color) +
                    0.587 * android.graphics.Color.green(color) +
                    0.114 * android.graphics.Color.blue(color)
                ) / 255.0
            if (luminance < 0.6) {
                view.setTextColor(0xFFFFFFFF.toInt())
            }
        }
    }

    private fun applyViewCorners(view: View) {
        if (pendingCornerRadius <= 0f) return
        val bg = view.background
        if (bg is GradientDrawable) {
            bg.cornerRadius = pendingCornerRadius
            return
        }
        // No solid background yet — corners only matter after backgroundTint creates one.
    }

    private fun cardFillColor(view: View): Int {
        // Prefer the value we stored — GradientDrawable.colors is often null after setColor().
        (view.getTag(CARD_FILL_TAG) as? Int)?.let { return it }
        val bg = view.background
        if (bg is GradientDrawable) {
            val colors = bg.colors
            if (colors != null && colors.isNotEmpty()) {
                return colors[0]
            }
        }
        // Never fall back to pure white: white text on white canvas looks "empty".
        return 0xFF455A64.toInt()
    }

    private fun cardStrokeColor(view: View): Int? = view.getTag(CARD_STROKE_COLOR_TAG) as? Int

    private fun cardStrokeWidth(view: View): Int = (view.getTag(CARD_STROKE_WIDTH_TAG) as? Int) ?: 0

    private fun applyCardBackground(
        view: View,
        color: Int,
        cornerRadiusPx: Float,
        strokeColor: Int? = null,
        strokeWidthPx: Int = 0
    ) {
        // Keep prior stroke when caller only changes fill/radius (e.g. cardCornerRadius).
        val resolvedStrokeColor = strokeColor ?: cardStrokeColor(view)
        val resolvedStrokeWidth = if (strokeColor != null || strokeWidthPx > 0) {
            strokeWidthPx
        } else {
            cardStrokeWidth(view)
        }

        val existing = view.background as? GradientDrawable
        val drawable = existing ?: GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = cornerRadiusPx.coerceAtLeast(0f)
        drawable.setColor(color)
        if (resolvedStrokeWidth > 0 && resolvedStrokeColor != null) {
            drawable.setStroke(resolvedStrokeWidth, resolvedStrokeColor)
        }
        view.backgroundTintList = null
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            view.foregroundTintList = null
        }
        view.background = drawable
        view.clipToOutline = true
        view.setTag(CARD_FILL_TAG, color)
        if (resolvedStrokeColor != null) {
            view.setTag(CARD_STROKE_COLOR_TAG, resolvedStrokeColor)
        }
        view.setTag(CARD_STROKE_WIDTH_TAG, resolvedStrokeWidth)
        view.setTag(CARD_RADIUS_TAG, cornerRadiusPx)
        log(
            "card bg #${Integer.toHexString(color)} radius=${
                cornerRadiusPx / context.resources.displayMetrics.density
            }dp stroke=${resolvedStrokeWidth}px"
        )
    }

    private fun parseCornerRadius(raw: String): Float {
        val density = context.resources.displayMetrics.density
        return if (raw.endsWith("%")) {
            val pct = raw.removeSuffix("%").toFloatOrNull() ?: 0f
            if (pct >= 50f) {
                // Fully rounded pill / circle target for typical control height.
                9999f * density
            } else {
                (48f * pct / 100f) * density
            }
        } else {
            parseDimension(raw, 20f * density)
        }
    }

    private fun parseColor(raw: String): Int {
        val s = raw.trim()
        colorResolver.resolve(s)?.let { return it }
        if (s.startsWith("@android:color/")) {
            return androidColorValue(s.removePrefix("@android:color/")) ?: 0xFF888888.toInt()
        }
        if (s.startsWith("@color/") || s.startsWith("@+color/")) {
            val colorName = s.substringAfterLast('/')
            colorResolver.resolve("@color/$colorName")?.let { return it }
            log("color unresolved: $s (projectDir=${projectDir?.absolutePath})")
            return 0xFF888888.toInt()
        }
        if (!s.startsWith("#")) {
            if (s.equals("white", true)) return 0xFFFFFFFF.toInt()
            if (s.equals("black", true)) return 0xFF000000.toInt()
            if (s.equals("transparent", true)) return 0
            colorResolver.resolve("@color/$s")?.let { return it }
            return 0xFF888888.toInt()
        }
        return try {
            android.graphics.Color.parseColor(s)
        } catch (_: Exception) {
            0xFF888888.toInt()
        }
    }

    private fun androidColorValue(name: String): Int? = try {
        val clazz = Class.forName("android.R\$color")
        val field = clazz.getField(name)
        val id = field.getInt(null)
        context.resources.getColor(id, context.theme)
    } catch (_: Exception) {
        when (name) {
            "white" -> 0xFFFFFFFF.toInt()
            "black" -> 0xFF000000.toInt()
            "transparent" -> 0
            "holo_blue_light" -> 0xFF33B5E5.toInt()
            "holo_blue_dark" -> 0xFF0099CC.toInt()
            "holo_green_light" -> 0xFF99CC00.toInt()
            "holo_green_dark" -> 0xFF669900.toInt()
            "holo_orange_light" -> 0xFFFFBB33.toInt()
            "holo_orange_dark" -> 0xFFFF8800.toInt()
            "holo_red_light" -> 0xFFFF4444.toInt()
            "holo_red_dark" -> 0xFFCC0000.toInt()
            "darker_gray" -> 0xFF444444.toInt()
            "dark_gray" -> 0xFFA9A9A9.toInt()
            "background_light" -> 0xFFFAFAFA.toInt()
            "background_dark" -> 0xFF303030.toInt()
            "secondary_text_light" -> 0xFFBEBEBE.toInt()
            "tertiary_text_light" -> 0xFFAFAFAF.toInt()
            else -> null
        }
    }

    // --- Style resolution ---

    private fun loadProjectStyles(): Map<String, Map<String, String>> {
        val dir = projectDir ?: return emptyMap()
        val valuesDirs = findValuesDirs(dir)
        val out = LinkedHashMap<String, MutableMap<String, String>>()
        for (valuesDir in valuesDirs) {
            val files = valuesDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
                .orEmpty()
            for (file in files) {
                parseStyleFile(file, out)
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

    private fun parseStyleFile(file: File, out: MutableMap<String, MutableMap<String, String>>) {
        val parser = Xml.newPullParser()
        try {
            FileReader(file).use { reader ->
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(reader)
                var event = parser.eventType
                var currentStyle: String? = null
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name?.substringAfterLast(':').orEmpty()
                            if (tag == "style") {
                                currentStyle = parser.getAttributeValue(null, "name")
                                    ?: attrLocal(parser, "name")
                                if (currentStyle != null) {
                                    out.putIfAbsent(currentStyle, LinkedHashMap())
                                }
                            } else if (tag == "item" && currentStyle != null) {
                                val itemName = parser.getAttributeValue(null, "name")
                                    ?: attrLocal(parser, "name")
                                val text = parser.nextText()
                                if (!itemName.isNullOrBlank() && text.isNotBlank()) {
                                    val cleanName = itemName.substringAfterLast(':')
                                    val cleanText = text.trim()
                                    out[currentStyle]?.putIfAbsent(cleanName, cleanText)
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val tag = parser.name?.substringAfterLast(':').orEmpty()
                            if (tag == "style") currentStyle = null
                        }
                    }
                    event = parser.next()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun resolveStyle(ref: String): Map<String, String>? {
        val name = when {
            ref.startsWith("@style/") -> ref.removePrefix("@style/")
            ref.startsWith("style/") -> ref.removePrefix("style/")
            else -> ref
        }.substringBefore('/')
        val exact = styleCache[name]
        if (exact != null) return exact
        // Try case-insensitive match
        return styleCache.entries.firstOrNull { (k, _) ->
            k.equals(name, ignoreCase = true)
        }?.value
    }

    private fun attrLocal(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            val local = parser.getAttributeName(i)?.substringAfterLast(':')
            if (local == name) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun parseVisibility(raw: String): Int = when (raw.trim().lowercase()) {
        "gone" -> View.GONE
        "invisible" -> View.INVISIBLE
        else -> View.VISIBLE
    }

    private fun log(msg: String) {
        if (trace.length < 4000) {
            trace.append(msg).append('\n')
        }
    }
}
