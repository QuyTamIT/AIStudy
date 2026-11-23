package com.aistudy.aistudy.playground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

class MemoryMatchEngineTest {

    private lateinit var engine: MemoryMatchEngine

    @Before
    fun setUp() {
        engine = MemoryMatchEngine(Random(12345))
    }

    @Test
    fun testInitGameCardCountAndPairs() {
        val cards = engine.initGame(6)
        assertEquals(12, cards.size)
        assertEquals(6, engine.getTotalPairs())
        assertEquals(0, engine.getMatchedPairsCount())
        assertEquals(0, engine.getMoves())

        // Ensure 6 pairs exist (2 cards per pairId)
        val pairCounts = cards.groupBy { it.pairId }
        assertEquals(6, pairCounts.size)
        pairCounts.forEach { (_, pairCards) ->
            assertEquals(2, pairCards.size)
            // One prompt and one definition
            assertTrue(pairCards.any { it.isPrompt })
            assertTrue(pairCards.any { !it.isPrompt })
            assertTrue(pairCards[0].subjectResId != 0)
            assertTrue(pairCards[0].textResId != 0)
            assertTrue(pairCards[1].textResId != 0)
        }
    }

    @Test
    fun testCheckMatchSuccessAndFailure() {
        val cards = engine.initGame(6)
        val firstPairId = cards[0].pairId
        val matchingIndex = cards.indexOfLast { it.pairId == firstPairId }
        val nonMatchingIndex = cards.indexOfFirst { it.pairId != firstPairId }

        // Test non-matching
        val isNonMatch = engine.checkMatch(0, nonMatchingIndex)
        assertFalse(isNonMatch)
        assertFalse(engine.getCard(0)!!.isMatched)
        assertFalse(engine.getCard(nonMatchingIndex)!!.isMatched)
        assertEquals(0, engine.getMatchedPairsCount())

        // Test matching
        val isMatch = engine.checkMatch(0, matchingIndex)
        assertTrue(isMatch)
        assertTrue(engine.getCard(0)!!.isMatched)
        assertTrue(engine.getCard(matchingIndex)!!.isMatched)
        assertEquals(1, engine.getMatchedPairsCount())
    }

    @Test
    fun testGameOverDetection() {
        val cards = engine.initGame(4)
        assertFalse(engine.isGameOver())

        // Match all 4 pairs
        val pairGroups = cards.indices.groupBy { cards[it].pairId }
        pairGroups.values.forEach { indices ->
            engine.checkMatch(indices[0], indices[1])
        }

        assertTrue(engine.isGameOver())
        assertEquals(4, engine.getMatchedPairsCount())
    }

    @Test
    fun testStarRatings() {
        engine.initGame(6) // totalPairs = 6
        // <= 14 moves: 3 stars
        assertEquals(3, engine.calculateStars(10))
        assertEquals(3, engine.calculateStars(14))
        // 15..20 moves: 2 stars
        assertEquals(2, engine.calculateStars(15))
        assertEquals(2, engine.calculateStars(20))
        // > 20 moves: 1 star
        assertEquals(1, engine.calculateStars(25))
    }

    @Test
    fun testFormatTime() {
        assertEquals("00:05", engine.formatTime(5))
        assertEquals("01:23", engine.formatTime(83))
        assertEquals("02:00", engine.formatTime(120))
    }
}

