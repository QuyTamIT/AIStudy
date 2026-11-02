package com.aistudy.aistudy.answer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTextBlocksTest {
    @Test fun preservesEveryCharacterWhileSeparatingExplicitCodeFromProse() {
        val source = "Explanation\n\n```kotlin\nval total = 2 + 3\n```\n\nResult: 5."

        val blocks = AnswerTextBlocks.split(source)

        assertEquals(listOf(
            AnswerTextBlock("Explanation\n\n", false),
            AnswerTextBlock("```kotlin\nval total = 2 + 3\n```\n", true),
            AnswerTextBlock("\nResult: 5.", false)
        ), blocks)
        assertEquals(source, blocks.joinToString("") { it.text })
    }

    @Test fun preservesCrLfUnicodeAndWhitespaceWithoutNormalizingContent() {
        val source = "Đáp án\u00a0\r\n\r\n  ~~~python\r\n\tprint(\"π = 3.14\")  \r\n  ~~~~ \t\r\n\u2003Kết thúc\r\n"

        val blocks = AnswerTextBlocks.split(source)

        assertEquals(3, blocks.size)
        assertTrue(blocks[1].isCode)
        assertEquals("  ~~~python\r\n\tprint(\"π = 3.14\")  \r\n  ~~~~ \t\r\n", blocks[1].text)
        assertEquals(source, blocks.joinToString("") { it.text })
    }

    @Test fun leavesInlineFencesIndentationAndOrdinaryCodeLikeProseUnchanged() {
        val source = "Use ```code``` here.\n    ```\n    x = 1\n    ```\nExample:\n    print(1)\n"

        assertEquals(listOf(AnswerTextBlock(source, false)), AnswerTextBlocks.split(source))
    }

    @Test fun unmatchedOrMismatchedFencesStayOneUnchangedProseBlock() {
        listOf(
            "Before\n```python\nprint(1)\n",
            "```\nvalue\n~~~\n",
            "~~~~\nvalue\n~~~\n",
            "```\nvalue\n``` trailing text\n"
        ).forEach { source ->
            assertEquals(listOf(AnswerTextBlock(source, false)), AnswerTextBlocks.split(source))
        }
    }

    @Test fun shorterFenceInsideCodeDoesNotCloseTheBlock() {
        val source = "````text\n```\ninside\n```\n`````\nAfter"

        assertEquals(listOf(
            AnswerTextBlock("````text\n```\ninside\n```\n`````\n", true),
            AnswerTextBlock("After", false)
        ), AnswerTextBlocks.split(source))
    }

    @Test fun acceptsThreeLeadingSpacesAndAClosingFenceAtEndOfInput() {
        val source = "   ```\nx\n   ```"

        assertEquals(listOf(AnswerTextBlock(source, true)), AnswerTextBlocks.split(source))
    }

    @Test fun preservesAdjacentCodeBlocksAndTheUnmatchedTail() {
        val first = "```\nx\n```\n"
        val second = "~~~\ny\n~~~\n"
        val tail = "Tail\n\n```\nunclosed"

        assertEquals(listOf(
            AnswerTextBlock(first, true),
            AnswerTextBlock(second, true),
            AnswerTextBlock(tail, false)
        ), AnswerTextBlocks.split(first + second + tail))
    }

    @Test fun returnsNoBlocksForEmptyInput() {
        assertTrue(AnswerTextBlocks.split("").isEmpty())
    }
}
