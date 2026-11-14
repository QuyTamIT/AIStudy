package com.aistudy.aistudy.quiz

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerPrompts
import com.aistudy.aistudy.answer.ResponseMode
import com.aistudy.aistudy.ask.AiRequestTrace
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.database.QuizSession
import com.aistudy.aistudy.xp.XpRules
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class QuizViewModel @JvmOverloads constructor(
    application: Application,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val traceFactory: (AiRequestTrace.Feature) -> AiRequestTrace = { AiRequestTrace(it) },
    private val requestQuiz: (String, String, JSONObject, String, AiRequestTrace) -> String =
        { key, prompt, schema, instruction, trace -> GeminiClient.ask(key, prompt, schema, instruction, trace) }
) : AndroidViewModel(application) {
    data class Quiz(val session: QuizSession, val items: List<QuizItem>)

    data class UiState(
        val quiz: Quiz? = null,
        val saving: Boolean = false,
        @get:StringRes val error: Int? = null
    )

    private val database by lazy { AppDatabase.getInstance(application) }
    private val preferences = application.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mutableState = MutableLiveData(UiState())
    val state: LiveData<UiState> = mutableState
    private var started = false
    @Volatile private var cleared = false

    fun start(ownerId: Long, subject: String, sessionId: Long, restoring: Boolean) {
        if (started) return
        started = true
        if (sessionId <= 0L && restoring) {
            mutableState.value = UiState(error = R.string.quiz_error)
            return
        }
        runOperation(UiState()) {
            checkActiveUser(ownerId)
            if (sessionId > 0L) {
                readQuiz(sessionId, ownerId).also { check(it.session.subject == subject) }
            } else {
                generateQuiz(ownerId, subject)
            }
        }
    }

    fun grade(itemId: Long, answer: String) = updateQuiz { quiz ->
        val session = quiz.session
        val item = quiz.items.first { it.id == itemId }
        val correct = normalizeAnswer(answer) == normalizeAnswer(item.correctAnswer)
        database.runInTransaction<Quiz> {
            checkActiveUser(session.userId)
            val itemDao = database.quizItemDao()
            itemDao.updateAnswer(itemId, session.id, session.userId, answer, correct)
            val saved = readQuiz(session.id, session.userId)
            // A second screen may have already graded the same item.
            check(saved.session.completed || saved.items.any {
                it.id == itemId && it.correct != null && it.userAnswer != null
            })
            saved
        }
    }

    fun finishQuiz() = updateQuiz { quiz ->
        val ownerId = quiz.session.userId
        database.runInTransaction<Quiz> {
            checkActiveUser(ownerId)
            val saved = readQuiz(quiz.session.id, ownerId)
            val session = saved.session
            if (session.completed) return@runInTransaction saved

            check(saved.items.all { it.correct != null && it.userAnswer != null })
            val score = saved.items.count { it.correct == true }
            val total = saved.items.size
            check(database.quizSessionDao().completeIfUnfinished(session.id, ownerId, score, total) == 1)

            val profileDao = database.profileDao()
            profileDao.getByUserId(ownerId)?.let { profile ->
                XpRules.recordDailyActivity(profile, profileDao)
                XpRules.addXp(profile, profileDao, score * XpRules.QUIZ_CORRECT + XpRules.QUIZ_COMPLETE_BONUS)
            }
            session.score = score
            session.total = total
            session.completed = true
            saved
        }
    }

    private fun updateQuiz(operation: (Quiz) -> Quiz) {
        val previous = mutableState.value ?: return
        val quiz = previous.quiz ?: return
        if (previous.saving || quiz.session.completed) return
        mutableState.value = previous.copy(saving = true, error = null)
        runOperation(previous) { operation(quiz) }
    }

    private fun runOperation(previous: UiState, operation: () -> Quiz) {
        executor.execute {
            val result = try {
                UiState(quiz = operation())
            } catch (error: QuizLoadException) {
                UiState(error = error.messageRes)
            } catch (error: Exception) {
                Log.w(TAG, "Quiz operation failed (${error.javaClass.simpleName})")
                previous.copy(saving = false, error = R.string.quiz_error)
            }
            if (!cleared) mutableState.postValue(result)
        }
    }

    private fun readQuiz(sessionId: Long, ownerId: Long): Quiz {
        val session = checkNotNull(database.quizSessionDao().getById(sessionId, ownerId))
        val items = database.quizItemDao().getBySessionId(sessionId)
        check(items.isNotEmpty())
        return Quiz(session, items)
    }

    private fun generateQuiz(ownerId: Long, subject: String): Quiz {
        val trace = traceFactory(AiRequestTrace.Feature.QUIZ)
        var outcome = AiRequestTrace.Outcome.REQUEST_FAILED
        try {
            if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
                outcome = AiRequestTrace.Outcome.KEY_MISSING
                throw QuizLoadException(R.string.quiz_no_api_key)
            }
            val recent = database.questionAnswerDao().getRecentByUserIdAndSubject(ownerId, subject, RECENT_LIMIT)
            val sources = projectSources(recent, getApplication<Application>().getString(R.string.ask_image_default_question))
            if (sources.size < MIN_SOURCE_QA) {
                outcome = AiRequestTrace.Outcome.INSUFFICIENT_SOURCES
                throw QuizLoadException(R.string.quiz_insufficient_sources)
            }
            checkActiveUser(ownerId)
            val prompt = buildQuizPrompt(sources, subject,
                AnswerPrompts.fallbackLanguageTag(getApplication<Application>().resources))
            val raw = requestQuiz(apiKey, prompt, quizSchema(), quizInstruction, trace)
            outcome = AiRequestTrace.Outcome.INVALID_RESPONSE
            val items = trace.validate { parseQuizBatch(raw) }
            outcome = AiRequestTrace.Outcome.SAVE_FAILED
            val session = QuizSession().apply {
                userId = ownerId
                this.subject = subject
                sourceQaIds = JSONArray(sources.map { it.sourceId }).toString()
                createdAt = System.currentTimeMillis()
                total = items.size
            }
            database.runInTransaction {
                checkActiveUser(ownerId)
                session.id = database.quizSessionDao().insert(session)
                val itemDao = database.quizItemDao()
                for (item in items) {
                    item.sessionId = session.id
                    item.id = itemDao.insert(item)
                }
            }
            outcome = AiRequestTrace.Outcome.SUCCESS
            return Quiz(session, items)
        } catch (error: Exception) {
            outcome = when {
                cleared || Thread.currentThread().isInterrupted || error is InterruptedException -> AiRequestTrace.Outcome.CANCELLED
                preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != ownerId -> AiRequestTrace.Outcome.SESSION_CHANGED
                error is GeminiClient.InvalidResponseException ||
                    (error is JSONException && outcome == AiRequestTrace.Outcome.REQUEST_FAILED) -> AiRequestTrace.Outcome.INVALID_RESPONSE
                else -> outcome
            }
            throw error
        } finally {
            trace.finish(outcome)
        }
    }

    private fun checkActiveUser(ownerId: Long) {
        check(!cleared && !Thread.currentThread().isInterrupted)
        check(ownerId > 0L && preferences.getLong(LoginActivity.KEY_USER_ID, 0L) == ownerId)
    }


    override fun onCleared() {
        cleared = true
        executor.shutdownNow()
        super.onCleared()
    }

    private class QuizLoadException(@get:StringRes val messageRes: Int) : Exception()

    companion object {
        private const val TAG = "QuizViewModel"
        private const val MIN_SOURCE_QA = 2
        private const val RECENT_LIMIT = 5
        internal const val TARGET_ITEMS = 4
        // shortcut: supplementary image-source context is capped in JSON characters, re-evaluate with measured usage/quality.
        internal const val IMAGE_CONTEXT_CHARS = 2048
        private val WHITESPACE = Regex("\\s+")
        private val ITEM_FIELDS = listOf("type", "prompt", "options", "correctAnswer", "explanation")

        internal data class Source(val sourceId: Long, val data: JSONObject)

        internal fun projectSources(recent: List<QuestionAnswer>, imagePlaceholder: String): List<Source> =
            recent.mapNotNull { qa ->
                val stored = try { JSONArray(qa.steps ?: "[]") } catch (_: JSONException) { null }
                val metadata = stored?.optJSONObject(0)?.optJSONObject("_answer")
                    ?.takeIf { it.opt("version") == 1 }
                val modeValue = metadata?.let {
                    when {
                        it.has("responseType") -> it.opt("responseType")
                        it.has("responseMode") -> it.opt("responseMode")
                        else -> null
                    }
                } as? String
                val mode = ResponseMode.parse(modeValue)
                if (mode == ResponseMode.CLARIFICATION || mode == ResponseMode.LIMITATION) return@mapNotNull null
                val data = JSONObject().put("question", qa.question ?: JSONObject.NULL)
                    .put("finalAnswer", qa.finalAnswer ?: JSONObject.NULL)
                    .put("difficulty", qa.difficulty ?: JSONObject.NULL)
                if (!qa.imagePath.isNullOrBlank() && qa.question == imagePlaceholder) {
                    val teaching = JSONArray()
                    var chars = 2
                    if (stored != null) for (index in 0 until stored.length()) {
                        val step = stored.optJSONObject(index) ?: continue
                        val title = (step.opt("title") as? String).orEmpty()
                        val body = step.opt("body") as? String ?: continue
                        val expr = (step.opt("expr") as? String).orEmpty()
                        if (body.isBlank()) continue
                        val projected = JSONObject().put("title", title).put("body", body).put("expr", expr)
                        val added = projected.toString().length + if (teaching.length() == 0) 0 else 1
                        if (chars + added > IMAGE_CONTEXT_CHARS) break
                        teaching.put(projected)
                        chars += added
                    }
                    if (teaching.length() == 0) return@mapNotNull null
                    data.put("teachingSteps", teaching)
                }
                Source(qa.id, data)
            }

        internal fun buildQuizPrompt(sources: List<Source>, subject: String, fallbackLanguageTag: String): String =
            JSONObject().put("subject", subject).put("fallbackLanguageTag", fallbackLanguageTag)
                .put("sources", JSONArray(sources.map { it.data })).toString()

        internal val quizInstruction: String = AnswerPrompts.commonInstruction + "\n\n" + """
            Create exactly $TARGET_ITEMS practice items grounded in the supplied sources, using similar concepts,
            not identical copies. Do not invent missing source facts.
            Determine the quiz language only from the actual texts in the newest supplied source.
            Prefer teachingSteps body prose when present, then finalAnswer containing natural language,
            then substantive question text. Ignore code, numbers, identifiers, and the automatic image
            question placeholder (the question of a source with teachingSteps). If language cannot be
            determined, use fallbackLanguageTag. Keep quiz instructions and explanations in that language;
            foreign-language learning examples keep the target language.
            Mix mcq, short and blank when suitable, including one of each if possible.
            Every prompt, correctAnswer and explanation must contain useful text.
            MCQ: exactly four nonempty choices distinct after trim, whitespace collapsing and lowercase;
            correctAnswer must match exactly one choice using the same normalization and should copy its text.
            Short/blank: options must be []; use one unambiguous canonical short answer suitable for that
            exact-match normalization. Give a brief useful explanation for every item.
        """.trimIndent()

        internal fun quizSchema(): JSONObject {
            val string = JSONObject().put("type", "string")
            val properties = JSONObject()
                .put("type", JSONObject().put("type", "string").put("enum", JSONArray(listOf("mcq", "short", "blank"))))
                .put("prompt", string).put("correctAnswer", string).put("explanation", string)
                .put("options", JSONObject().put("type", "array").put("items", string).put("maxItems", 4))
            val item = JSONObject().put("type", "object").put("properties", properties)
                .put("required", JSONArray(ITEM_FIELDS)).put("additionalProperties", false)
            return JSONObject().put("type", "object").put("required", JSONArray(listOf("items")))
                .put("additionalProperties", false).put("properties", JSONObject().put("items",
                    JSONObject().put("type", "array").put("items", item)
                        .put("minItems", TARGET_ITEMS).put("maxItems", TARGET_ITEMS)))
        }

        internal fun parseQuizBatch(raw: String): List<QuizItem> {
            val root = JSONObject(raw.trim().removePrefix("\uFEFF").trim())
            require(root.length() == 1 && root.has("items")) { "Invalid quiz object" }
            val array = root.opt("items") as? JSONArray ?: throw IllegalArgumentException("Invalid quiz items")
            require(array.length() == TARGET_ITEMS) { "Invalid quiz size" }
            return (0 until array.length()).map { index ->
                val item = array.optJSONObject(index) ?: throw IllegalArgumentException("Invalid quiz item")
                require(item.length() == ITEM_FIELDS.size && ITEM_FIELDS.all { item.has(it) }) { "Invalid quiz fields" }
                val type = item.opt("type") as? String
                require(type == "mcq" || type == "short" || type == "blank") { "Invalid quiz type" }
                val prompt = text(item, "prompt")
                val answer = text(item, "correctAnswer")
                val explanation = text(item, "explanation")
                val options = item.opt("options") as? JSONArray ?: throw IllegalArgumentException("Invalid quiz options")
                val choices = (0 until options.length()).map { option ->
                    val choice = options.opt(option) as? String ?: throw IllegalArgumentException("Invalid quiz option")
                    require(choice.isNotBlank()) { "Empty quiz option" }
                    choice
                }
                if (type == "mcq") {
                    require(choices.size == 4) { "Invalid choice count" }
                    val normalized = choices.map(::normalizeAnswer)
                    require(normalized.toSet().size == 4) { "Duplicate choices" }
                    val expected = normalizeAnswer(answer)
                    require(normalized.count { it == expected } == 1) { "Unmatched correct answer" }
                } else require(choices.isEmpty()) { "Unexpected choices" }
                QuizItem().apply {
                    this.type = type
                    this.prompt = prompt
                    correctAnswer = answer
                    this.explanation = explanation
                    optionsJson = options.toString()
                }
            }
        }

        private fun text(item: JSONObject, field: String): String {
            val value = item.opt(field) as? String ?: throw IllegalArgumentException("Invalid quiz text")
            require(value.isNotBlank()) { "Empty quiz text" }
            return value.trim()
        }

        internal fun normalizeAnswer(value: String?): String =
            value.orEmpty().trim().replace(WHITESPACE, " ").lowercase()
    }
}
