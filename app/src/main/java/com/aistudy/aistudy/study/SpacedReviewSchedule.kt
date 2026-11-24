package com.aistudy.aistudy.study

import java.util.Calendar

enum class RecallRating { AGAIN, HARD, GOOD, EASY }

data class ReviewSchedule(val intervalDays: Int, val dueAt: Long)

object SpacedReviewSchedule {
    private const val AGAIN_DELAY_MS = 10L * 60L * 1000L

    fun next(currentIntervalDays: Int, rating: RecallRating, now: Long): ReviewSchedule {
        val intervalDays = when (rating) {
            RecallRating.AGAIN -> 0
            RecallRating.HARD -> if (currentIntervalDays <= 1) 1
                else kotlin.math.ceil(currentIntervalDays * 1.2).toInt()
            RecallRating.GOOD -> when (currentIntervalDays) {
                0 -> 1
                1 -> 3
                else -> currentIntervalDays * 2
            }
            RecallRating.EASY -> maxOf(3, currentIntervalDays * 3)
        }
        val dueAt = if (rating == RecallRating.AGAIN) now + AGAIN_DELAY_MS else {
            Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, intervalDays)
            }.timeInMillis
        }
        return ReviewSchedule(intervalDays, dueAt)
    }
}
