package com.aistudy.aistudy.playground

import androidx.annotation.StringRes
import com.aistudy.aistudy.R
import java.util.Random

data class MathQuestion(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    @param:StringRes val categoryResId: Int,
    @param:StringRes val explanationResId: Int,
    val explanationArgs: List<Any>,
    @param:StringRes val promptSuffixResId: Int? = null
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
                createQuestion(prompt, ans, R.string.playground_math_category_addition, R.string.playground_math_explanation_addition, listOf(a, b, ans))
            }
            1 -> {
                val a = random.nextInt(70) + 30
                val b = random.nextInt(a - 15) + 10
                val ans = a - b
                val prompt = "$a − $b = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_subtraction, R.string.playground_math_explanation_subtraction, listOf(a, b, ans))
            }
            2 -> {
                val a = random.nextInt(9) + 6 // 6..14
                val b = random.nextInt(8) + 4 // 4..11
                val ans = a * b
                val prompt = "$a × $b = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_multiplication, R.string.playground_math_explanation_multiplication, listOf(a, b, ans))
            }
            else -> {
                val b = random.nextInt(8) + 3 // 3..10
                val ans = random.nextInt(12) + 4 // 4..15
                val a = b * ans
                val prompt = "$a ÷ $b = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_division, R.string.playground_math_explanation_division, listOf(a, b, ans))
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
                createQuestion(prompt, ans, R.string.playground_math_category_order, R.string.playground_math_explanation_order_multiply_add, listOf(a, b, c, a * b, ans))
            }
            1 -> {
                val a = random.nextInt(40) + 50
                val b = random.nextInt(6) + 3
                val c = random.nextInt(6) + 3
                val ans = a - b * c
                val prompt = "$a − $b × $c = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_order, R.string.playground_math_explanation_order_subtract_multiply, listOf(a, b, c, b * c, ans))
            }
            else -> {
                val a = random.nextInt(10) + 5
                val b = random.nextInt(10) + 5
                val c = random.nextInt(5) + 2
                val ans = (a + b) * c
                val prompt = "($a + $b) × $c = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_parentheses, R.string.playground_math_explanation_parentheses, listOf(a, b, c, a + b, ans))
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
                createQuestion(prompt, ans, R.string.playground_math_category_power, R.string.playground_math_explanation_power_subtract, listOf(exp, power, offset, ans))
            }
            1 -> {
                val base = 3
                val exp = random.nextInt(3) + 2 // 3^2, 3^3, 3^4
                var power = 1
                for (i in 1..exp) power *= base
                val offset = random.nextInt(15) + 5
                val ans = power + offset
                val prompt = "3^$exp + $offset = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_power, R.string.playground_math_explanation_power_add, listOf(exp, power, offset, ans))
            }
            else -> {
                val roots = intArrayOf(4, 5, 6, 7, 8, 9, 10, 11, 12)
                val r = roots[random.nextInt(roots.size)]
                val square = r * r
                val offset = random.nextInt(15) + 5
                val ans = r + offset
                val prompt = "√$square + $offset = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_square_root, R.string.playground_math_explanation_square_root, listOf(square, r, offset, ans))
            }
        }
    }

    private fun generateAlgebraEquation(): MathQuestion {
        val a = random.nextInt(4) + 2 // 2..5
        val x = random.nextInt(12) + 3 // 3..14
        val b = random.nextInt(20) + 5
        val c = a * x + b
        val prompt = "$a·x + $b = $c   (x = ?)"
        return createQuestion(prompt, x, R.string.playground_math_category_equation, R.string.playground_math_explanation_equation, listOf(a, c, b, a * x, x))
    }

    private fun generateCodeLogic(): MathQuestion {
        val type = random.nextInt(2)
        return when (type) {
            0 -> {
                val a = random.nextInt(35) + 15
                val b = random.nextInt(6) + 3 // 3..8
                val ans = a % b
                val prompt = "$a % $b = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_modulo, R.string.playground_math_explanation_modulo, listOf(a, b, a / b, ans), R.string.playground_math_prompt_modulo)
            }
            else -> {
                val shift = random.nextInt(4) + 1 // 1..4
                val ans = 1 shl shift
                val prompt = "1 shl $shift = ?"
                createQuestion(prompt, ans, R.string.playground_math_category_bitwise, R.string.playground_math_explanation_bitwise, listOf(shift, ans), R.string.playground_math_prompt_bitwise)
            }
        }
    }


    private fun createQuestion(
        prompt: String,
        answer: Int,
        @StringRes categoryResId: Int,
        @StringRes explanationResId: Int,
        explanationArgs: List<Any>,
        @StringRes promptSuffixResId: Int? = null
    ): MathQuestion {
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
            categoryResId = categoryResId,
            explanationResId = explanationResId,
            explanationArgs = explanationArgs,
            promptSuffixResId = promptSuffixResId
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

