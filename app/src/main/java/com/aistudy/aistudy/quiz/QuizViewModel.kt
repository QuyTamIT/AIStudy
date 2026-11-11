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
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.database.QuizSession
import com.aistudy.aistudy.xp.XpRules
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

internal class QuizViewModel @JvmOverloads constructor(
    application: Application,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val requestQuiz: (String, String) -> String = { key, prompt -> GeminiClient.ask(key, prompt) }
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
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
            throw QuizLoadException(R.string.quiz_no_api_key)
        }
        val sources = database.questionAnswerDao().getRecentByUserIdAndSubject(ownerId, subject, RECENT_LIMIT)
        if (sources.size < MIN_SOURCE_QA) throw QuizLoadException(R.string.quiz_need_more)

        checkActiveUser(ownerId)
        val raw = requestQuiz(apiKey, buildQuizPrompt(sources, subject))
        val array = JSONObject(extractJson(raw)).getJSONArray("items")
        val items = (0 until minOf(array.length(), TARGET_ITEMS)).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val prompt = item.optString("prompt", "").trim()
            val answer = item.optString("correctAnswer", "").trim()
            if (prompt.isEmpty() || answer.isEmpty()) return@mapNotNull null
            QuizItem().apply {
                type = normalizeType(item.optString("type", "mcq"))
                this.prompt = prompt
                correctAnswer = answer
                explanation = item.optString("explanation", "").trim()
                optionsJson = item.optJSONArray("options")?.toString() ?: "[]"
            }
        }
        check(items.isNotEmpty())
        val session = QuizSession().apply {
            userId = ownerId
            this.subject = subject
            sourceQaIds = JSONArray(sources.map { it.id }).toString()
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
        return Quiz(session, items)
    }

    private fun checkActiveUser(ownerId: Long) {
        check(!cleared && !Thread.currentThread().isInterrupted)
        check(ownerId > 0L && preferences.getLong(LoginActivity.KEY_USER_ID, 0L) == ownerId)
    }

    private fun buildQuizPrompt(sources: List<QuestionAnswer>, subject: String): String {
        val sb = StringBuilder()
        sb.append("You are AI Study Mentor. Create a practice quiz from the student's recent questions.\n")
            .append("Return ONLY valid JSON (no markdown):\n")
            .append("{\n")
            .append("  \"items\": [\n")
            .append("    {\n")
            .append("      \"type\": \"mcq\",\n")
            .append("      \"prompt\": \"question text\",\n")
            .append("      \"options\": [\"A\", \"B\", \"C\", \"D\"],\n")
            .append("      \"correctAnswer\": \"exact matching option text\",\n")
            .append("      \"explanation\": \"why this is correct\"\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"type\": \"short\",\n")
            .append("      \"prompt\": \"question text\",\n")
            .append("      \"options\": [],\n")
            .append("      \"correctAnswer\": \"expected short answer\",\n")
            .append("      \"explanation\": \"brief explanation\"\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"type\": \"blank\",\n")
            .append("      \"prompt\": \"Fill in the blank: ...\",\n")
            .append("      \"options\": [],\n")
            .append("      \"correctAnswer\": \"word or phrase\",\n")
            .append("      \"explanation\": \"brief explanation\"\n")
            .append("    }\n")
            .append("  ]\n")
            .append("}\n\n")
            .append("Rules:\n")
            .append("- Create exactly ").append(TARGET_ITEMS).append(" items\n")
            .append("- Mix types: include at least one mcq, one short, one blank if possible\n")
            .append("- For mcq: options must have exactly 4 distinct choices; correctAnswer must equal one option exactly\n")
            .append("- Subject: ").append(subject).append("\n")
            .append("- Questions must be based on the source material below (similar concepts, not identical copies)\n")
            .append("- English only\n\n")
            .append("Source questions:\n")
        for ((i, qa) in sources.withIndex()) {
            sb.append(i + 1).append(". Q: ").append(qa.question)
                .append("\n   Final: ").append(qa.finalAnswer)
                .append("\n   Difficulty: ").append(qa.difficulty ?: "medium")
                .append("\n")
        }
        return sb.toString()
    }

    private fun normalizeType(type: String): String = when (type) {
        "short", "blank", "mcq" -> type
        else -> "mcq"
    }

    private fun normalizeAnswer(value: String?): String =
        value.orEmpty().trim().replace(Regex("\\s+"), " ").lowercase()

    private fun extractJson(raw: String): String {
        val text = raw.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    override fun onCleared() {
        cleared = true
        executor.shutdownNow()
        super.onCleared()
    }

    private class QuizLoadException(@get:StringRes val messageRes: Int) : Exception()

    private companion object {
        const val TAG = "QuizViewModel"
        const val MIN_SOURCE_QA = 2
        const val RECENT_LIMIT = 5
        const val TARGET_ITEMS = 4
    }
}
