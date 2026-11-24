package com.aistudy.aistudy.study

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuizItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class StudyToolsActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var userId = 0L
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("${PREFS_STUDY}_$userId", MODE_PRIVATE) }
    private fun reviewedMistakeIds() = prefs.getStringSet(KEY_REVIEWED_MISTAKES, emptySet()).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L)
        if (userId <= 0L) { finish(); return }
        setContentView(R.layout.activity_study_tools)
        content = findViewById(R.id.layout_study_content)
        findViewById<View>(R.id.btn_study_back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && userId > 0L) load()
    }

    private fun load() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PLAN
        findViewById<TextView>(R.id.tv_study_title).setText(when (mode) {
            MODE_REVIEWS -> R.string.study_reviews_title
            MODE_MISTAKES -> R.string.study_mistakes_title
            else -> R.string.study_plan_title
        })
        findViewById<TextView>(R.id.tv_study_subtitle).setText(when (mode) {
            MODE_REVIEWS -> R.string.study_reviews_subtitle
            MODE_MISTAKES -> R.string.study_mistakes_subtitle
            else -> R.string.study_plan_subtitle
        })
        executor.execute {
            val db = AppDatabase.getInstance(applicationContext)
            val qaDao = db.questionAnswerDao()
            val quizDao = db.quizItemDao()
            val due = qaDao.getDueReviews(userId, System.currentTimeMillis(), MAX_ITEMS)
            val mistakes = quizDao.getIncorrectByUserId(userId, MAX_ITEMS)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (mode) {
                    MODE_REVIEWS -> showReviews(due)
                    MODE_MISTAKES -> showMistakes(mistakes)
                    else -> showPlan(due.size, mistakes.count { it.id.toString() !in reviewedMistakeIds() })
                }
            }
        }
    }

    private fun showPlan(dueCount: Int, mistakeCount: Int) {
        content.removeAllViews()
        val goalKey = "goal_${todayKey()}"
        val goalDone = prefs.getBoolean(goalKey, false)
        addHeading(getString(R.string.study_plan_today))
        val completedCount = (if (dueCount == 0) 1 else 0) +
            (if (mistakeCount == 0) 1 else 0) + (if (goalDone) 1 else 0)
        addBody(getString(R.string.study_plan_progress, completedCount))
        addTask(
            getString(R.string.study_plan_review_task, dueCount),
            getString(R.string.study_plan_review_body),
            dueCount == 0
        ) { open(MODE_REVIEWS) }
        addTask(
            getString(R.string.study_plan_mistake_task, mistakeCount),
            getString(R.string.study_plan_mistake_body),
            mistakeCount == 0
        ) { open(MODE_MISTAKES) }
        val goal = CheckBox(this).apply {
            text = getString(R.string.study_plan_goal_task)
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground))
            isChecked = goalDone
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(goalKey, checked).apply()
                load()
            }
        }
        addCard(goal)
        val reviewButton = actionButton(R.string.study_reviews_open) { open(MODE_REVIEWS) }
        content.addView(reviewButton)
        val mistakeButton = actionButton(R.string.study_mistakes_open) { open(MODE_MISTAKES) }
        content.addView(mistakeButton)
        addBody(getString(R.string.study_plan_local_note))
    }

    private fun showReviews(items: List<QuestionAnswer>) {
        content.removeAllViews()
        if (items.isEmpty()) { addEmpty(R.string.study_reviews_empty); return }
        addHeading(getString(R.string.study_reviews_count, items.size))
        items.forEach { item ->
            val box = taskCard(item.question.orEmpty().ifBlank { getString(R.string.study_saved_image_question) })
            val meta = TextView(this).apply {
                text = getString(R.string.study_review_interval, item.reviewIntervalDays)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground_secondary))
            }
            (box.getChildAt(0) as LinearLayout).addView(meta)
            val button = actionButton(R.string.study_review_open) {
                startActivity(Intent(this, AnswerActivity::class.java).putExtra(AnswerActivity.EXTRA_QA_ID, item.id))
            }
            (box.getChildAt(0) as LinearLayout).addView(button)
            content.addView(box)
        }
    }

    private fun showMistakes(items: List<QuizItem>) {
        content.removeAllViews()
        if (items.isEmpty()) { addEmpty(R.string.study_mistakes_empty); return }
        addHeading(getString(R.string.study_mistakes_count, items.size))
        items.forEach { item ->
            val prompt = item.prompt.orEmpty()
            val box = taskCard(prompt)
            val button = actionButton(R.string.study_mistake_review) {
                showMistakeDialog(item)
            }
            (box.getChildAt(0) as LinearLayout).addView(button)
            content.addView(box)
        }
    }

    private fun showMistakeDialog(item: QuizItem) {
        val details = buildString {
            append(getString(R.string.study_your_answer, item.userAnswer.orEmpty()))
            append("\n\n")
            append(getString(R.string.study_correct_answer, item.correctAnswer.orEmpty()))
            if (!item.explanation.isNullOrBlank()) {
                append("\n\n")
                append(getString(R.string.study_explanation, item.explanation.orEmpty()))
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(item.prompt.orEmpty())
            .setMessage(details)
            .setPositiveButton(R.string.study_mark_mistake_reviewed) { _, _ ->
                val updated = reviewedMistakeIds().toMutableSet().apply { add(item.id.toString()) }
                prefs.edit().putStringSet(KEY_REVIEWED_MISTAKES, updated).apply()
                load()
            }
            .show()
    }

    private fun addTask(title: String, description: String, done: Boolean, click: () -> Unit) {
        val card = taskCard(title)
        val body = TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground_secondary))
        }
        val inner = card.getChildAt(0) as LinearLayout
        inner.addView(body)
        val state = TextView(this).apply {
            text = getString(if (done) R.string.study_task_done else R.string.study_task_open)
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
            setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_primary))
        }
        inner.addView(state)
        card.setOnClickListener { click() }
        content.addView(card)
    }

    private fun taskCard(title: String): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_divider))
            setCardBackgroundColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_surface))
            val inner = LinearLayout(this@StudyToolsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(TextView(this@StudyToolsActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
            }
            addView(inner)
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        card.layoutParams = lp
        return card
    }

    private fun addCard(view: View) {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            setStrokeColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_divider))
            setCardBackgroundColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_surface))
            setContentPadding(dp(12), dp(8), dp(12), dp(8)); addView(view)
        }
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        content.addView(card)
    }

    private fun actionButton(label: Int, click: () -> Unit) = MaterialButton(this).apply {
        setText(label); setOnClickListener { click() }; minHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun addHeading(value: String) = content.addView(TextView(this).apply {
        text = value; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    })

    private fun addBody(value: String) = content.addView(TextView(this).apply {
        text = value; textSize = 14f
        setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground_secondary))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    })

    private fun addEmpty(message: Int) = content.addView(TextView(this).apply {
        setText(message); textSize = 15f; gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(this@StudyToolsActivity, R.color.answer_foreground_secondary))
        setPadding(dp(16), dp(32), dp(16), dp(32))
    })

    private fun open(mode: String) { startActivity(Intent(this, StudyToolsActivity::class.java).putExtra(EXTRA_MODE, mode)) }
    private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    companion object {
        const val EXTRA_MODE = "study_mode"
        const val MODE_PLAN = "plan"
        const val MODE_REVIEWS = "reviews"
        const val MODE_MISTAKES = "mistakes"
        private const val PREFS_STUDY = "study_plan_state"
        private const val KEY_REVIEWED_MISTAKES = "reviewed_mistake_ids"
        private const val MAX_ITEMS = 50
    }
}
