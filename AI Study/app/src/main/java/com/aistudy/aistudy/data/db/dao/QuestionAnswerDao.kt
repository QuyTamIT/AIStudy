package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.flow.Flow

/**
 * Truy cập bảng `question_answers` — FR3, FR4, FR5, FR6.
 *
 * [findByHash] là đường tra cache chính xác của cơ chế AI Cost Management.
 * [search] dùng LIKE chứ không phải full-text — giới hạn đã ghi trong design doc.
 */
@Dao
interface QuestionAnswerDao {

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<QuestionAnswer>>

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND isBookmarked = 1 ORDER BY createdAt DESC")
    fun observeBookmarked(userId: Long): Flow<List<QuestionAnswer>>

    @Query("SELECT * FROM question_answers WHERE id = :id")
    suspend fun findById(id: Long): QuestionAnswer?

    @Query("SELECT * FROM question_answers WHERE questionHash = :hash ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByHash(hash: String): QuestionAnswer?

    /** 50 câu gần nhất — cửa sổ quét cho thuật toán khớp mờ Jaccard. */
    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentByUser(userId: Long, limit: Int = 50): List<QuestionAnswer>

    @Query(
        """
        SELECT * FROM question_answers
        WHERE userId = :userId AND questionText LIKE '%' || :keyword || '%'
        ORDER BY createdAt DESC
        """
    )
    suspend fun search(userId: Long, keyword: String): List<QuestionAnswer>

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND subjectCode = :subject ORDER BY createdAt DESC")
    suspend fun bySubject(userId: Long, subject: Subject): List<QuestionAnswer>

    @Insert
    suspend fun insert(item: QuestionAnswer): Long

    @Query("UPDATE question_answers SET isBookmarked = :value WHERE id = :id")
    suspend fun setBookmarked(id: Long, value: Boolean)

    @Query("UPDATE question_answers SET reviewTimeSeconds = reviewTimeSeconds + :seconds WHERE id = :id")
    suspend fun addReviewTime(id: Long, seconds: Int)

    @Query("UPDATE question_answers SET simplifiedExplanation = :text WHERE id = :id")
    suspend fun setSimplifiedExplanation(id: Long, text: String)

    @Query("UPDATE question_answers SET alternativeApproach = :text WHERE id = :id")
    suspend fun setAlternativeApproach(id: Long, text: String)

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId")
    suspend fun countByUser(userId: Long): Int

    /** FR8 — thống kê số câu theo môn. */
    @Query("SELECT subjectCode, COUNT(*) AS total FROM question_answers WHERE userId = :userId GROUP BY subjectCode")
    suspend fun countBySubject(userId: Long): List<SubjectCount>

    /** FR8 — tổng thời gian đã dành đọc câu trả lời. */
    @Query("SELECT COALESCE(SUM(reviewTimeSeconds), 0) FROM question_answers WHERE userId = :userId")
    suspend fun totalReviewSeconds(userId: Long): Long

    /** FR6 — số môn khác nhau đã hỏi, điều kiện huy hiệu SUBJECT_EXPLORER. */
    @Query("SELECT COUNT(DISTINCT subjectCode) FROM question_answers WHERE userId = :userId")
    suspend fun distinctSubjectCount(userId: Long): Int

    /** N3 GDPR — xoá toàn bộ lịch sử của một user. */
    @Query("DELETE FROM question_answers WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}

/** Kết quả trả về của truy vấn nhóm — Room cần một lớp bao bọc riêng. */
data class SubjectCount(
    val subjectCode: Subject,
    val total: Int
)
