package com.aistudy.aistudy.game

data class GameQuestion(
    val id: String,
    val subject: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/** Pure round rules. The caller supplies monotonic time, including time spent in background. */
class GameRound(
    val questions: List<GameQuestion>,
    val timed: Boolean,
    val deadlineMs: Long,
    restoredAnswers: List<Int> = emptyList(),
    finished: Boolean = false
) {
    init {
        require(questions.isNotEmpty())
        require(questions.map { it.id }.distinct().size == questions.size)
        require(restoredAnswers.size <= questions.size)
        require(restoredAnswers.withIndex().all { (i, answer) -> answer in questions[i].options.indices })
    }

    private val selections = restoredAnswers.toMutableList()
    val answers: List<Int> get() = selections.toList()
    var finished: Boolean = finished || selections.size == questions.size
        private set
    val correctCount: Int get() = selections.indices.count { selections[it] == questions[it].correctIndex }
    val combo: Int get() = selections.indices.reversed()
        .takeWhile { selections[it] == questions[it].correctIndex }.size
    val bestCombo: Int get() {
        var streak = 0
        var best = 0
        selections.forEachIndexed { i, answer ->
            streak = if (answer == questions[i].correctIndex) streak + 1 else 0
            best = maxOf(best, streak)
        }
        return best
    }
    val score: Int get() {
        var streak = 0
        var points = 0
        selections.forEachIndexed { i, answer ->
            streak = if (answer == questions[i].correctIndex) streak + 1 else 0
            if (streak > 0) points += 100 + (minOf(streak, 5) - 1) * 25
        }
        return points
    }

    fun remainingMs(nowMs: Long): Long = if (timed) (deadlineMs - nowMs).coerceIn(0L, DURATION_MS) else 0L

    fun expire(nowMs: Long): Boolean {
        if (timed && remainingMs(nowMs) == 0L) finished = true
        return finished
    }

    // The question index guards against queued/double taps answering the next question.
    fun submit(questionIndex: Int, option: Int, nowMs: Long): Boolean {
        if (expire(nowMs) || questionIndex != selections.size) return false
        if (option !in questions[questionIndex].options.indices) return false
        selections.add(option)
        if (selections.size == questions.size) finished = true
        return true
    }

    companion object {
        const val DURATION_MS = 60_000L
    }
}
