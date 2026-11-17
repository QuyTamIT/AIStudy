package com.aistudy.aistudy.playground

import java.util.Random

data class MathQuestion(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: String,
    val explanation: String
)

data class GameResult(
    val score: Int,
    val correctCount: Int,
    val totalAnswered: Int,
    val maxCombo: Int,
    val xpEarned: Int
)

class SpeedMathEngine(private val random: Random = Random()) {

    companion object {
        const val GAME_DURATION_SECONDS = 45
        const val BASE_POINTS_PER_CORRECT = 10
        const val BASE_XP_PER_CORRECT = 5
        const val BONUS_XP_PER_COMBO = 2
        const val COMPLETION_XP_BONUS = 15
        const val MAX_XP_LIMIT = 80
    }

    private var previousPrompt: String? = null

    fun generateQuestion(level: Int = 1): MathQuestion {
        // Try up to 5 times to avoid immediate repeating question
        for (attempt in 0..4) {
            val q = when (random.nextInt(5)) {
                0 -> generateArithmetic()
                1 -> generateOrderOfOperations()
                2 -> generatePowersAndRoots()
                3 -> generateAlgebraEquation()
                else -> generateCodeLogic()
            }
            if (q.prompt != previousPrompt || attempt == 4) {
                previousPrompt = q.prompt
                return q
            }
        }
        return generateArithmetic()
    }

    private fun generateArithmetic(): MathQuestion {
        val type = random.nextInt(4)
        return when (type) {
            0 -> {
                val a = random.nextInt(60) + 15
                val b = random.nextInt(60) + 15
                val ans = a + b
                val prompt = "$a + $b = ?"
                createQuestion(prompt, ans, "Addition", "$a + $b = $ans")
            }
            1 -> {
                val a = random.nextInt(70) + 30
                val b = random.nextInt(a - 15) + 10
                val ans = a - b
                val prompt = "$a − $b = ?"
                createQuestion(prompt, ans, "Subtraction", "$a − $b = $ans")
            }
            2 -> {
                val a = random.nextInt(9) + 6 // 6..14
                val b = random.nextInt(8) + 4 // 4..11
                val ans = a * b
                val prompt = "$a × $b = ?"
                createQuestion(prompt, ans, "Multiplication", "$a × $b = $ans")
            }
            else -> {
                val b = random.nextInt(8) + 3 // 3..10
                val ans = random.nextInt(12) + 4 // 4..15
                val a = b * ans
                val prompt = "$a ÷ $b = ?"
                createQuestion(prompt, ans, "Division", "$a ÷ $b = $ans")
            }
        }
    }

    private fun generateOrderOfOperations(): MathQuestion {
        val type = random.nextInt(3)
        return when (type) {
            0 -> {
                val a = random.nextInt(8) + 3
                val b = random.nextInt(8) + 4
                val c = random.nextInt(25) + 5
                val ans = a * b + c
                val prompt = "$a × $b + $c = ?"
                createQuestion(prompt, ans, "Order of operations", "($a × $b) + $c = ${a * b} + $c = $ans")
            }
            1 -> {
                val a = random.nextInt(40) + 50
                val b = random.nextInt(6) + 3
                val c = random.nextInt(6) + 3
                val ans = a - b * c
                val prompt = "$a − $b × $c = ?"
                createQuestion(prompt, ans, "Order of operations", "$a − ($b × $c) = $a − ${b * c} = $ans")
            }
            else -> {
                val a = random.nextInt(10) + 5
                val b = random.nextInt(10) + 5
                val c = random.nextInt(5) + 2
                val ans = (a + b) * c
                val prompt = "($a + $b) × $c = ?"
                createQuestion(prompt, ans, "Parentheses", "($a + $b) × $c = ${a + b} × $c = $ans")
            }
        }
    }

    private fun generatePowersAndRoots(): MathQuestion {
        val type = random.nextInt(3)
        return when (type) {
            0 -> {
                val base = 2
                val exp = random.nextInt(4) + 3 // 2^3..2^6
                val power = 1 shl exp
                val offset = random.nextInt(15) + 5
                val ans = power - offset
                val prompt = "2^$exp − $offset = ?"
                createQuestion(prompt, ans, "Powers", "2^$exp = $power, $power − $offset = $ans")
            }
            1 -> {
                val base = 3
                val exp = random.nextInt(3) + 2 // 3^2, 3^3, 3^4
                var power = 1
                for (i in 1..exp) power *= base
                val offset = random.nextInt(15) + 5
                val ans = power + offset
                val prompt = "3^$exp + $offset = ?"
                createQuestion(prompt, ans, "Powers", "3^$exp = $power, $power + $offset = $ans")
            }
            else -> {
                val roots = intArrayOf(4, 5, 6, 7, 8, 9, 10, 11, 12)
                val r = roots[random.nextInt(roots.size)]
                val square = r * r
                val offset = random.nextInt(15) + 5
                val ans = r + offset
                val prompt = "√$square + $offset = ?"
                createQuestion(prompt, ans, "Square roots", "√$square = $r, $r + $offset = $ans")
            }
        }
    }

    private fun generateAlgebraEquation(): MathQuestion {
        val a = random.nextInt(4) + 2 // 2..5
        val x = random.nextInt(12) + 3 // 3..14
        val b = random.nextInt(20) + 5
        val c = a * x + b
        val prompt = "$a·x + $b = $c   (x = ?)"
        return createQuestion(prompt, x, "Equations", "$a·x = $c − $b = ${a * x} ⇒ x = $x")
    }

    private fun generateCodeLogic(): MathQuestion {
        val type = random.nextInt(2)
        return when (type) {
            0 -> {
                val a = random.nextInt(35) + 15
                val b = random.nextInt(6) + 3 // 3..8
                val ans = a % b
                val prompt = "$a % $b = ? (remainder)"
                createQuestion(prompt, ans, "Modulo", "$a = $b × ${a / b} + $ans ⇒ remainder is $ans")
            }
            else -> {
                val shift = random.nextInt(4) + 1 // 1..4
                val ans = 1 shl shift
                val prompt = "1 shl $shift (left bit shift 1 << $shift) = ?"
                createQuestion(prompt, ans, "Bitwise operations", "1 << $shift = 2^$shift = $ans")
            }
        }
    }

    private fun createQuestion(prompt: String, answer: Int, category: String, explanation: String): MathQuestion {
        val distractorSet = mutableSetOf<Int>()
        val candidateDeltas = intArrayOf(1, -1, 10, -10, 2, -2, 5, -5, 3, -3)

        // Add varying distractors
        for (delta in candidateDeltas.toList().shuffled(random)) {
            val d = answer + delta
            if (d != answer && (answer >= 0 || d >= 0)) {
                distractorSet.add(d)
                if (distractorSet.size == 3) break
            }
        }

        // Fallback if not enough
        var offset = 4
        while (distractorSet.size < 3) {
            val d = answer + offset
            if (d != answer) distractorSet.add(d)
            offset += 3
        }

        val allChoices = (distractorSet.toList() + answer).shuffled(random)
        val correctIndex = allChoices.indexOf(answer)
        val options = allChoices.map { it.toString() }

        return MathQuestion(
            prompt = prompt,
            options = options,
            correctIndex = correctIndex,
            category = category,
            explanation = explanation
        )
    }

    fun evaluateAnswer(question: MathQuestion, selectedIndex: Int): Boolean {
        return selectedIndex == question.correctIndex
    }

    fun calculateComboMultiplier(combo: Int): Int {
        return when {
            combo >= 10 -> 4
            combo >= 6 -> 3
            combo >= 3 -> 2
            else -> 1
        }
    }

    fun updateScoreAndCombo(currentScore: Int, currentCombo: Int, isCorrect: Boolean): Pair<Int, Int> {
        return if (isCorrect) {
            val multiplier = calculateComboMultiplier(currentCombo)
            val points = BASE_POINTS_PER_CORRECT * multiplier
            Pair(currentScore + points, currentCombo + 1)
        } else {
            Pair(currentScore, 0)
        }
    }

    fun calculateResult(score: Int, correctCount: Int, totalAnswered: Int, maxCombo: Int): GameResult {
        var xp = correctCount * BASE_XP_PER_CORRECT + (maxCombo * BONUS_XP_PER_COMBO)
        if (totalAnswered >= 5) {
            xp += COMPLETION_XP_BONUS
        }
        val boundedXp = xp.coerceIn(0, MAX_XP_LIMIT)
        return GameResult(
            score = score,
            correctCount = correctCount,
            totalAnswered = totalAnswered,
            maxCombo = maxCombo,
            xpEarned = boundedXp
        )
    }
}

