package com.aistudy.aistudy.answer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnswerContentTest {
    private fun topic(id: String, title: String = id, body: String = "Discuss $id.") =
        AnswerStep(title, body, "", id)

    private fun listContent() = AnswerContent(
        listOf(topic("food"), topic("travel")), AnswerType.TOPIC_LIST, true, 0
    )

    @Test fun hidesOnlyRedundantListFinalAndAlwaysKeepsMathFinal() {
        val content = listContent()
        assertFalse(content.showFinalAnswer("language"))
        assertTrue(content.showFinalAnswer("math"))
        assertTrue(content.copy(finalAnswerRedundant = false).showFinalAnswer("language"))
        assertTrue(content.copy(type = AnswerType.SOLUTION).showFinalAnswer("science"))
    }

    @Test fun legacyStepsRemainReadableWithoutGuessingTypeFromWords() {
        val stored = """[{"title":"Five topics","body":"Keep\nthis text.","expr":"x + 2"}]"""
        val content = AnswerContent.read(stored)
        assertNull(content.type)
        assertEquals("Keep\nthis text.", content.steps.single().body)
        assertTrue(content.showFinalAnswer("language"))
        assertFalse(content.canExpand)
    }

    @Test fun roundTripKeepsOldArrayFormatAndExpansionLimitAcrossReopening() {
        var content = listContent()
        repeat(3) { round ->
            content = content.append((1..3).map { topic("extra-$round-$it") })
            content = AnswerContent.read(content.toJson())
        }
        assertEquals(11, content.steps.size)
        assertEquals(3, content.moreCount)
        assertFalse(content.canExpand)
        assertEquals("food", JSONArray(content.toJson()).getJSONObject(0).getString("title"))
        assertThrows(IllegalStateException::class.java) {
            content.append((1..3).map { topic("fourth-$it") })
        }
    }

    @Test fun appendsOnlyNewTopicsAndPreservesOriginalContentAndOrder() {
        val original = listContent()
        val batch = listOf(
            topic("FOOD!", "Reworded food"),
            topic("different-id", "TRAVEL!"),
            topic("books"), topic("music"), topic("work")
        )
        val updated = original.append(batch)
        assertEquals(original.steps, updated.steps.take(2))
        assertEquals(listOf("food", "travel", "books", "music", "work"), updated.steps.map { it.topicId })
        assertEquals(1, updated.moreCount)
    }

    @Test fun duplicateBodiesAndDuplicateItemsWithinBatchDoNotConsumeLimit() {
        val original = listContent()
        assertThrows(IllegalArgumentException::class.java) {
            original.append(listOf(
                topic("other", "Different", "Discuss food."),
                topic("books"), topic("BOOKS!", "Books again")
            ))
        }
        assertEquals(0, original.moreCount)
        assertEquals(2, original.steps.size)
    }

    @Test fun classificationAddsMetadataWithoutRewritingExistingItems() {
        val legacy = AnswerContent.read("""[{"title":"Food","body":"Try a recipe.","expr":""}]""")
        val classified = legacy.classified(JSONObject("""
            {"answerType":"topic_list","finalAnswerRedundant":true,"topicIds":["food"]}
        """))
        assertEquals(legacy.steps.single().body, classified.steps.single().body)
        assertEquals("food", classified.steps.single().topicId)
        assertTrue(classified.canExpand)
    }

    @Test fun generatedMetadataIsValidatedRatherThanInferredFromQuestionKeywords() {
        val json = JSONObject("""
            {"answerType":"topic_list","finalAnswerRedundant":true,
             "steps":[{"topicId":"food","title":"Food","body":"Order lunch.","expr":""}]}
        """)
        assertEquals(AnswerType.TOPIC_LIST, AnswerContent.generated(json).type)
        json.put("answerType", "made_up")
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(json) }
    }
}
