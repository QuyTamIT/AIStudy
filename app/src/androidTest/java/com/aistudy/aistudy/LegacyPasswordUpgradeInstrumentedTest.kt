package com.aistudy.aistudy

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.auth.PasswordHasher
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.User
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyPasswordUpgradeInstrumentedTest {
    @Test
    fun upgradeOnlyReplacesMatchingAccountAndPassword() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = database.userDao()
            val firstId = dao.insert(User().apply {
                email = "first@example.com"
                password = "LegacyPass123"
            })
            val secondId = dao.insert(User().apply {
                email = "second@example.com"
                password = "OtherPassword123"
            })
            val replacement = PasswordHasher.hash("LegacyPass123")

            assertEquals(0, dao.replacePasswordIfUnchanged(firstId, "WrongOldPassword", replacement))
            assertEquals(0, dao.replacePasswordIfUnchanged(secondId, "LegacyPass123", replacement))
            assertEquals("LegacyPass123", dao.getById(firstId)?.password)
            assertEquals("OtherPassword123", dao.getById(secondId)?.password)

            assertEquals(1, dao.replacePasswordIfUnchanged(firstId, "LegacyPass123", replacement))
            assertTrue(PasswordHasher.matches("LegacyPass123", dao.getById(firstId)?.password))
            assertFalse(PasswordHasher.matches(replacement, dao.getById(firstId)?.password))
            assertEquals(0, dao.replacePasswordIfUnchanged(firstId, "LegacyPass123", replacement))
            assertEquals("OtherPassword123", dao.getById(secondId)?.password)
        } finally {
            database.close()
        }
    }

    @Test
    fun signingInWithLegacyPasswordUpgradesOnlyThatAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val previousId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        val database = AppDatabase.getInstance(context)
        val dao = database.userDao()
        val email = "legacy-${UUID.randomUUID()}@example.com"
        val userId = dao.insert(User().apply {
            this.email = email
            password = "LegacyPass123"
        })

        try {
            assertTrue(preferences.edit().remove(LoginActivity.KEY_USER_ID).commit())
            ActivityScenario.launch(LoginActivity::class.java).use {
                onView(withId(R.id.edit_email)).perform(replaceText(email))
                onView(withId(R.id.edit_password)).perform(replaceText("LegacyPass123"), closeSoftKeyboard())
                onView(withId(R.id.btn_submit)).perform(click())
                val deadline = SystemClock.uptimeMillis() + 10_000L
                while (preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId &&
                    SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(50)
                }
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                val updated = requireNotNull(dao.findByEmail(email))
                assertEquals(userId, updated.id)
                assertTrue(PasswordHasher.matches("LegacyPass123", updated.password))
                assertFalse(updated.password == "LegacyPass123")
                assertFalse(PasswordHasher.matches(updated.password!!, updated.password))
            }
        } finally {
            assertTrue(preferences.edit().remove(LoginActivity.KEY_USER_ID).commit())
            database.openHelper.writableDatabase.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(userId))
            database.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id = ?", arrayOf(userId))
            val editor = preferences.edit()
            if (previousId > 0L) editor.putLong(LoginActivity.KEY_USER_ID, previousId)
            assertTrue(editor.commit())
        }
    }

    @Test
    fun sha256LoginRejectsStoredDigestAndPreservesLearningData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val previousId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        val database = AppDatabase.getInstance(context)
        val email = "sha256-${UUID.randomUUID()}@example.com"
        val digest = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        val userId = database.userDao().insert(User().apply { this.email = email; password = digest })
        com.aistudy.aistudy.database.ProfileDefaults.ensureExists(database.profileDao(), userId).also {
            it.onboarded = true; it.xp = 123; it.streak = 4; database.profileDao().update(it)
        }
        val qaId = database.questionAnswerDao().insert(com.aistudy.aistudy.database.QuestionAnswer().apply {
            this.userId = userId; question = "Existing history"; finalAnswer = "Saved answer"; reviewedSeconds = 17
        })
        try {
            assertTrue(preferences.edit().remove(LoginActivity.KEY_USER_ID).commit())
            ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
                onView(withId(R.id.edit_email)).perform(replaceText(email))
                onView(withId(R.id.edit_password)).perform(replaceText(digest), closeSoftKeyboard())
                onView(withId(R.id.btn_submit)).perform(click())
                val deadline = SystemClock.uptimeMillis() + 10_000L
                var rejected = false
                while (!rejected && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val message = activity.findViewById<android.widget.TextView>(R.id.tv_auth_error)
                        rejected = message.visibility == android.view.View.VISIBLE &&
                            message.text.toString() == context.getString(R.string.auth_error_invalid_credentials)
                    }
                    if (!rejected) SystemClock.sleep(50)
                }
                assertTrue("Stored SHA-256 must not authenticate", rejected)
                assertEquals(0L, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                assertEquals(digest, database.userDao().getById(userId)?.password)
                onView(withId(R.id.edit_password)).perform(replaceText("password"), closeSoftKeyboard())
                onView(withId(R.id.btn_submit)).perform(click())
                val successDeadline = SystemClock.uptimeMillis() + 10_000L
                while (preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId && SystemClock.uptimeMillis() < successDeadline) {
                    SystemClock.sleep(50)
                }
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                val stored = requireNotNull(database.userDao().getById(userId)?.password)
                assertFalse(PasswordHasher.needsUpgrade(stored))
                assertTrue(PasswordHasher.matches("password", stored))
                assertFalse(PasswordHasher.matches(stored, stored))
                assertEquals(123, database.profileDao().getByUserId(userId)?.xp)
                assertEquals(4, database.profileDao().getByUserId(userId)?.streak)
                assertEquals(1, database.questionAnswerDao().countByUserId(userId))
                assertEquals("Saved answer", database.questionAnswerDao().getById(qaId, userId)?.finalAnswer)
                assertEquals(17, database.questionAnswerDao().getById(qaId, userId)?.reviewedSeconds)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).toList().forEach { it.finish() }
            }
            val sql = database.openHelper.writableDatabase
            sql.execSQL("DELETE FROM question_answers WHERE userId = ?", arrayOf(userId))
            sql.execSQL("DELETE FROM friends WHERE ownerUserId = ?", arrayOf(userId))
            sql.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(userId))
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(userId))
            preferences.edit().remove(LoginActivity.KEY_USER_ID).also {
                if (previousId > 0L) it.putLong(LoginActivity.KEY_USER_ID, previousId)
            }.commit()
        }
    }
}
