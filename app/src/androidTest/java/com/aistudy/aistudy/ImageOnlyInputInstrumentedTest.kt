package com.aistudy.aistudy

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.User
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageOnlyInputInstrumentedTest {
    @Test fun imageOnlyDraftSurvivesRecreationAndDoesNotTouchSavedImage() = withImageDraft { scenario, draft, savedImage ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        val database = AppDatabase.getInstance(context)

        onView(withId(R.id.edit_question)).check(matches(withText("")))
        onView(withId(R.id.layout_image_preview)).check(matches(isDisplayed()))
        assertTrue(draft.exists())
        scenario.recreate()
        onView(withId(R.id.layout_image_preview)).check(matches(isDisplayed()))
        assertTrue(draft.exists())
        // Send must pass the empty-text validation but stop before network: no active account.
        assertTrue(preferences.edit().remove(LoginActivity.KEY_USER_ID).commit())
        onView(withId(R.id.btn_send)).perform(click())
        if (BuildConfig.GEMINI_API_KEY.isBlank() || BuildConfig.GEMINI_API_KEY.startsWith("YOUR_", ignoreCase = true)) {
            onView(withId(R.id.tv_ask_error)).check(matches(withText(R.string.ask_error_key_missing)))
            onView(withId(R.id.btn_remove_image)).perform(click())
            assertFalse(draft.exists())
        } else {
            val deadline = SystemClock.uptimeMillis() + 8_000L
            var loginVisible = false
            while (!loginVisible && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    loginVisible = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).any { it is LoginActivity }
                }
                if (!loginVisible) SystemClock.sleep(50)
            }
            assertTrue("Image-only input must reach the session guard", loginVisible)
        }
        assertTrue(savedImage.exists())
        assertEquals(0, database.questionAnswerDao().countByUserId(userId))
        assertEquals(0, database.profileDao().getByUserId(userId)?.xp)
    }

    @Test fun leavingAskWhileSendingDeletesDraft() = withImageDraft { scenario, draft, savedImage ->
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AskFragment
            // Enter submit's loading state without starting a Gemini request.
            AskFragment::class.java.getDeclaredMethod("setLoading", java.lang.Boolean.TYPE)
                .apply { isAccessible = true }
                .invoke(fragment, true)
        }
        assertTrue(draft.exists())
        onView(withId(R.id.nav_history)).perform(click())
        scenario.onActivity { activity ->
            activity.supportFragmentManager.executePendingTransactions()
        }
        assertFalse("Leaving Ask while sending must delete its draft image", draft.exists())
        assertTrue(savedImage.exists())
    }

    private fun withImageDraft(checkDraft: (ActivityScenario<MainActivity>, File, File) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val oldUser = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        val database = AppDatabase.getInstance(context)
        val userId = database.userDao().insert(User().apply { email = "image-${UUID.randomUUID()}@example.com" })
        ProfileDefaults.ensureExists(database.profileDao(), userId).also {
            it.onboarded = true; database.profileDao().update(it)
        }
        val dir = File(context.filesDir, "qa_images").apply { mkdirs() }
        // Intentionally wrong suffix: copy/preview must inspect image bytes, not MIME from the provider.
        val savedImage = File.createTempFile("input-fixture-", ".jpg", dir)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        savedImage.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var draft: File? = null
        try {
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
            assertTrue(preferences.edit().putLong(LoginActivity.KEY_USER_ID, userId).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.nav_ask)).perform(click())
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AskFragment
                    // Drive the real provider-copy/result handlers without relying on a device-specific gallery UI.
                    val copy = AskFragment::class.java.getDeclaredMethod("copyUriToQaImages", Uri::class.java).apply { isAccessible = true }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", savedImage)
                    draft = copy.invoke(fragment, uri) as File
                    val select = AskFragment::class.java.getDeclaredMethod("setSelectedImage", File::class.java).apply { isAccessible = true }
                    select.invoke(fragment, draft)
                }
                checkDraft(scenario, requireNotNull(draft), savedImage)
            }
        } finally {
            instrumentation.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).toList().forEach { it.finish() }
            }
            draft?.delete()
            savedImage.delete()
            val sql = database.openHelper.writableDatabase
            sql.execSQL("DELETE FROM friends WHERE ownerUserId = ?", arrayOf(userId))
            sql.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(userId))
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(userId))
            preferences.edit().remove(LoginActivity.KEY_USER_ID).also {
                if (oldUser > 0L) it.putLong(LoginActivity.KEY_USER_ID, oldUser)
            }.commit()
        }
    }
}
