package com.aistudy.aistudy

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.LocaleList
import android.os.SystemClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.auth.RegisterActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.User
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.navigation.CurvedAnimatedBottomNav
import com.aistudy.aistudy.profile.ProfileActivity
import com.aistudy.aistudy.settings.AppLanguage
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val auth = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
    private var originalUserId = 0L
    private var hadUserId = false
    private var originalLocales = LocaleList.getEmptyLocaleList()
    private var testUserId = 0L

    @Before
    fun prepare() {
        originalLocales = localeManager.applicationLocales
        hadUserId = auth.contains(LoginActivity.KEY_USER_ID)
        originalUserId = auth.getLong(LoginActivity.KEY_USER_ID, 0L)
        assertTrue(auth.edit().remove(LoginActivity.KEY_USER_ID).commit())
        instrumentation.runOnMainSync {
            localeManager.applicationLocales = LocaleList.forLanguageTags("en")
        }
        instrumentation.waitForIdleSync()
    }

    @After
    fun restore() {
        val editor = auth.edit()
        if (hadUserId) editor.putLong(LoginActivity.KEY_USER_ID, originalUserId)
        else editor.remove(LoginActivity.KEY_USER_ID)
        assertTrue(editor.commit())
        instrumentation.runOnMainSync { localeManager.applicationLocales = originalLocales }
        instrumentation.waitForIdleSync()
        if (testUserId > 0) {
            val db = AppDatabase.getInstance(context).openHelper.writableDatabase
            db.execSQL("DELETE FROM friends WHERE ownerUserId = ?", arrayOf(testUserId))
            db.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(testUserId))
            db.execSQL("DELETE FROM users WHERE id = ?", arrayOf(testUserId))
        }
    }

    @Test
    fun loginPickerPersistsAcrossRecreationAndRelaunchAndSwitchesBack() {
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            onView(withId(R.id.btn_language)).perform(scrollTo(), click())
            onView(withText("Cancel")).perform(click())
            onView(withId(R.id.tv_auth_title)).check(matches(withText("Log in")))
            onView(withId(R.id.btn_language)).perform(scrollTo(), click())
            onView(withText("Tiếng Việt")).perform(click())
            awaitLanguage(scenario, "vi")
            onView(withId(R.id.tv_auth_title)).check(matches(withText("Đăng nhập")))
            assertEquals("vi", localeManager.applicationLocales.toLanguageTags())
            scenario.recreate()
            onView(withId(R.id.tv_auth_title)).check(matches(withText("Đăng nhập")))
        }
        ActivityScenario.launch(RegisterActivity::class.java).use { scenario ->
            awaitLanguage(scenario, "vi")
            onView(withId(R.id.tv_auth_title)).check(matches(withText("Tạo tài khoản")))
            onView(withId(R.id.btn_language)).perform(scrollTo(), click())
            onView(withText("English")).perform(click())
            awaitLanguage(scenario, "en")
            onView(withId(R.id.tv_auth_title)).check(matches(withText("Create account")))
            assertEquals("en", localeManager.applicationLocales.toLanguageTags())
        }
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.tv_auth_title)).check(matches(withText("Log in")))
        }
    }

    @Test
    fun profilePickerTranslatesSettingsAndPreservesStudentData() {
        signInTestUser()
        ActivityScenario.launch(ProfileActivity::class.java).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 10_000
            var loaded = false
            while (!loaded && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    loaded = it.findViewById<TextView>(R.id.tv_profile_name).text == "Language Test"
                }
                if (!loaded) SystemClock.sleep(50)
            }
            assertTrue("Profile should load", loaded)
            onView(withId(R.id.row_app_language)).perform(scrollTo(), click())
            onView(withText("Tiếng Việt")).perform(click())
            awaitLanguage(scenario, "vi")
            onView(withId(R.id.row_app_language)).check(matches(withText("Ngôn ngữ · Tiếng Việt")))
            scenario.onActivity {
                assertEquals("Đổi mật khẩu", it.getString(R.string.profile_change_password))
                assertEquals("Toán học", it.getString(R.string.subject_math))
            }
            assertEquals(125, AppDatabase.getInstance(context).profileDao().getByUserId(testUserId)?.xp)
            assertEquals(testUserId, auth.getLong(LoginActivity.KEY_USER_ID, 0L))
        }
    }

    @Test
    fun changingLanguageKeepsSelectedTabAndTranslatesNavigation() {
        signInTestUser()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HISTORY)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { AppLanguage.showPicker(it) }
            onView(withText("Tiếng Việt")).perform(click())
            awaitLanguage(scenario, "vi")
            scenario.onActivity {
                assertEquals(R.id.nav_history, it.findViewById<CurvedAnimatedBottomNav>(R.id.bottom_nav).selectedItemId)
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.fragment_container) is HistoryFragment)
                assertEquals("Trang chủ", it.getString(R.string.nav_home))
            }
            onView(withText("Thư viện bài học")).check(matches(withText("Thư viện bài học")))
        }
    }

    private fun signInTestUser() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
        )
        val db = AppDatabase.getInstance(context)
        testUserId = db.userDao().insert(User().apply {
            fullName = "Language Test"
            email = "language-${UUID.randomUUID()}@example.com"
            password = "unused-test-password"
        })
        val profile = ProfileDefaults.ensureExists(db.profileDao(), testUserId)
        profile.onboarded = true
        profile.xp = 125
        db.profileDao().update(profile)
        assertTrue(auth.edit().putLong(LoginActivity.KEY_USER_ID, testUserId).commit())
    }

    private fun <T : AppCompatActivity> awaitLanguage(scenario: ActivityScenario<T>, tag: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var actual = ""
        while (actual != tag && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { actual = it.resources.configuration.locales[0].language }
            if (actual != tag) SystemClock.sleep(50)
        }
        assertEquals(tag, actual)
    }
}
