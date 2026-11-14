package com.aistudy.aistudy.answer

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyMathTextTest {
    @Test fun legacyExpressionsBecomeReadableWithoutChangingGrouping() {
        assertEquals("x = (21)/(3) ⇒ x = 7", StudyMathText.format("""x = \frac{21}{3} \implies x = 7"""))
        assertEquals("(a + b)/(c - d)", StudyMathText.format("""\frac{a + b}{c - d}"""))
    }

    @Test fun inlineAndDisplayMathKeepVietnameseAndRealNewlines() {
        assertEquals("Nghiệm là x = 7.\n2 × 3 = 6", StudyMathText.format("Nghiệm là \$x = 7\$.\n\$\$2 \\times 3 = 6\$\$"))
        assertEquals("x = 6", StudyMathText.format("""\(x = 6\)"""))
    }

    @Test fun nestedFractionsPreservePrecedence() {
        assertEquals("((a)/(b))/(c)", StudyMathText.format("""\frac{\frac{a}{b}}{c}"""))
    }

    @Test fun codeIsPreservedVerbatim() {
        val source = """
            Nghiệm ${'$'}x = 7${'$'}.
            ```python
            formula = r"\frac{21}{3}"
            price = "${'$'}5"
            ```
            Inline `\implies` và `${'$'}x${'$'}` giữ nguyên.
        """.trimIndent()
        assertEquals(source.replace("Nghiệm ${'$'}x = 7${'$'}.", "Nghiệm x = 7."), StudyMathText.format(source))
    }

    @Test fun ordinaryCurrencyAndPathsStayUnchanged() {
        for (source in listOf("Price ${'$'}5 and ${'$'}10", "Giá ${'$'}5 và ${'$'}10", "${'$'}5–${'$'}10", "Budget ${'$'}100", """C:\notes\new.txt""", "Text in Vietnamese: tiếng Việt.")) {
            assertEquals(source, StudyMathText.format(source))
        }
    }

    @Test fun doubleBacktickCodeMayContainSingleBackticks() {
        val source = "Giữ `` `\\implies` `` nguyên."
        assertEquals(source, StudyMathText.format(source))
    }

    @Test fun malformedOrUnsupportedMathIsNotSilentlyDiscarded() {
        for (source in listOf("""\frac{21}""", """${'$'}\unknown{x}${'$'}""", """${'$'}\begin{matrix}1&2\end{matrix}${'$'}""")) {
            assertEquals(source, StudyMathText.format(source))
        }
    }
}
