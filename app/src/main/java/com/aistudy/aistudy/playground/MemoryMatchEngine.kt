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
            ConceptPair(1, "Toán học", "Định lý Pythagoras", "a² + b² = c²"),
            ConceptPair(2, "Toán học", "Số Pi (π)", "≈ 3.14159"),
            ConceptPair(3, "Toán học", "Đạo hàm của x²", "2x"),
            ConceptPair(4, "Toán học", "Diện tích hình tròn", "S = π · r²"),
            ConceptPair(5, "Toán học", "Định lý Thales", "Tỉ lệ song song"),

            // Science
            ConceptPair(6, "Khoa học", "Công thức Nước", "H₂O"),
            ConceptPair(7, "Khoa học", "Khí Carbonic", "CO₂"),
            ConceptPair(8, "Khoa học", "Thuyết tương đối", "E = m · c²"),
            ConceptPair(9, "Khoa học", "Định luật II Newton", "F = m · a"),
            ConceptPair(10, "Khoa học", "Vận tốc ánh sáng", "≈ 300.000 km/s"),
            ConceptPair(11, "Khoa học", "Muối ăn", "NaCl"),

            // Code
            ConceptPair(12, "Lập trình", "Độ phức tạp O(1)", "Thời gian hằng số"),
            ConceptPair(13, "Lập trình", "Binary Search", "Độ phức tạp O(log n)"),
            ConceptPair(14, "Lập trình", "Ngôn ngữ SQL", "Truy vấn dữ liệu"),
            ConceptPair(15, "Lập trình", "Cấu trúc FIFO", "Queue (Hàng đợi)"),
            ConceptPair(16, "Lập trình", "Cấu trúc LIFO", "Stack (Ngăn xếp)"),
            ConceptPair(17, "Lập trình", "HTTP 404", "Not Found"),

            // History
            ConceptPair(18, "Lịch sử", "Năm 1945", "CM Tháng 8 & Độc lập"),
            ConceptPair(19, "Lịch sử", "Năm 1975", "Thống nhất đất nước"),
            ConceptPair(20, "Lịch sử", "Năm 1911", "Bác Hồ ra đi tìm đường"),

            // Language
            ConceptPair(21, "Ngoại ngữ", "Synonym", "Từ đồng nghĩa"),
            ConceptPair(22, "Ngoại ngữ", "Antonym", "Từ trái nghĩa"),
            ConceptPair(23, "Ngoại ngữ", "Metaphor", "Phép ẩn dụ")
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

