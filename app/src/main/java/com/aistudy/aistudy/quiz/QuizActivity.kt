package com.aistudy.aistudy.quiz

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.StudyMathText
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import kotlin.math.roundToInt

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_SESSION_ID = "sessionId"
    }

    private lateinit var model: QuizViewModel
    private var userId: Long = -1
    private lateinit var subject: String
    private var requestedSessionId = -1L
    private var quiz: QuizViewModel.Quiz? = null
    private val items: List<QuizItem>
        get() = quiz?.items.orEmpty()
    private var currentIndex = 0
    private val answered: Boolean
        get() = items.getOrNull(currentIndex)?.let { it.userAnswer != null && it.correct != null } == true
    private var restoredDraft: String? = null

    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutQuiz: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvQuizTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvItemType: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var groupOptions: RadioGroup
    private lateinit var etAnswer: EditText
    private lateinit var layoutFeedback: LinearLayout
    private lateinit var tvFeedbackResult: TextView
    private lateinit var tvFeedbackCorrect: TextView
    private lateinit var tvExplanation: TextView
    private lateinit var btnPrimary: MaterialButton
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultXp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Study)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val subjectExtra = intent.getStringExtra(EXTRA_SUBJECT)
            ?: savedInstanceState?.getString("subject")
        if (subjectExtra.isNullOrEmpty()) {
            Toast.makeText(this, R.string.quiz_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        subject = subjectExtra

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId <= 0L) {
            finish()
            return
        }

        layoutLoading = findViewById(R.id.layout_loading)
        layoutQuiz = findViewById(R.id.layout_quiz)
        layoutResult = findViewById(R.id.layout_result)
        tvQuizTitle = findViewById(R.id.tv_quiz_title)
        tvProgress = findViewById(R.id.tv_progress)
        tvItemType = findViewById(R.id.tv_item_type)
        tvPrompt = findViewById(R.id.tv_prompt)
        groupOptions = findViewById(R.id.group_options)
        etAnswer = findViewById(R.id.et_answer)
        groupOptions.isSaveEnabled = false
        etAnswer.isSaveEnabled = false
        layoutFeedback = findViewById(R.id.layout_feedback)
        tvFeedbackResult = findViewById(R.id.tv_feedback_result)
        tvFeedbackCorrect = findViewById(R.id.tv_feedback_correct)
        tvExplanation = findViewById(R.id.tv_explanation)
        btnPrimary = findViewById(R.id.btn_primary)
        tvResultScore = findViewById(R.id.tv_result_score)
        tvResultXp = findViewById(R.id.tv_result_xp)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnDone = findViewById<MaterialButton>(R.id.btn_done)

        tvQuizTitle.text = getString(R.string.quiz_practice_on, subjectLabel(subject))
        btnBack.setOnClickListener { finish() }
        btnDone.setOnClickListener { finish() }
        btnPrimary.setOnClickListener { onPrimaryClick() }

        if (savedInstanceState != null && savedInstanceState.getLong("ownerId", userId) != userId) {
            failRestore()
            return
        }
        requestedSessionId = savedInstanceState?.getLong(EXTRA_SESSION_ID, -1L)?.takeIf { it > 0L }
            ?: intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        currentIndex = savedInstanceState?.getInt("currentIndex", 0) ?: 0
        restoredDraft = savedInstanceState?.getString("draftAnswer")

        showLoading()
        model = ViewModelProvider(this)[QuizViewModel::class.java]
        model.start(userId, subject, requestedSessionId, savedInstanceState != null)
        model.state.observe(this, ::renderState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::model.isInitialized) return
        val sessionId = model.state.value?.quiz?.session?.id ?: requestedSessionId
        outState.putLong(EXTRA_SESSION_ID, sessionId)
        outState.putInt("currentIndex", currentIndex)
        outState.putString("subject", subject)
        outState.putLong("ownerId", userId)
        outState.putString("draftAnswer", if (quiz != null) currentInput() else restoredDraft)
    }

    private fun renderState(state: QuizViewModel.UiState) {
        if (isFinishing || isDestroyed) return
        if (!isCurrentSession()) {
            failRestore()
            return
        }
        val loaded = state.quiz
        if (loaded == null) {
            state.error?.let { failRestore(it) }
            return
        }
        if (loaded.session.userId != userId || currentIndex !in loaded.items.indices) {
            failRestore()
            return
        }
        if (loaded.session.completed) {
            quiz = loaded
            showResult(loaded.session.score, loaded.session.total)
            return
        }
        if (quiz !== loaded) {
            quiz = loaded
            showQuiz()
            bindCurrentItem()
        }
        btnPrimary.isEnabled = !state.saving
        setInputsEnabled(!answered && !state.saving)
        state.error?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }

    private fun onPrimaryClick() {
        if (!isCurrentSession()) {
            failRestore()
            return
        }
        if (model.state.value?.saving == true) return
        val item = items.getOrNull(currentIndex) ?: return
        if (!answered) {
            checkAnswer(item)
        } else if (currentIndex < items.lastIndex) {
            currentIndex++
            bindCurrentItem()
        } else {
            model.finishQuiz()
        }
    }

    private fun checkAnswer(item: QuizItem) {
        val userAnswer = currentInput()
        if (userAnswer.isBlank()) {
            val message = if (item.type == "mcq") R.string.quiz_select_option else R.string.quiz_enter_answer
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        model.grade(item.id, if (item.type == "mcq") userAnswer else userAnswer.trim())
    }

    private fun renderFeedback(item: QuizItem) {
        val correct = item.correct == true
        layoutFeedback.visibility = View.VISIBLE
        if (correct) {
            tvFeedbackResult.setText(R.string.quiz_correct)
            tvFeedbackResult.setTextColor(ContextCompat.getColor(this, R.color.answer_primary))
            tvFeedbackCorrect.visibility = View.GONE
        } else {
            tvFeedbackResult.setText(R.string.quiz_incorrect)
            tvFeedbackResult.setTextColor(ContextCompat.getColor(this, R.color.streak))
            tvFeedbackCorrect.visibility = View.VISIBLE
            tvFeedbackCorrect.text = getString(R.string.quiz_correct_answer_label,
                StudyMathText.format(item.correctAnswer.orEmpty()))
        }
        tvExplanation.text = if (item.explanation.isNullOrEmpty()) "—"
            else StudyMathText.format(item.explanation.orEmpty())

        setInputsEnabled(false)
        if (currentIndex < items.size - 1) {
            btnPrimary.setText(R.string.quiz_next)
        } else {
            btnPrimary.setText(R.string.quiz_finish)
        }
    }

    private fun showResult(score: Int, total: Int) {
        val xpGain = score * XpRules.QUIZ_CORRECT + XpRules.QUIZ_COMPLETE_BONUS
        layoutQuiz.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvResultScore.text = getString(R.string.quiz_result_score, score, total)
        tvResultXp.text = getString(R.string.quiz_result_xp, xpGain)
    }

    private fun bindCurrentItem() {
        val item = items[currentIndex]
        tvProgress.text = getString(R.string.quiz_progress, currentIndex + 1, items.size)
        tvItemType.text = typeLabel(item.type)
        tvPrompt.text = StudyMathText.format(item.prompt.orEmpty())
        layoutFeedback.visibility = View.GONE
        btnPrimary.isEnabled = true
        btnPrimary.setText(R.string.quiz_check)
        setInputsEnabled(true)

        groupOptions.removeAllViews()
        groupOptions.clearCheck()
        etAnswer.setText("")

        if (item.type == "mcq") {
            groupOptions.visibility = View.VISIBLE
            etAnswer.visibility = View.GONE
            try {
                val options = JSONArray(item.optionsJson ?: "[]")
                val padH = dp(14)
                val padV = dp(12)
                for (i in 0 until options.length()) {
                    val option = options.getString(i)
                    val rb = RadioButton(this)
                    rb.id = View.generateViewId()
                    rb.text = StudyMathText.format(option)
                    rb.tag = option
                    rb.setTextColor(ContextCompat.getColor(this, R.color.answer_foreground))
                    rb.setPadding(padH, padV, padH, padV)
                    rb.minHeight = dp(48)
                    groupOptions.addView(rb)
                }
            } catch (_: Exception) {
            }

            val selected = item.userAnswer ?: restoredDraft
            val options = groupOptions.children.filterIsInstance<RadioButton>()
            val checked = options.firstOrNull { it.tag == selected }
                ?: item.userAnswer?.let { saved ->
                    // Older versions trimmed the option before saving the answer.
                    options.firstOrNull { (it.tag as String).trim() == saved }
                }
            checked?.let { groupOptions.check(it.id) }
        } else {
            groupOptions.visibility = View.GONE
            etAnswer.visibility = View.VISIBLE
            etAnswer.setText(item.userAnswer ?: restoredDraft.orEmpty())
        }
        restoredDraft = null
        if (answered) renderFeedback(item)
    }

    private fun currentInput(): String {
        if (items.getOrNull(currentIndex)?.type != "mcq") return etAnswer.text?.toString().orEmpty()
        return (findViewById<RadioButton>(groupOptions.checkedRadioButtonId)?.tag as? String).orEmpty()
    }

    private fun isCurrentSession(): Boolean = userId > 0L && getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
        .getLong(LoginActivity.KEY_USER_ID, 0L) == userId

    private fun failRestore(@StringRes message: Int = R.string.quiz_error) {
        if (isFinishing || isDestroyed) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setInputsEnabled(enabled: Boolean) {
        for (i in 0 until groupOptions.childCount) {
            groupOptions.getChildAt(i).isEnabled = enabled
        }
        etAnswer.isEnabled = enabled
    }

    private fun showLoading() {
        layoutLoading.visibility = View.VISIBLE
        layoutQuiz.visibility = View.GONE
        layoutResult.visibility = View.GONE
    }

    private fun showQuiz() {
        layoutLoading.visibility = View.GONE
        layoutQuiz.visibility = View.VISIBLE
        layoutResult.visibility = View.GONE
    }

    private fun typeLabel(type: String?): String {
        return when (type) {
            "short" -> getString(R.string.quiz_type_short)
            "blank" -> getString(R.string.quiz_type_blank)
            else -> getString(R.string.quiz_type_mcq)
        }
    }

    private fun subjectLabel(value: String?): String {
        return when (value) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
