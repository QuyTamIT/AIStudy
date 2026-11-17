package com.aistudy.aistudy

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.room.Room
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.GameResult
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.game.GameActivity
import com.aistudy.aistudy.game.GameResultRepository
import com.google.android.material.chip.ChipGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class GameInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun resultsAwardXpOnceAndKeepBestScoresSeparate() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.profileDao().insert(Profile().apply { userId = 42; xp = 50 })
            val repo = GameResultRepository(db)
            val first = GameResult("first", 42, "math", true, 225, 2, 3, 2, 0, 123)
            assertEquals(20, repo.save(first).xpAwarded)
            repeat(3) { assertEquals(20, repo.save(first).xpAwarded) }
            assertEquals(70, db.profileDao().getByUserId(42)!!.xp)
            assertEquals(20, db.profileDao().getByUserId(42)!!.weeklyXp)
            repo.save(first.copy(roundId = "practice", timed = false, score = 500, correctCount = 4))
            repo.save(first.copy(roundId = "science", subject = "science", score = 100, correctCount = 1))
            assertEquals(225, db.gameResultDao().bestScore(42, "math", true))
            assertEquals(500, db.gameResultDao().bestScore(42, "math", false))
            assertEquals(0, db.gameResultDao().bestScore(99, "math", true))
        } finally {
            db.close()
        }
    }

    @Test fun migrationFromEightAddsGamesAndPreservesProfile() {
        val name = "game-migration-${UUID.randomUUID()}.db"
        // Build an isolated v8 fixture from the unchanged v8 tables; never touch aistudy.db.
        Room.databaseBuilder(context, AppDatabase::class.java, name).build().let { db ->
            db.profileDao().insert(Profile().apply { userId = 42; xp = 123; streak = 7 })
            db.close()
        }
        context.openOrCreateDatabase(name, 0, null).use {
            it.execSQL("DROP TABLE game_results")
            it.version = 8
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_8_9).build()
        try {
            assertEquals(123, migrated.profileDao().getByUserId(42)!!.xp)
            assertEquals(7, migrated.profileDao().getByUserId(42)!!.streak)
            assertEquals(0, migrated.gameResultDao().bestScore(42, "math", true))
            assertEquals(9, migrated.openHelper.readableDatabase.version)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun offlineRoundSurvivesRecreationAndShowsReview() {
        val prefs = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val hadUser = prefs.contains(LoginActivity.KEY_USER_ID)
        val originalId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)
        val fixtureId = System.nanoTime()
        prefs.edit().putLong(LoginActivity.KEY_USER_ID, fixtureId).commit()
        try {
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)).use { scenario ->
                capture("setup")
                scenario.onActivity {
                    it.findViewById<ChipGroup>(R.id.game_modes).check(R.id.game_untimed)
                }
                onView(withId(R.id.game_start)).perform(scrollTo(), click())
                scenario.onActivity {
                    val options = it.findViewById<LinearLayout>(R.id.game_options)
                    assertEquals(4, options.childCount)
                }
                capture("question")
                scenario.onActivity {
                    it.findViewById<LinearLayout>(R.id.game_options).getChildAt(0).performClick()
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.game_feedback).visibility)
                }
                capture("feedback")
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.game_feedback).visibility)
                    assertEquals(it.getString(R.string.game_progress, 1, 10), it.findViewById<TextView>(R.id.game_question_number).text)
                    it.findViewById<View>(R.id.game_next).performClick()
                }
                repeat(9) { index ->
                    scenario.onActivity {
                        it.findViewById<LinearLayout>(R.id.game_options).getChildAt(0).performClick()
                        if (index < 8) it.findViewById<View>(R.id.game_next).performClick()
                    }
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.game_result).visibility)
                    assertEquals(20, it.findViewById<LinearLayout>(R.id.game_reviews).childCount)
                }
                awaitSaved(scenario)
                capture("result")
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.game_result).visibility)
                }
                awaitSaved(scenario)
            }
        } finally {
            prefs.edit().apply {
                if (hadUser) putLong(LoginActivity.KEY_USER_ID, originalId) else remove(LoginActivity.KEY_USER_ID)
            }.commit()
            // Remove only results belonging to this test's unique fixture identity.
            AppDatabase.getInstance(context).openHelper.writableDatabase
                .execSQL("DELETE FROM game_results WHERE userId = ?", arrayOf(fixtureId))
        }
    }

    @Test fun timedRoundExpiresAfterBeingBackgrounded() {
        val prefs = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val hadUser = prefs.contains(LoginActivity.KEY_USER_ID)
        val originalId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)
        val fixtureId = System.nanoTime()
        prefs.edit().putLong(LoginActivity.KEY_USER_ID, fixtureId).commit()
        try {
            ActivityScenario.launch<GameActivity>(Intent(context, GameActivity::class.java)).use { scenario ->
                scenario.onActivity { it.findViewById<View>(R.id.game_start).performClick() }
                scenario.moveToState(Lifecycle.State.CREATED)
                SystemClock.sleep(60_100)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity {
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.game_result).visibility)
                    assertEquals(it.getString(R.string.game_time_up), it.findViewById<TextView>(R.id.game_result_title).text)
                    assertEquals(it.getString(R.string.game_result_score, 0), it.findViewById<TextView>(R.id.game_result_score).text)
                }
                awaitSaved(scenario)
            }
        } finally {
            prefs.edit().apply {
                if (hadUser) putLong(LoginActivity.KEY_USER_ID, originalId) else remove(LoginActivity.KEY_USER_ID)
            }.commit()
            AppDatabase.getInstance(context).openHelper.writableDatabase
                .execSQL("DELETE FROM game_results WHERE userId = ?", arrayOf(fixtureId))
        }
    }

    private fun awaitSaved(scenario: ActivityScenario<GameActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity {
                ready = it.findViewById<TextView>(R.id.game_save_status).text.toString() == it.getString(R.string.game_xp, 0)
            }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("The offline round result was not saved")
    }

    private fun capture(name: String) {
        // Let the next frame and the optional 120ms feedback fade reach the screen.
        SystemClock.sleep(200)
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "game-$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
