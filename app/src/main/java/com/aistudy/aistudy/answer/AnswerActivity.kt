package com.aistudy.aistudy.answer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import java.io.File
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnswerActivity : AppCompatActivity() {
    private lateinit var model: AnswerViewModel
    private var userId = 0L
    private var renderedQaId = 0L
    private var renderedSteps = emptyList<AnswerStep>()

    private var qaDao: QuestionAnswerDao? = null
    private lateinit var answerRepository: AnswerRepository
    private lateinit var profileDao: ProfileDao
    private var currentQa: QuestionAnswer? = null
    private var extrasExecutor: ExecutorService? = null

    private lateinit var tvDifficulty: TextView
    private lateinit var btnSimplify: TextView
    private lateinit var btnAlternative: TextView
    private lateinit var btnConcepts: TextView
    private lateinit var btnMistakes: TextView
    private lateinit var btnFollowups: TextView
    private lateinit var tvExtras: TextView
    private lateinit var progressExtras: ProgressBar
    private lateinit var btnMarkReviewed: MaterialButton

    private var activeExtraKey: String? = null
    private var extrasLoading = false
    private var reviewSessionStartMs: Long = 0

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

        // Phase 9 views
        tvDifficulty = findViewById(R.id.tv_difficulty)
        btnSimplify = findViewById(R.id.btn_simplify)
        btnAlternative = findViewById(R.id.btn_alternative)
        btnConcepts = findViewById(R.id.btn_concepts)
        btnMistakes = findViewById(R.id.btn_mistakes)
        btnFollowups = findViewById(R.id.btn_followups)
        tvExtras = findViewById(R.id.tv_extras)
        progressExtras = findViewById(R.id.progress_extras)
        btnMarkReviewed = findViewById(R.id.btn_mark_reviewed)

        btnSimplify.setOnClickListener { onExtraAction(KEY_SIMPLIFY) }
        btnAlternative.setOnClickListener { onExtraAction(KEY_ALTERNATIVE) }
        btnConcepts.setOnClickListener { onExtraAction(KEY_CONCEPTS) }
        btnMistakes.setOnClickListener { onExtraAction(KEY_MISTAKES) }
        btnFollowups.setOnClickListener { onExtraAction(KEY_FOLLOWUPS) }
        btnMarkReviewed.setOnClickListener { markReviewed() }

        extrasExecutor = Executors.newSingleThreadExecutor()

        model = ViewModelProvider(this)[AnswerViewModel::class.java]
        findViewById<View>(R.id.btn_more_topics).setOnClickListener { model.showMore() }
        val database = AppDatabase.getInstance(applicationContext)
        qaDao = database.questionAnswerDao()
        profileDao = database.profileDao()

        val preferences = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
        val apiKey = BuildConfig.GEMINI_API_KEY
        answerRepository = AnswerRepository(database.questionAnswerDao(), qaId, userId,
            { preferences.getLong(LoginActivity.KEY_USER_ID, 0L) }) { prompt, schema, instruction, trace ->
            if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
                throw AnswerViewModel.MissingKeyException()
            }
            GeminiClient.ask(apiKey, prompt, schema, instruction, trace)
        }
        model.state.observe(this) { state ->
            if (isFinishing || isDestroyed) return@observe
            // An answer can only be displayed to the account that saved it.
            if (state.loadFailed || preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId) {
                showLoadError()
            } else state.snapshot?.let { snapshot ->
                currentQa = snapshot.item
                showAnswer(snapshot.item, snapshot.content)
                showMoreState(state, snapshot.content)
                refreshReviewButton()
                maybeAutoReview()
            }
        }
        model.start(answerRepository)
        reviewSessionStartMs = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
                .getLong(LoginActivity.KEY_USER_ID, 0L) != userId) {
            showLoadError()
            return
        }
        reviewSessionStartMs = System.currentTimeMillis()
    }

    override fun onPause() {
        flushReviewSeconds()
        super.onPause()
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
        tvDifficulty.text = difficultyLabel(item.difficulty)
        val btnPractice = findViewById<MaterialButton>(R.id.btn_practice)
        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(item.subject))
        btnPractice.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            intent.putExtra(QuizActivity.EXTRA_SUBJECT, item.subject)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.tv_question).setTextIfChanged(item.question.orEmpty())

        val layoutAnswerImage = findViewById<LinearLayout>(R.id.layout_answer_image)
        val imgAnswer = findViewById<ImageView>(R.id.img_answer)
        val imagePath = item.imagePath
        if (!imagePath.isNullOrEmpty()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val bitmap = decodeSampledBitmap(imageFile.absolutePath, 800, 600)
                if (bitmap != null) {
                    imgAnswer.setImageBitmap(bitmap)
                    layoutAnswerImage.visibility = View.VISIBLE
                } else {
                    layoutAnswerImage.visibility = View.GONE
                }
            } else {
                layoutAnswerImage.visibility = View.GONE
            }
        } else {
            layoutAnswerImage.visibility = View.GONE
        }
        val isStepByStep = content.responseMode == ResponseMode.STEP_BY_STEP
        val headingRes = when (content.responseMode) {
            ResponseMode.STEP_BY_STEP -> R.string.answer_heading_solution
            ResponseMode.DIRECT -> R.string.answer_heading_direct
            ResponseMode.CREATIVE -> R.string.answer_heading_creative
            ResponseMode.CLARIFICATION -> R.string.answer_heading_clarification
            ResponseMode.LIMITATION -> R.string.answer_heading_limitation
        }
        findViewById<TextView>(R.id.tv_answer_explanation_heading).setText(headingRes)

        findViewById<TextView>(R.id.tv_answer_step_count).apply {
            if (isStepByStep && content.steps.size > 1) {
                text = resources.getQuantityString(R.plurals.answer_step_count, content.steps.size, content.steps.size)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        // Adaptive Notice Card for Creative, Clarification, Limitation
        val cardNotice = findViewById<View>(R.id.card_answer_notice)
        val tvNoticeTitle = findViewById<TextView>(R.id.tv_answer_notice_title)
        val tvNoticeBody = findViewById<TextView>(R.id.tv_answer_notice_body)
        when (content.responseMode) {
            ResponseMode.CREATIVE -> {
                tvNoticeTitle.setText(R.string.answer_badge_creative)
                tvNoticeBody.setText(R.string.answer_disclaimer_creative)
                cardNotice?.visibility = View.VISIBLE
            }
            ResponseMode.CLARIFICATION -> {
                tvNoticeTitle.setText(R.string.answer_notice_clarification_title)
                tvNoticeBody.setText(R.string.answer_notice_clarification_msg)
                cardNotice?.visibility = View.VISIBLE
            }
            ResponseMode.LIMITATION -> {
                tvNoticeTitle.setText(R.string.answer_notice_limitation_title)
                tvNoticeBody.setText(R.string.answer_notice_limitation_msg)
                cardNotice?.visibility = View.VISIBLE
            }
            else -> {
                cardNotice?.visibility = View.GONE
            }
        }

        val finalLabelRes = when (content.responseMode) {
            ResponseMode.STEP_BY_STEP -> R.string.answer_final_title
            ResponseMode.CLARIFICATION -> R.string.answer_final_label_next_step
            else -> R.string.answer_final_label_takeaway
        }
        findViewById<TextView>(R.id.tv_final_title).setText(finalLabelRes)
        findViewById<TextView>(R.id.tv_final_answer).setTextIfChanged(item.finalAnswer.orEmpty())
        val finalVisibility = if (content.showFinalAnswer(item.subject) && !item.finalAnswer.isNullOrBlank()) View.VISIBLE else View.GONE
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

                val stepNumberView = stepView.findViewById<TextView>(R.id.tv_answer_step_number)
                val connectorView = stepView.findViewById<View>(R.id.answer_step_connector)
                val stepTitleView = stepView.findViewById<TextView>(R.id.tv_answer_step_title)

                if (isStepByStep) {
                    stepNumberView.visibility = View.VISIBLE
                    stepNumberView.text = getString(R.string.answer_step_index, index + 1)
                    stepTitleView.visibility = View.VISIBLE
                    stepTitleView.apply {
                        text = title.ifBlank { getString(R.string.answer_step_number, index + 1) }
                        contentDescription = if (title.isBlank()) text
                            else getString(R.string.answer_step_title, index + 1, title)
                    }
                } else {
                    stepNumberView.visibility = View.GONE
                    connectorView.visibility = View.GONE
                    val isGenericTitle = title.isBlank() ||
                        Regex("^(step|bước|phần)\\s*\\d*$", RegexOption.IGNORE_CASE).matches(title)
                    if (isGenericTitle) {
                        stepTitleView.visibility = View.GONE
                    } else {
                        stepTitleView.visibility = View.VISIBLE
                        stepTitleView.text = title
                        stepTitleView.contentDescription = title
                    }
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
            message.findViewById<View>(R.id.tv_answer_step_number).visibility = View.GONE
            message.findViewById<View>(R.id.answer_step_connector).visibility = View.GONE
            message.findViewById<TextView>(R.id.tv_answer_step_title).setText(R.string.answer_steps_unavailable)
            message.findViewById<View>(R.id.tv_answer_step_body).visibility = View.GONE
            message.findViewById<View>(R.id.layout_answer_step_expr).visibility = View.GONE
            container.addView(message)
        }
        // Reconnect connectors only when step-by-step
        for (index in 0 until container.childCount) {
            container.getChildAt(index).findViewById<View>(R.id.answer_step_connector).visibility =
                if (isStepByStep && index < container.childCount - 1) View.VISIBLE else View.GONE
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

    // Phase 9: Auto Review & XP Rules
    private fun maybeAutoReview() {
        val item = currentQa ?: return
        val now = System.currentTimeMillis()
        if (item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)) {
            return
        }
        markReviewed()
    }

    private fun markReviewed() {
        val item = currentQa ?: return
        if (!isCurrentSession() || item.userId != userId) return
        val now = System.currentTimeMillis()
        if (item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)) {
            refreshReviewButton()
            return
        }
        val previousReviewedAt = item.reviewedAt
        item.reviewedAt = now
        refreshReviewButton()
        extrasExecutor?.execute {
            val db = AppDatabase.getInstance(applicationContext)
            var awarded = false
            db.runInTransaction {
                if (!isCurrentSession()) return@runInTransaction
                if (qaDao?.updateReviewedAt(item.id, userId, previousReviewedAt, now) != 1) return@runInTransaction
                val profile = profileDao.getByUserId(userId)
                if (profile != null) {
                    XpRules.addXp(profile, profileDao, XpRules.REVIEW)
                    awarded = true
                }
            }
            runOnUiThread {
                if (awarded && isCurrentSession() && !isFinishing && !isDestroyed) {
                    Toast.makeText(this@AnswerActivity, R.string.answer_review_xp_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshReviewButton() {
        val item = currentQa ?: return
        val now = System.currentTimeMillis()
        val doneToday = item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)
        btnMarkReviewed.isEnabled = !doneToday
        btnMarkReviewed.setText(if (doneToday) R.string.answer_reviewed else R.string.answer_mark_reviewed)
    }

    // Phase 9: Learning Extras Actions & On-demand Gemini caching
    private fun onExtraAction(key: String) {
        if (extrasLoading || !isCurrentSession()) return
        activeExtraKey = key
        styleExtraButtons()
        val cached = readExtraField(key)
        if (!cached.isNullOrEmpty()) {
            tvExtras.text = cached
            return
        }
        loadExtras(key)
    }

    private fun loadExtras(focusKey: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
            Toast.makeText(this, R.string.ask_error_key_missing, Toast.LENGTH_LONG).show()
            return
        }
        val item = currentQa ?: return
        val snapshot = model.state.value?.snapshot ?: return
        if (snapshot.item.id != item.id || snapshot.item.userId != userId || !isCurrentSession()) return
        extrasLoading = true
        progressExtras.visibility = View.VISIBLE
        tvExtras.setText(R.string.answer_extras_loading)

        extrasExecutor?.execute {
            if (!isCurrentSession()) return@execute
            try {
                val extras = answerRepository.extras(snapshot)
                runOnUiThread {
                    if (!isCurrentSession() || isFinishing || isDestroyed) return@runOnUiThread
                    val current = currentQa
                    if (current == null || current.id != snapshot.item.id || current.userId != userId) return@runOnUiThread
                    current.extrasJson = extras.toString()
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    val text = AnswerContent.extraField(extras, focusKey)
                    if (text.isNullOrEmpty()) tvExtras.setText(R.string.answer_extras_error) else tvExtras.text = text
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isCurrentSession() || isFinishing || isDestroyed) return@runOnUiThread
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    tvExtras.setText(R.string.answer_extras_error)
                    Toast.makeText(this@AnswerActivity, R.string.answer_extras_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readExtraField(key: String): String? {
        val extrasJson = currentQa?.extrasJson
        if (extrasJson.isNullOrEmpty()) return null
        return try { AnswerContent.extraField(org.json.JSONObject(extrasJson), key) } catch (_: Exception) { null }
    }
    private fun styleExtraButtons() {
        styleAction(btnSimplify, KEY_SIMPLIFY == activeExtraKey)
        styleAction(btnAlternative, KEY_ALTERNATIVE == activeExtraKey)
        styleAction(btnConcepts, KEY_CONCEPTS == activeExtraKey)
        styleAction(btnMistakes, KEY_MISTAKES == activeExtraKey)
        styleAction(btnFollowups, KEY_FOLLOWUPS == activeExtraKey)
    }

    private fun styleAction(btn: TextView, active: Boolean) {
        if (active) {
            btn.setBackgroundResource(R.drawable.bg_answer_action_active)
            btn.setTextColor(ContextCompat.getColor(this, R.color.answer_on_primary))
        } else {
            btn.setBackgroundResource(R.drawable.bg_answer_action)
            btn.setTextColor(ContextCompat.getColor(this, R.color.answer_foreground))
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        com.aistudy.aistudy.ask.ImagePreparation.decodeSampledBitmap(path, reqWidth, reqHeight)

    private fun difficultyLabel(difficulty: String?): String {
        return when (difficulty?.lowercase()) {
            "easy" -> getString(R.string.answer_difficulty_easy)
            "hard" -> getString(R.string.answer_difficulty_hard)
            else -> getString(R.string.answer_difficulty_medium)
        }
    }

    private fun subjectLabel(value: String?): String {
        return when (value) {
            "math", ProfileDefaults.SUBJECT_MATH -> getString(R.string.subject_math)
            "science", ProfileDefaults.SUBJECT_SCIENCE -> getString(R.string.subject_science)
            "code", ProfileDefaults.SUBJECT_CODE -> getString(R.string.subject_code)
            "history", ProfileDefaults.SUBJECT_HISTORY -> getString(R.string.subject_history)
            "language", ProfileDefaults.SUBJECT_LANGUAGE -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }


    private fun TextView.setTextIfChanged(value: String) {
        if (text.toString() != value) text = value
    }

    private fun showLoadError() {
        Toast.makeText(this, R.string.answer_error_load, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun flushReviewSeconds() {
        val item = currentQa
        val dao = qaDao
        if (item == null || dao == null || reviewSessionStartMs <= 0 || !isCurrentSession() || item.userId != userId) {
            return
        }
        val elapsedMs = System.currentTimeMillis() - reviewSessionStartMs
        reviewSessionStartMs = 0
        var delta = (elapsedMs / 1000L).toInt()
        if (delta <= 0) {
            return
        }
        if (delta > MAX_SESSION_SECONDS) {
            delta = MAX_SESSION_SECONDS
        }
        item.reviewedSeconds += delta
        val id = item.id
        extrasExecutor?.execute {
            if (isCurrentSession()) dao.addReviewedSeconds(id, userId, delta)
        }
    }

    private fun isCurrentSession(): Boolean = userId > 0L &&
        getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L) == userId

    override fun onDestroy() {
        flushReviewSeconds()
        extrasExecutor?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QA_ID = "qa_id"
        private const val MAX_SESSION_SECONDS = 1800
        private const val KEY_SIMPLIFY = "simplify"
        private const val KEY_ALTERNATIVE = "alternative"
        private const val KEY_CONCEPTS = "concepts"
        private const val KEY_MISTAKES = "mistakes"
        private const val KEY_FOLLOWUPS = "followUps"

        private fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance().apply { timeInMillis = a }
            val cb = Calendar.getInstance().apply { timeInMillis = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }
    }
}
