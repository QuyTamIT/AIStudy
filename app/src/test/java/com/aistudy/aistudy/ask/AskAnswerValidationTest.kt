package com.aistudy.aistudy.ask

import com.aistudy.aistudy.answer.AnswerContent
import com.aistudy.aistudy.answer.AnswerType
import com.aistudy.aistudy.answer.ResponseMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AskAnswerValidationTest {
    private fun answer(): JSONObject = JSONObject()
        .put("subject", "math").put("difficulty", "easy")
        .put("responseType", "direct").put("answerType", "solution")
        .put("finalAnswerRedundant", false)
        .put("steps", JSONArray().put(JSONObject().put("topicId", "").put("title", "")
            .put("body", "Hai cộng hai bằng bốn.").put("expr", "2 + 2 = 4")))
        .put("finalAnswer", "4")

    private fun rejected(json: JSONObject) {
        val error = assertThrows(Exception::class.java) { AskFragment.parseAnswer(json.toString()) }
        assertTrue(error is JSONException || error is IllegalArgumentException)
    }

    @Test fun simpleDirectSolutionDoesNotRequireMultipleSteps() {
        val saved = AskFragment.parseAnswer(answer().toString())
        val content = AnswerContent.read(saved.steps)
        assertEquals(ResponseMode.DIRECT, content.responseMode)
        assertEquals(AnswerType.SOLUTION, content.type)
        assertEquals("4", saved.finalAnswer)
        assertEquals("Hai cộng hai bằng bốn.", content.steps.single().body)
    }

    @Test fun directTopicListKeepsExpansionMetadataAndMathConclusionProtection() {
        val json = answer().put("subject", "language").put("answerType", "topic_list")
            .put("finalAnswerRedundant", true).put("finalAnswer", "Food and travel")
            .put("steps", JSONArray()
                .put(JSONObject().put("topicId", "food").put("title", "Ẩm thực").put("body", "Nói về món ăn.").put("expr", ""))
                .put(JSONObject().put("topicId", "travel").put("title", "Du lịch").put("body", "Kể về một chuyến đi.").put("expr", "")))
        val content = AnswerContent.read(AskFragment.parseAnswer(json.toString()).steps)
        assertEquals(ResponseMode.DIRECT, content.responseMode)
        assertEquals(AnswerType.TOPIC_LIST, content.type)
        assertTrue(content.canExpand)
        assertFalse(content.showFinalAnswer("language"))
        assertTrue(content.showFinalAnswer("math"))
    }

    @Test fun missingRequiredMetadataIsNotInferredForNewAnswers() {
        for (field in listOf("subject", "difficulty", "responseType", "answerType", "finalAnswerRedundant", "steps", "finalAnswer")) {
            rejected(answer().apply { remove(field) })
        }
    }

    @Test fun invalidEnumAndRealJsonTypesAreRejected() {
        for (field in listOf("subject", "difficulty", "responseType", "answerType")) {
            for (value in listOf<Any>(42, true, JSONObject(), JSONArray(), "unknown", JSONObject.NULL)) {
                rejected(answer().put(field, value))
            }
        }
        rejected(answer().put("subject", "MATH"))
        rejected(answer().put("difficulty", "MEDIUM"))
        rejected(answer().put("responseType", "DIRECT"))
        for (value in listOf<Any>("false", 0, JSONObject.NULL)) rejected(answer().put("finalAnswerRedundant", value))
    }

    @Test fun nonListRedundancyCannotSuppressUsefulFinalAnswer() {
        for (type in listOf("solution", "explanation")) {
            rejected(answer().put("answerType", type).put("finalAnswerRedundant", true))
        }
    }

    @Test fun finalAnswerMustContainRealTextInsteadOfFallingBackToStepTitle() {
        for (value in listOf<Any>("", " \n\t", 4, false, JSONObject(), JSONObject.NULL)) {
            rejected(answer().put("finalAnswer", value))
        }
    }

    @Test fun everyStepFieldMustBeAStringAndBodyMustBeUseful() {
        for (field in listOf("topicId", "title", "body", "expr")) {
            rejected(answer().apply { getJSONArray("steps").getJSONObject(0).remove(field) })
            for (value in listOf<Any>(1, false, JSONObject(), JSONArray(), JSONObject.NULL)) {
                rejected(answer().apply { getJSONArray("steps").getJSONObject(0).put(field, value) })
            }
        }
        rejected(answer().put("steps", JSONArray()))
        rejected(answer().apply { getJSONArray("steps").getJSONObject(0).put("body", " \n") })
    }

    @Test fun newTopicIdsMustBeCanonicalAndDistinct() {
        for (id in listOf("", "FOOD", "two words", "café", "food!", "-food")) {
            rejected(answer().put("answerType", "topic_list")
                .apply { getJSONArray("steps").getJSONObject(0).put("topicId", id) })
        }
        rejected(answer().put("answerType", "topic_list").apply {
            val step = getJSONArray("steps").getJSONObject(0).put("topicId", "food")
            getJSONArray("steps").put(JSONObject(step.toString()).put("title", "Another title"))
        })
    }

    @Test fun legacyReaderStillAcceptsOldMetadataAndMissingPresentationMode() {
        val legacy = """[{"title":"Old title","body":"Giữ nguyên nội dung cũ.","expr":"","_answer":{"version":1,"answerType":"solution","finalAnswerRedundant":true,"moreCount":0}}]"""
        val content = AnswerContent.read(legacy)
        assertEquals("Giữ nguyên nội dung cũ.", content.steps.single().body)
        assertEquals(ResponseMode.STEP_BY_STEP, content.responseMode)
        assertTrue(content.showFinalAnswer("math"))
    }
}
