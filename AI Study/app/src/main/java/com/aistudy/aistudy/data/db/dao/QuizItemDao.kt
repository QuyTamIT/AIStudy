package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aistudy.aistudy.domain.model.entity.QuizItem
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.flow.Flow

/**
 * Truy cập bảng `quiz_items` — FR7.
 *
 * [accuracy] cố tình lọc `isCorrect IS NOT NULL` để câu chưa làm không
 * vào mẫu số — đây là ca kiểm thử TC-QUI-07.
 */
@Dao
interface QuizItemDao {

    @Query("SELECT * FROM quiz_items WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<QuizItem>>

    @Query("SELECT * FROM quiz_items WHERE userId = :userId AND isCorrect IS NULL ORDER BY createdAt DESC")
    fun observeUnanswered(userId: Long): Flow<List<QuizItem>>

    @Query("SELECT * FROM quiz_items WHERE userId = :userId AND subjectCode = :subject ORDER BY createdAt DESC")
    fun observeBySubject(userId: Long, subject: Subject): Flow<List<QuizItem>>

    @Query("SELECT * FROM quiz_items WHERE id = :id")
    suspend fun findById(id: Long): QuizItem?

    @Insert
    suspend fun insertAll(items: List<QuizItem>): List<Long>

    @Insert
    suspend fun insert(item: QuizItem): Long

    @Query(
        """
        UPDATE quiz_items
        SET isCorrect = :isCorrect, attempts = attempts + 1, answeredAt = :answeredAt
        WHERE id = :id
        """
    )
    suspend fun recordAttempt(id: Long, isCorrect: Boolean, answeredAt: Long)

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END), 0) * 1.0
        / NULLIF(COUNT(*), 0)
        FROM quiz_items
        WHERE userId = :userId AND isCorrect IS NOT NULL
        """
    )
    suspend fun accuracy(userId: Long): Double

    @Query("SELECT COUNT(*) FROM quiz_items WHERE userId = :userId")
    suspend fun countByUser(userId: Long): Int

    /**
     * So cau DA LAM - `isCorrect IS NOT NULL`, khong phai `attempts`.
     * Cau chua lam khong duoc tinh vao mau so (TC-QUI-07).
     */
    @Query("SELECT COUNT(*) FROM quiz_items WHERE userId = :userId AND isCorrect IS NOT NULL")
    suspend fun answeredCount(userId: Long): Int

    @Query("SELECT COUNT(DISTINCT subjectCode) FROM quiz_items WHERE userId = :userId")
    suspend fun countDistinctSubjects(userId: Long): Int

    /** N3 GDPR. */
    @Query("DELETE FROM quiz_items WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
