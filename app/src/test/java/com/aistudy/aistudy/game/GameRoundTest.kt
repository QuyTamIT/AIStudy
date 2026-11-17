package com.aistudy.aistudy.game

import org.junit.Assert.*
import org.junit.Test

class GameRoundTest {
    private val deck = GameQuestionBank.questions.filter { it.subject == "science" }

    @Test fun comboRewardsStreaksCapsBonusAndResetsOnMistake() {
        val round = GameRound(deck, false, 0)
        repeat(6) { assertTrue(round.submit(it, 0, 0)) }
        assertEquals(950, round.score) // 100 + 125 + 150 + 175 + 200 + 200
        assertEquals(6, round.combo)
        assertTrue(round.submit(6, 1, 0))
        assertEquals(0, round.combo)
        assertTrue(round.submit(7, 0, 0))
        assertEquals(1050, round.score)
        assertEquals(6, round.bestCombo)
        assertEquals(7, round.correctCount)
    }

    @Test fun deadlineRejectsLateAnswersIncludingAfterBackground() {
        val round = GameRound(deck, true, 60_000)
        assertTrue(round.submit(0, 0, 59_999))
        assertFalse(round.submit(1, 0, 60_000))
        assertTrue(round.finished)
        assertEquals(listOf(0), round.answers)
        assertEquals(0L, round.remainingMs(80_000))
    }

    @Test fun untimedModeDoesNotExpireAndRoundEndsAfterLastQuestion() {
        val round = GameRound(deck, false, 1)
        assertFalse(round.expire(Long.MAX_VALUE))
        deck.indices.forEach { assertTrue(round.submit(it, 0, Long.MAX_VALUE)) }
        assertTrue(round.finished)
        assertFalse(round.submit(deck.size, 0, Long.MAX_VALUE))
        assertEquals(10, round.correctCount)
    }

    @Test fun duplicateTapAndInvalidOptionsCannotChangeScore() {
        val round = GameRound(deck, false, 0)
        assertFalse(round.submit(0, -1, 0))
        assertFalse(round.submit(0, 4, 0))
        assertTrue(round.submit(0, 0, 0))
        assertFalse(round.submit(0, 0, 0))
        assertFalse(round.submit(3, 0, 0))
        assertEquals(100, round.score)
        assertEquals(1, round.answers.size)
    }

    @Test fun recreationRestoresScoreComboAndOriginalDeadline() {
        val original = GameRound(deck, true, 60_000)
        listOf(0, 1, 0, 0).forEachIndexed { i, choice -> original.submit(i, choice, i * 1000L) }
        val restored = GameRound(deck, true, original.deadlineMs, original.answers, original.finished)
        assertEquals(original.score, restored.score)
        assertEquals(original.bestCombo, restored.bestCombo)
        assertEquals(2, restored.combo)
        assertEquals(10_000L, restored.remainingMs(50_000))
        assertTrue(restored.expire(61_000))
        assertFalse(restored.submit(4, 0, 61_000))
    }

    @Test fun bankHasTenValidDistinctQuestionsPerSubject() {
        assertEquals(50, GameQuestionBank.questions.size)
        assertEquals(50, GameQuestionBank.questions.map { it.id }.distinct().size)
        GameQuestionBank.subjects.forEach { subject ->
            val questions = GameQuestionBank.questions.filter { it.subject == subject }
            assertEquals(10, questions.size)
            assertEquals(10, questions.map { it.prompt }.distinct().size)
            questions.forEach {
                assertEquals(4, it.options.distinct().size)
                assertTrue(it.correctIndex in it.options.indices)
                assertTrue(it.prompt.isNotBlank() && it.explanation.isNotBlank())
            }
        }
    }
}
