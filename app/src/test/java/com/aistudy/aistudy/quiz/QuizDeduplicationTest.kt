package com.aistudy.aistudy.quiz

import com.aistudy.aistudy.database.QuestionAnswer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuizDeduplicationTest {
    private val placeholder = "Please solve the problem shown in this image."

    private fun item(type: String = "mcq"): JSONObject = JSONObject()
        .put("type", type).put("prompt", "Choose the city.")
        .put("options", if (type == "mcq") JSONArray(listOf("New York", "Paris", "Tokyo", "Huế")) else JSONArray())
        .put("correctAnswer", if (type == "mcq") " new   YORK " else "Paris")
        .put("explanation", "This follows from the source.")

    private fun batch(): JSONObject = JSONObject().put("items", JSONArray()
        .put(item()).put(item("short")).put(item("blank")).put(item()))

    private fun reject(json: JSONObject) {
        assertThrows(IllegalArgumentException::class.java) { QuizViewModel.parseQuizBatch(json.toString()) }
    }

    private fun source(id: Long, question: String = "Câu hỏi có nội dung", steps: String? = null) =
        QuestionAnswer().apply {
            this.id = id
            userId = 7
            subject = "math"
            this.question = question
            finalAnswer = "4"
            difficulty = "easy"
            this.steps = steps
        }

    private fun step(body: String, title: String = "", expr: String = "") =
        JSONObject().put("title", title).put("body", body).put("expr", expr)

    @Test fun productionNormalizerKeepsExistingGradingSemantics() {
        assertEquals("paris", QuizViewModel.normalizeAnswer("  Paris  "))
        assertEquals("new york city", QuizViewModel.normalizeAnswer("New   York\nCity"))
        assertEquals("x = 5", QuizViewModel.normalizeAnswer("x  =  5"))
        assertEquals("", QuizViewModel.normalizeAnswer(null))
        assertNotEquals(QuizViewModel.normalizeAnswer("Huế"), QuizViewModel.normalizeAnswer("Hue"))
    }

    @Test fun fullBatchUsesTheSameAnswerNormalizationAsGrading() {
        val parsed = QuizViewModel.parseQuizBatch(batch().toString())
        assertEquals(listOf("mcq", "short", "blank", "mcq"), parsed.map { it.type })
        val choices = JSONArray(parsed.first().optionsJson)
        assertEquals("New York", choices.getString(0))
        assertEquals(QuizViewModel.normalizeAnswer(choices.getString(0)),
            QuizViewModel.normalizeAnswer(parsed.first().correctAnswer))
        assertEquals("This follows from the source.", parsed.first().explanation)
    }

    @Test fun wrongBatchSizeOrOneInvalidItemRejectsTheWholeBatch() {
        reject(batch().apply { getJSONArray("items").remove(3) })
        reject(batch().apply { getJSONArray("items").put(item()) })
        reject(batch().apply { getJSONArray("items").getJSONObject(2).put("prompt", " ") })
        reject(batch().put("unexpected", true))
        reject(JSONObject().put("items", "not an array"))
    }

    @Test fun requiredItemFieldsMustHaveRealJsonTypesAndNonblankContent() {
        for (field in listOf("type", "prompt", "correctAnswer", "explanation")) {
            for (value in listOf<Any>(false, 42, JSONObject(), JSONArray(), JSONObject.NULL, " ")) {
                reject(batch().apply { getJSONArray("items").getJSONObject(0).put(field, value) })
            }
            reject(batch().apply { getJSONArray("items").getJSONObject(0).remove(field) })
        }
        reject(batch().apply { getJSONArray("items").getJSONObject(0).put("type", "unknown") })
        reject(batch().apply { getJSONArray("items").getJSONObject(0).put("options", "A,B,C,D") })
        reject(batch().apply { getJSONArray("items").getJSONObject(0).put("options", JSONArray(listOf("A", 2, "C", "D"))) })
    }

    @Test fun mcqRequiresFourDistinctNonemptyOptionsAndOneMatchingAnswer() {
        for (options in listOf(
            emptyList(), listOf("New York", "Paris", "Tokyo"),
            listOf("New York", "Paris", "Tokyo", "Huế", "Hanoi"),
            listOf("New York", " NEW\n YORK ", "Tokyo", "Huế"),
            listOf("New York", "", "Tokyo", "Huế"))) {
            reject(batch().apply { getJSONArray("items").getJSONObject(0).put("options", JSONArray(options)) })
        }
        reject(batch().apply { getJSONArray("items").getJSONObject(0).put("correctAnswer", "not an option") })
        reject(batch().apply { getJSONArray("items").getJSONObject(1).put("options", JSONArray(listOf("Paris"))) })
        reject(batch().apply { getJSONArray("items").getJSONObject(2).put("options", JSONArray(listOf("Paris"))) })
    }

    @Test fun filteringUsesExplicitMetadataWithoutClassifyingOrRejectingLegacySources() {
        fun metadata(mode: Any) = JSONArray().put(step("Preserved source.").put("_answer",
            JSONObject().put("version", 1).put("responseType", mode))).toString()
        val projected = QuizViewModel.projectSources(listOf(
            source(9), source(8, steps = metadata("clarification")), source(7, steps = metadata("limitation")),
            source(6, steps = metadata(42)), source(5, steps = metadata("direct"))
        ), placeholder)
        assertEquals(listOf(9L, 6L, 5L), projected.map { it.sourceId })
        assertEquals(setOf("question", "finalAnswer", "difficulty"), projected.first().data.keys().asSequence().toSet())
    }

    @Test fun imageProjectionPreservesCompleteCodeAndFormulaWithoutPrivateMetadata() {
        val body = "Giải thích\n```kotlin\nval n = 2\n```\nGiữ dấu tiếng Việt."
        val original = step(body, "Một bước", "x = 2").put("_answer",
            JSONObject().put("version", 1).put("answerType", "solution").put("secret", "METADATA_CANARY"))
        val qa = source(8, placeholder, JSONArray().put(original).toString()).apply { imagePath = "PATH_CANARY" }
        val projection = QuizViewModel.projectSources(listOf(qa), placeholder).single().data
        val teaching = projection.getJSONArray("teachingSteps")
        assertEquals(body, teaching.getJSONObject(0).getString("body"))
        assertEquals("x = 2", teaching.getJSONObject(0).getString("expr"))
        assertEquals(setOf("title", "body", "expr"), teaching.getJSONObject(0).keys().asSequence().toSet())
        assertTrue(teaching.toString().length <= QuizViewModel.IMAGE_CONTEXT_CHARS)
        assertFalse(projection.toString().contains("METADATA_CANARY"))
        assertFalse(projection.toString().contains("PATH_CANARY"))
        val data = JSONObject(QuizViewModel.buildQuizPrompt(
            QuizViewModel.projectSources(listOf(qa, source(7)), placeholder), "math", "vi-VN"))
        assertEquals("vi-VN", data.getString("fallbackLanguageTag"))
        assertEquals(body, data.getJSONArray("sources").getJSONObject(0).getJSONArray("teachingSteps")
            .getJSONObject(0).getString("body"))
    }

    @Test fun imageContextIsAWholeUsefulPrefixWithinExactSerializedCharacterLimit() {
        val overhead = step("").toString().length + 2
        val atLimit = step("a".repeat(QuizViewModel.IMAGE_CONTEXT_CHARS - overhead))
        fun projected(vararg steps: JSONObject) = QuizViewModel.projectSources(listOf(
            source(1, placeholder, JSONArray(steps.toList()).toString()).apply { imagePath = "fixture" }
        ), placeholder)
        val exact = projected(atLimit).single().data.getJSONArray("teachingSteps")
        assertEquals(QuizViewModel.IMAGE_CONTEXT_CHARS, exact.toString().length)
        assertEquals(atLimit.getString("body"), exact.getJSONObject(0).getString("body"))
        val oversized = step(atLimit.getString("body") + "a")
        assertTrue(projected(oversized, step("Later small step")).isEmpty())
        val prefix = projected(step(" "), step("Complete first step."), oversized, step("Do not skip ahead."))
            .single().data.getJSONArray("teachingSteps")
        assertEquals(1, prefix.length())
        assertEquals("Complete first step.", prefix.getJSONObject(0).getString("body"))
        assertTrue(projected(step(" ")).isEmpty())
    }

    @Test fun normalQuestionAndFinalAreNeverTruncatedAndImagesAreNotResent() {
        val qa = source(1, "q".repeat(4000), JSONArray().put(step("Must not be added.")).toString()).apply {
            imagePath = "PRIVATE_PATH"
            finalAnswer = "f".repeat(4000)
        }
        val data = QuizViewModel.projectSources(listOf(qa), placeholder).single().data
        assertEquals(qa.question, data.getString("question"))
        assertEquals(qa.finalAnswer, data.getString("finalAnswer"))
        assertFalse(data.has("teachingSteps"))
        assertFalse(data.has("imagePath"))
        assertFalse(data.toString().contains("PRIVATE_PATH"))
        assertEquals(1, QuizViewModel.projectSources(listOf(source(2, placeholder)), placeholder).size)
    }
}

