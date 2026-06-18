package com.neonide.studio.app.editor.completion

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionMergingTest {

    @Test
    fun testMergeAndSort() {
        val lspItems = listOf(
            SimpleCompletionItem("abc", "LSP Item", 0, "abc")
        )
        val snippets = listOf(
            SimpleCompletionItem("abb", "Snippet", 0, "abb").apply {
                kind =
                    CompletionItemKind.Snippet
            }
        )

        // Merge and sort completions from multiple sources.
        // Usually snippets are preferred or sorted by label if score is same.
        val merged = mergeAndSort(lspItems, snippets)

        assertEquals(2, merged.size)
        // With default comparator (label-based if no fuzzy score): abb < abc
        assertEquals("abb", merged[0].label)
        assertEquals("abc", merged[1].label)
    }

    private fun mergeAndSort(
        lsp: List<CompletionItem>,
        snippets: List<CompletionItem>
    ): List<CompletionItem> {
        // Merge and sort completions from multiple sources
        val all = lsp + snippets
        return all.sortedWith { a, b -> a.label.toString().compareTo(b.label.toString()) }
    }
}
