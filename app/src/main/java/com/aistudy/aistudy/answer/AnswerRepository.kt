package com.aistudy.aistudy.answer

import com.aistudy.aistudy.ask.AiRequestTrace
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import org.json.JSONException
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
    private val traceFactory: (AiRequestTrace.Feature) -> AiRequestTrace = { AiRequestTrace(it) },
    private val generate: (String, JSONObject, String, AiRequestTrace) -> String
) {
    class AccessException : IllegalStateException()
    class ContentChangedException : IllegalStateException()
    class SaveException(cause: Throwable? = null) : IllegalStateException(cause)

    fun load(): AnswerSnapshot {
        checkSession()
        val item = dao.getById(qaId, userId) ?: throw AccessException()
        if (item.userId != userId) throw AccessException()
        var content = AnswerContent.read(item.steps)
        // Legacy math already has a structured subject; its explanation/result need no network classification.
        if (content.type == null && item.subject == "math") content = content.copy(type = AnswerType.SOLUTION)
        return AnswerSnapshot(item, content)
    }

    fun classify(snapshot: AnswerSnapshot): AnswerSnapshot = runTask(AiRequestTrace.Feature.ANSWER_CLASSIFY) { trace ->
        check(snapshot.content.type == null)
        checkSnapshot(snapshot)
        val raw = generate(
            AnswerPrompts.classify(snapshot),
            AnswerPrompts.classificationSchema(snapshot.content.steps.size),
            AnswerPrompts.classificationInstruction,
            trace
        )
        val content = trace.validate { snapshot.content.classified(JSONObject(raw)) }
        save(snapshot, content)
    }

    fun expand(snapshot: AnswerSnapshot): AnswerSnapshot = runTask(AiRequestTrace.Feature.MORE_TOPICS) { trace ->
        check(snapshot.content.canExpand)
        checkSnapshot(snapshot)
        val raw = generate(
            AnswerPrompts.more(snapshot),
            AnswerPrompts.moreSchema(),
            AnswerPrompts.moreInstruction,
            trace
        )
        val content = trace.validate {
            val response = JSONObject(raw)
            snapshot.content.append(AnswerContent.parseGeneratedSteps(response.getJSONArray("steps")))
        }
        save(snapshot, content)
    }

    fun extras(snapshot: AnswerSnapshot): JSONObject = runTask(AiRequestTrace.Feature.LEARNING_EXTRAS) { trace ->
        checkSnapshot(snapshot)
        val raw = generate(
            AnswerPrompts.extras(snapshot),
            AnswerPrompts.extrasSchema(),
            AnswerPrompts.extrasInstruction,
            trace
        )
        val extras = trace.validate { AnswerContent.validateExtras(JSONObject(raw)) }
        checkSession()
        val saved = try { dao.updateExtras(qaId, userId, extras.toString()) } catch (error: RuntimeException) {
            throw SaveException(error)
        }
        if (saved != 1) {
            checkSession()
            throw SaveException()
        }
        extras
    }

    private fun save(snapshot: AnswerSnapshot, content: AnswerContent): AnswerSnapshot {
        checkSession()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val stored = content.toJson()
        // Compare-and-set avoids lost topics / exceeding the limit if two screens update the same answer.
        val changed = try {
            dao.updateSteps(qaId, userId, snapshot.storedSteps, stored)
        } catch (error: RuntimeException) {
            throw SaveException(error)
        }
        if (changed != 1) {
            checkSession()
            val current = dao.getById(qaId, userId)
            if (current?.steps != snapshot.storedSteps) throw ContentChangedException()
            throw SaveException()
        }
        return load()
    }

    private fun <T> runTask(feature: AiRequestTrace.Feature, block: (AiRequestTrace) -> T): T {
        val trace = traceFactory(feature)
        var outcome = AiRequestTrace.Outcome.REQUEST_FAILED
        try {
            val value = block(trace)
            outcome = AiRequestTrace.Outcome.SUCCESS
            return value
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            outcome = AiRequestTrace.Outcome.CANCELLED
            throw error
        } catch (error: AccessException) {
            outcome = AiRequestTrace.Outcome.SESSION_CHANGED
            throw error
        } catch (error: ContentChangedException) {
            outcome = AiRequestTrace.Outcome.CONTENT_CHANGED
            throw error
        } catch (error: SaveException) {
            outcome = AiRequestTrace.Outcome.SAVE_FAILED
            throw error
        } catch (error: GeminiClient.InvalidResponseException) {
            outcome = AiRequestTrace.Outcome.INVALID_RESPONSE
            throw error
        } catch (error: JSONException) {
            outcome = if (trace.contentValidated) AiRequestTrace.Outcome.SAVE_FAILED else AiRequestTrace.Outcome.INVALID_RESPONSE
            throw error
        } catch (error: IllegalArgumentException) {
            outcome = if (trace.contentValidated) AiRequestTrace.Outcome.SAVE_FAILED else AiRequestTrace.Outcome.INVALID_RESPONSE
            throw error
        } catch (error: RuntimeException) {
            if (trace.contentValidated) outcome = AiRequestTrace.Outcome.SAVE_FAILED
            throw error
        } finally {
            trace.finish(outcome)
        }
    }

    private fun checkSnapshot(snapshot: AnswerSnapshot) {
        checkSession()
        if (snapshot.item.id != qaId || snapshot.item.userId != userId) throw AccessException()
    }

    private fun checkSession() {
        if (currentUserId() != userId) throw AccessException()
    }
}
