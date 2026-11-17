package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameResultDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(result: GameResult): Long

    @Query("SELECT * FROM game_results WHERE roundId = :roundId LIMIT 1")
    fun getByRoundId(roundId: String): GameResult?

    @Query("SELECT COALESCE(MAX(score), 0) FROM game_results WHERE userId = :userId AND subject = :subject AND timed = :timed")
    fun bestScore(userId: Long, subject: String, timed: Boolean): Int
}
