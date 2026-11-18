package com.aistudy.aistudy.game

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.GameResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

class GameActivity : AppCompatActivity() {
    private var userId = -1L
    private var round: GameRound? = null
    private var roundId = ""
    private var seed = 0
    private var feedbackVisible = false
    private var timer: CountDownTimer? = null
    private var saving = false
    private var saved = false
    private var exitDialog: androidx.appcompat.app.AlertDialog? = null
    private lateinit var subjects: ChipGroup
    private lateinit var modes: ChipGroup
    private lateinit var setup: View
    private lateinit var play: View
    private lateinit var result: View
    private lateinit var options: LinearLayout
    private lateinit var next: MaterialButton
    private lateinit var feedback: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Game)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_game)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.game_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE).getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) {
            finish()
            return
        }
        if (savedInstanceState != null && savedInstanceState.getLong("userId", userId) != userId) {
            finish()
            return
        }
        subjects = findViewById(R.id.game_subjects)
        modes = findViewById(R.id.game_modes)
        setup = findViewById(R.id.game_setup)
        play = findViewById(R.id.game_play)
        result = findViewById(R.id.game_result)
        options = findViewById(R.id.game_options)
        next = findViewById(R.id.game_next)
        feedback = findViewById(R.id.game_feedback)
        findViewById<View>(R.id.game_back).setOnClickListener { requestExit() }
        findViewById<View>(R.id.game_done).setOnClickListener { finish() }
        findViewById<View>(R.id.game_start).setOnClickListener { startRound() }
        findViewById<View>(R.id.game_retry).setOnClickListener { saveResult() }
        findViewById<View>(R.id.game_replay).setOnClickListener {
            round = null
            saved = false
            showSetup()
        }
        next.setOnClickListener {
            feedbackVisible = false
            val current = round ?: return@setOnClickListener
            if (current.expire(SystemClock.elapsedRealtime())) showResult() else bindQuestion()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestExit()
        })
        val ids = savedInstanceState?.getStringArrayList("questionIds")
        if (!ids.isNullOrEmpty()) {
            val questions = ids.mapNotNull { id -> GameQuestionBank.questions.find { it.id == id } }
            if (questions.size == ids.size) {
                seed = savedInstanceState.getInt("seed")
                roundId = savedInstanceState.getString("roundId") ?: UUID.randomUUID().toString()
                feedbackVisible = savedInstanceState.getBoolean("feedback")
                round = GameRound(questions, savedInstanceState.getBoolean("timed"),
                    savedInstanceState.getLong("deadline"), savedInstanceState.getIntArray("answers")?.toList().orEmpty(),
                    savedInstanceState.getBoolean("finished"))
                subjects.check(subjectIds.getValue(questions.first().subject))
                modes.check(if (round!!.timed) R.id.game_timed else R.id.game_untimed)
                if (round!!.expire(SystemClock.elapsedRealtime())) showResult() else bindQuestion()
                return
            }
        }
        showSetup()
    }

    private val subjectIds get() = mapOf("math" to R.id.game_math, "science" to R.id.game_science,
        "code" to R.id.game_code, "history" to R.id.game_history, "language" to R.id.game_language)

    private fun startRound() {
        val subject = subjectIds.entries.firstOrNull { it.value == subjects.checkedChipId }?.key ?: "math"
        seed = Random.nextInt()
        roundId = UUID.randomUUID().toString()
        val questions = GameQuestionBank.questions.filter { it.subject == subject }.shuffled(Random(seed)).take(10)
        round = GameRound(questions, modes.checkedChipId == R.id.game_timed, SystemClock.elapsedRealtime() + GameRound.DURATION_MS)
        feedbackVisible = false
        saved = false
        bindQuestion()
        startTimer()
    }

    private fun showSetup() {
        setup.isVisible = true
        play.isVisible = false
        result.isVisible = false
        scrollTop()
    }

    private fun bindQuestion() {
        val current = round ?: return
        if (current.finished) {
            showResult()
            return
        }
        setup.isVisible = false
        play.isVisible = true
        result.isVisible = false
        val index = current.answers.size - if (feedbackVisible) 1 else 0
        val question = current.questions[index]
        text(R.id.game_question_number).text = getString(R.string.game_progress, index + 1, current.questions.size)
        text(R.id.game_question).text = question.prompt
        text(R.id.game_points).text = getString(R.string.game_score_combo, current.score, current.combo)
        text(R.id.game_clock).setText(R.string.game_untimed)
        findViewById<View>(R.id.game_time_bar).isVisible = current.timed
        updateClock()
        options.removeAllViews()
        question.options.indices.shuffled(Random(seed xor question.id.hashCode())).forEach { option ->
            val button = (layoutInflater.inflate(R.layout.item_game_option, options, false) as MaterialButton).apply {
                text = question.options[option]
                backgroundTintList = ColorStateList.valueOf(color(R.color.answer_surface))
                setTextColor(color(R.color.answer_foreground))
                tag = option
                setOnClickListener { submit(index, option) }
            }
            options.addView(button)
        }
        feedback.isVisible = false
        next.isVisible = false
        if (feedbackVisible) showFeedback(index, 0, animate = false)
        scrollTop()
        text(R.id.game_question).sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    private fun submit(index: Int, option: Int) {
        val current = round ?: return
        if (feedbackVisible) return
        val previousScore = current.score
        if (!current.submit(index, option, SystemClock.elapsedRealtime())) {
            if (current.finished) showResult()
            return
        }
        feedbackVisible = true
        if (current.finished) showResult() else showFeedback(index, current.score - previousScore)
    }

    private fun showFeedback(index: Int, delta: Int, animate: Boolean = true) {
        val current = round ?: return
        val question = current.questions[index]
        val selected = current.answers[index]
        val correct = selected == question.correctIndex
        for (i in 0 until options.childCount) {
            val button = options.getChildAt(i) as MaterialButton
            val option = button.tag as Int
            button.isEnabled = false
            if (option == question.correctIndex) {
                button.backgroundTintList = ColorStateList.valueOf(color(R.color.answer_result_surface))
                button.strokeColor = ColorStateList.valueOf(color(R.color.answer_primary))
            } else if (option == selected) {
                button.backgroundTintList = ColorStateList.valueOf(color(R.color.answer_error_surface))
                button.strokeColor = ColorStateList.valueOf(color(R.color.answer_error))
            }
            // Explicit feedback text carries correctness as well as the tonal option treatment.
            button.setTextColor(color(R.color.answer_foreground))
        }
        val earned = if (delta > 0) delta else if (correct) 100 + (minOf(current.combo, 5) - 1) * 25 else 0
        val heading = if (correct) getString(R.string.game_correct, earned) else getString(R.string.game_wrong)
        feedback.text = getString(R.string.game_feedback_details, heading,
            getString(R.string.game_answer, question.options[question.correctIndex]), question.explanation)
        feedback.isVisible = true
        next.isVisible = true
        text(R.id.game_points).text = getString(R.string.game_score_combo, current.score, current.combo)
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            feedback.alpha = 0f
            feedback.animate().alpha(1f).setDuration(120).start()
        }
    }

    private fun updateClock() {
        val current = round ?: return
        if (!current.timed) return
        val seconds = ((current.remainingMs(SystemClock.elapsedRealtime()) + 999L) / 1000L).toInt()
        text(R.id.game_clock).text = getString(R.string.game_seconds, seconds)
        text(R.id.game_clock).setTextColor(color(if (seconds <= 10) R.color.answer_error else R.color.answer_primary))
        findViewById<ProgressBar>(R.id.game_time_bar).progress = seconds
    }

    private fun startTimer() {
        timer?.cancel()
        val current = round ?: return
        if (current.finished) return
        if (current.expire(SystemClock.elapsedRealtime())) {
            showResult()
            return
        }
        if (!current.timed) return
        timer = object : CountDownTimer(current.remainingMs(SystemClock.elapsedRealtime()), 200) {
            override fun onTick(millisUntilFinished: Long) { updateClock() }
            override fun onFinish() {
                if (current.expire(SystemClock.elapsedRealtime())) showResult() else startTimer()
            }
        }.start()
    }

    private fun showResult() {
        val current = round ?: return
        timer?.cancel()
        exitDialog?.dismiss()
        setup.isVisible = false
        play.isVisible = false
        result.isVisible = true
        text(R.id.game_result_title).setText(if (current.timed && current.answers.size < current.questions.size) R.string.game_time_up else R.string.game_result)
        text(R.id.game_result_score).text = getString(R.string.game_result_score, current.score)
        text(R.id.game_result_summary).text = getString(R.string.game_result_summary, current.correctCount, current.answers.size, current.bestCombo)
        val reviews = findViewById<LinearLayout>(R.id.game_reviews)
        reviews.removeAllViews()
        if (current.answers.isEmpty()) reviews.addView(reviewText(getString(R.string.game_no_answers)))
        current.answers.forEachIndexed { i, answer ->
            val question = current.questions[i]
            val correct = answer == question.correctIndex
            val title = getString(if (correct) R.string.game_review_correct else R.string.game_review_wrong, i + 1, question.prompt)
            reviews.addView(reviewText(title, heading = true))
            reviews.addView(reviewText("${getString(R.string.game_your_answer, question.options[answer])}\n${getString(R.string.game_answer, question.options[question.correctIndex])}\n\n${question.explanation}"))
        }
        scrollTop()
        saveResult()
    }

    private fun reviewText(value: String, heading: Boolean = false) = TextView(this).apply {
        text = value
        setTextAppearance(if (heading) R.style.TextAppearance_AIStudy_Answer_StepTitle else R.style.TextAppearance_AIStudy_Answer_Body)
        isAccessibilityHeading = heading
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (heading) 24 else 8) }
    }

    private fun saveResult() {
        val current = round ?: return
        if (saving || saved || !current.finished) return
        saving = true
        text(R.id.game_save_status).setText(R.string.game_saving)
        text(R.id.game_best).text = ""
        findViewById<View>(R.id.game_retry).isVisible = false
        findViewById<View>(R.id.game_replay).isEnabled = false
        findViewById<View>(R.id.game_done).isEnabled = false
        lifecycleScope.launch {
            try {
                val (persisted, best) = withContext(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(applicationContext)
                    val input = GameResult(roundId, userId, current.questions.first().subject, current.timed,
                        current.score, current.correctCount, current.answers.size, current.bestCombo, 0, System.currentTimeMillis())
                    val persisted = GameResultRepository(db).save(input)
                    persisted to db.gameResultDao().bestScore(userId, input.subject, input.timed)
                }
                saved = true
                text(R.id.game_save_status).text = getString(R.string.game_xp, persisted.xpAwarded)
                text(R.id.game_best).text = getString(R.string.game_best, best)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                text(R.id.game_save_status).setText(R.string.game_save_error)
                findViewById<View>(R.id.game_retry).isVisible = true
            } finally {
                saving = false
                findViewById<View>(R.id.game_replay).isEnabled = saved
                findViewById<View>(R.id.game_done).isEnabled = true
            }
        }
    }

    private fun requestExit() {
        val current = round
        if (current == null || current.finished) {
            finish()
            return
        }
        if (exitDialog?.isShowing == true) return
        exitDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.game_exit_title).setMessage(R.string.game_exit_body)
            .setNegativeButton(R.string.game_stay, null)
            .setPositiveButton(R.string.game_exit) { _, _ -> finish() }.show()
    }

    override fun onStart() {
        super.onStart()
        startTimer()
    }

    override fun onStop() {
        timer?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        timer?.cancel()
        exitDialog?.dismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("userId", userId)
        round?.let { current ->
            outState.putStringArrayList("questionIds", ArrayList(current.questions.map { it.id }))
            outState.putIntArray("answers", current.answers.toIntArray())
            outState.putBoolean("timed", current.timed)
            outState.putLong("deadline", current.deadlineMs)
            outState.putBoolean("finished", current.finished)
            outState.putBoolean("feedback", feedbackVisible)
            outState.putString("roundId", roundId)
            outState.putInt("seed", seed)
        }
        super.onSaveInstanceState(outState)
    }

    private fun text(id: Int): TextView = findViewById(id)
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun scrollTop() { findViewById<ScrollView>(R.id.game_scroll).scrollTo(0, 0) }
}
