package com.aistudy.aistudy

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.auth.RegisterActivity
import com.aistudy.aistudy.database.AppDatabase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
    private val database = AppDatabase.getInstance(context)

    @Test
    fun registerLoginLogoutPreservesAccountAndClearsBackStack() {
        val email = "auth-${UUID.randomUUID()}@example.com"
        val password = "TestPass123!"
        val originalUserId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        val originalUserCount = userCount()
        // Keep existing accounts; only reset the session while exercising the auth screens.
        assertTrue(preferences.edit().remove(LoginActivity.KEY_USER_ID).commit())

        try {
            ActivityScenario.launch(LoginActivity::class.java).use {
                onView(withId(R.id.btn_toggle_mode)).perform(scrollTo(), click())
                awaitActivity(RegisterActivity::class.java)
                onView(withId(R.id.edit_full_name)).perform(replaceText("Auth Regression"))
                onView(withId(R.id.edit_email)).perform(replaceText("invalid-email"))
                onView(withId(R.id.edit_password)).perform(replaceText(password), closeSoftKeyboard())
                submitExpectingToast(R.string.auth_error_invalid_email)
                awaitActivity(RegisterActivity::class.java)
                assertEquals(originalUserCount, userCount())

                onView(withId(R.id.edit_email)).perform(replaceText(email))
                onView(withId(R.id.edit_password)).perform(replaceText("123"), closeSoftKeyboard())
                submitExpectingToast(R.string.auth_error_password_short)
                awaitActivity(RegisterActivity::class.java)
                assertNull(database.userDao().findByEmail(email))

                onView(withId(R.id.edit_password)).perform(replaceText(password), closeSoftKeyboard())
                assertEquals("Đăng ký tài khoản thành công!", context.getString(R.string.auth_success_register))
                submitExpectingToast(R.string.auth_success_register)
                awaitActivity(LoginActivity::class.java)
                val account = database.userDao().findByEmail(email)
                assertNotNull(account)
                val userId = requireNotNull(account).id
                assertTrue(userId > 0L)
                assertEquals("Auth Regression", account.fullName)
                assertEquals(originalUserCount + 1, userCount())
                assertNoSession()
                assertSingleLoginActivity()

                onView(withId(R.id.btn_toggle_mode)).perform(scrollTo(), click())
                awaitActivity(RegisterActivity::class.java)
                onView(withId(R.id.edit_full_name)).perform(replaceText("Duplicate Account"))
                onView(withId(R.id.edit_email)).perform(replaceText(email.uppercase()))
                onView(withId(R.id.edit_password)).perform(replaceText(password), closeSoftKeyboard())
                submitExpectingToast(R.string.auth_error_email_taken)
                awaitActivity(RegisterActivity::class.java)
                onView(withId(R.id.btn_submit)).check(matches(isDisplayed()))
                assertEquals(userId, database.userDao().findByEmail(email)?.id)
                assertEquals("Auth Regression", database.userDao().findByEmail(email)?.fullName)
                assertEquals(originalUserCount + 1, userCount())
                assertNoSession()

                onView(withId(R.id.btn_toggle_mode)).perform(scrollTo(), click())
                awaitActivity(LoginActivity::class.java)
                fillLogin(email, "WrongPassword")
                submitExpectingToast(R.string.auth_error_invalid_credentials)
                awaitActivity(LoginActivity::class.java)
                assertNoSession()

                fillLogin(email, password)
                onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
                awaitActivity(MainActivity::class.java)
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                logoutAndAssertAccount(email, userId)

                pressBackUnconditionally()
                instrumentation.waitForIdleSync()
                assertFalse(resumedActivity() is MainActivity)
                assertNoSession()

                // Reopening the launcher must still require sign-in after logout.
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                awaitActivity(LoginActivity::class.java)
                fillLogin(email, password)
                onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
                awaitActivity(MainActivity::class.java)
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                logoutAndAssertAccount(email, userId)
                assertEquals(originalUserCount + 1, userCount())
            }
        } finally {
            val activity = resumedActivity()
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            val editor = preferences.edit()
            if (originalUserId > 0L) {
                editor.putLong(LoginActivity.KEY_USER_ID, originalUserId)
            } else {
                editor.remove(LoginActivity.KEY_USER_ID)
            }
            assertTrue(editor.commit())
        }
    }

    private fun fillLogin(email: String, password: String) {
        onView(withId(R.id.edit_email)).perform(replaceText(email))
        onView(withId(R.id.edit_password)).perform(replaceText(password), closeSoftKeyboard())
    }

    private fun logoutAndAssertAccount(email: String, userId: Long) {
        onView(withId(R.id.btn_logout)).perform(click())
        awaitActivity(LoginActivity::class.java)
        assertNoSession()
        assertSingleLoginActivity()
        assertEquals(userId, database.userDao().findByEmail(email)?.id)
    }

    private fun submitExpectingToast(message: Int) {
        val expected = context.getString(message)
        val event = instrumentation.uiAutomation.executeAndWaitForEvent(
            { onView(withId(R.id.btn_submit)).perform(scrollTo(), click()) },
            { event ->
                event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.text.any { it.toString() == expected }
            },
            10_000L
        )
        event.recycle()
        // Allow the toast to finish before submitting the next form.
        SystemClock.sleep(2_200L)
    }

    private fun assertNoSession() {
        assertFalse(preferences.contains(LoginActivity.KEY_USER_ID))
    }

    private fun assertSingleLoginActivity() {
        val manager = context.getSystemService(ActivityManager::class.java)
        val task = manager.appTasks.mapNotNull { it.taskInfo }
            .single { it.topActivity?.className == LoginActivity::class.java.name }
        assertEquals(LoginActivity::class.java.name, task.baseActivity?.className)
        assertEquals(1, task.numActivities)
    }

    private fun userCount(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM users").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun resumedActivity(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return activity
    }

    private fun awaitActivity(expected: Class<out Activity>) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (expected.isInstance(resumedActivity())) return
            SystemClock.sleep(50L)
        }
        assertTrue("Expected ${expected.simpleName}, got ${resumedActivity()}", expected.isInstance(resumedActivity()))
    }
}
