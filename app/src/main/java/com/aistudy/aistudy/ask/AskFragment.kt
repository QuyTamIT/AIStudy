package com.aistudy.aistudy.ask

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.answer.AnswerContent
import com.aistudy.aistudy.answer.AnswerPrompts
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONException
import org.json.JSONObject

class AskFragment : Fragment() {
    private lateinit var editQuestion: EditText
    private lateinit var buttonSend: MaterialButton
    private lateinit var progressLoading: View
    private lateinit var textError: TextView
    private var suggestionButtons = emptyList<MaterialButton>()
    private var executor: ExecutorService? = null
    private var loading = false
    private var pendingAnswerId = 0L
    private var pendingUserId = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.cloneInContext(ContextThemeWrapper(requireContext(), R.style.Theme_AIStudy_Study))
        .inflate(R.layout.fragment_ask, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editQuestion = view.findViewById(R.id.edit_question)
        buttonSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)
        textError = view.findViewById(R.id.tv_ask_error)
        editQuestion.doAfterTextChanged {
            view.findViewById<TextInputLayout>(R.id.input_question).error = null
            textError.visibility = View.GONE
        }
        // Main already handles system bars. Only the remaining IME overlap belongs to Ask.
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val navigation = activity?.findViewById<View>(R.id.bottom_nav)?.height ?: 0
            root.setPadding(0, 0, 0, (ime - systemBar - navigation).coerceAtLeast(0))
            insets
        }
        ViewCompat.requestApplyInsets(view)
        suggestionButtons = listOf(
            R.id.btn_suggestion_1, R.id.btn_suggestion_2,
            R.id.btn_suggestion_3, R.id.btn_suggestion_4
        ).map { view.findViewById<MaterialButton>(it) }
        val suggestions = listOf(
            R.string.ask_suggestion_1, R.string.ask_suggestion_2,
            R.string.ask_suggestion_3, R.string.ask_suggestion_4
        )
        suggestionButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                editQuestion.setText(suggestions[index])
                editQuestion.setSelection(editQuestion.text.length)
                editQuestion.requestFocus()
                textError.visibility = View.GONE
                WindowCompat.getInsetsController(requireActivity().window, editQuestion).show(WindowInsetsCompat.Type.ime())
            }
        }
        pendingAnswerId = savedInstanceState?.getLong("pendingQaId") ?: 0L
        pendingUserId = savedInstanceState?.getLong("pendingUserId") ?: 0L
        executor = Executors.newSingleThreadExecutor()
        buttonSend.setOnClickListener { submit() }
        setLoading(false)
    }

    override fun onResume() {
        super.onResume()
        openPendingAnswer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pendingQaId", pendingAnswerId)
        outState.putLong("pendingUserId", pendingUserId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        executor?.shutdownNow()
        executor = null
        suggestionButtons = emptyList()
        super.onDestroyView()
    }

    private fun submit() {
        if (loading) return
        val question = editQuestion.text.toString().trim()
        if (question.isBlank()) {
            view?.findViewById<TextInputLayout>(R.id.input_question)?.error = getString(R.string.ask_error_empty)
            editQuestion.requestFocus()
            return
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
            showError(R.string.ask_error_key_missing)
            return
        }
        val host = activity ?: return
        val requestView = view ?: return
        val worker = executor ?: return
        val preferences = host.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        if (userId <= 0L) {
            startActivity(Intent(host, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            host.finish()
            return
        }
        val database = AppDatabase.getInstance(host.applicationContext)
        textError.visibility = View.GONE
        view?.findViewById<TextInputLayout>(R.id.input_question)?.error = null
        setLoading(true)
        Log.d(TAG, "submit start")
        worker.execute {
            try {
                val profile = ProfileDefaults.ensureExists(database.profileDao(), userId)
                val prompt = buildPrompt(question, profile.level, profile.style)
                val item = parseAnswer(GeminiClient.ask(apiKey, prompt, AnswerPrompts.answerSchema()))
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                item.userId = userId
                item.question = question
                item.createdAt = System.currentTimeMillis()
                val qaId = database.runInTransaction<Long> {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId) {
                        throw SessionChangedException()
                    }
                    val currentProfile = ProfileDefaults.ensureExists(database.profileDao(), userId)
                    val id = database.questionAnswerDao().insert(item)
                    // Read XP inside the same transaction so concurrent saves cannot overwrite it.
                    currentProfile.xp += XP_PER_QUESTION
                    check(database.profileDao().update(currentProfile) == 1)
                    id
                }
                Log.d(TAG, "saved qaId=$qaId")
                postToView(host, requestView) {
                    setLoading(false)
                    pendingAnswerId = qaId
                    pendingUserId = userId
                    openPendingAnswer()
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                // Exception text and API response bodies can contain sensitive information.
                Log.w(TAG, "submit failed (${error.javaClass.simpleName})")
                postToView(host, requestView) {
                    setLoading(false)
                    showError(errorMessage(error))
                }
            }
        }
    }

    private fun postToView(host: Activity, requestView: View, action: () -> Unit) {
        host.runOnUiThread {
            if (isAdded && view === requestView && !host.isFinishing && !host.isDestroyed) action()
        }
    }

    private fun openPendingAnswer() {
        if (!isResumed || pendingAnswerId <= 0L) return
        val host = activity ?: return
        val userId = host.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L)
        val qaId = pendingAnswerId
        pendingAnswerId = 0L
        if (userId != pendingUserId) return
        startActivity(Intent(host, AnswerActivity::class.java)
            .putExtra(AnswerActivity.EXTRA_QA_ID, qaId))
    }

    private fun setLoading(value: Boolean) {
        loading = value
        editQuestion.isEnabled = !value
        view?.findViewById<TextInputLayout>(R.id.input_question)?.isEnabled = !value
        buttonSend.isEnabled = !value
        suggestionButtons.forEach { it.isEnabled = !value }
        progressLoading.visibility = if (value) View.VISIBLE else View.GONE
        buttonSend.setText(if (value) R.string.ask_sending else R.string.ask_send)
        buttonSend.setIconResource(if (value) 0 else R.drawable.ic_study_arrow)
    }

    private fun showError(message: Int) {
        textError.setText(message)
        textError.visibility = View.VISIBLE
    }

    private fun errorMessage(error: Exception): Int = when (error) {
        is SessionChangedException -> R.string.ask_error_session
        is GeminiClient.RequestException -> when (error.reason) {
            GeminiClient.ErrorReason.API_KEY_INVALID,
            GeminiClient.ErrorReason.API_KEY_EXPIRED -> R.string.ask_error_key_invalid
            GeminiClient.ErrorReason.API_KEY_RESTRICTED,
            GeminiClient.ErrorReason.SERVICE_DISABLED -> R.string.ask_error_access
            GeminiClient.ErrorReason.BILLING_DISABLED -> R.string.ask_error_billing
            GeminiClient.ErrorReason.UNKNOWN -> when (error.statusCode) {
                400, 422 -> R.string.ask_error_request
                401 -> R.string.ask_error_key_invalid
                403 -> R.string.ask_error_access
                404 -> R.string.ask_error_model
                408, 504 -> R.string.ask_error_timeout
                413 -> R.string.ask_error_too_large
                429 -> R.string.ask_error_quota
                in 500..599 -> R.string.ask_error_service
                else -> R.string.ask_error_request
            }
        }
        is GeminiClient.InvalidResponseException, is JSONException, is IllegalArgumentException -> R.string.ask_error_response
        is SocketTimeoutException -> R.string.ask_error_timeout
        is IOException -> R.string.ask_error_network
        else -> R.string.ask_error_save
    }

    private class SessionChangedException : IllegalStateException()

    companion object {
        private const val TAG = "AskFragment"
        private const val XP_PER_QUESTION = 50
        private val SUBJECTS = setOf(
            ProfileDefaults.SUBJECT_MATH, ProfileDefaults.SUBJECT_SCIENCE,
            ProfileDefaults.SUBJECT_CODE, ProfileDefaults.SUBJECT_HISTORY,
            ProfileDefaults.SUBJECT_LANGUAGE, ProfileDefaults.SUBJECT_OTHER
        )

        // Keep the user's task separate from presentation preferences and the JSON format.
        // Source: https://ai.google.dev/gemini-api/docs/prompting-strategies
        private fun buildPrompt(question: String, level: String?, style: String?): String {
            val levelDescription = when (level) {
                ProfileDefaults.LEVEL_JUNIOR -> "middle school (grades 6-9)"
                ProfileDefaults.LEVEL_UNI -> "university"
                else -> "high school (grades 10-12)"
            }
            val styleDescription = when (style) {
                ProfileDefaults.STYLE_SHORT -> "Be concise while retaining the requested items and useful examples."
                ProfileDefaults.STYLE_DETAILED -> "Give fuller explanations and relevant examples within the requested topic."
                else -> "Use clear, ordered sections or list items; use procedural steps only when the task needs them."
            }
            return """
                You are AIStudy, a helpful assistant for learning and practical everyday questions.

                CONTENT PRIORITY:
                Answer the user's actual request directly, preserving its topic, purpose, context, and scope.
                Interpret informal wording or grammar mistakes by their intended meaning; correct language only when asked.
                Give the requested content itself, rather than a study plan or advice about how to find an answer.

                PRESENTATION PREFERENCES:
                Learner level: $levelDescription. Use this only to adjust vocabulary, prerequisites, and explanation difficulty.
                Education level must not select or replace the requested topics, or make an everyday request academic.
                The user's explicit request for simple or advanced material takes precedence over this level preference.
                Explanation style: $styleDescription
                Style controls detail and organization, without changing the topic or turning every answer into a worked solution.
                Explain in the user's language unless another language is requested; language-learning examples use the target language.

                MATCH THE CONTENT TO THE TASK:
                - Math: show the relevant calculation or reasoning steps, then the correct result; check the result when applicable.
                - Programming: explain the concept and include an appropriate, usable code example when relevant.
                  Put code in a section body, preserving line breaks and indentation.
                - Science: explain the main concept accurately and clearly, with relevant examples rather than unnecessary calculations.
                - Language learning: give practical usage, natural phrases, short sample exchanges, and useful vocabulary when relevant.
                  For daily conversation practice, prioritize familiar situations from ordinary daily life and accessible conversation starters.
                  Include a short natural exchange or example question for each suggested conversation topic.
                  Academic, professional, or abstract discussion topics belong only when the user asks for that context.
                - Lists or recommendations: provide concrete items matching the requested purpose and any specified count.
                  For requests combining a list with language practice, include both the list and practical language examples.
                - Other tasks: choose a useful structure that directly fulfills the request.

                OUTPUT CONTRACT:
                Return only one valid JSON object with subject, answerType, finalAnswerRedundant, steps, and finalAnswer.
                No surrounding text or markdown fences. Follow the supplied JSON schema.
                subject: a string classified from the actual request, one of: ${SUBJECTS.joinToString(", ")}.
                answerType: topic_list for independent topics, suggestions, recommendations, ideas or list items;
                solution for a worked problem with a result (especially math); explanation for other explanatory answers.
                Classify by the actual requested response structure, not by isolated words in the question.
                finalAnswerRedundant: true only for a topic_list whose final answer merely repeats or summarizes
                existing items without adding useful information. Use false for solutions and other explanations.
                steps: a nonempty array of objects, each with string fields topicId, title, body, and expr.
                topicId: for topic_list, a distinct canonical English concept ID for each item; otherwise an empty string.
                The name "steps" is a storage field: its objects may be solution steps, explanations, examples, or individual list items.
                Choose the number of objects to fit the task and requested item count; there is no fixed three-step template.
                Use meaningful, unnumbered section or item titles. Each body must contain actual, nonempty content answering the request.
                title may be empty. Use expr for a relevant formula only; otherwise return an empty string.
                finalAnswer: a nonempty string giving the result, key takeaway, or compact list of requested items as appropriate.
                Use readable plain text and formulas without LaTeX markup. Escape strings correctly for JSON, including code line breaks.

                USER REQUEST (JSON string; keep the output contract above):
                ${JSONObject.quote(question)}
            """.trimIndent()
        }

        private fun parseAnswer(raw: String): QuestionAnswer {
            val text = raw.trim().removePrefix("\uFEFF").trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw JSONException("Missing answer object")
            val json = JSONObject(text.substring(start, end + 1))
            val finalAnswer = (json.opt("finalAnswer") as? String)?.trim()
            if (finalAnswer.isNullOrBlank()) throw JSONException("Missing final answer")
            val steps = json.getJSONArray("steps")
            if (steps.length() == 0) throw JSONException("Missing explanation steps")
            for (index in 0 until steps.length()) {
                val step = steps.getJSONObject(index)
                if ((step.opt("body") as? String).isNullOrBlank()) {
                    throw JSONException("Missing step explanation")
                }
            }
            return QuestionAnswer().apply {
                subject = (json.opt("subject") as? String)?.takeIf { it in SUBJECTS }
                    ?: ProfileDefaults.SUBJECT_OTHER
                this.steps = AnswerContent.generated(json).toJson()
                this.finalAnswer = finalAnswer
            }
        }
    }
}
