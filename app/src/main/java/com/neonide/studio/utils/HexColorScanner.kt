package com.neonide.studio.utils

import android.graphics.Color
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer.HighlightText
import io.github.rosemoe.sora.lang.styling.color.ConstColor

/**
 * Scans text for hex color literals in two formats:
 *   • CSS style: #RGB, #ARGB, #RRGGBB, #AARRGGBB
 *   • Code style: 0xRGB, 0xARGB, 0xRRGGBB, 0xAARRGGBB
 * and produces [HighlightTextContainer] ranges for editor rendering.
 */
object HexColorScanner {

    private const val MAX_HIGHLIGHTS = 400
    private const val MAX_HEX_DIGITS = 8
    private const val BORDER_DARKEN_FACTOR = 0.75f

    private val VALID_HEX_LENGTHS = setOf(3, 4, 6, 8)

    fun computeHighlights(text: CharSequence): HighlightTextContainer? {
        val container = HighlightTextContainer()
        appendHighlights(text, container)
        return if (container.isEmpty()) null else container
    }

    /**
     * Scans [text] for hex color literals and appends them as [HighlightText]
     * to the given [container].
     */
    fun appendHighlights(text: CharSequence, container: HighlightTextContainer) {
        val source = text.toString()
        if (source.isEmpty()) return

        var line = 0
        var column = 0
        var highlightCount = 0
        var index = 0

        while (index < source.length && highlightCount < MAX_HIGHLIGHTS) {
            val currentChar = source[index]

            if (currentChar == '0' &&
                index + 1 < source.length &&
                (source[index + 1] == 'x' || source[index + 1] == 'X')
            ) {
                val startLine = line
                val startColumn = column
                val hexStart = index + 2 // skip "0x"
                val hexEnd = scanHexDigits(source, hexStart)
                val hexLength = hexEnd - hexStart

                if (hexLength in VALID_HEX_LENGTHS) {
                    val hexPart = source.substring(hexStart, hexEnd)
                    val color = parseHexDigits(hexPart)

                    if (color != null) {
                        val literalLength = hexEnd - index // "0x" + digits
                        val endColumn = startColumn + literalLength
                        container.add(
                            HighlightText(
                                startLine,
                                startColumn,
                                startLine,
                                endColumn,
                                ConstColor(color),
                                ConstColor(darkenForBorder(color))
                            )
                        )
                        highlightCount++
                    }
                }

                // Advance past the whole token
                for (k in index until hexEnd) {
                    advancePosition(source[k], line, column).let { (l, c) ->
                        line = l
                        column = c
                    }
                }
                index = hexEnd
                continue
            }

            if (currentChar == '#') {
                val startLine = line
                val startColumn = column
                val hexEnd = scanHexDigits(source, index + 1)
                val hexLength = hexEnd - (index + 1)

                if (hexLength in VALID_HEX_LENGTHS) {
                    val literal = source.substring(index, hexEnd)
                    val color = parseHashColor(literal)

                    if (color != null) {
                        val endColumn = startColumn + (hexEnd - index)
                        container.add(
                            HighlightText(
                                startLine,
                                startColumn,
                                startLine,
                                endColumn,
                                ConstColor(color),
                                ConstColor(darkenForBorder(color))
                            )
                        )
                        highlightCount++
                    }
                }

                for (k in index until hexEnd) {
                    advancePosition(source[k], line, column).let { (l, c) ->
                        line = l
                        column = c
                    }
                }
                index = hexEnd
                continue
            }

            advancePosition(currentChar, line, column).let { (l, c) ->
                line = l
                column = c
            }
            index++
        }
    }

    /**
     * Returns the index after consuming consecutive hex digits,
     * capped at [MAX_HEX_DIGITS] from [start].
     */
    private fun scanHexDigits(source: String, start: Int): Int {
        var end = start
        while (end < source.length && isHexDigit(source[end]) && (end - start) < MAX_HEX_DIGITS) {
            end++
        }
        return end
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /**
     * Advances line/column counters for the given character.
     */
    private fun advancePosition(ch: Char, line: Int, column: Int): Pair<Int, Int> =
        if (ch == '\n') line + 1 to 0 else line to column + 1

    /**
     * Parses a `#`-prefixed literal (#RGB, #ARGB, #RRGGBB, #AARRGGBB).
     */
    private fun parseHashColor(literal: String): Int? {
        if (!literal.startsWith('#')) return null
        val hex = literal.substring(1)
        return normalizeAndParse(hex)
    }

    /**
     * Parses raw hex digits (no prefix) from a `0x`-prefixed literal.
     */
    private fun parseHexDigits(hex: String): Int? = normalizeAndParse(hex)

    /**
     * Normalizes a 3/4-char shorthand to full form, then parses via
     * [Color.parseColor] (which expects `#RRGGBB` or `#AARRGGBB`).
     */
    private fun normalizeAndParse(hex: String): Int? {
        val normalized = when (hex.length) {
            3 -> expandShortHex(hex, hasAlpha = false)

            // RGB → RRGGBB
            4 -> expandShortHex(hex, hasAlpha = true)

            // ARGB → AARRGGBB
            6, 8 -> "#$hex"

            else -> return null
        }
        return runCatching { Color.parseColor(normalized) }.getOrNull()
    }

    /**
     * Expands shorthand hex to full form with a `#` prefix.
     */
    private fun expandShortHex(hex: String, hasAlpha: Boolean): String = if (hasAlpha) {
        val (a, r, g, b) = hex.toList()
        "#$a$a$r$r$g$g$b$b"
    } else {
        val (r, g, b) = hex.toList()
        "#$r$r$g$g$b$b"
    }

    /**
     * Darkens the color by [BORDER_DARKEN_FACTOR] for highlight borders.
     */
    private fun darkenForBorder(color: Int): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * BORDER_DARKEN_FACTOR).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * BORDER_DARKEN_FACTOR).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * BORDER_DARKEN_FACTOR).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
