package com.aistudy.aistudy.answer

internal data class AnswerTextBlock(val text: String, val isCode: Boolean)

/** Presentation-only segmentation: source text, including fence markers, is never rewritten. */
internal object AnswerTextBlocks {
    private val fenceLine = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")

    fun split(source: String): List<AnswerTextBlock> {
        val blocks = mutableListOf<AnswerTextBlock>()
        var proseStart = 0
        var openingStart = -1
        var openingMarker = ' '
        var openingLength = 0
        var lineStart = 0

        while (lineStart < source.length) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) source.length else newline
            val contentEnd = if (lineEnd > lineStart && source[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
            val nextLine = if (newline < 0) source.length else newline + 1
            val match = fenceLine.matchEntire(source.substring(lineStart, contentEnd))
            if (match != null) {
                val fence = match.groupValues[1]
                val suffix = match.groupValues[2]
                if (openingStart < 0) {
                    // A backtick in a backtick fence's info string is ambiguous Markdown.
                    if (fence[0] != '`' || '`' !in suffix) {
                        openingStart = lineStart
                        openingMarker = fence[0]
                        openingLength = fence.length
                    }
                } else if (fence[0] == openingMarker && fence.length >= openingLength &&
                    suffix.all { it.isWhitespace() }) {
                    if (openingStart > proseStart) {
                        blocks.add(AnswerTextBlock(source.substring(proseStart, openingStart), false))
                    }
                    blocks.add(AnswerTextBlock(source.substring(openingStart, nextLine), true))
                    proseStart = nextLine
                    openingStart = -1
                }
            }
            lineStart = nextLine
        }

        // An unmatched opening fence stays inside the untouched prose tail.
        if (proseStart < source.length) {
            blocks.add(AnswerTextBlock(source.substring(proseStart), false))
        }
        return blocks
    }
}
