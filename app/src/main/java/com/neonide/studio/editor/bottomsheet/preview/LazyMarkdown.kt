package com.neonide.studio.editor.bottomsheet.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstDocument
import com.halilibo.richtext.markdown.node.AstHtmlBlock
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.currentRichTextStyle
import com.halilibo.richtext.ui.resolveDefaults

private val alignOpenRegex = Regex(
    """<(div|p)\s+align\s*=\s*"(left|center|right|justify)"\s*>""",
    setOf(RegexOption.IGNORE_CASE)
)
private val centerTagRegex = Regex("""<center\s*>""", RegexOption.IGNORE_CASE)
private val blockCloseRegex = Regex("""</(div|p|center)\s*>""", RegexOption.IGNORE_CASE)

private fun parseAlignFromOpenTag(literal: String): String? {
    val trimmed = literal.trim()
    alignOpenRegex.matchEntire(trimmed)?.let { return it.groupValues[2].lowercase() }
    if (centerTagRegex.matchEntire(trimmed) != null) return "center"
    return null
}

private fun isBlockCloseTag(literal: String): Boolean =
    blockCloseRegex.matchEntire(literal.trim()) != null

private fun findMatchingCloseTag(children: List<AstNode>, startIndex: Int): Int {
    var nesting = 0
    for (i in startIndex until children.size) {
        val child = children[i]
        if (child.type !is AstHtmlBlock) continue
        val literal = (child.type as AstHtmlBlock).literal
        if (isBlockCloseTag(literal)) {
            if (nesting == 0) return i
            nesting--
        } else if (parseAlignFromOpenTag(literal) != null) {
            nesting++
        }
    }
    return -1
}

private fun AstNode.directChildren(): List<AstNode> {
    val list = mutableListOf<AstNode>()
    var child = links.firstChild
    while (child != null) {
        list.add(child)
        child = child.links.next
    }
    return list
}

/**
 * Groups top-level AST siblings so open/close alignment HTML wrappers stay one Lazy item,
 * matching [com.halilibo.richtext.markdown.renderChildren] behavior.
 */
private fun batchChildren(children: List<AstNode>): List<List<AstNode>> {
    val batches = mutableListOf<List<AstNode>>()
    var i = 0
    while (i < children.size) {
        val child = children[i]
        if (child.type is AstHtmlBlock) {
            val alignValue = parseAlignFromOpenTag((child.type as AstHtmlBlock).literal)
            if (alignValue != null) {
                val endIndex = findMatchingCloseTag(children, i + 1)
                if (endIndex != -1) {
                    batches.add(children.subList(i, endIndex + 1).toList())
                    i = endIndex + 1
                    continue
                }
            }
        }
        batches.add(listOf(child))
        i++
    }
    return batches
}

private fun batchKey(index: Int, batch: List<AstNode>): String {
    val parts = batch.map { node ->
        when (val type = node.type) {
            is AstHtmlBlock -> "h:${type.literal}"
            else -> "${type::class.simpleName}:${node.hashCode()}"
        }
    }
    return "$index|${parts.joinToString("|")}"
}

/**
 * Lazily renders a Markdown AST at the document root. Alignment wrappers
 * (`div/p align`, `center`) stay batched as a single item so layout matches
 * eager [BasicMarkdown]. Deeper nodes still render eagerly.
 */
@Composable
fun RichTextScope.LazyMarkdown(
    astNode: AstNode,
    astBlockNodeComposer: AstBlockNodeComposer? = null
) {
    require(astNode.type == AstDocument) {
        "Lazy Markdown rendering requires root level node to have a type of AstDocument."
    }
    val currentStyle = currentRichTextStyle
    val resolvedStyle = remember(currentStyle) { currentStyle.resolveDefaults() }
    val blockSpacing = with(LocalDensity.current) {
        resolvedStyle.paragraphSpacing!!.toDp()
    }
    val batches = remember(astNode) {
        batchChildren(astNode.directChildren())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(blockSpacing)
    ) {
        itemsIndexed(
            items = batches,
            key = { index, batch -> batchKey(index, batch) }
        ) { _, batch ->
            val isAligned = batch.size > 1
            val alignValue = if (isAligned) {
                (batch.first().type as? AstHtmlBlock)?.literal?.let { parseAlignFromOpenTag(it) }
            } else {
                null
            }
            if (isAligned && alignValue != null) {
                val textAlign = when (alignValue) {
                    "center" -> TextAlign.Center
                    "right" -> TextAlign.End
                    "justify" -> TextAlign.Justify
                    else -> TextAlign.Start
                }
                val boxAlignment = when (alignValue) {
                    "center" -> Alignment.TopCenter
                    "right" -> Alignment.TopEnd
                    else -> Alignment.TopStart
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (j in 1 until batch.size - 1) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = boxAlignment
                        ) {
                            ProvideTextStyle(TextStyle(textAlign = textAlign)) {
                                BasicMarkdown(batch[j], astBlockNodeComposer)
                            }
                        }
                    }
                }
            } else {
                BasicMarkdown(batch.first(), astBlockNodeComposer)
            }
        }
    }
}
