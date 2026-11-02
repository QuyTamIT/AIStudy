package com.aistudy.aistudy.answer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton

class AnswerActivity : AppCompatActivity() {
    private lateinit var model: AnswerViewModel
    private var userId = 0L
    private var renderedQaId = 0L
    private var renderedSteps = emptyList<AnswerStep>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Answer)
        super.onCreate(savedInstanceState)
        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L)
        if (userId <= 0L) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        val qaId = intent.getLongExtra(EXTRA_QA_ID, 0L)
        if (qaId <= 0L) {
            showLoadError()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)
        // Give the section heading a full line at accessibility font sizes.
        if (resources.configuration.fontScale >= 1.3f) {
            findViewById<LinearLayout>(R.id.layout_explanation_heading).orientation = LinearLayout.VERTICAL
            findViewById<TextView>(R.id.tv_answer_explanation_heading).layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            findViewById<TextView>(R.id.tv_answer_step_count).layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.answer_space_2)
                }
        }
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val padding = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        model = ViewModelProvider(this)[AnswerViewModel::class.java]
        findViewById<View>(R.id.btn_more_topics).setOnClickListener { model.showMore() }
        val database = AppDatabase.getInstance(applicationContext)
        val preferences = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
        val apiKey = BuildConfig.GEMINI_API_KEY
        val repository = AnswerRepository(database.questionAnswerDao(), qaId, userId,
            { preferences.getLong(LoginActivity.KEY_USER_ID, 0L) }) { prompt, schema ->
            if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
                throw AnswerViewModel.MissingKeyException()
            }
            GeminiClient.ask(apiKey, prompt, schema)
        }
        model.state.observe(this) { state ->
            if (isFinishing || isDestroyed) return@observe
            // An answer can only be displayed to the account that saved it.
            if (state.loadFailed || preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId) {
                showLoadError()
            } else state.snapshot?.let { snapshot ->
                showAnswer(snapshot.item, snapshot.content)
                showMoreState(state, snapshot.content)
            }
        }
        model.start(repository)
    }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
                .getLong(LoginActivity.KEY_USER_ID, 0L) != userId) showLoadError()
    }

    private fun showAnswer(item: QuestionAnswer, content: AnswerContent) {
        val subject = when (item.subject) {
            ProfileDefaults.SUBJECT_MATH -> R.string.onboarding_subject_math
            ProfileDefaults.SUBJECT_SCIENCE -> R.string.onboarding_subject_science
            ProfileDefaults.SUBJECT_CODE -> R.string.onboarding_subject_code
            ProfileDefaults.SUBJECT_HISTORY -> R.string.onboarding_subject_history
            ProfileDefaults.SUBJECT_LANGUAGE -> R.string.onboarding_subject_language
            else -> R.string.onboarding_subject_other
        }
        findViewById<TextView>(R.id.tv_subject).setText(subject)
        findViewById<TextView>(R.id.tv_question).setTextIfChanged(item.question.orEmpty())
        findViewById<TextView>(R.id.tv_answer_step_count).apply {
            text = resources.getQuantityString(R.plurals.answer_step_count, content.steps.size, content.steps.size)
            visibility = if (content.steps.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.tv_final_answer).setTextIfChanged(item.finalAnswer.orEmpty())
        val finalVisibility = if (content.showFinalAnswer(item.subject)) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layout_final_answer).visibility = finalVisibility
        val container = findViewById<LinearLayout>(R.id.layout_steps)
        val previousItemsPreserved = renderedQaId == item.id &&
            content.steps.size >= renderedSteps.size && renderedSteps.indices.all { index ->
                val old = renderedSteps[index]
                val current = content.steps[index]
                old.title == current.title && old.body == current.body && old.expr == current.expr
            }
        if (!previousItemsPreserved) {
            container.removeAllViews()
            renderedSteps = emptyList()
        }
        if (content.steps != renderedSteps) {
            for (index in renderedSteps.size until content.steps.size) {
                val step = content.steps[index]
                val stepView = layoutInflater.inflate(R.layout.item_answer_step, container, false)
                val title = step.title.trim()
                stepView.findViewById<TextView>(R.id.tv_answer_step_number).text =
                    getString(R.string.answer_step_index, index + 1)
                stepView.findViewById<TextView>(R.id.tv_answer_step_title).apply {
                    text = title.ifBlank { getString(R.string.answer_step_number, index + 1) }
                    contentDescription = if (title.isBlank()) text
                        else getString(R.string.answer_step_title, index + 1, title)
                }
                AnswerBodyRenderer.bind(stepView, step.body)
                stepView.findViewById<TextView>(R.id.tv_answer_step_expr).text = step.expr
                stepView.findViewById<View>(R.id.layout_answer_step_expr).visibility =
                    if (step.expr.isBlank()) View.GONE else View.VISIBLE
                container.addView(stepView)
            }
        }
        if (content.steps.isEmpty() && container.childCount == 0) {
            val message = layoutInflater.inflate(R.layout.item_answer_step, container, false)
            message.findViewById<View>(R.id.tv_answer_step_number).visibility = View.INVISIBLE
            message.findViewById<TextView>(R.id.tv_answer_step_title).setText(R.string.answer_steps_unavailable)
            message.findViewById<View>(R.id.tv_answer_step_body).visibility = View.GONE
            message.findViewById<View>(R.id.layout_answer_step_expr).visibility = View.GONE
            container.addView(message)
        }
        // Reconnect the previous last step when more topics are appended, without rebuilding it.
        for (index in 0 until container.childCount) {
            container.getChildAt(index).findViewById<View>(R.id.answer_step_connector).visibility =
                if (index < container.childCount - 1) View.VISIBLE else View.INVISIBLE
        }
        renderedQaId = item.id
        renderedSteps = content.steps
        findViewById<View>(R.id.progress_answer_loading).visibility = View.GONE
        findViewById<View>(R.id.answer_content).visibility = View.VISIBLE
    }

    private fun showMoreState(state: AnswerUiState, content: AnswerContent) {
        val eligible = content.type == null || content.type == AnswerType.TOPIC_LIST
        findViewById<View>(R.id.layout_more_topics).visibility = if (eligible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.progress_more_topics).visibility = if (state.busy) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tv_more_status).text = when {
            state.busy && content.type == null -> getString(R.string.answer_more_checking)
            state.busy -> getString(R.string.answer_more_loading)
            content.type == null -> ""
            else -> getString(R.string.answer_more_count, content.moreCount, AnswerContent.MAX_MORE)
        }
        findViewById<TextView>(R.id.tv_more_error).apply {
            visibility = if (state.error == null) View.GONE else View.VISIBLE
            state.error?.let { setText(it) }
        }
        findViewById<MaterialButton>(R.id.btn_more_topics).apply {
            visibility = if (content.type == null && state.busy) View.GONE else View.VISIBLE
            isEnabled = !state.busy && (content.type == null || content.canExpand)
            setIconResource(if (isEnabled) R.drawable.ic_answer_more else 0)
            setText(when {
                state.busy -> R.string.answer_more_loading
                content.type == null -> R.string.answer_more_retry
                !content.canExpand -> R.string.answer_more_limit
                else -> R.string.answer_more_topics
            })
        }
    }

    private fun TextView.setTextIfChanged(value: String) {
        if (text.toString() != value) text = value
    }

    private fun showLoadError() {
        Toast.makeText(this, R.string.answer_error_load, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_QA_ID = "qa_id"
    }
}
