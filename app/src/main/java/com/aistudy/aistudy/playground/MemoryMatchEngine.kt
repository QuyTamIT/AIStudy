package com.aistudy.aistudy.playground

import androidx.annotation.StringRes
import com.aistudy.aistudy.R
import java.util.Random

data class ConceptPair(
    val pairId: Int,
    @param:StringRes val subjectResId: Int,
    @param:StringRes val termResId: Int,
    @param:StringRes val definitionResId: Int
)

data class ConceptCard(
    val id: Int,
    val pairId: Int,
    @param:StringRes val textResId: Int,
    @param:StringRes val subjectResId: Int,
    val isPrompt: Boolean,
    var isFlipped: Boolean = false,
    var isMatched: Boolean = false
)

data class MemoryMatchResult(
    val moves: Int,
    val timeSeconds: Int,
    val stars: Int,
    val xpEarned: Int
)

class MemoryMatchEngine(private val random: Random = Random()) {

    companion object {
        const val DEFAULT_PAIRS_COUNT = 6 // 12 cards total (fits 3x4 or 4x3 grid)
        const val BASE_WIN_XP = 30
        const val STAR_BONUS_XP = 5

        val CONCEPT_CATALOG: List<ConceptPair> = listOf(
            // Math
            ConceptPair(1, R.string.memory_subject_math, R.string.memory_term_pythagorean, R.string.memory_definition_pythagorean),
            ConceptPair(2, R.string.memory_subject_math, R.string.memory_term_pi, R.string.memory_definition_pi),
            ConceptPair(3, R.string.memory_subject_math, R.string.memory_term_derivative, R.string.memory_definition_derivative),
            ConceptPair(4, R.string.memory_subject_math, R.string.memory_term_circle_area, R.string.memory_definition_circle_area),
            ConceptPair(5, R.string.memory_subject_math, R.string.memory_term_thales, R.string.memory_definition_thales),

            // Science
            ConceptPair(6, R.string.memory_subject_science, R.string.memory_term_water_formula, R.string.memory_definition_water_formula),
            ConceptPair(7, R.string.memory_subject_science, R.string.memory_term_carbon_dioxide, R.string.memory_definition_carbon_dioxide),
            ConceptPair(8, R.string.memory_subject_science, R.string.memory_term_relativity, R.string.memory_definition_relativity),
            ConceptPair(9, R.string.memory_subject_science, R.string.memory_term_newton_second_law, R.string.memory_definition_newton_second_law),
            ConceptPair(10, R.string.memory_subject_science, R.string.memory_term_light_speed, R.string.memory_definition_light_speed),
            ConceptPair(11, R.string.memory_subject_science, R.string.memory_term_table_salt, R.string.memory_definition_table_salt),

            // Code
            ConceptPair(12, R.string.memory_subject_programming, R.string.memory_term_constant_complexity, R.string.memory_definition_constant_complexity),
            ConceptPair(13, R.string.memory_subject_programming, R.string.memory_term_binary_search, R.string.memory_definition_binary_search),
            ConceptPair(14, R.string.memory_subject_programming, R.string.memory_term_sql, R.string.memory_definition_sql),
            ConceptPair(15, R.string.memory_subject_programming, R.string.memory_term_fifo, R.string.memory_definition_fifo),
            ConceptPair(16, R.string.memory_subject_programming, R.string.memory_term_lifo, R.string.memory_definition_lifo),
            ConceptPair(17, R.string.memory_subject_programming, R.string.memory_term_http_404, R.string.memory_definition_http_404),

            // History
            ConceptPair(18, R.string.memory_subject_history, R.string.memory_term_year_1945, R.string.memory_definition_year_1945),
            ConceptPair(19, R.string.memory_subject_history, R.string.memory_term_year_1975, R.string.memory_definition_year_1975),
            ConceptPair(20, R.string.memory_subject_history, R.string.memory_term_year_1911, R.string.memory_definition_year_1911),

            // Language
            ConceptPair(21, R.string.memory_subject_language, R.string.memory_term_synonym, R.string.memory_definition_synonym),
            ConceptPair(22, R.string.memory_subject_language, R.string.memory_term_antonym, R.string.memory_definition_antonym),
            ConceptPair(23, R.string.memory_subject_language, R.string.memory_term_metaphor, R.string.memory_definition_metaphor)
        )
    }

    private val cards = mutableListOf<ConceptCard>()
    private var moves = 0
    private var matchedPairsCount = 0
    private var totalPairs = DEFAULT_PAIRS_COUNT

    fun initGame(pairsCount: Int = DEFAULT_PAIRS_COUNT): List<ConceptCard> {
        totalPairs = pairsCount.coerceIn(4, minOf(pairsCount, CONCEPT_CATALOG.size))
        val selectedPairs = CONCEPT_CATALOG.shuffled(random).take(totalPairs)

        cards.clear()
        moves = 0
        matchedPairsCount = 0

        var cardId = 0
        for (pair in selectedPairs) {
            // Card 1: Term / Concept
            cards.add(
                ConceptCard(
                    id = cardId++,
                    pairId = pair.pairId,
                    textResId = pair.termResId,
                    subjectResId = pair.subjectResId,
                    isPrompt = true
                )
            )
            // Card 2: Definition / Match
            cards.add(
                ConceptCard(
                    id = cardId++,
                    pairId = pair.pairId,
                    textResId = pair.definitionResId,
                    subjectResId = pair.subjectResId,
                    isPrompt = false
                )
            )
        }

        cards.shuffle(random)
        return cards.toList()
    }

    fun getCards(): List<ConceptCard> = cards.toList()

    fun getCard(index: Int): ConceptCard? {
        if (index in cards.indices) return cards[index]
        return null
    }

    fun incrementMoves() {
        moves++
    }

    fun getMoves(): Int = moves

    fun getMatchedPairsCount(): Int = matchedPairsCount

    fun getTotalPairs(): Int = totalPairs

    fun checkMatch(firstIndex: Int, secondIndex: Int): Boolean {
        if (firstIndex !in cards.indices || secondIndex !in cards.indices) return false
        if (firstIndex == secondIndex) return false

        val card1 = cards[firstIndex]
        val card2 = cards[secondIndex]

        val isMatch = card1.pairId == card2.pairId
        if (isMatch) {
            card1.isMatched = true
            card2.isMatched = true
            matchedPairsCount++
        }
        return isMatch
    }

    fun isGameOver(): Boolean {
        return matchedPairsCount >= totalPairs
    }

    fun calculateStars(moves: Int): Int {
        // Efficiency threshold: 6 pairs minimum 6 moves (perfect).
        // <= 14 moves: 3 stars
        // <= 20 moves: 2 stars
        // > 20 moves: 1 star
        return when {
            moves <= totalPairs * 2 + 2 -> 3
            moves <= totalPairs * 3 + 2 -> 2
            else -> 1
        }
    }

    fun calculateResult(timeSeconds: Int): MemoryMatchResult {
        val stars = calculateStars(moves)
        val xp = BASE_WIN_XP + (stars * STAR_BONUS_XP)
        return MemoryMatchResult(
            moves = moves,
            timeSeconds = timeSeconds,
            stars = stars,
            xpEarned = xp
        )
    }

    fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}

