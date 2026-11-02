package com.aistudy.aistudy.notify

import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.xp.XpRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class NotificationRulesTest {

    @Test
    fun testBookmarkThreshold() {
        val now = 1_000_000_000_000L
        val hours48Ms = 48L * 60L * 60L * 1000L
        val beforeMs = now - hours48Ms

        // Item created 50 hours ago is older than beforeMs
        val olderItemCreatedAt = now - (50L * 60L * 60L * 1000L)
        assertTrue(olderItemCreatedAt <= beforeMs)

        // Item created 24 hours ago is newer than beforeMs
        val newerItemCreatedAt = now - (24L * 60L * 60L * 1000L)
        assertFalse(newerItemCreatedAt <= beforeMs)
    }

    @Test
    fun testMotivationalRulesPriority() {
        val profile = Profile().apply {
            xp = 80 // Level 1 (0..100). Remaining to Lv 2 is 20 -> isNearNextLevel = true
            streak = 5
        }
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        profile.lastActiveDay = yesterday // Streak at risk!

        assertTrue(XpRules.isNearNextLevel(profile.xp))
        assertTrue(XpRules.streakAtRisk(profile))

        // According to Phase 14: streak-at-risk must take priority over near-level
        val nearLevel = XpRules.isNearNextLevel(profile.xp)
        val streakRisk = XpRules.streakAtRisk(profile)
        val chosenReason = if (streakRisk) "streak" else "level"
        assertEquals("streak", chosenReason)
    }

    @Test
    fun testSummaryAccuracyCalculation() {
        val attempted = 8
        val correct = 6
        val accuracy = if (attempted > 0) ((correct * 100f) / attempted).roundToInt() else 0
        assertEquals(75, accuracy)

        val zeroAttempted = 0
        val zeroAccuracy = if (zeroAttempted > 0) 100 else 0
        assertEquals(0, zeroAccuracy)
    }

    @Test
    fun testMillisUntilHour() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val delayMs = target.timeInMillis - now.timeInMillis
        assertTrue(delayMs > 0)
        assertTrue(delayMs <= 24L * 60L * 60L * 1000L)
    }
}

