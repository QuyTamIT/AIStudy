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
        QuestionAnswer::class,
        QuizSession::class,
        QuizItem::class,
        Friend::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun profileDao(): ProfileDao

    abstract fun questionAnswerDao(): QuestionAnswerDao

    abstract fun quizSessionDao(): QuizSessionDao

    abstract fun quizItemDao(): QuizItemDao

    abstract fun friendDao(): FriendDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `question_answers` ADD COLUMN `difficulty` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `question_answers` ADD COLUMN `extrasJson` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `question_answers` ADD COLUMN `reviewedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `question_answers` ADD COLUMN `imagePath` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quiz_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `subject` TEXT,
                        `sourceQaIds` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `score` INTEGER NOT NULL,
                        `total` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quiz_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `type` TEXT,
                        `prompt` TEXT,
                        `optionsJson` TEXT,
                        `correctAnswer` TEXT,
                        `explanation` TEXT,
                        `userAnswer` TEXT,
                        `isCorrect` INTEGER
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `question_answers` ADD COLUMN `reviewedSeconds` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `avatarId` TEXT DEFAULT 'fox'")
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `themeId` TEXT DEFAULT 'default'")
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `weeklyXp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `weekStartMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `lastActiveDay` TEXT DEFAULT NULL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `friends` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerUserId` INTEGER NOT NULL,
                        `displayName` TEXT,
                        `weeklyXp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: create(context, "aistudy.db").also { instance = it }
        }

        fun create(context: Context, name: String): AppDatabase = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, name
        )
            .allowMainThreadQueries()
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
            )
            .build()

    }
}
