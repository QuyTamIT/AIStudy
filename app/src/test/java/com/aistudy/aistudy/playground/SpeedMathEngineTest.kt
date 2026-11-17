package com.aistudy.aistudy.playground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

class SpeedMathEngineTest {

    private lateinit var engine: SpeedMathEngine

    @Before
    fun setUp() {
        engine = SpeedMathEngine(Random(42))
    }

    @Test
    fun testGenerateQuestionStructure() {
        for (i in 1..20) {
            val q = engine.generateQuestion()
            assertNotNull(q.prompt)
            assertTrue(q.prompt.isNotEmpty())
            assertEquals(4, q.options.size)
            assertTrue(q.correctIndex in 0..3)
            assertEquals(4, q.options.toSet().size) // All 4 choices must be strictly unique!
            assertNotNull(q.category)
            assertNotNull(q.explanation)
        }
    }

    @Test
    fun testEvaluateAnswer() {
        val q = engine.generateQuestion()
        assertTrue(engine.evaluateAnswer(q, q.correctIndex))
        val wrongIndex = (q.correctIndex + 1) % 4
        assertFalse(engine.evaluateAnswer(q, wrongIndex))
    }

    @Test
    fun testComboMultiplierThresholds() {
        assertEquals(1, engine.calculateComboMultiplier(0))
        assertEquals(1, engine.calculateComboMultiplier(2))
        assertEquals(2, engine.calculateComboMultiplier(3))
        assertEquals(2, engine.calculateComboMultiplier(5))
        assertEquals(3, engine.calculateComboMultiplier(6))
        assertEquals(3, engine.calculateComboMultiplier(9))
        assertEquals(4, engine.calculateComboMultiplier(10))
        assertEquals(4, engine.calculateComboMultiplier(20))
    }

    @Test
    fun testScoreAndComboProgression() {
        // Correct answers build combo and score
        var score = 0
        var combo = 0

        // 1st correct: combo 0->1, mult 1, +10 => score 10
        val step1 = engine.updateScoreAndCombo(score, combo, true)
        assertEquals(10, step1.first)
        assertEquals(1, step1.second)

        // 3 consecutive corrects reach combo 3
        var current = Pair(0, 0)
        for (i in 1..3) {
            current = engine.updateScoreAndCombo(current.first, current.second, true)
        }
        assertEquals(3, current.second) // combo = 3
        // 4th correct with combo 3 uses multiplier 2 (+20 points)
        val step4 = engine.updateScoreAndCombo(current.first, current.second, true)
        assertEquals(current.first + 20, step4.first)
        assertEquals(4, step4.second)

        // Incorrect resets combo to 0 without decreasing score
        val miss = engine.updateScoreAndCombo(step4.first, step4.second, false)
        assertEquals(step4.first, miss.first)
        assertEquals(0, miss.second)
    }

    @Test
    fun testResultCalculation() {
        val res = engine.calculateResult(score = 150, correctCount = 8, totalAnswered = 10, maxCombo = 5)
        assertEquals(150, res.score)
        assertEquals(8, res.correctCount)
        assertEquals(10, res.totalAnswered)
        assertEquals(5, res.maxCombo)
        // XP: 8 * 5 + 5 * 2 + 15 = 40 + 10 + 15 = 65 XP
        assertEquals(65, res.xpEarned)
    }
}

