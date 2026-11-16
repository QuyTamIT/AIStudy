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
            {"responseType":"direct","answerType":"topic_list","finalAnswerRedundant":true,
             "steps":[{"topicId":"food","title":"Food","body":"Order lunch.","expr":""}]}
        """)
        assertEquals(AnswerType.TOPIC_LIST, AnswerContent.generated(json).type)
        json.put("answerType", "made_up")
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(json) }
    }

    @Test fun generatedRequiresTypedIndependentMetadataAndCanonicalIds() {
        val base = JSONObject("""
            {"responseType":"direct","answerType":"explanation","finalAnswerRedundant":false,
             "steps":[{"topicId":"","title":"Definition","body":"A clear explanation.","expr":""}]}
        """)
        assertEquals(AnswerType.EXPLANATION, AnswerContent.generated(base).type)
        base.put("responseType", 1)
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(base) }
        base.put("responseType", "direct").put("answerType", 1)
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(base) }
        base.put("answerType", "explanation").put("finalAnswerRedundant", 1)
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(base) }
        base.put("finalAnswerRedundant", true)
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(base) }
        base.put("finalAnswerRedundant", false)
            .put("steps", JSONArray().put(JSONObject()
                .put("topicId", "not allowed!").put("title", "Definition")
                .put("body", "Text").put("expr", "")))
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(base) }
    }

    @Test fun generatedTopicIdsAreCanonicalAndUniqueButLegacyClassificationMayRepeat() {
        fun generated(ids: List<String>) = JSONObject().apply {
            put("responseType", "direct").put("answerType", "topic_list")
                .put("finalAnswerRedundant", true)
            put("steps", JSONArray(ids.map { id ->
                JSONObject().put("topicId", id).put("title", id)
                    .put("body", "Discuss $id.").put("expr", "")
            }))
        }
        assertEquals(2, AnswerContent.generated(generated(listOf("food", "travel"))).steps.size)
        assertThrows(IllegalArgumentException::class.java) {
            AnswerContent.generated(generated(listOf("food", "food")))
        }
        val legacy = AnswerContent.read("""[
            {"title":"A","body":"A body","expr":""},
            {"title":"B","body":"B body","expr":""}
        ]""")
        val classified = legacy.classified(JSONObject("""
            {"answerType":"topic_list","finalAnswerRedundant":true,"topicIds":["same","same"]}
        """))
        assertEquals(listOf("same", "same"), classified.steps.map { it.topicId })
    }

    @Test fun generatedStepsRejectWrongTypesButLegacyReadRemainsCompatible() {
        val step = JSONObject().put("topicId", "").put("title", "Title")
            .put("body", "Body").put("expr", "")
        val generated = JSONObject().put("responseType", "direct").put("answerType", "explanation")
            .put("finalAnswerRedundant", false).put("steps", JSONArray().put(step))
        step.put("body", 42)
        assertThrows(IllegalArgumentException::class.java) { AnswerContent.generated(generated) }
        assertEquals("Legacy body", AnswerContent.read("""[{"body":"Legacy body"}]""").steps.single().body)
    }

    @Test fun moreDeduplicatesLegacyIdsBeforeCanonicalValidationAndConsumesOnlySuccess() {
        val legacy = AnswerContent(
            listOf(topic("FOOD!")), AnswerType.TOPIC_LIST, false, 0
        )
        val updated = legacy.append(listOf(
            topic("food", "Reworded food"),
            topic("books"), topic("music"), topic("work")
        ))
        assertEquals(4, updated.steps.size)
        assertEquals(1, updated.moreCount)
        assertThrows(IllegalArgumentException::class.java) {
            legacy.append(listOf(topic("bad id!"), topic("one"), topic("two")))
        }
        assertEquals(0, legacy.moreCount)
    }

    @Test fun extrasValidateExactSchemaAndSelectedLegacyFieldsOnly() {
        val extras = JSONObject()
            .put("simplify", "A simpler explanation")
            .put("alternative", "Another approach")
            .put("concepts", JSONArray().put("Concept one"))
            .put("mistakes", JSONArray().put("Mistake one").put("Mistake two"))
            .put("followUps", JSONArray().put("What next?"))
        assertSame(extras, AnswerContent.validateExtras(extras))
        assertEquals("• Mistake one\n\n• Mistake two", AnswerContent.extraField(extras, "mistakes"))
        val partial = JSONObject().put("mistakes", JSONArray().put("Legacy item"))
            .put("unusedMalformedField", JSONObject())
        assertEquals("• Legacy item", AnswerContent.extraField(partial, "mistakes"))
        assertNull(AnswerContent.extraField(JSONObject().put("mistakes", 42), "mistakes"))
        assertThrows(IllegalArgumentException::class.java) {
            AnswerContent.validateExtras(JSONObject(extras.toString()).put("concepts", JSONArray().put(42)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnswerContent.validateExtras(JSONObject(extras.toString()).put("unexpected", true))
        }
    }

    @Test fun everyExtrasFieldHasStrictTypeAndUsefulContent() {
        fun valid() = JSONObject("""{"simplify":"Simple","alternative":"Another way","concepts":["One"],"mistakes":["Two"],"followUps":["Next?"]}""")
        for (field in listOf("simplify", "alternative", "concepts", "mistakes", "followUps")) {
            assertThrows(IllegalArgumentException::class.java) {
                AnswerContent.validateExtras(valid().apply { remove(field) })
            }
            for (value in listOf<Any>(42, false, JSONObject(), JSONObject.NULL)) {
                assertThrows(IllegalArgumentException::class.java) {
                    AnswerContent.validateExtras(valid().put(field, value))
                }
            }
        }
        for (field in listOf("simplify", "alternative")) {
            for (value in listOf<Any>(" ", JSONArray().put("Wrong shape"))) {
                assertThrows(IllegalArgumentException::class.java) {
                    AnswerContent.validateExtras(valid().put(field, value))
                }
                assertNull(AnswerContent.extraField(JSONObject().put(field, value), field))
            }
        }
        for (field in listOf("concepts", "mistakes", "followUps")) {
            for (value in listOf<Any>("Wrong shape", JSONArray(), JSONArray().put(" "), JSONArray().put("Good").put(2))) {
                assertThrows(IllegalArgumentException::class.java) {
                    AnswerContent.validateExtras(valid().put(field, value))
                }
                assertNull(AnswerContent.extraField(JSONObject().put(field, value), field))
            }
            assertNull(AnswerContent.extraField(JSONObject(), field))
        }
    }

    @Test fun extrasProjectionDoesNotInventLegacyMetadataOrSendInternalFields() {
        val row = com.aistudy.aistudy.database.QuestionAnswer().apply {
            question = "Câu hỏi"; finalAnswer = "Kết luận"
            steps = """[{"title":"Tiêu đề","body":"Nội dung nguồn","expr":""}]"""
        }
        val legacy = AnswerSnapshot(row, AnswerContent.read(row.steps))
        val data = JSONObject(AnswerPrompts.extras(legacy))
        assertTrue(data.isNull("responseType"))
        assertTrue(data.isNull("answerType"))
        assertEquals("Nội dung nguồn", data.getJSONArray("steps").getJSONObject(0).getString("body"))
        val tagged = legacy.content.copy(type = AnswerType.EXPLANATION, responseMode = ResponseMode.CREATIVE)
        row.steps = tagged.toJson()
        val metadata = JSONArray(row.steps).getJSONObject(0).getJSONObject("_answer")
            .put("privateMarker", "INTERNAL_METADATA_CANARY")
        val stored = JSONArray(row.steps).apply { getJSONObject(0).put("_answer", metadata) }
        row.steps = stored.toString()
        val known = JSONObject(AnswerPrompts.extras(AnswerSnapshot(row, AnswerContent.read(row.steps))))
        assertEquals("creative", known.getString("responseType"))
        assertEquals("explanation", known.getString("answerType"))
        assertFalse(known.getJSONArray("steps").getJSONObject(0).has("_answer"))
        assertFalse(known.toString().contains("INTERNAL_METADATA_CANARY"))
    }
}
