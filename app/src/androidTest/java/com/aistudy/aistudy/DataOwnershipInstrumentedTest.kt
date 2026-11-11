package com.aistudy.aistudy

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataOwnershipInstrumentedTest {
    @Test
    fun wrongOwnerCannotReadOrMutateHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.questionAnswerDao()
            val id = dao.insert(QuestionAnswer().apply {
                userId = 101L; question = "Owner A"; steps = "original"; reviewedAt = 12L
            })
            val otherId = dao.insert(QuestionAnswer().apply { userId = 202L; question = "Owner B" })
            assertNull(dao.getById(id, 202L))
            assertEquals(0, dao.toggleStar(id, 202L))
            assertEquals(0, dao.updateExtras(id, 202L, "wrong"))
            assertEquals(0, dao.updateReviewedAt(id, 202L, 12L, 99L))
            assertEquals(0, dao.addReviewedSeconds(id, 202L, 100))
            assertEquals(0, dao.updateSteps(id, 202L, "original", "wrong"))
            val untouched = requireNotNull(dao.getById(id, 101L))
            assertFalse(untouched.starred)
            assertNull(untouched.extrasJson)
            assertEquals(12L, untouched.reviewedAt)
            assertEquals(0, untouched.reviewedSeconds)
            assertEquals("original", untouched.steps)
            assertEquals(1, dao.toggleStar(id, 101L))
            assertEquals(1, dao.updateExtras(id, 101L, "extra"))
            assertEquals(1, dao.updateReviewedAt(id, 101L, 12L, 99L))
            assertEquals(0, dao.updateReviewedAt(id, 101L, 12L, 100L))
            assertEquals(1, dao.addReviewedSeconds(id, 101L, 5))
            assertEquals(0, dao.addReviewedSeconds(id, 101L, -5))
            val updated = requireNotNull(dao.getById(id, 101L))
            assertTrue(updated.starred)
            assertEquals("extra", updated.extrasJson)
            assertEquals(99L, updated.reviewedAt)
            assertEquals(5, updated.reviewedSeconds)
            assertEquals(1, dao.getAllByUserId(202L).size)
            assertEquals(otherId, dao.getAllByUserId(202L).single().id)
        } finally { db.close() }
    }

    @Test
    fun changedSessionCannotSaveAiCallbackOnRealRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val original = """[{"topicId":"food","title":"Food","body":"Order lunch.","expr":"","_answer":{"version":1,"answerType":"topic_list","finalAnswerRedundant":true,"moreCount":0}}]"""
            val id = db.questionAnswerDao().insert(QuestionAnswer().apply {
                userId = 101L; subject = "language"; steps = original
            })
            var activeUser = 101L
            val repository = com.aistudy.aistudy.answer.AnswerRepository(db.questionAnswerDao(), id, 101L, { activeUser }) { _, _ ->
                activeUser = 202L
                """{"steps":[{"topicId":"travel","title":"Travel","body":"Plan a trip.","expr":""},{"topicId":"work","title":"Work","body":"Discuss work.","expr":""},{"topicId":"study","title":"Study","body":"Discuss study.","expr":""}]}"""
            }
            assertThrows(com.aistudy.aistudy.answer.AnswerRepository.AccessException::class.java) {
                repository.expand(repository.load())
            }
            assertEquals(original, db.questionAnswerDao().getById(id, 101L)?.steps)
            assertThrows(com.aistudy.aistudy.answer.AnswerRepository.AccessException::class.java) { repository.load() }
        } finally { db.close() }
    }

    @Test
    fun quizWritesRequireOwnerAndCompletionIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val sessions = db.quizSessionDao()
            val items = db.quizItemDao()
            val id = sessions.insert(com.aistudy.aistudy.database.QuizSession().apply { userId = 101L; total = 1 })
            val itemId = items.insert(com.aistudy.aistudy.database.QuizItem().apply { sessionId = id; correctAnswer = "4" })
            assertNull(sessions.getById(id, 202L))
            assertEquals(0, items.updateAnswer(itemId, id, 202L, "wrong", false))
            assertEquals(0, sessions.completeIfUnfinished(id, 202L, 0, 1))
            assertNull(items.getBySessionId(id).single().userAnswer)
            assertFalse(requireNotNull(sessions.getById(id, 101L)).completed)
            assertEquals(1, items.updateAnswer(itemId, id, 101L, "4", true))
            assertEquals(1, sessions.completeIfUnfinished(id, 101L, 1, 1))
            assertEquals(0, sessions.completeIfUnfinished(id, 101L, 0, 1))
            assertEquals(0, items.updateAnswer(itemId, id, 101L, "wrong", false))
            assertEquals(1, sessions.getById(id, 101L)?.score)
            assertEquals("4", items.getBySessionId(id).single().userAnswer)
        } finally { db.close() }
    }
}
