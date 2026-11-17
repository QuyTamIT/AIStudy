package com.aistudy.aistudy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Independent historical SQL fixtures, never the installed aistudy.db. */
@RunWith(AndroidJUnit4::class)
class ProfileMigrationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migratesV1() = verifyUpgrade(1)
    @Test fun migratesV2() = verifyUpgrade(2)
    @Test fun migratesV3() = verifyUpgrade(3)
    @Test fun migratesV4() = verifyUpgrade(4)
    @Test fun migratesV5() = verifyUpgrade(5)
    @Test fun migratesV6() = verifyUpgrade(6)
    @Test fun migratesV7() = verifyUpgrade(7)

    @Test fun unsupportedVersionFailsWithoutDeletingAccount() = verifyUnsupportedVersion { db ->
        assertThrows(IllegalStateException::class.java) { db.openHelper.writableDatabase }
    }

    @Test fun unsupportedVersionShowsStartupErrorWithoutDeletingAccount() = verifyUnsupportedVersion { db ->
        val prefs = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userKey = LoginActivity.KEY_USER_ID
        val previousUserId = if (prefs.contains(userKey)) prefs.getLong(userKey, -1L) else null
        val instanceField = AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val previousDatabase = instanceField.get(null)

        try {
            instanceField.set(null, db)
            assertTrue(prefs.edit().putLong(userKey, 42L).commit())
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withText(R.string.startup_database_error_message)).check(matches(isDisplayed()))
            }
            assertEquals(42L, prefs.getLong(userKey, -1L))
        } finally {
            instanceField.set(null, previousDatabase)
            val editor = prefs.edit()
            if (previousUserId == null) editor.remove(userKey) else editor.putLong(userKey, previousUserId)
            assertTrue(editor.commit())
        }
    }

    private fun verifyUnsupportedVersion(check: (AppDatabase) -> Unit) {
        val name = "missing-migration-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
            createUsers(sql)
            sql.version = 9
        }
        val db = AppDatabase.create(context, name)
        try {
            check(db)
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                assertEquals(9, sql.version)
                sql.rawQuery("SELECT password FROM users WHERE id = 42", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("LegacyPass123", it.getString(0))
                }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun verifyUpgrade(version: Int) {
        val name = "migration-v$version-${UUID.randomUUID()}.db"
        val image = File.createTempFile("migration-image-", ".png", context.filesDir)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var database: AppDatabase? = null
        try {
            createFixture(name, version, image.path)
            database = AppDatabase.create(context, name)
            val db = database
            val user = requireNotNull(db.userDao().findByEmail("legacy@example.com"))
            assertEquals(42L, user.id)
            assertEquals("Legacy User", user.fullName)
            assertEquals("LegacyPass123", user.password)
            val profile = requireNotNull(db.profileDao().getByUserId(42L))
            assertEquals(if (version == 1) 0 else 123, profile.xp)
            assertEquals(if (version == 1) 0 else 4, profile.streak)
            assertEquals(if (version == 1) "high" else "uni", profile.level)
            assertEquals(if (version == 1) "math,science" else "code", profile.subjects)
            assertEquals(if (version == 1) "stepwise" else "detailed", profile.style)
            assertEquals(version != 1, profile.onboarded)
            assertEquals("fox", profile.avatarId)
            assertEquals("default", profile.themeId)
            repeat(3) { ProfileDefaults.ensureExists(db.profileDao(), 42L) }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM profiles WHERE userId = 42").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
            if (version >= 3) {
                val qa = requireNotNull(db.questionAnswerDao().getById(55L, 42L))
                assertEquals("Legacy question", qa.question)
                assertEquals("[]", qa.steps)
                assertEquals("4", qa.finalAnswer)
                assertTrue(qa.starred)
                assertEquals(123456L, qa.createdAt)
                assertEquals(if (version >= 4) "easy" else null, qa.difficulty)
                assertEquals(if (version >= 4) "{\"simplify\":\"saved\"}" else null, qa.extrasJson)
                assertEquals(if (version >= 4) 54321L else 0L, qa.reviewedAt)
                assertEquals(if (version >= 7) 77 else 0, qa.reviewedSeconds)
                if (version >= 5) {
                    assertEquals(image.path, qa.imagePath)
                    val restored = requireNotNull(BitmapFactory.decodeFile(qa.imagePath))
                    assertEquals(8, restored.width)
                    restored.recycle()
                } else assertNull(qa.imagePath)
            } else assertEquals(0, db.questionAnswerDao().countByUserId(42L))
            if (version >= 6) {
                val session = requireNotNull(db.quizSessionDao().getById(66L, 42L))
                assertEquals(1, session.score); assertEquals(1, session.total)
                assertEquals("[55]", session.sourceQaIds); assertTrue(session.completed)
                val item = db.quizItemDao().getBySessionId(66L).single()
                assertEquals("4", item.userAnswer); assertEquals(true, item.correct)
            } else assertTrue(db.quizSessionDao().getAllByUserId(42L).isEmpty())
            assertTrue(image.exists())
            db.close()
            database = AppDatabase.create(context, name)
            assertEquals("LegacyPass123", database.userDao().getById(42L)?.password)
            assertEquals(if (version == 1) 0 else 123, database.profileDao().getByUserId(42L)?.xp)
            assertEquals(8, database.openHelper.readableDatabase.version)
        } finally {
            database?.close()
            context.deleteDatabase(name)
            image.delete()
        }
    }

    private fun createUsers(sql: SQLiteDatabase) {
        sql.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fullName TEXT, email TEXT, password TEXT)")
        sql.execSQL("CREATE UNIQUE INDEX index_users_email ON users(email)")
        sql.execSQL("INSERT INTO users VALUES (42, 'Legacy User', 'legacy@example.com', 'LegacyPass123')")
    }

    private fun createFixture(name: String, version: Int, imagePath: String) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
            createUsers(sql)
            if (version >= 2) {
                sql.execSQL("CREATE TABLE profiles (userId INTEGER NOT NULL PRIMARY KEY, onboarded INTEGER NOT NULL, level TEXT, subjects TEXT, style TEXT, xp INTEGER NOT NULL, streak INTEGER NOT NULL)")
                sql.execSQL("INSERT INTO profiles VALUES (42, 1, 'uni', 'code', 'detailed', 123, 4)")
            }
            if (version >= 3) {
                val extraColumns = buildString {
                    if (version >= 4) append(", difficulty TEXT DEFAULT NULL, extrasJson TEXT DEFAULT NULL, reviewedAt INTEGER NOT NULL DEFAULT 0")
                    if (version >= 5) append(", imagePath TEXT DEFAULT NULL")
                    if (version >= 7) append(", reviewedSeconds INTEGER NOT NULL DEFAULT 0")
                }
                sql.execSQL("CREATE TABLE question_answers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, subject TEXT, question TEXT, steps TEXT, finalAnswer TEXT, starred INTEGER NOT NULL, createdAt INTEGER NOT NULL$extraColumns)")
                sql.execSQL("INSERT INTO question_answers(id,userId,subject,question,steps,finalAnswer,starred,createdAt) VALUES (55,42,'math','Legacy question','[]','4',1,123456)")
                if (version >= 4) sql.execSQL("UPDATE question_answers SET difficulty='easy',extrasJson=?,reviewedAt=54321", arrayOf("{\"simplify\":\"saved\"}"))
                if (version >= 5) sql.execSQL("UPDATE question_answers SET imagePath=?", arrayOf(imagePath))
                if (version >= 7) sql.execSQL("UPDATE question_answers SET reviewedSeconds=77")
            }
            if (version >= 6) {
                sql.execSQL("CREATE TABLE quiz_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, subject TEXT, sourceQaIds TEXT, createdAt INTEGER NOT NULL, score INTEGER NOT NULL, total INTEGER NOT NULL, completed INTEGER NOT NULL)")
                sql.execSQL("CREATE TABLE quiz_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, type TEXT, prompt TEXT, optionsJson TEXT, correctAnswer TEXT, explanation TEXT, userAnswer TEXT, isCorrect INTEGER)")
                sql.execSQL("INSERT INTO quiz_sessions VALUES (66,42,'math','[55]',123456,1,1,1)")
                sql.execSQL("INSERT INTO quiz_items VALUES (77,66,'short','2+2','[]','4','Two pairs','4',1)")
            }
            sql.version = version
        }
    }
}
