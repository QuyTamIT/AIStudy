package com.aistudy.aistudy.answer

/** Presentation only; stored answers and grading values remain unchanged. */
internal object StudyMathText {
    private val inlineCode = Regex("(?<!`)(`+)(?!`)(.*?)\\1(?!`)", RegexOption.DOT_MATCHES_ALL)
    private val unclosedFence = Regex("(?m)^ {0,3}(?:`{3,}|~{3,}).*$")
    private val math = Regex("""\$\$(.+?)\$\$|(?<![\\$])\$([^$\n]+)\$|\\\((.+?)\\\)|\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
    private val fraction = Regex("""\\frac\s*\{([^{}]+)\}\s*\{([^{}]+)\}""")
    private val command = Regex("""\\[a-zA-Z]+""")
    private val words = Regex("""\p{L}{2,}""")
    private val symbols = mapOf(
        "\\implies" to "⇒", "\\Rightarrow" to "⇒", "\\times" to "×",
        "\\cdot" to "·", "\\div" to "÷", "\\pm" to "±",
        "\\neq" to "≠", "\\leq" to "≤", "\\geq" to "≥"
    )

    fun format(source: String): String = AnswerTextBlocks.split(source).joinToString("") { block ->
        if (block.isCode) block.text else {
            // An unfinished code fence must also keep its literal contents.
            val fenceStart = unclosedFence.find(block.text)?.range?.first ?: block.text.length
            val prose = block.text.substring(0, fenceStart)
            buildString {
                var start = 0
                for (code in inlineCode.findAll(prose)) {
                    append(formatProse(prose.substring(start, code.range.first)))
                    append(code.value)
                    start = code.range.last + 1
                }
                append(formatProse(prose.substring(start)))
                append(block.text.substring(fenceStart))
            }
        }
    }

    private fun formatProse(source: String): String {
        val delimited = math.replace(source) { match ->
            val expression = match.groupValues.drop(1).first { it.isNotEmpty() }
            // Currency such as "$5 and $10" is prose, not a math delimiter pair.
            val nextIsAmount = match.value.startsWith('$') &&
                source.getOrNull(match.range.last + 1)?.isDigit() == true
            if (nextIsAmount || words.containsMatchIn(command.replace(expression, ""))) match.value
            else readableExpression(expression) ?: match.value
        }
        return readableExpression(delimited) ?: delimited
    }

    private fun readableExpression(source: String): String? {
        var text = source
        // Support the legacy fractions seen in saved answers, preserving grouping.
        for (depth in 0 until 16) {
            val next = fraction.replace(text) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
            if (next == text) break
            text = next
        }
        text = command.replace(text) { symbols[it.value] ?: it.value }
        // Unsupported LaTeX stays intact rather than silently losing notation.
        return text.takeUnless { command.containsMatchIn(it) }
    }
}
