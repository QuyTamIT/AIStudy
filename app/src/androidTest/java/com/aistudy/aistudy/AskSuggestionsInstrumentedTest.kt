package com.aistudy.aistudy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.core.widget.doAfterTextChanged
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.ask.AskSuggestions
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.User
import com.aistudy.aistudy.profile.ProfileActivity
import com.aistudy.aistudy.profile.ProfileViewModel
import com.aistudy.aistudy.profile.StudentProfileRepository
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AskSuggestionsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database = AppDatabase.getInstance(context)
    private val buttons = listOf(R.id.btn_suggestion_1, R.id.btn_suggestion_2, R.id.btn_suggestion_3, R.id.btn_suggestion_4)

    @Test fun visibleQuestionsFollowProfileEditsAndTheActiveAccount() {
        val session = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val key = LoginActivity.KEY_USER_ID
        val previous = if (session.contains(key)) session.getLong(key, 0L) else null
        val accounts = mutableListOf<Long>()
        fun account(subjects: String?): Long {
            val id = database.userDao().insert(User().apply { email = "suggestions-${UUID.randomUUID()}@example.com" })
            accounts.add(id)
            database.profileDao().insert(ProfileDefaults.createDefault(id).apply {
                onboarded = true
                this.subjects = subjects
            })
            return id
        }
        try {
            val first = account("code,language")
            val second = account(null)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            assertTrue(session.edit().putLong(key, first).commit())
            val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                awaitQuestions(scenario, mapOf(R.array.ask_questions_code to 2, R.array.ask_questions_language to 2))
                val repository = StudentProfileRepository(context)
                assertTrue(repository.getProfile(second).favoriteSubjects.isEmpty())
                repository.updatePreferences(second, ProfileDefaults.LEVEL_HIGH, ProfileDefaults.STYLE_SHORT, listOf("history")).getOrThrow()
                repository.updatePreferences(first, ProfileDefaults.LEVEL_HIGH, ProfileDefaults.STYLE_SHORT, listOf("math")).getOrThrow()
                val math = awaitQuestions(scenario, mapOf(R.array.ask_questions_math to 4))
                onView(withId(buttons.first())).perform(scrollTo(), click())
                onView(withId(R.id.edit_question)).check(matches(withText(math.first())))
                assertTrue(session.edit().putLong(key, second).commit())
                awaitQuestions(scenario, mapOf(R.array.ask_questions_history to 4))
                clearLastSubjectInProfile(scenario, second)
                awaitQuestions(scenario, mapOf(R.array.ask_questions_general to 4))
            }
        } finally {
            val editor = session.edit()
            if (previous == null) editor.remove(key) else editor.putLong(key, previous)
            editor.commit()
            val sql = database.openHelper.writableDatabase
            for (id in accounts) {
                sql.execSQL("DELETE FROM friends WHERE ownerUserId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM profiles WHERE userId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM users WHERE id = ?", arrayOf(id))
            }
        }
    }

    private fun clearLastSubjectInProfile(main: ActivityScenario<MainActivity>, userId: Long) {
        val monitor = instrumentation.addMonitor(ProfileActivity::class.java.name, null, false)
        val profile = try {
            main.onActivity { it.startActivity(Intent(it, ProfileActivity::class.java)) }
            requireNotNull(monitor.waitForActivityWithTimeout(15_000) as? ProfileActivity)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        try {
            val loaded = CountDownLatch(1)
            val cleared = CountDownLatch(1)
            instrumentation.runOnMainSync {
                ViewModelProvider(profile)[ProfileViewModel::class.java].uiState.observe(profile) { state ->
                    val data = state.profile
                    if (data?.userId == userId) {
                        if (data.favoriteSubjects == listOf("history")) loaded.countDown()
                        if (data.favoriteSubjects.isEmpty()) cleared.countDown()
                    }
                }
            }
            assertTrue("Profile did not load the selected subject", loaded.await(15, TimeUnit.SECONDS))
            onView(withId(R.id.btn_edit_profile_action)).perform(click())
            onView(withId(R.id.chip_subject_history)).check(matches(isChecked())).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())
            assertTrue("Clearing the last subject was not saved", cleared.await(15, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { profile.finish() }
        }
    }

    @Test fun allSubjectCombinationsAreBalancedAndLocalized() {
        val subjects = mapOf(
            "math" to R.array.ask_questions_math, "science" to R.array.ask_questions_science,
            "code" to R.array.ask_questions_code, "history" to R.array.ask_questions_history,
            "language" to R.array.ask_questions_language
        )
        val questionsByLanguage = mutableListOf<Set<String>>()
        for (language in listOf("en", "vi")) {
            val fallback = if (language == "vi") "en" else "vi"
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(language), Locale.forLanguageTag(fallback)))
            }
            val resources = context.createConfigurationContext(configuration).resources
            val pools = subjects.mapValues { (_, id) -> resources.getStringArray(id).toSet() }
            for (mask in 1 until (1 shl subjects.size)) {
                val selected = subjects.keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                val questions = AskSuggestions.forSubjects(resources, selected.joinToString(","), buttons.size).map { it.text }
                assertEquals(buttons.size, questions.distinct().size)
                val counts = selected.map { subject -> questions.count { it in pools.getValue(subject) } }
                assertEquals(buttons.size, counts.sum())
                assertTrue("Uneven distribution for $selected", counts.max() - counts.min() <= 1)
            }
            val general = resources.getStringArray(R.array.ask_questions_general).toList()
            for (missing in listOf(null, "", " ,other,unknown")) {
                assertEquals(general, AskSuggestions.forSubjects(resources, missing, buttons.size).map { it.text })
            }
            val mixed = AskSuggestions.forSubjects(resources, " code,code,unknown, language ", buttons.size)
            assertEquals(2, mixed.count { it.text in pools.getValue("code") })
            assertEquals(2, mixed.count { it.text in pools.getValue("language") })
            val firstCode = resources.getStringArray(R.array.ask_questions_code).first()
            assertEquals(if (language == "en") "How does a for loop work in Python?"
                else "Vòng lặp for trong Python hoạt động như thế nào?", firstCode)
            questionsByLanguage += (pools.values.flatten() + general).toSet()
        }
        assertTrue("Every question needs both translations", questionsByLanguage[0].intersect(questionsByLanguage[1]).isEmpty())
    }

    private fun awaitQuestions(scenario: ActivityScenario<MainActivity>, counts: Map<Int, Int>): List<String> {
        val ready = CountDownLatch(1)
        var labels = emptyList<String>()
        lateinit var detach: () -> Unit
        scenario.onActivity { activity ->
            activity.supportFragmentManager.executePendingTransactions()
            val views = buttons.map { activity.findViewById<TextView>(it) }
            val pools = counts.mapKeys { (id, _) -> activity.resources.getStringArray(id).toSet() }
            fun checkLabels() {
                labels = views.map { it.text.toString() }
                if (labels.distinct().size == buttons.size && pools.all { (pool, count) -> labels.count { it in pool } == count }) {
                    ready.countDown()
                }
            }
            val watchers = views.map { it.doAfterTextChanged { checkLabels() } }
            detach = { views.zip(watchers).forEach { (view, watcher) -> view.removeTextChangedListener(watcher) } }
            checkLabels()
        }
        try {
            assertTrue("Ask did not show the expected subject distribution", ready.await(15, TimeUnit.SECONDS))
        } finally {
            scenario.onActivity { detach() }
        }
        return labels
    }
}
