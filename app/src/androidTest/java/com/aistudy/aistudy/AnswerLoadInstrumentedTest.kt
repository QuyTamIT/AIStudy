package com.aistudy.aistudy

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.answer.AnswerViewModel
import com.aistudy.aistudy.auth.LoginActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnswerLoadInstrumentedTest {
    @Test
    fun missingAnswerShowsBackAndDoesNotOfferRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val auth = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val key = LoginActivity.KEY_USER_ID
        val oldUser = if (auth.contains(key)) auth.getLong(key, 0L) else null
        val failed = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var target: AnswerActivity? = null
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity === target && stage == Stage.DESTROYED) closed.countDown()
        }
        val intent = Intent(context, AnswerActivity::class.java)
            .putExtra(AnswerActivity.EXTRA_QA_ID, Long.MAX_VALUE)

        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            assertTrue(auth.edit().putLong(key, Long.MAX_VALUE).commit())
            ActivityScenario.launch<AnswerActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    target = activity
                    ViewModelProvider(activity)[AnswerViewModel::class.java].state.observe(activity) {
                        if (it.loadFailed) failed.countDown()
                    }
                }
                assertTrue("Answer load failure was not delivered", failed.await(8, TimeUnit.SECONDS))
                onView(withText(R.string.answer_error_load)).perform(scrollTo()).check(matches(isDisplayed()))
                onView(withId(R.id.btn_error_retry)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.btn_error_home)).perform(scrollTo(), click())
                assertTrue("Back did not close the answer", closed.await(8, TimeUnit.SECONDS))
            }
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
            val editor = auth.edit()
            if (oldUser == null) editor.remove(key) else editor.putLong(key, oldUser)
            assertTrue(editor.commit())
        }
    }
}
