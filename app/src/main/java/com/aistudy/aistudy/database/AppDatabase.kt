package com.aistudy.aistudy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        User::class,
        Profile::class,
        QuestionAnswer::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun profileDao(): ProfileDao

    abstract fun questionAnswerDao(): QuestionAnswerDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `userId` INTEGER NOT NULL,
                        `onboarded` INTEGER NOT NULL,
                        `level` TEXT,
                        `subjects` TEXT,
                        `style` TEXT,
                        `xp` INTEGER NOT NULL,
                        `streak` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `profiles` (`userId`, `onboarded`, `level`, `subjects`, `style`, `xp`, `streak`)
                    SELECT `id`, 0, '${ProfileDefaults.LEVEL_HIGH}',
                        '${ProfileDefaults.SUBJECT_MATH},${ProfileDefaults.SUBJECT_SCIENCE}',
                        '${ProfileDefaults.STYLE_STEPWISE}', 0, 0 FROM `users`
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `question_answers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `subject` TEXT,
                        `question` TEXT,
                        `steps` TEXT,
                        `finalAnswer` TEXT,
                        `starred` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aistudy.db"
                )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
