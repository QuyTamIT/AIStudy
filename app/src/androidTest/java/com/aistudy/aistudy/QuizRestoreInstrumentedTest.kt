package com.aistudy.aistudy

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.*
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.quiz.QuizViewModel
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class QuizRestoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)

    @Test
    fun rotationKeepsSelectionFeedbackAndAwardsXpOnlyOnce() = fixture(correctOption = " 4 ") { owner, sessionId ->
        val intent = Intent(context, QuizActivity::class.java)
            .putExtra(QuizActivity.EXTRA_SUBJECT, "math").putExtra("sessionId", sessionId)
        ActivityScenario.launch<QuizActivity>(intent).use { scenario ->
            awaitQuiz(scenario)
            onView(withText(" 4 ")).perform(click())
            scenario.recreate()
            awaitQuiz(scenario)
            scenario.onActivity { activity ->
                val group = activity.findViewById<RadioGroup>(R.id.group_options)
                assertTrue(group.checkedRadioButtonId != -1)
            }
            onView(withId(R.id.btn_primary)).perform(click())
            await(scenario, R.id.layout_feedback)
            onView(withText(" 4 ")).check(matches(isChecked()))
            onView(withId(R.id.layout_feedback)).check(matches(isDisplayed()))
            scenario.recreate()
            awaitQuiz(scenario)
            onView(withId(R.id.layout_feedback)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_primary)).check(matches(withText(R.string.quiz_next)))
            onView(withText(" 4 ")).check(matches(isChecked())).check(matches(isNotEnabled()))
            onView(withId(R.id.btn_primary)).perform(click())
            onView(withId(R.id.et_answer)).perform(replaceText("two"), closeSoftKeyboard())
            scenario.recreate()
            awaitQuiz(scenario)
            onView(withId(R.id.et_answer)).check(matches(withText("two")))
            onView(withId(R.id.btn_primary)).perform(click())
            await(scenario, R.id.layout_feedback)
            onView(withId(R.id.btn_primary)).perform(click())
            awaitResult(scenario)
            val xp = requireNotNull(db.profileDao().getByUserId(owner)).xp
            assertEquals(51, xp)
            scenario.recreate()
            awaitResult(scenario)
            assertEquals(xp, db.profileDao().getByUserId(owner)?.xp)
            assertEquals(1, db.quizSessionDao().getAllByUserId(owner).size)
        }
        ActivityScenario.launch<QuizActivity>(intent).use { reopened ->
            awaitResult(reopened)
            assertEquals(51, db.profileDao().getByUserId(owner)?.xp)
        }
    }

    @Test
    fun savesSessionIdWhileRestoreIsStillLoading() = fixture { _, sessionId ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val savedSessionId = AtomicLong(-1L)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is QuizActivity && stage == Stage.CREATED) {
                // The Room worker cannot update the screen while this creation callback is running.
                val state = Bundle()
                instrumentation.callActivityOnSaveInstanceState(activity, state)
                savedSessionId.set(state.getLong(QuizActivity.EXTRA_SESSION_ID, -1L))
            }
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            val intent = Intent(context, QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_SUBJECT, "math")
                .putExtra(QuizActivity.EXTRA_SESSION_ID, sessionId)
            ActivityScenario.launch<QuizActivity>(intent).use { scenario ->
                assertEquals(sessionId, savedSessionId.get())
                awaitQuiz(scenario)
            }
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
        }
    }

    @Test
    fun recreatingWhileGeneratingKeepsOneRequest() = fixture { owner, fixtureSessionId ->
        repeat(2) { index ->
            db.questionAnswerDao().insert(QuestionAnswer().apply {
                userId = owner
                subject = "math"
                question = "Fixture question $index"
                finalAnswer = "4"
            })
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val started = CountDownLatch(1)
        val respond = CountDownLatch(1)
        val requestCount = AtomicInteger()
        val factory = viewModelFactory {
            initializer {
                QuizViewModel(context.applicationContext as Application, apiKey = "fixture") { _, _ ->
                    requestCount.incrementAndGet()
                    started.countDown()
                    check(respond.await(15, TimeUnit.SECONDS))
                    """{"items":[{"type":"short","prompt":"2 + 2?","correctAnswer":"4"}]}"""
                }
            }
        }
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is QuizActivity && stage == Stage.PRE_ON_CREATE) {
                ViewModelProvider(activity, factory)[QuizViewModel::class.java]
            }
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            val intent = Intent(context, QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_SUBJECT, "math")
            ActivityScenario.launch<QuizActivity>(intent).use { scenario ->
                assertTrue("Quiz request did not start", started.await(8, TimeUnit.SECONDS))
                scenario.recreate()
                onView(withId(R.id.layout_loading)).check(matches(isDisplayed()))
                respond.countDown()
                awaitQuiz(scenario)
                assertEquals(1, requestCount.get())
                val generatedSession = db.quizSessionDao().getAllByUserId(owner).single { it.id != fixtureSessionId }
                val state = Bundle()
                scenario.onActivity { instrumentation.callActivityOnSaveInstanceState(it, state) }
                assertEquals(generatedSession.id, state.getLong(QuizActivity.EXTRA_SESSION_ID, -1L))
            }
        } finally {
            respond.countDown()
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
        }
    }

    @Test
    fun competingGradeUsesStoredAnswerForFeedbackScoreAndXp() = fixture(correctOption = " 4 ") { owner, sessionId ->
        val intent = Intent(context, QuizActivity::class.java)
            .putExtra(QuizActivity.EXTRA_SUBJECT, "math")
            .putExtra(QuizActivity.EXTRA_SESSION_ID, sessionId)
        ActivityScenario.launch<QuizActivity>(intent).use { scenario ->
            awaitQuiz(scenario)
            val first = db.quizItemDao().getBySessionId(sessionId).first()
            // Commit another grade after the Activity read the ungraded question.
            assertEquals(1, db.quizItemDao().updateAnswer(first.id, sessionId, owner, "4", true))
            onView(withText("5")).perform(click())
            onView(withId(R.id.btn_primary)).perform(click())
            await(scenario, R.id.layout_feedback)
            onView(withText(" 4 ")).check(matches(isChecked()))
            var feedback = ""
            scenario.onActivity {
                feedback = it.findViewById<TextView>(R.id.tv_feedback_result).text.toString()
            }
            onView(withId(R.id.btn_primary)).perform(click())
            onView(withId(R.id.et_answer)).perform(replaceText("three"), closeSoftKeyboard())
            onView(withId(R.id.btn_primary)).perform(click())
            await(scenario, R.id.layout_feedback)
            onView(withId(R.id.btn_primary)).perform(click())
            awaitResult(scenario)
            assertEquals(1, db.quizSessionDao().getById(sessionId, owner)?.score)
            assertEquals(41, db.profileDao().getByUserId(owner)?.xp)
            assertEquals(context.getString(R.string.quiz_correct), feedback)
        }
    }

    @Test
    fun anotherAccountCannotRestoreOrModifySession() = fixture { _, sessionId ->
        prefs.edit().putLong(LoginActivity.KEY_USER_ID, 999_999L).commit()
        val intent = Intent(context, QuizActivity::class.java)
            .putExtra(QuizActivity.EXTRA_SUBJECT, "math").putExtra("sessionId", sessionId)
        ActivityScenario.launch<QuizActivity>(intent).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 8_000L
            while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
            }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
            assertTrue(db.quizItemDao().getBySessionId(sessionId).all { it.userAnswer == null })
        }
    }

    private fun fixture(correctOption: String = "4", block: (Long, Long) -> Unit) {
        val oldId = prefs.getLong(LoginActivity.KEY_USER_ID, 0L)
        val owner = db.userDao().insert(User().apply { email = "quiz-${UUID.randomUUID()}@example.com" })
        ProfileDefaults.ensureExists(db.profileDao(), owner).also { it.xp = 11; db.profileDao().update(it) }
        val sessionId = db.quizSessionDao().insert(QuizSession().apply { userId = owner; subject = "math"; total = 2 })
        db.quizItemDao().insert(QuizItem().apply {
            this.sessionId = sessionId; type = "mcq"; prompt = "2 + 2?"
            optionsJson = JSONArray(listOf("3", correctOption, "5", "6")).toString()
            correctAnswer = "4"; explanation = "Two pairs make four."
        })
        db.quizItemDao().insert(QuizItem().apply {
            this.sessionId = sessionId; type = "short"; prompt = "One plus one (word)?"; correctAnswer = "two"
        })
        prefs.edit().putLong(LoginActivity.KEY_USER_ID, owner).commit()
        try { block(owner, sessionId) } finally {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("DELETE FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = ?)", arrayOf(owner))
            sql.execSQL("DELETE FROM quiz_sessions WHERE userId = ?", arrayOf(owner))
            sql.execSQL("DELETE FROM question_answers WHERE userId = ?", arrayOf(owner))
            sql.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(owner))
            sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(owner))
            prefs.edit().remove(LoginActivity.KEY_USER_ID).also { if (oldId > 0) it.putLong(LoginActivity.KEY_USER_ID, oldId) }.commit()
        }
    }

    private fun awaitQuiz(scenario: ActivityScenario<QuizActivity>) = await(scenario, R.id.layout_quiz)
    private fun awaitResult(scenario: ActivityScenario<QuizActivity>) = await(scenario, R.id.layout_result)
    private fun await(scenario: ActivityScenario<QuizActivity>, viewId: Int) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var visible = false
            if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) throw AssertionError("Quiz closed instead of restoring fixture")
            scenario.onActivity { visible = it.findViewById<View>(viewId)?.visibility == View.VISIBLE }
            if (visible) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Quiz state not ready")
    }
}
