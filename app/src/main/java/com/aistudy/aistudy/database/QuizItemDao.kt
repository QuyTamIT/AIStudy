package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QuizItemDao {

    @Insert
    fun insert(item: QuizItem): Long

    @Query("UPDATE quiz_items SET userAnswer = :answer, isCorrect = :correct WHERE id = :id AND sessionId = :sessionId AND isCorrect IS NULL AND sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND completed = 0)")
    fun updateAnswer(id: Long, sessionId: Long, userId: Long, answer: String, correct: Boolean): Int

    @Query("SELECT * FROM quiz_items WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getBySessionId(sessionId: Long): List<QuizItem>

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId) AND isCorrect = 1")
    fun countCorrectByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserIdAndSubject(userId: Long, subject: String): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject) AND isCorrect = 1")
    fun countCorrectByUserIdAndSubject(userId: Long, subject: String): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject AND createdAt >= :fromMs AND createdAt < :toMs) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserIdSubjectAndRange(userId: Long, subject: String, fromMs: Long, toMs: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId AND subject = :subject AND createdAt >= :fromMs AND createdAt < :toMs) AND isCorrect = 1")
    fun countCorrectByUserIdSubjectAndRange(userId: Long, subject: String, fromMs: Long, toMs: Long): Int

    @Query("SELECT qs.subject FROM quiz_items qi INNER JOIN quiz_sessions qs ON qi.sessionId = qs.id WHERE qs.userId = :userId AND qi.isCorrect = 0 AND qs.createdAt >= :fromMs GROUP BY qs.subject HAVING COUNT(*) >= 2 LIMIT 1")
    fun findSubjectWithRepeatedMistakes(userId: Long, fromMs: Long): String?
}
