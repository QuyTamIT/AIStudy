package com.aistudy.aistudy

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileMigrationInstrumentedTest {
    @Test
    fun migrationPreservesLegacyUserAndCreatesOneProfile() {
        // A separate fixture database; never delete or downgrade the user's aistudy.db.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-fixture-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("""
                CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fullName TEXT, email TEXT, password TEXT)
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX index_users_email ON users(email)")
            db.execSQL("INSERT INTO users VALUES (42, 'Legacy User', 'legacy@example.com', 'LegacyPass123')")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
                val user = requireNotNull(database.userDao().findByEmail("legacy@example.com"))
                assertEquals(42L, user.id)
                assertEquals("Legacy User", user.fullName)
                assertEquals("LegacyPass123", user.password)
                val dao = database.profileDao()
                val profile = requireNotNull(dao.getByUserId(user.id))
                assertFalse(profile.onboarded)
                assertEquals("high", profile.level)
                assertEquals("math,science", profile.subjects)
                assertEquals("stepwise", profile.style)
                assertEquals(0, profile.xp)
                assertEquals(0, profile.streak)
                profile.onboarded = true
                profile.level = "uni"
                profile.subjects = "code"
                profile.style = "detailed"
                assertEquals(1, dao.update(profile))
                repeat(3) { ProfileDefaults.ensureExists(dao, user.id) }
                val persisted = requireNotNull(dao.getByUserId(user.id))
                assertTrue(persisted.onboarded)
                assertEquals("uni", persisted.level)
                assertEquals("code", persisted.subjects)
                assertEquals("detailed", persisted.style)
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM profiles WHERE userId = 42").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                }
        } finally {
            database.close()
        }
    }
}
