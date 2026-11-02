package com.aistudy.aistudy.xp

import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class XpRulesTest {

    private class FakeProfileDao : ProfileDao {
        var updatedProfile: Profile? = null
        override fun insert(profile: Profile): Long = 1L
        override fun update(profile: Profile): Int {
            updatedProfile = profile
            return 1
        }
        override fun getByUserId(userId: Long): Profile? = updatedProfile
    }

    @Test
    fun testLevelFromXp() {
        assertEquals(1, XpRules.levelFromXp(0))
        assertEquals(1, XpRules.levelFromXp(50))
        assertEquals(1, XpRules.levelFromXp(99))
        assertEquals(2, XpRules.levelFromXp(100))
        assertEquals(2, XpRules.levelFromXp(249))
        assertEquals(3, XpRules.levelFromXp(250))
        assertEquals(4, XpRules.levelFromXp(500))
        assertEquals(5, XpRules.levelFromXp(1000))
        assertEquals(6, XpRules.levelFromXp(2000))
        assertEquals(6, XpRules.levelFromXp(5000))
    }

    @Test
    fun testNextLevelThreshold() {
        assertEquals(100, XpRules.nextLevelThreshold(0))
        assertEquals(100, XpRules.nextLevelThreshold(50))
        assertEquals(250, XpRules.nextLevelThreshold(100))
        assertEquals(500, XpRules.nextLevelThreshold(250))
        assertEquals(1000, XpRules.nextLevelThreshold(500))
        assertEquals(2000, XpRules.nextLevelThreshold(1000))
        assertEquals(2000, XpRules.nextLevelThreshold(2000))
    }

    @Test
    fun testProgressPercentToNext() {
        assertEquals(0, XpRules.progressPercentToNext(0))
        assertEquals(50, XpRules.progressPercentToNext(50))
        assertEquals(0, XpRules.progressPercentToNext(100))
        // level 2 span is 100 to 250 -> 150 span. At 175 xp -> (75/150)*100 = 50%
        assertEquals(50, XpRules.progressPercentToNext(175))
        assertEquals(100, XpRules.progressPercentToNext(2000))
        assertEquals(100, XpRules.progressPercentToNext(3000))
    }

    @Test
    fun testXpToNextLevel() {
        assertEquals(100, XpRules.xpToNextLevel(0))
        assertEquals(50, XpRules.xpToNextLevel(50))
        assertEquals(150, XpRules.xpToNextLevel(100))
        assertEquals(0, XpRules.xpToNextLevel(2000))
    }

    @Test
    fun testIsNearNextLevel() {
        assertFalse(XpRules.isNearNextLevel(0))
        assertFalse(XpRules.isNearNextLevel(10))
        assertTrue(XpRules.isNearNextLevel(50)) // remain is 50 -> in 1..50
        assertTrue(XpRules.isNearNextLevel(80)) // remain is 20 -> in 1..50
        assertFalse(XpRules.isNearNextLevel(100)) // remain is 150 -> not in 1..50
    }

    @Test
    fun testEnsureWeek() {
        val profile = Profile().apply {
            weekStartMs = 12345L
            weeklyXp = 500
        }
        XpRules.ensureWeek(profile)
        assertEquals(XpRules.currentWeekStartMs(), profile.weekStartMs)
        assertEquals(0, profile.weeklyXp)

        // Calling again in the same week keeps weeklyXp
        profile.weeklyXp = 80
        XpRules.ensureWeek(profile)
        assertEquals(80, profile.weeklyXp)
    }

    @Test
    fun testAddXp() {
        val dao = FakeProfileDao()
        val profile = Profile().apply {
            xp = 100
            weeklyXp = 20
            weekStartMs = XpRules.currentWeekStartMs()
        }
        XpRules.addXp(profile, dao, XpRules.ASK) // +50
        assertEquals(150, profile.xp)
        assertEquals(70, profile.weeklyXp)
        assertEquals(profile, dao.updatedProfile)

        // Non positive does nothing
        XpRules.addXp(profile, dao, 0)
        assertEquals(150, profile.xp)
    }

    @Test
    fun testRecordDailyActivity() {
        val dao = FakeProfileDao()
        val profile = Profile().apply {
            streak = 0
            lastActiveDay = null
        }

        // First day ever
        XpRules.recordDailyActivity(profile, dao)
        assertEquals(1, profile.streak)
        assertEquals(XpRules.todayKey(), profile.lastActiveDay)

        // Same day -> no change
        XpRules.recordDailyActivity(profile, dao)
        assertEquals(1, profile.streak)

        // Yesterday -> streak increment
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        profile.lastActiveDay = yesterday
        profile.streak = 2
        XpRules.recordDailyActivity(profile, dao)
        assertEquals(3, profile.streak)
        assertEquals(XpRules.todayKey(), profile.lastActiveDay)

        // 3 days ago -> streak reset to 1
        val calOld = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -3) }
        profile.lastActiveDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calOld.time)
        profile.streak = 5
        XpRules.recordDailyActivity(profile, dao)
        assertEquals(1, profile.streak)
    }

    @Test
    fun testStreakAtRisk() {
        val profile = Profile().apply {
            streak = 3
        }
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        profile.lastActiveDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        assertTrue(XpRules.streakAtRisk(profile))

        profile.lastActiveDay = XpRules.todayKey()
        assertFalse(XpRules.streakAtRisk(profile))

        val calOld = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -2) }
        profile.lastActiveDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calOld.time)
        assertFalse(XpRules.streakAtRisk(profile))
    }
}

