package com.aistudy.aistudy.answer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdaptiveAiResponseTest {

    // 1. Math: "Solve 2x + 5 = 15" -> step_by_step
    @Test
    fun testMathProblemSolveLinearEquation() {
        val json = JSONObject("""
            {
                "subject": "math",
                "difficulty": "easy",
                "responseType": "step_by_step",
                "answerType": "solution",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Subtract 5 from both sides",
                        "body": "Subtract 5 from both sides of the equation to isolate the term with x.",
                        "expr": "2x = 15 - 5"
                    },
                    {
                        "topicId": "",
                        "title": "Divide by 2",
                        "body": "Divide both sides by 2 to solve for x.",
                        "expr": "x = 10 / 2 = 5"
                    }
                ],
                "finalAnswer": "x = 5"
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.STEP_BY_STEP, content.responseMode)
        assertEquals(AnswerType.SOLUTION, content.type)
        assertEquals(2, content.steps.size)
        assertTrue(content.showFinalAnswer("math"))

        // Verify JSON round-trip preserves ResponseMode.STEP_BY_STEP
        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.STEP_BY_STEP, roundTrip.responseMode)
        assertEquals(AnswerType.SOLUTION, roundTrip.type)
        assertEquals(2, roundTrip.steps.size)
        assertEquals("x = 10 / 2 = 5", roundTrip.steps[1].expr)
    }

    // 2. Conceptual: "Explain polymorphism in Kotlin." -> direct
    @Test
    fun testKotlinPolymorphismDirectExplanation() {
        val json = JSONObject("""
            {
                "subject": "code",
                "difficulty": "medium",
                "responseType": "direct",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Polymorphism in Kotlin",
                        "body": "Polymorphism allows objects of different classes to be treated through a common superclass.\n\n```kotlin\nopen class Animal {\n    open fun sound() = \"sound\"\n}\nclass Dog : Animal() {\n    override fun sound() = \"bark\"\n}\n```\n\nWith polymorphism, `Animal` references can invoke subclass behavior dynamically.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Polymorphism enables flexibility by decoupling interface from implementation."
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.DIRECT, content.responseMode)
        assertEquals(AnswerType.EXPLANATION, content.type)
        assertEquals(1, content.steps.size)

        // Code block inside step body is recognized by AnswerTextBlocks
        val blocks = AnswerTextBlocks.split(content.steps.single().body)
        assertTrue(blocks.any { it.isCode })

        // Round-trip preserves direct mode
        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.DIRECT, roundTrip.responseMode)
    }

    // 3. Factual: "What is Room Database?" -> direct
    @Test
    fun testWhatIsRoomDatabaseDirectAnswer() {
        val json = JSONObject("""
            {
                "subject": "code",
                "difficulty": "easy",
                "responseType": "direct",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Overview of Room Database",
                        "body": "Room is part of Android Jetpack. It provides an abstraction layer over SQLite to allow fluent database access while leveraging the full power of SQLite. Its three major components are Entity, DAO, and Database.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Room is a robust SQLite abstraction library for Android apps."
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.DIRECT, content.responseMode)
        assertEquals(AnswerType.EXPLANATION, content.type)

        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.DIRECT, roundTrip.responseMode)
        assertEquals("Overview of Room Database", roundTrip.steps.single().title)
    }

    // 4. Creative: "Write an original short poem." -> creative
    @Test
    fun testWriteOriginalShortPoemCreativeMode() {
        val json = JSONObject("""
            {
                "subject": "other",
                "difficulty": "medium",
                "responseType": "creative",
                "answerType": "explanation",
                "finalAnswerRedundant": true,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Golden Dawn",
                        "body": "Golden dawn begins to gleam,\nWaking meadows from their dream.\n\nWhispers dance upon the breeze,\nLeaves awoken in the trees.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Theme: Awakening and the beauty of a new morning."
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.CREATIVE, content.responseMode)
        assertEquals(AnswerType.EXPLANATION, content.type)
        assertTrue(content.steps.single().body.contains("\n\n"))

        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.CREATIVE, roundTrip.responseMode)
    }

    // 5. Copyrighted Song: "Give me the full lyrics of Bohemian Rhapsody" -> limitation
    @Test
    fun testCopyrightedSongLyricsTriggersLimitation() {
        val json = JSONObject("""
            {
                "subject": "other",
                "difficulty": "medium",
                "responseType": "limitation",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Copyright Notice & Thematic Overview",
                        "body": "AIStudy respects copyright protection and cannot reproduce full copyrighted song lyrics. Bohemian Rhapsody by Queen (1975) is celebrated for its progressive rock and operatic suite structure. Official recordings are lawfully accessible via authorized streaming platforms such as Spotify and Apple Music.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Full lyrics protected by copyright; educational commentary provided."
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.LIMITATION, content.responseMode)
        assertEquals(AnswerType.EXPLANATION, content.type)
        assertTrue(content.steps.single().body.contains("cannot reproduce full copyrighted"))

        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.LIMITATION, roundTrip.responseMode)
    }

    // 6. Ambiguous Song Title: "lyrics to Hello" without artist -> clarification
    @Test
    fun testAmbiguousSongTitleTriggersClarification() {
        val json = JSONObject("""
            {
                "subject": "other",
                "difficulty": "easy",
                "responseType": "clarification",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Clarification Required",
                        "body": "Multiple renowned songs share the title 'Hello'—notably Adele (2015), Lionel Richie (1984), and Martin Solveig (2010). Please clarify which artist you are asking about.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Please provide the artist name (e.g., Adele or Lionel Richie)."
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.CLARIFICATION, content.responseMode)
        assertEquals(AnswerType.EXPLANATION, content.type)

        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.CLARIFICATION, roundTrip.responseMode)
    }

    // 7. Unfamiliar / Unverifiable Question -> limitation with uncertainty
    @Test
    fun testUnfamiliarQuestionExpressesUncertainty() {
        val json = JSONObject("""
            {
                "subject": "history",
                "difficulty": "hard",
                "responseType": "limitation",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Unverified Information",
                        "body": "AIStudy lacks reliable historical records verifying 'XYZ-Secret-Potion-1823'. To maintain educational integrity, we refrain from inventing facts.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Historical records are unavailable or unverified."
            }
        """.trimIndent())

        val content = AnswerContent.generated(json)
        assertEquals(ResponseMode.LIMITATION, content.responseMode)
        assertEquals(AnswerType.EXPLANATION, content.type)

        val roundTrip = AnswerContent.read(content.toJson())
        assertEquals(ResponseMode.LIMITATION, roundTrip.responseMode)
    }

    // 8. Long Code Example with Literal Backslashes & Math Preserved
    @Test
    fun testLongCodeExampleWithLiteralBackslashes() {
        val rawCode = "```kotlin\nval path = \"C:\\\\Users\\\\Admin\\\\Documents\"\nval regex = Regex(\"\\\\d+\\\\s+\\\\w+\")\nprintln(\"Hello\\nWorld\\tTabbed\")\n```"
        val proseWithMathAndNewlines = "Formula: \$a \\\\neq b\$ and \$\\nabla f(x)\$.\\nSecond line with `\\n` escape character.\\r\\nThird line."

        // 1. Verify code block preserves literal backslashes intact
        val blocks = AnswerTextBlocks.split(rawCode)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].isCode)
        assertEquals(rawCode, blocks[0].text)
        assertTrue(blocks[0].text.contains("C:\\\\Users\\\\Admin"))
        assertTrue(blocks[0].text.contains("\\d+"))

        // 2. Verify sanitizeProse unescapes prose newlines while keeping LaTeX math & inline code intact
        val sanitized = AnswerBodyRenderer.sanitizeProse(proseWithMathAndNewlines)
        assertTrue("LaTeX \\neq preserved", sanitized.contains("\\neq"))
        assertTrue("LaTeX \\nabla preserved", sanitized.contains("\\nabla"))
        assertTrue("Inline code `\\n` preserved", sanitized.contains("`\\n`"))
        assertTrue("Literal \\n unescaped to newline", sanitized.contains("\nSecond line"))
        assertTrue("Literal \\r\\n unescaped to newline", sanitized.contains("\nThird line"))
    }

    // 9. Legacy Stored Answer Compatibility (Room Database JSON)
    @Test
    fun testLegacyStoredAnswerCompatibility() {
        // A. Legacy 1-item answer without _answer metadata
        val legacySingle = """[{"title":"Definition","body":"A legacy explanation.","expr":""}]"""
        val parsedSingle = AnswerContent.read(legacySingle)
        assertNull(parsedSingle.type)
        assertEquals(ResponseMode.DIRECT, parsedSingle.responseMode)
        assertEquals(1, parsedSingle.steps.size)
        assertEquals("A legacy explanation.", parsedSingle.steps.single().body)

        // B. Legacy multi-step answer without _answer metadata
        val legacyMulti = """[
            {"title":"Step 1","body":"Do this first.","expr":""},
            {"title":"Step 2","body":"Do this second.","expr":""}
        ]"""
        val parsedMulti = AnswerContent.read(legacyMulti)
        assertNull(parsedMulti.type)
        assertEquals(ResponseMode.STEP_BY_STEP, parsedMulti.responseMode)
        assertEquals(2, parsedMulti.steps.size)

        // C. Legacy answer with version:1 but no responseType in _answer
        val legacyV1Solution = """[
            {
                "title":"Step 1",
                "body":"Legacy solution body",
                "expr":"2x = 10",
                "_answer":{"version":1,"answerType":"solution","finalAnswerRedundant":false,"moreCount":0}
            }
        ]"""
        val parsedV1 = AnswerContent.read(legacyV1Solution)
        assertEquals(AnswerType.SOLUTION, parsedV1.type)
        assertEquals(ResponseMode.STEP_BY_STEP, parsedV1.responseMode)
        assertEquals("2x = 10", parsedV1.steps.single().expr)

        // D. Round-trip serialization adds responseType without corrupting schema
        val resaved = AnswerContent.read(parsedV1.toJson())
        assertEquals(ResponseMode.STEP_BY_STEP, resaved.responseMode)
        assertEquals(AnswerType.SOLUTION, resaved.type)
        assertEquals("Legacy solution body", resaved.steps.single().body)
    }
}
