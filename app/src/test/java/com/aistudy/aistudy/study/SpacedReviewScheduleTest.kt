package com.aistudy.aistudy.study

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SpacedReviewScheduleTest {
    private val now = 1_800_000_000_000L

    @Test fun againReturnsInTenMinutes() {
        val result = SpacedReviewSchedule.next(5, RecallRating.AGAIN, now)
        assertEquals(0, result.intervalDays)
        assertEquals(now + 10 * 60 * 1000L, result.dueAt)
    }

    @Test fun goodStartsAtOneDayThenGrows() {
        assertEquals(1, SpacedReviewSchedule.next(0, RecallRating.GOOD, now).intervalDays)
        assertEquals(3, SpacedReviewSchedule.next(1, RecallRating.GOOD, now).intervalDays)
        assertEquals(6, SpacedReviewSchedule.next(3, RecallRating.GOOD, now).intervalDays)
    }

    @Test fun hardAndEasyAdjustIntervals() {
        assertEquals(1, SpacedReviewSchedule.next(1, RecallRating.HARD, now).intervalDays)
        assertEquals(6, SpacedReviewSchedule.next(5, RecallRating.HARD, now).intervalDays)
        assertEquals(3, SpacedReviewSchedule.next(0, RecallRating.EASY, now).intervalDays)
        assertEquals(12, SpacedReviewSchedule.next(4, RecallRating.EASY, now).intervalDays)
    }

    @Test fun regularReviewsUseCalendarDays() {
        val result = SpacedReviewSchedule.next(1, RecallRating.GOOD, now)
        val expected = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 3)
        }
        val actual = Calendar.getInstance().apply { timeInMillis = result.dueAt }
        assertEquals(expected.get(Calendar.YEAR), actual.get(Calendar.YEAR))
        assertEquals(expected.get(Calendar.DAY_OF_YEAR), actual.get(Calendar.DAY_OF_YEAR))
    }
}
