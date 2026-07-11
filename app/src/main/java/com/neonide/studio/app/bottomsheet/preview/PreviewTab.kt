package com.neonide.studio.app.bottomsheet.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstHtmlBlock
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.ui.BadgeStyle
import com.halilibo.richtext.ui.BlockQuoteGutter
import com.halilibo.richtext.ui.BlockQuoteStyle
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.TaskListStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import java.io.File

@Composable
fun PreviewTab(content: String, activeFilePath: String? = null) {
    val baseDir = activeFilePath?.let { File(it).parent } ?: ""
    HtmlImageComposer.baseDir = baseDir

    val isDark = LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val codeBlockBg = if (isDark) Color(0xFF151B23) else Color(0xFFF0F6FC)
    val previewBg = if (isDark) Color(0xFF0D1117) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)
    val gutterColor = if (isDark) Color(0xFF3D444D) else Color(0xFFD1D9E0)
    val quoteColor = if (isDark) Color(0xFF8F98A1) else Color(0xFF59646E)
    ProvideTextStyle(
        TextStyle(fontSize = 14.9.sp)
    ) {
        RichText(
            style = RichTextStyle(
                headingStyle = { level, textStyle ->
                    when (level) {
                        1 -> textStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        2 -> textStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        3 -> textStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        4 -> textStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        5 -> textStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        6 -> textStyle.copy(
                            fontSize = 13.6.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        else -> textStyle
                    }
                },
                codeBlockStyle = CodeBlockStyle(
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400
                    ),
                    wordWrap = false,
                    modifier = Modifier.background(codeBlockBg)
                ),
                stringStyle = RichTextStringStyle(
                    boldStyle = SpanStyle(fontWeight = FontWeight.W600)
                ),
                badgeStyle = BadgeStyle(
                    height = 20.dp,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopStart,
                    spacing = 8.dp,
                    flowWrap = true
                ),
                taskListStyle = TaskListStyle(
                    size = 16.dp,
                    checkmarkSize = 13.dp
                ),
                horizontalRuleHeight = 4.dp,
                horizontalRuleColorProvider = { gutterColor },
                blockQuoteStyle = BlockQuoteStyle(
                    textStyle = TextStyle(fontSize = 15.sp, color = quoteColor),
                    gutter = BlockQuoteGutter.BarGutter(
                        startMargin = 0.sp,
                        barWidth = 4.sp,
                        endMargin = 20.sp,
                        color = { gutterColor },
                        shape = RoundedCornerShape(0.dp)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, borderColor)
                .background(previewBg)
                .verticalScroll(rememberScrollState())
                .padding(35.dp)
        ) {
            Markdown(content, astBlockNodeComposer = HtmlImageComposer)
        }
    }
}

private object HtmlImageComposer : AstBlockNodeComposer {

    var baseDir: String = ""

    private val imgPattern =
        Regex(
            "<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]([^>]*width\\s*=\\s*['\"]?(\\d+)%?['\"]?)?",
            RegexOption.IGNORE_CASE
        )

    @Composable
    private fun ImageRow(images: List<Pair<String, Int>>) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            images.forEach { (src, w) ->
                AsyncImage(
                    model = src,
                    contentDescription = null,
                    modifier = Modifier.weight(w.toFloat()).fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    override fun predicate(type: AstBlockNodeType): Boolean = type is AstHtmlBlock

    @Composable
    override fun RichTextScope.Compose(
        astNode: AstNode,
        visitChildren: @Composable (AstNode) -> Unit
    ) {
        val literal = (astNode.type as AstHtmlBlock).literal
        val images = imgPattern.findAll(literal).map { match ->
            val src = match.groupValues[1]
            val resolvedSrc = when {
                src.startsWith("http://") || src.startsWith("https://") -> src
                src.startsWith("/") -> "$baseDir$src"
                src.startsWith("./") -> "$baseDir/${src.substring(2)}"
                baseDir.isNotEmpty() -> "$baseDir/$src"
                else -> src
            }
            val widthPct = match.groupValues[3].toIntOrNull() ?: 100
            resolvedSrc to widthPct
        }.toList()
        if (images.isEmpty()) {
            visitChildren(astNode)
            return
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            var row = mutableListOf<Pair<String, Int>>()
            var rowTotal = 0
            for ((src, w) in images) {
                rowTotal += w
                if (rowTotal > 100 && row.isNotEmpty()) {
                    ImageRow(row)
                    row = mutableListOf()
                    rowTotal = w
                }
                row.add(src to w)
            }
            if (row.isNotEmpty()) ImageRow(row)
        }
    }
}
