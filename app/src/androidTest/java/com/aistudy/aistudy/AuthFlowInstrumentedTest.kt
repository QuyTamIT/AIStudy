package com.aistudy.aistudy

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.auth.RegisterActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.onboarding.OnboardingActivity
import java.io.File
import android.graphics.Bitmap
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
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        assertTrue(preferences.edit().remove(LoginActivity.KEY_USER_ID).commit())
        try {
            ActivityScenario.launch(LoginActivity::class.java).use {
                awaitActivity(LoginActivity::class.java)
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
                onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
                awaitActivity(OnboardingActivity::class.java)
                val account = database.userDao().findByEmail(email)
                assertNotNull(account)
                val userId = requireNotNull(account).id
                assertTrue(userId > 0L)
                assertEquals("Auth Regression", account.fullName)
                assertEquals(originalUserCount + 1, userCount())
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                val defaultProfile = requireNotNull(database.profileDao().getByUserId(userId))
                assertFalse(defaultProfile.onboarded)
                assertEquals(0, defaultProfile.xp)
                assertEquals(0, defaultProfile.streak)

                completeOnboarding(userId)
                awaitActivity(MainActivity::class.java)
                logoutAndAssertAccount(email, userId)
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

                // Recreate the launch Activity without clearing the session or Room data.
                val main = requireNotNull(resumedActivity())
                instrumentation.runOnMainSync { main.finish() }
                launchMain()
                awaitActivity(MainActivity::class.java)
                onView(withId(R.id.btn_logout)).check(matches(isDisplayed()))

                logoutAndAssertAccount(email, userId)

                pressBackUnconditionally()
                instrumentation.waitForIdleSync()
                assertFalse(resumedActivity() is MainActivity)
                assertNoSession()

                // Reopening the launcher must still require sign-in after logout.
                launchMain()
                awaitActivity(LoginActivity::class.java)
                fillLogin(email, password)
                onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
                awaitActivity(MainActivity::class.java)
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                // All four existing navigation destinations must remain usable.
                listOf(R.id.nav_ask, R.id.nav_history, R.id.nav_progress, R.id.nav_home).forEach { id ->
                    onView(withId(id)).perform(click())
                }
                logoutAndAssertAccount(email, userId)
                assertEquals(originalUserCount + 1, userCount())
            }
        } finally {
            val activity = resumedActivity()
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            database.userDao().findByEmail(email)?.id?.let { createdId ->
                database.openHelper.writableDatabase.execSQL("DELETE FROM friends WHERE ownerUserId = ?", arrayOf(createdId))
                database.openHelper.writableDatabase.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(createdId))
                database.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id = ?", arrayOf(createdId))
            }
            preferences.edit().remove(LoginActivity.KEY_USER_ID).also {
                if (originalUserId > 0L) it.putLong(LoginActivity.KEY_USER_ID, originalUserId)
            }.commit()
        }
    }

    private fun completeOnboarding(userId: Long) {
        onView(withId(R.id.choice_level_uni)).perform(scrollTo(), click())
        onView(withId(R.id.choice_level_uni)).check(matches(isSelected()))
        capture("step-1")
        onView(withId(R.id.btn_next)).perform(scrollTo(), click())
        onView(withId(R.id.choice_subject_math)).perform(scrollTo(), click())
        onView(withId(R.id.choice_subject_science)).perform(scrollTo(), click())
        submitExpectingToast(R.string.onboarding_pick_subject, R.id.btn_next)
        onView(withId(R.id.step_subjects)).check(matches(isDisplayed()))
        onView(withId(R.id.tv_subject_error)).check(matches(isDisplayed()))
        onView(withId(R.id.choice_subject_math)).perform(scrollTo(), click())
        onView(withId(R.id.choice_subject_code)).perform(scrollTo(), click())
        capture("step-2")
        onView(withId(R.id.btn_next)).perform(scrollTo(), click())
        onView(withId(R.id.choice_style_detailed)).perform(scrollTo(), click())
        onView(withId(R.id.btn_back)).perform(scrollTo(), click())
        onView(withId(R.id.choice_subject_math)).check(matches(isSelected()))
        onView(withId(R.id.choice_subject_code)).check(matches(isSelected()))
        onView(withId(R.id.btn_back)).perform(scrollTo(), click())
        onView(withId(R.id.choice_level_uni)).check(matches(isSelected()))
        onView(withId(R.id.btn_next)).perform(scrollTo(), click())
        onView(withId(R.id.btn_next)).perform(scrollTo(), click())
        onView(withId(R.id.choice_style_detailed)).check(matches(isSelected()))

        val activity = requireNotNull(resumedActivity())
        instrumentation.runOnMainSync { activity.recreate() }
        awaitActivity(OnboardingActivity::class.java)
        onView(withId(R.id.choice_style_detailed)).check(matches(isSelected()))
        // System Back follows the same steps and retains choices after recreation.
        pressBackUnconditionally()
        onView(withId(R.id.choice_subject_math)).check(matches(isSelected()))
        onView(withId(R.id.choice_subject_code)).check(matches(isSelected()))
        onView(withId(R.id.btn_next)).perform(scrollTo(), click())
        capture("step-3")
        onView(withId(R.id.btn_next)).perform(scrollTo(), click())
        awaitActivity(MainActivity::class.java)
        val profile = requireNotNull(database.profileDao().getByUserId(userId))
        assertTrue(profile.onboarded)
        assertEquals("uni", profile.level)
        assertEquals("math,code", profile.subjects)
        assertEquals("detailed", profile.style)
        assertEquals(0, profile.xp)
        assertEquals(0, profile.streak)
        val tasks = context.getSystemService(ActivityManager::class.java).appTasks.mapNotNull { it.taskInfo }
        assertEquals(1, tasks.single { it.topActivity?.className == MainActivity::class.java.name }.numActivities)
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val folder = File(context.getExternalFilesDir(null), "phase2-validation").apply { mkdirs() }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun launchMain() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun fillLogin(email: String, password: String) {
        onView(withId(R.id.edit_email)).perform(replaceText(email))
        onView(withId(R.id.edit_password)).perform(replaceText(password), closeSoftKeyboard())
    }

    private fun logoutAndAssertAccount(email: String, userId: Long) {
        onView(withId(R.id.nav_progress)).perform(click())
        onView(withId(R.id.btn_logout)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        awaitActivity(LoginActivity::class.java)
        assertNoSession()
        assertSingleLoginActivity()
        assertEquals(userId, database.userDao().findByEmail(email)?.id)
    }

    private fun submitExpectingToast(message: Int, buttonId: Int = R.id.btn_submit) {
        onView(withId(buttonId)).perform(scrollTo(), click())
        val expected = context.getString(message)
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var displayed = false
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                val fieldId = when (message) {
                    R.string.auth_error_invalid_email, R.string.auth_error_email_taken -> R.id.input_email
                    R.string.auth_error_password_short -> R.id.input_password
                    else -> 0
                }
                displayed = if (fieldId != 0) {
                    activity?.findViewById<com.google.android.material.textfield.TextInputLayout>(fieldId)?.error?.toString() == expected
                } else {
                    val textId = if (message == R.string.onboarding_pick_subject) R.id.tv_subject_error else R.id.tv_auth_error
                    activity?.findViewById<android.widget.TextView>(textId)?.let {
                        it.visibility == View.VISIBLE && it.text.toString() == expected
                    } == true
                }
            }
            if (displayed) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Expected inline validation: $expected")
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
            val activity = resumedActivity()
            var ready = false
            instrumentation.runOnMainSync {
                ready = expected.isInstance(activity) && activity != null &&
                    !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus() && when (activity) {
                    is OnboardingActivity -> activity.findViewById<View>(R.id.btn_next)?.isEnabled == true
                    is MainActivity -> activity.findViewById<View>(R.id.main)?.visibility == View.VISIBLE
                    else -> true
                }
            }
            if (ready) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("Expected a ready ${expected.simpleName}, got ${resumedActivity()}")
    }
}
