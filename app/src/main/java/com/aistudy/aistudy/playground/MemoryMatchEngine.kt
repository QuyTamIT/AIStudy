package com.aistudy.aistudy.playground

import java.util.Random

data class ConceptPair(
    val pairId: Int,
    val subject: String,
    val term: String,
    val definition: String
)

data class ConceptCard(
    val id: Int,
    val pairId: Int,
    val text: String,
    val subject: String,
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
            ConceptPair(1, "Math", "Pythagorean theorem", "a² + b² = c²"),
            ConceptPair(2, "Math", "Pi (π)", "≈ 3.14159"),
            ConceptPair(3, "Math", "Derivative of x²", "2x"),
            ConceptPair(4, "Math", "Area of a circle", "S = π · r²"),
            ConceptPair(5, "Math", "Triangle proportionality theorem", "Parallel lines divide sides proportionally"),

            // Science
            ConceptPair(6, "Science", "Water formula", "H₂O"),
            ConceptPair(7, "Science", "Carbon dioxide", "CO₂"),
            ConceptPair(8, "Science", "Relativity", "E = m · c²"),
            ConceptPair(9, "Science", "Newton’s second law", "F = m · a"),
            ConceptPair(10, "Science", "Speed of light", "≈ 300,000 km/s"),
            ConceptPair(11, "Science", "Table salt", "NaCl"),

            // Code
            ConceptPair(12, "Programming", "Complexity O(1)", "Constant time"),
            ConceptPair(13, "Programming", "Binary Search", "Complexity O(log n)"),
            ConceptPair(14, "Programming", "SQL", "Query data"),
            ConceptPair(15, "Programming", "FIFO structure", "Queue"),
            ConceptPair(16, "Programming", "LIFO structure", "Stack"),
            ConceptPair(17, "Programming", "HTTP 404", "Not Found"),

            // History
            ConceptPair(18, "History", "Vietnam, 1945", "August Revolution & Independence"),
            ConceptPair(19, "History", "Vietnam, 1975", "Reunification"),
            ConceptPair(20, "History", "Vietnam, 1911", "Ho Chi Minh leaves Vietnam"),

            // Language
            ConceptPair(21, "Languages", "Synonym", "Words with similar meanings"),
            ConceptPair(22, "Languages", "Antonym", "Words with opposite meanings"),
            ConceptPair(23, "Languages", "Metaphor", "Figurative comparison")
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
                    text = pair.term,
                    subject = pair.subject,
                    isPrompt = true
                )
            )
            // Card 2: Definition / Match
            cards.add(
                ConceptCard(
                    id = cardId++,
                    pairId = pair.pairId,
                    text = pair.definition,
                    subject = pair.subject,
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

