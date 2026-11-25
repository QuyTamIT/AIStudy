package com.aistudy.aistudy

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.auth.PasswordHasher
import com.aistudy.aistudy.auth.RegisterActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.User
import com.aistudy.aistudy.navigation.CurvedAnimatedBottomNav
import com.aistudy.aistudy.onboarding.OnboardingActivity
import java.io.File
import java.util.UUID
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Password verification and legacy hash upgrades run off the UI thread.
internal const val AUTH_TIMEOUT_MS = 60_000L

@RunWith(AndroidJUnit4::class)
class AuthFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
    private val database = AppDatabase.getInstance(context)
    private val sessionKeys = setOf(LoginActivity.KEY_USER_ID, "user_id")
    private val savedIds = sessionKeys.filter(preferences::contains).associateWith { preferences.getLong(it, 0L) }
    private val fixtureIds = mutableSetOf<Long>()
    private val fixturePassword = "TestPass123!"

    @Before
    fun startSignedOut() {
        assertTrue(preferences.edit().apply { sessionKeys.forEach { remove(it) } }.commit())
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun restoreState() {
        val activity = resumedActivity()
        instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
        try {
            val sql = database.openHelper.writableDatabase
            fixtureIds.forEach { id ->
                sql.execSQL("DELETE FROM friends WHERE ownerUserId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(id))
            }
        } finally {
            val editor = preferences.edit()
            sessionKeys.forEach { editor.remove(it) }
            savedIds.forEach { (key, id) -> editor.putLong(key, id) }
            assertTrue(editor.commit())
        }
    }

    @Test
    fun registerLoginLogoutPreservesAccountAndClearsBackStack() {
        val email = "auth-${UUID.randomUUID()}@example.com"
        val password = fixturePassword
        val originalUserCount = userCount()
        try {
            ActivityScenario.launch(LoginActivity::class.java).use {
                awaitActivity(LoginActivity::class.java)
                val registrationNotice = onView(withId(com.google.android.material.R.id.snackbar_text))
                registrationNotice.check(doesNotExist())
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
                awaitActivity(LoginActivity::class.java)
                registrationNotice.check(matches(allOf(isDisplayed(), withText(R.string.auth_success_register))))
                onView(withId(R.id.tv_auth_subtitle)).check(matches(withText(R.string.auth_login_after_register)))
                assertNoSession()
                val account = database.userDao().findByEmail(email)
                assertNotNull(account)
                val userId = requireNotNull(account).id
                assertTrue(userId > 0L)
                assertEquals("Auth Regression", account.fullName)
                assertEquals(originalUserCount + 1, userCount())
                val defaultProfile = requireNotNull(database.profileDao().getByUserId(userId))
                assertFalse(defaultProfile.onboarded)
                assertEquals(0, defaultProfile.xp)
                assertEquals(0, defaultProfile.streak)

                fillLogin(email, password)
                onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
                awaitActivity(OnboardingActivity::class.java)
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                restartFromLauncher()
                awaitActivity(OnboardingActivity::class.java)
                completeOnboarding(userId)
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
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

                restartFromLauncher()
                awaitActivity(MainActivity::class.java)
                onView(withId(R.id.btn_logout)).check(matches(isDisplayed()))

                logoutAndAssertAccount(email, userId)

                pressBackUnconditionally()
                instrumentation.waitForIdleSync()
                val afterBack = resumedActivity()
                assertTrue(afterBack == null || afterBack is LoginActivity)
                assertNoSession()

                // Reopening the launcher must still require sign-in after logout.
                launchMain()
                awaitActivity(LoginActivity::class.java)
                assertNoSession()
                fillLogin(email, password)
                onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
                awaitActivity(MainActivity::class.java)
                assertEquals(userId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
                // All four existing navigation destinations must remain usable.
                listOf(R.id.nav_ask, R.id.nav_history, R.id.nav_progress, R.id.nav_home).forEach(::selectTab)
                logoutAndAssertAccount(email, userId)
                assertEquals(originalUserCount + 1, userCount())
            }
        } finally {
            database.userDao().findByEmail(email)?.id?.let(fixtureIds::add)
        }
    }

    @Test
    fun launcherRejectsMissingRoomAccountBeforeCreatingProfile() {
        val email = "removed-${UUID.randomUUID()}@example.com"
        val id = insertAccount(email)
        database.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE id = ?", arrayOf(id))
        assertTrue(preferences.edit().putLong(LoginActivity.KEY_USER_ID, id).commit())

        launchMain()
        awaitActivity(LoginActivity::class.java)
        assertNoSession()
        assertNull(database.profileDao().getByUserId(id))
    }

    @Test
    fun legacySessionRequiresLoginAndEachAccountUsesItsOwnOnboardingStatus() {
        val existingEmail = "existing-${UUID.randomUUID()}@example.com"
        val newEmail = "new-${UUID.randomUUID()}@example.com"
        val existingId = insertAccount(existingEmail)
        val newId = insertAccount(newEmail)
        val existingProfile = ProfileDefaults.createDefault(existingId).apply {
            onboarded = true
            xp = 78
        }
        database.profileDao().insert(existingProfile)
        assertTrue(preferences.edit().putLong("user_id", existingId).commit())

        launchMain()
        awaitActivity(LoginActivity::class.java)
        assertNoSession()
        fillLogin(existingEmail, fixturePassword)
        onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
        awaitActivity(MainActivity::class.java)
        logoutAndAssertAccount(existingEmail, existingId)

        fillLogin(newEmail, fixturePassword)
        onView(withId(R.id.btn_submit)).perform(scrollTo(), click())
        awaitActivity(OnboardingActivity::class.java)
        assertEquals(newId, preferences.getLong(LoginActivity.KEY_USER_ID, 0L))
        assertFalse(requireNotNull(database.profileDao().getByUserId(newId)).onboarded)
        val savedProfile = requireNotNull(database.profileDao().getByUserId(existingId))
        assertTrue(savedProfile.onboarded)
        assertEquals(78, savedProfile.xp)
    }

    private fun insertAccount(email: String): Long = database.userDao().insert(User().apply {
        this.email = email
        password = PasswordHasher.hash(fixturePassword)
    }).also(fixtureIds::add)

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
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(launchIntent)
    }

    private fun restartFromLauncher() {
        val activity = requireNotNull(resumedActivity())
        instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
        instrumentation.waitForIdleSync()
        launchMain()
    }

    private fun fillLogin(email: String, password: String) {
        onView(withId(R.id.edit_email)).perform(replaceText(email))
        onView(withId(R.id.edit_password)).perform(replaceText(password), closeSoftKeyboard())
    }

    private fun logoutAndAssertAccount(email: String, userId: Long) {
        selectTab(R.id.nav_progress)
        onView(withId(R.id.btn_logout)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        awaitActivity(LoginActivity::class.java)
        assertNoSession()
        assertSingleLoginActivity()
        assertEquals(userId, database.userDao().findByEmail(email)?.id)
    }

    private fun selectTab(id: Int) {
        val label = CurvedAnimatedBottomNav.TABS.single { it.id == id }.label
        onView(allOf(withText(label), isDescendantOfA(withId(R.id.bottom_nav)))).perform(click())
    }

    private fun submitExpectingToast(message: Int, buttonId: Int = R.id.btn_submit) {
        onView(withId(buttonId)).perform(scrollTo(), click())
        val expected = context.getString(message)
        val deadline = SystemClock.uptimeMillis() + AUTH_TIMEOUT_MS
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
        val deadline = SystemClock.uptimeMillis() + AUTH_TIMEOUT_MS
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
