package com.aistudy.aistudy.answer

import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnswerRepositoryTest {
    private fun content() = AnswerContent(
        listOf(AnswerStep("Food", "Order lunch.", "", "food")),
        AnswerType.TOPIC_LIST, true, 0
    )

    private fun row() = QuestionAnswer().apply {
        id = 42; userId = 7; subject = "language"; question = "Conversation topics"
        steps = content().toJson(); finalAnswer = "Food"; starred = true; createdAt = 123
    }

    private fun batch(prefix: String) = JSONObject().put("steps", JSONArray().apply {
        repeat(3) { put(JSONObject().put("topicId", "$prefix-$it")
            .put("title", "$prefix $it").put("body", "Discuss $prefix $it.").put("expr", "")) }
    }).toString()

    @Test fun updatesSameRowAndPersistsThreeRoundLimitWithoutInserting() {
        val dao = FakeDao(row())
        var requests = 0
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _ -> batch("round-${requests++}") }
        repeat(3) { repository.expand(repository.load()) }
        assertEquals(3, dao.updates)
        assertEquals(0, dao.inserts)
        assertEquals(42, dao.row.id)
        assertEquals(7, dao.row.userId)
        assertEquals(123, dao.row.createdAt)
        assertTrue(dao.row.starred)
        assertEquals("Food", dao.row.finalAnswer)
        assertEquals(10, repository.load().content.steps.size)
        assertThrows(IllegalStateException::class.java) { repository.expand(repository.load()) }
        assertEquals(3, requests)
    }

    @Test fun networkFailureKeepsContentAndDoesNotConsumeARound() {
        val dao = FakeDao(row())
        val before = dao.row.steps
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _ -> throw IOException() }
        assertThrows(IOException::class.java) { repository.expand(repository.load()) }
        assertEquals(before, dao.row.steps)
        assertEquals(0, dao.updates)
        assertEquals(0, repository.load().content.moreCount)
    }

    @Test fun changedSessionCannotSaveOrReadAnotherAccountsAnswer() {
        val dao = FakeDao(row())
        var session = 7L
        val repository = AnswerRepository(dao, 42, 7, { session }) { _, _ ->
            session = 8; batch("new")
        }
        assertThrows(AnswerRepository.AccessException::class.java) { repository.expand(repository.load()) }
        assertEquals(0, dao.updates)
        assertThrows(AnswerRepository.AccessException::class.java) { repository.load() }
        val otherUser = AnswerRepository(dao, 42, 8, { 8 }) { _, _ -> batch("new") }
        assertThrows(AnswerRepository.AccessException::class.java) { otherUser.load() }
    }

    @Test fun concurrentExpansionCannotOverwriteAlreadySavedTopics() {
        val dao = FakeDao(row())
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _ ->
            dao.row.steps = content().append((1..3).map {
                AnswerStep("Concurrent $it", "Discuss concurrent $it.", "", "concurrent-$it")
            }).toJson()
            batch("stale")
        }
        assertThrows(AnswerRepository.ContentChangedException::class.java) {
            repository.expand(repository.load())
        }
        assertEquals(4, repository.load().content.steps.size)
        assertEquals(0, dao.updates)
    }

    @Test fun legacyClassificationPreservesTextAndIsSavedInExistingSteps() {
        val dao = FakeDao(row().apply { steps = """[{"title":"Food","body":"Order lunch.","expr":""}]""" })
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _ ->
            """{"answerType":"topic_list","finalAnswerRedundant":true,"topicIds":["food"]}"""
        }
        val classified = repository.classify(repository.load())
        assertEquals("Order lunch.", classified.content.steps.single().body)
        assertFalse(classified.content.showFinalAnswer("language"))
        assertEquals(AnswerType.TOPIC_LIST, repository.load().content.type)
        assertEquals(1, dao.updates)
        assertEquals(0, dao.inserts)
    }

    private class FakeDao(var row: QuestionAnswer) : QuestionAnswerDao {
        var updates = 0
        var inserts = 0
        override fun getById(id: Long, userId: Long) = row.takeIf { it.id == id && it.userId == userId }
        override fun updateSteps(id: Long, userId: Long, previousSteps: String?, steps: String): Int {
            if (id != row.id || userId != row.userId || previousSteps != row.steps) return 0
            row.steps = steps
            updates++
            return 1
        }
        override fun insert(qa: QuestionAnswer): Long { inserts++; return 99 }
        override fun updateExtras(id: Long, userId: Long, extrasJson: String?): Int =
            error("Expansion must not change extras")
        override fun updateReviewedAt(id: Long, userId: Long, previousReviewedAt: Long, reviewedAt: Long): Int =
            error("Expansion must not change review state")
        override fun getAllByUserId(userId: Long) = listOf(row).filter { it.userId == userId }
        override fun getLatestByUserId(userId: Long) = row.takeIf { it.userId == userId }
        override fun toggleStar(id: Long, userId: Long): Int = error("Expansion must not change stars")
        override fun countBySubject(userId: Long, subject: String) = 1
        override fun countByUserId(userId: Long) = 1
        override fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int) =
            listOf(row).filter { it.userId == userId && it.subject == subject }.take(limit)
        override fun addReviewedSeconds(id: Long, userId: Long, delta: Int): Int =
            error("Expansion must not change review seconds")
        override fun sumReviewedSecondsByUserId(userId: Long) = if (row.userId == userId) row.reviewedSeconds else 0
        override fun getTopSubjectByUserId(userId: Long) = row.subject.takeIf { row.userId == userId }
        override fun countStarredByUserId(userId: Long) = if (row.userId == userId && row.starred) 1 else 0
        override fun countCreatedSince(userId: Long, fromMs: Long) = if (row.userId == userId && row.createdAt >= fromMs) 1 else 0
        override fun getOldestUnreviewedStarred(userId: Long, beforeMs: Long) =
            row.takeIf { it.userId == userId && it.starred && it.reviewedAt == 0L && it.createdAt <= beforeMs }
        override fun countUnreviewedStarredBefore(userId: Long, beforeMs: Long) =
            if (row.userId == userId && row.starred && row.reviewedAt == 0L && row.createdAt <= beforeMs) 1 else 0
    }
}
