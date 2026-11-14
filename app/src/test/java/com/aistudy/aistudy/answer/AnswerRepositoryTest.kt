package com.aistudy.aistudy.answer

import com.aistudy.aistudy.ask.AiRequestTrace
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

    private fun validExtras() = """{"simplify":"Simple.","alternative":"Another way.","concepts":["Concept"],"mistakes":["Mistake"],"followUps":["Question"]}"""

    private fun taskOutcomes(events: List<JSONObject>) = events.filter {
        it.optString("event") == "task_end"
    }.map { it.optString("outcome") }

    @Test fun updatesSameRowAndPersistsThreeRoundLimitWithoutInserting() {
        val dao = FakeDao(row())
        var requests = 0
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _, _, _ -> batch("round-${requests++}") }
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
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _, _, _ -> throw IOException() }
        assertThrows(IOException::class.java) { repository.expand(repository.load()) }
        assertEquals(before, dao.row.steps)
        assertEquals(0, dao.updates)
        assertEquals(0, repository.load().content.moreCount)
    }

    @Test fun changedSessionCannotSaveOrReadAnotherAccountsAnswer() {
        val dao = FakeDao(row())
        var session = 7L
        val repository = AnswerRepository(dao, 42, 7, { session }) { _, _, _, _ ->
            session = 8; batch("new")
        }
        assertThrows(AnswerRepository.AccessException::class.java) { repository.expand(repository.load()) }
        assertEquals(0, dao.updates)
        assertThrows(AnswerRepository.AccessException::class.java) { repository.load() }
        val otherUser = AnswerRepository(dao, 42, 8, { 8 }) { _, _, _, _ -> batch("new") }
        assertThrows(AnswerRepository.AccessException::class.java) { otherUser.load() }
    }

    @Test fun concurrentExpansionCannotOverwriteAlreadySavedTopics() {
        val dao = FakeDao(row())
        val events = mutableListOf<JSONObject>()
        val repository = AnswerRepository(dao, 42, 7, { 7 },
            { feature -> AiRequestTrace(feature, clock = { 0L }, sink = { events += it }) }) { _, _, _, _ ->
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
        assertEquals(listOf("content_changed"), taskOutcomes(events))
        assertEquals("valid", events.single { it.getString("event") == "validation" }.getString("outcome"))
    }

    @Test fun legacyClassificationPreservesTextAndIsSavedInExistingSteps() {
        val dao = FakeDao(row().apply { steps = """[{"title":"Food","body":"Order lunch.","expr":""}]""" })
        val repository = AnswerRepository(dao, 42, 7, { 7 }) { _, _, _, _ ->
            """{"answerType":"topic_list","finalAnswerRedundant":true,"topicIds":["food"]}"""
        }
        val classified = repository.classify(repository.load())
        assertEquals("Order lunch.", classified.content.steps.single().body)
        assertFalse(classified.content.showFinalAnswer("language"))
        assertEquals(AnswerType.TOPIC_LIST, repository.load().content.type)
        assertEquals(1, dao.updates)
        assertEquals(0, dao.inserts)
    }

    @Test fun invalidExtrasLeavesDatabaseAndSnapshotCacheUntouched() {
        val oldCache = validExtras()
        val dao = FakeDao(row().apply { extrasJson = oldCache })
        val events = mutableListOf<JSONObject>()
        val repository = AnswerRepository(dao, 42, 7, { 7 },
            { feature -> AiRequestTrace(feature, clock = { 0L }, sink = { events += it }) }) { _, _, _, _ -> "{}" }
        val snapshot = repository.load()
        assertThrows(IllegalArgumentException::class.java) { repository.extras(snapshot) }
        assertEquals(oldCache, dao.row.extrasJson)
        assertEquals(oldCache, snapshot.item.extrasJson)
        assertEquals(0, dao.extrasUpdates)
        assertEquals(listOf("invalid_response"), taskOutcomes(events))
    }

    @Test fun validExtrasSaveFailureIsClassifiedWithoutChangingCache() {
        val oldCache = validExtras()
        val dao = FakeDao(row().apply { extrasJson = oldCache }).apply { failExtras = true }
        val events = mutableListOf<JSONObject>()
        val repository = AnswerRepository(dao, 42, 7, { 7 },
            { feature -> AiRequestTrace(feature, clock = { 0L }, sink = { events += it }) }) { _, _, _, _ -> validExtras() }
        val snapshot = repository.load()
        assertThrows(AnswerRepository.SaveException::class.java) { repository.extras(snapshot) }
        assertEquals(oldCache, dao.row.extrasJson)
        assertEquals(oldCache, snapshot.item.extrasJson)
        assertEquals(1, dao.extrasUpdates)
        assertEquals(listOf("save_failed"), taskOutcomes(events))
        assertTrue(events.any { it.optString("event") == "validation" && it.optString("outcome") == "valid" })
    }

    @Test fun successfulExtrasPersistsButLeavesSnapshotForTheUiOwner() {
        val oldCache = validExtras().replace("Simple.", "Old cached explanation.")
        val dao = FakeDao(row().apply { extrasJson = oldCache })
        val events = mutableListOf<JSONObject>()
        val repository = AnswerRepository(dao, 42, 7, { 7 },
            { feature -> AiRequestTrace(feature, sink = { events.add(it) }) }) { _, _, _, _ -> validExtras() }
        val snapshot = repository.load()
        val result = repository.extras(snapshot)
        assertEquals("Simple.", result.getString("simplify"))
        assertEquals(result.toString(), dao.row.extrasJson)
        assertEquals(oldCache, snapshot.item.extrasJson)
        assertEquals(listOf("success"), taskOutcomes(events))
        assertEquals(1, events.count { it.getString("event") == "validation" })
    }

    @Test fun changedSessionOrForeignSnapshotCannotOverwriteExtras() {
        val oldCache = validExtras()
        val dao = FakeDao(row().apply { extrasJson = oldCache })
        var session = 7L
        val events = mutableListOf<JSONObject>()
        val repository = AnswerRepository(dao, 42, 7, { session },
            { feature -> AiRequestTrace(feature, sink = { events.add(it) }) }) { _, _, _, _ ->
            session = 8
            validExtras()
        }
        val snapshot = repository.load()
        assertThrows(AnswerRepository.AccessException::class.java) { repository.extras(snapshot) }
        assertEquals(oldCache, dao.row.extrasJson)
        assertEquals(oldCache, snapshot.item.extrasJson)
        assertEquals(0, dao.extrasUpdates)
        assertEquals(listOf("session_changed"), taskOutcomes(events))
        assertEquals("valid", events.single { it.getString("event") == "validation" }.getString("outcome"))
        var calls = 0
        val foreign = AnswerRepository(dao, 42, 7, { 7 }) { _, _, _, _ -> calls++; validExtras() }
        val wrongRow = QuestionAnswer().apply { id = 99; userId = 8 }
        assertThrows(AnswerRepository.AccessException::class.java) {
            foreign.extras(snapshot.copy(item = wrongRow))
        }
        assertEquals(0, calls)
        assertEquals(oldCache, dao.row.extrasJson)
    }

    private class FakeDao(var row: QuestionAnswer) : QuestionAnswerDao {
        var updates = 0
        var inserts = 0
        var extrasUpdates = 0
        var failExtras = false
        override fun getById(id: Long, userId: Long): QuestionAnswer? {
            val stored = row.takeIf { it.id == id && it.userId == userId } ?: return null
            return QuestionAnswer().apply {
                this.id = stored.id; this.userId = stored.userId; subject = stored.subject
                question = stored.question; steps = stored.steps; finalAnswer = stored.finalAnswer
                starred = stored.starred; createdAt = stored.createdAt; difficulty = stored.difficulty
                extrasJson = stored.extrasJson; reviewedAt = stored.reviewedAt
                imagePath = stored.imagePath; reviewedSeconds = stored.reviewedSeconds
            }
        }
        override fun updateSteps(id: Long, userId: Long, previousSteps: String?, steps: String): Int {
            if (id != row.id || userId != row.userId || previousSteps != row.steps) return 0
            row.steps = steps
            updates++
            return 1
        }
        override fun insert(qa: QuestionAnswer): Long { inserts++; return 99 }
        override fun updateExtras(id: Long, userId: Long, extrasJson: String?): Int {
            if (id != row.id || userId != row.userId) return 0
            extrasUpdates++
            if (failExtras) return 0
            row.extrasJson = extrasJson
            return 1
        }
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
