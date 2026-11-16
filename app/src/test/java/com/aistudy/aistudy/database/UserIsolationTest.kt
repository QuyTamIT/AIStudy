package com.aistudy.aistudy.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIsolationTest {

    private class FakeQuestionAnswerDao : QuestionAnswerDao {
        val rows = mutableListOf<QuestionAnswer>()

        override fun insert(qa: QuestionAnswer): Long {
            qa.id = (rows.size + 1).toLong()
            rows.add(qa)
            return qa.id
        }

        override fun getAllByUserId(userId: Long): List<QuestionAnswer> =
            rows.filter { it.userId == userId }.sortedByDescending { it.createdAt }

        override fun getLatestByUserId(userId: Long): QuestionAnswer? =
            rows.filter { it.userId == userId }.maxByOrNull { it.createdAt }

        override fun countByUserId(userId: Long): Int =
            rows.count { it.userId == userId }

        override fun getById(id: Long, userId: Long): QuestionAnswer? =
            rows.firstOrNull { it.id == id && it.userId == userId }

        override fun updateSteps(id: Long, userId: Long, previousSteps: String?, steps: String): Int {
            val item = rows.firstOrNull { it.id == id && it.userId == userId } ?: return 0
            if (item.steps != previousSteps) return 0
            item.steps = steps
            return 1
        }

        override fun toggleStar(id: Long, userId: Long): Int {
            val item = rows.firstOrNull { it.id == id && it.userId == userId } ?: return 0
            item.starred = !item.starred
            return 1
        }

        override fun updateExtras(id: Long, userId: Long, extrasJson: String?): Int {
            val item = rows.firstOrNull { it.id == id && it.userId == userId } ?: return 0
            item.extrasJson = extrasJson
            return 1
        }

        override fun updateReviewedAt(id: Long, userId: Long, previousReviewedAt: Long, reviewedAt: Long): Int {
            val item = rows.firstOrNull { it.id == id && it.userId == userId && it.reviewedAt == previousReviewedAt } ?: return 0
            item.reviewedAt = reviewedAt
            return 1
        }

        override fun addReviewedSeconds(id: Long, userId: Long, delta: Int): Int {
            val item = rows.firstOrNull { it.id == id && it.userId == userId && delta > 0 } ?: return 0
            item.reviewedSeconds += delta
            return 1
        }

        override fun sumReviewedSecondsByUserId(userId: Long): Int =
            rows.filter { it.userId == userId }.sumOf { it.reviewedSeconds }

        override fun getTopSubjectByUserId(userId: Long): String? =
            rows.filter { it.userId == userId }
                .groupBy { it.subject }
                .maxByOrNull { it.value.size }
                ?.key

        override fun countBySubject(userId: Long, subject: String): Int =
            rows.count { it.userId == userId && it.subject == subject }

        override fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int): List<QuestionAnswer> =
            rows.filter { it.userId == userId && it.subject == subject }
                .sortedByDescending { it.createdAt }
                .take(limit)

        override fun countStarredByUserId(userId: Long): Int =
            rows.count { it.userId == userId && it.starred }

        override fun countCreatedSince(userId: Long, fromMs: Long): Int =
            rows.count { it.userId == userId && it.createdAt >= fromMs }

        override fun getOldestUnreviewedStarred(userId: Long, beforeMs: Long): QuestionAnswer? =
            rows.filter { it.userId == userId && it.starred && it.reviewedAt == 0L && it.createdAt <= beforeMs }
                .minByOrNull { it.createdAt }

        override fun countUnreviewedStarredBefore(userId: Long, beforeMs: Long): Int =
            rows.count { it.userId == userId && it.starred && it.reviewedAt == 0L && it.createdAt <= beforeMs }
    }

    @Test
    fun testUserIsolationForQuestionsAndStats() {
        val dao = FakeQuestionAnswerDao()
        val user1 = 1L
        val user2 = 2L

        dao.insert(QuestionAnswer().apply { userId = user1; question = "User 1 Q1"; subject = "math"; createdAt = 100; starred = true })
        dao.insert(QuestionAnswer().apply { userId = user1; question = "User 1 Q2"; subject = "math"; createdAt = 200; starred = false })
        dao.insert(QuestionAnswer().apply { userId = user2; question = "User 2 Q1"; subject = "science"; createdAt = 300; starred = true })

        // Check user 1 sees only user 1 questions
        val user1Items = dao.getAllByUserId(user1)
        assertEquals(2, user1Items.size)
        assertTrue(user1Items.all { it.userId == user1 })

        // Check user 2 sees only user 2 questions
        val user2Items = dao.getAllByUserId(user2)
        assertEquals(1, user2Items.size)
        assertEquals("User 2 Q1", user2Items[0].question)

        // Check latest question isolation
        assertEquals("User 1 Q2", dao.getLatestByUserId(user1)?.question)
        assertEquals("User 2 Q1", dao.getLatestByUserId(user2)?.question)

        // Check counts
        assertEquals(2, dao.countByUserId(user1))
        assertEquals(1, dao.countByUserId(user2))
        assertEquals(1, dao.countStarredByUserId(user1))
        assertEquals(1, dao.countStarredByUserId(user2))

        // Check subject counts
        assertEquals(2, dao.countBySubject(user1, "math"))
        assertEquals(0, dao.countBySubject(user1, "science"))
        assertEquals(0, dao.countBySubject(user2, "math"))
        assertEquals(1, dao.countBySubject(user2, "science"))
    }
}

