package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QuestionAnswerDao {

    @Insert
    fun insert(qa: QuestionAnswer): Long

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC, id DESC")
    fun getAllByUserId(userId: Long): List<QuestionAnswer>

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun getLatestByUserId(userId: Long): QuestionAnswer?

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId")
    fun countByUserId(userId: Long): Int

    @Query("SELECT * FROM question_answers WHERE id = :id AND userId = :userId LIMIT 1")
    fun getById(id: Long, userId: Long): QuestionAnswer?

    @Query("UPDATE question_answers SET steps = :steps WHERE id = :id AND userId = :userId AND steps IS :previousSteps")
    fun updateSteps(id: Long, userId: Long, previousSteps: String?, steps: String): Int

    @Query("UPDATE question_answers SET starred = NOT starred WHERE id = :id AND userId = :userId")
    fun toggleStar(id: Long, userId: Long): Int

    @Query("UPDATE question_answers SET extrasJson = :extrasJson WHERE id = :id AND userId = :userId")
    fun updateExtras(id: Long, userId: Long, extrasJson: String?): Int

    @Query("UPDATE question_answers SET reviewedAt = :reviewedAt WHERE id = :id AND userId = :userId AND reviewedAt = :previousReviewedAt")
    fun updateReviewedAt(id: Long, userId: Long, previousReviewedAt: Long, reviewedAt: Long): Int

    @Query("UPDATE question_answers SET reviewedSeconds = reviewedSeconds + :delta WHERE id = :id AND userId = :userId AND :delta > 0")
    fun addReviewedSeconds(id: Long, userId: Long, delta: Int): Int


    @Query("SELECT COALESCE(SUM(reviewedSeconds), 0) FROM question_answers WHERE userId = :userId")
    fun sumReviewedSecondsByUserId(userId: Long): Int

    @Query("SELECT subject FROM question_answers WHERE userId = :userId GROUP BY subject ORDER BY COUNT(*) DESC LIMIT 1")
    fun getTopSubjectByUserId(userId: Long): String?

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND subject = :subject")
    fun countBySubject(userId: Long, subject: String): Int

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND subject = :subject ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int): List<QuestionAnswer>

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND starred = 1")
    fun countStarredByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND createdAt >= :fromMs")
    fun countCreatedSince(userId: Long, fromMs: Long): Int

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND starred = 1 AND reviewedAt = 0 AND createdAt <= :beforeMs ORDER BY createdAt ASC LIMIT 1")
    fun getOldestUnreviewedStarred(userId: Long, beforeMs: Long): QuestionAnswer?

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND starred = 1 AND reviewedAt = 0 AND createdAt <= :beforeMs")
    fun countUnreviewedStarredBefore(userId: Long, beforeMs: Long): Int
}
