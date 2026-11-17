package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuizSessionDao {

    @Insert
    fun insert(session: QuizSession): Long

    @Update
    fun update(session: QuizSession)

    @Query("SELECT * FROM quiz_sessions WHERE id = :id AND userId = :userId LIMIT 1")
    fun getById(id: Long, userId: Long): QuizSession?

    @Query("UPDATE quiz_sessions SET completed = 1, score = :score, total = :total WHERE id = :id AND userId = :userId AND completed = 0")
    fun completeIfUnfinished(id: Long, userId: Long, score: Int, total: Int): Int

    @Query("SELECT * FROM quiz_sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuizSession>

    @Query("SELECT COUNT(*) FROM quiz_sessions WHERE userId = :userId AND completed = 1 AND score = total AND total > 0")
    fun countPerfectByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_sessions WHERE userId = :userId AND completed = 1 AND createdAt >= :fromMs")
    fun countCompletedSince(userId: Long, fromMs: Long): Int
}
