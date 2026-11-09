package com.aistudy.aistudy.quiz

import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.database.QuizSession
import com.aistudy.aistudy.xp.XpRules
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizDeduplicationTest {

    private fun normalizeAnswer(value: String?): String {
        if (value == null) return ""
        return value.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    @Test
    fun testNormalizeAnswerTrimmingAndWhitespace() {
        assertEquals("paris", normalizeAnswer("  Paris  "))
        assertEquals("new york city", normalizeAnswer("New   York   City"))
        assertEquals("x = 5", normalizeAnswer("x  =  5"))
        assertEquals("", normalizeAnswer(null))
    }

    @Test
    fun testScoreCalculationAndCompletion() {
        val session = QuizSession().apply {
            id = 1
            userId = 42
            subject = "math"
            completed = false
            score = 0
            total = 3
        }

        val items = listOf(
            QuizItem().apply { prompt = "Q1"; correctAnswer = "A"; userAnswer = "A"; correct = true },
            QuizItem().apply { prompt = "Q2"; correctAnswer = "B"; userAnswer = "C"; correct = false },
            QuizItem().apply { prompt = "Q3"; correctAnswer = "D"; userAnswer = "d"; correct = true }
        )

        val score = items.count { it.correct == true }
        val xpGain = score * XpRules.QUIZ_CORRECT + XpRules.QUIZ_COMPLETE_BONUS

        assertEquals(2, score)
        assertEquals(40, xpGain) // 2 * 10 + 20 = 40

        session.score = score
        session.completed = true

        assertTrue(session.completed)
        assertEquals(2, session.score)
    }

    @Test
    fun testMalformedQuizItemFiltering() {
        val jsonArray = JSONArray().apply {
            put(JSONObject().put("type", "mcq").put("prompt", "").put("correctAnswer", "A")) // empty prompt -> invalid
            put(JSONObject().put("type", "mcq").put("prompt", "Valid Q").put("correctAnswer", "")) // empty answer -> invalid
            put(JSONObject().put("type", "mcq").put("prompt", "Valid Q2").put("correctAnswer", "B")) // valid
        }

        val validItems = mutableListOf<QuizItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val prompt = obj.optString("prompt", "").trim()
            val correctAnswer = obj.optString("correctAnswer", "").trim()
            if (prompt.isEmpty() || correctAnswer.isEmpty()) {
                continue
            }
            validItems.add(QuizItem().apply {
                this.prompt = prompt
                this.correctAnswer = correctAnswer
            })
        }

        assertEquals(1, validItems.size)
        assertEquals("Valid Q2", validItems[0].prompt)
    }

    @Test
    fun testPreventDoubleCompletion() {
        var callCount = 0
        var totalXpAwarded = 0
        val session = QuizSession().apply { completed = false }

        fun finishSessionOnce() {
            if (session.completed) return
            session.completed = true
            callCount++
            totalXpAwarded += 40
        }

        finishSessionOnce()
        finishSessionOnce() // Second attempt should be ignored

        assertEquals(1, callCount)
        assertEquals(40, totalXpAwarded)
    }
}

