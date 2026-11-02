package com.aistudy.aistudy.answer

import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import org.json.JSONObject

data class AnswerSnapshot(
    val item: QuestionAnswer,
    val content: AnswerContent,
    val storedSteps: String? = item.steps
)

class AnswerRepository(
    private val dao: QuestionAnswerDao,
    private val qaId: Long,
    private val userId: Long,
    private val currentUserId: () -> Long,
    private val generate: (String, JSONObject) -> String
) {
    class AccessException : IllegalStateException()
    class ContentChangedException : IllegalStateException()

    fun load(): AnswerSnapshot {
        checkSession()
        val item = dao.getById(qaId) ?: throw AccessException()
        if (item.userId != userId) throw AccessException()
        var content = AnswerContent.read(item.steps)
        // Legacy math already has a structured subject; its explanation/result need no network classification.
        if (content.type == null && item.subject == "math") content = content.copy(type = AnswerType.SOLUTION)
        return AnswerSnapshot(item, content)
    }

    fun classify(snapshot: AnswerSnapshot): AnswerSnapshot {
        check(snapshot.content.type == null)
        checkSession()
        val response = JSONObject(generate(AnswerPrompts.classify(snapshot),
            AnswerPrompts.classificationSchema(snapshot.content.steps.size)))
        return save(snapshot, snapshot.content.classified(response))
    }

    fun expand(snapshot: AnswerSnapshot): AnswerSnapshot {
        check(snapshot.content.canExpand)
        checkSession()
        val response = JSONObject(generate(AnswerPrompts.more(snapshot), AnswerPrompts.moreSchema()))
        val additions = AnswerContent.parseSteps(response.getJSONArray("steps"))
        return save(snapshot, snapshot.content.append(additions))
    }

    private fun save(snapshot: AnswerSnapshot, content: AnswerContent): AnswerSnapshot {
        checkSession()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val stored = content.toJson()
        // Compare-and-set avoids lost topics / exceeding the limit if two screens update the same answer.
        val changed = dao.updateSteps(qaId, userId, snapshot.storedSteps, stored)
        if (changed != 1) throw ContentChangedException()
        return load()
    }

    private fun checkSession() {
        if (currentUserId() != userId) throw AccessException()
    }
}
