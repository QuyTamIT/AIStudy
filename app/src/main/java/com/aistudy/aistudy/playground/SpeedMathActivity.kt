package com.aistudy.aistudy.playground

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpeedMathActivity : AppCompatActivity() {

    companion object {
        const val PREFS_PLAYGROUND = "playground_prefs"
        const val KEY_SPEED_BEST_PREFIX = "speed_best_score_"
    }

    private val engine = SpeedMathEngine()
    private var countDownTimer: CountDownTimer? = null

    private var userId: Long = -1L
    private var currentQuestion: MathQuestion? = null
    private var currentScore = 0
    private var currentCombo = 0
    private var maxCombo = 0
    private var correctCount = 0
    private var totalAnswered = 0
    private var isAnsweringLocked = false
    private var isGameActive = false

    private lateinit var tvScore: TextView
    private lateinit var tvComboBadge: TextView
    private lateinit var progressTimer: ProgressBar
    private lateinit var layoutGameContent: View
    private lateinit var tvCategory: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var tvFeedbackHint: TextView
    private lateinit var tvTimeLeft: TextView

    private val optionButtons = mutableListOf<MaterialButton>()

    // Result Overlay views
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultXp: TextView
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultCorrect: TextView
    private lateinit var tvResultMaxCombo: TextView
    private lateinit var btnPlayAgain: MaterialButton
    private lateinit var btnDone: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Study)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_speed_math)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)

        initViews()
        setupListeners()
        startRound()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isGameActive) {
                    showQuitConfirmation()
                } else {
                    finish()
                }
            }
        })
    }

    private fun initViews() {
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            if (isGameActive) {
                showQuitConfirmation()
            } else {
                finish()
            }
        }

        tvScore = findViewById(R.id.tv_score)
        tvComboBadge = findViewById(R.id.tv_combo_badge)
        progressTimer = findViewById(R.id.progress_timer)
        layoutGameContent = findViewById(R.id.layout_game_content)
        tvCategory = findViewById(R.id.tv_category)
        tvQuestion = findViewById(R.id.tv_question)
        tvFeedbackHint = findViewById(R.id.tv_feedback_hint)
        tvTimeLeft = findViewById(R.id.tv_time_left)

        optionButtons.clear()
        optionButtons.add(findViewById(R.id.btn_option_0))
        optionButtons.add(findViewById(R.id.btn_option_1))
        optionButtons.add(findViewById(R.id.btn_option_2))
        optionButtons.add(findViewById(R.id.btn_option_3))

        layoutResult = findViewById(R.id.layout_result)
        tvResultTitle = findViewById(R.id.tv_result_title)
        tvResultXp = findViewById(R.id.tv_result_xp)
        tvResultScore = findViewById(R.id.tv_result_score)
        tvResultCorrect = findViewById(R.id.tv_result_correct)
        tvResultMaxCombo = findViewById(R.id.tv_result_max_combo)
        btnPlayAgain = findViewById(R.id.btn_play_again)
        btnDone = findViewById(R.id.btn_done)
    }

    private fun setupListeners() {
        for (i in optionButtons.indices) {
            optionButtons[i].setOnClickListener {
                if (!isAnsweringLocked && isGameActive) {
                    handleOptionSelected(i)
                }
            }
        }

        btnPlayAgain.setOnClickListener {
            startRound()
        }

        btnDone.setOnClickListener {
            finish()
        }
    }

    private fun startRound() {
        countDownTimer?.cancel()

        currentScore = 0
        currentCombo = 0
        maxCombo = 0
        correctCount = 0
        totalAnswered = 0
        isAnsweringLocked = false
        isGameActive = true

        layoutResult.visibility = View.GONE
        layoutGameContent.visibility = View.VISIBLE
        tvFeedbackHint.visibility = View.INVISIBLE

        updateScoreAndComboUI()
        loadNextQuestion()

        progressTimer.max = SpeedMathEngine.GAME_DURATION_SECONDS
        progressTimer.progress = SpeedMathEngine.GAME_DURATION_SECONDS

        countDownTimer = object : CountDownTimer(SpeedMathEngine.GAME_DURATION_SECONDS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                progressTimer.progress = secondsRemaining
                tvTimeLeft.text = getString(R.string.game_speed_math_time_left, secondsRemaining)
            }

            override fun onFinish() {
                progressTimer.progress = 0
                tvTimeLeft.text = getString(R.string.game_speed_math_time_left, 0)
                endRound()
            }
        }.start()
    }

    private fun loadNextQuestion() {
        currentQuestion = engine.generateQuestion()
        val q = currentQuestion ?: return

        tvCategory.text = q.category
        tvQuestion.text = q.prompt

        for (i in optionButtons.indices) {
            val btn = optionButtons[i]
            btn.text = q.options[i]
            btn.setBackgroundResource(R.drawable.bg_playground_choice)
            btn.setTextColor(ContextCompat.getColor(this, R.color.answer_foreground))
            btn.isEnabled = true
        }

        isAnsweringLocked = false
    }

    private fun handleOptionSelected(index: Int) {
        val q = currentQuestion ?: return
        isAnsweringLocked = true
        totalAnswered++

        val isCorrect = engine.evaluateAnswer(q, index)
        val selectedBtn = optionButtons[index]

        if (isCorrect) {
            correctCount++
            val updated = engine.updateScoreAndCombo(currentScore, currentCombo, true)
            currentScore = updated.first
            currentCombo = updated.second
            if (currentCombo > maxCombo) {
                maxCombo = currentCombo
            }

            selectedBtn.setBackgroundResource(R.drawable.bg_playground_choice_correct)
            val mult = engine.calculateComboMultiplier(currentCombo - 1)
            val gained = SpeedMathEngine.BASE_POINTS_PER_CORRECT * mult
            tvFeedbackHint.text = "+$gained"
            tvFeedbackHint.setTextColor(ContextCompat.getColor(this, R.color.answer_primary))
            tvFeedbackHint.visibility = View.VISIBLE
        } else {
            val updated = engine.updateScoreAndCombo(currentScore, currentCombo, false)
            currentScore = updated.first
            currentCombo = updated.second

            selectedBtn.setBackgroundResource(R.drawable.bg_playground_choice_wrong)
            optionButtons[q.correctIndex].setBackgroundResource(R.drawable.bg_playground_choice_correct)

            tvFeedbackHint.text = "Incorrect! ${q.explanation}"
            tvFeedbackHint.setTextColor(ContextCompat.getColor(this, R.color.answer_error))
            tvFeedbackHint.visibility = View.VISIBLE
        }

        updateScoreAndComboUI()

        // Short pause to view feedback, then next question
        selectedBtn.postDelayed({
            if (isGameActive) {
                tvFeedbackHint.visibility = View.INVISIBLE
                loadNextQuestion()
            }
        }, 320)
    }

    private fun updateScoreAndComboUI() {
        tvScore.text = "$currentScore points"
        if (currentCombo >= 2) {
            tvComboBadge.visibility = View.VISIBLE
            tvComboBadge.text = getString(R.string.game_speed_math_combo, currentCombo)
        } else {
            tvComboBadge.visibility = View.GONE
        }
    }

    private fun endRound() {
        isGameActive = false
        countDownTimer?.cancel()

        val result = engine.calculateResult(
            score = currentScore,
            correctCount = correctCount,
            totalAnswered = totalAnswered,
            maxCombo = maxCombo
        )

        // Save High Score
        if (userId != -1L) {
            val prefs = getSharedPreferences(PREFS_PLAYGROUND, Context.MODE_PRIVATE)
            val key = KEY_SPEED_BEST_PREFIX + userId
            val best = prefs.getInt(key, 0)
            if (result.score > best) {
                prefs.edit().putInt(key, result.score).apply()
            }

            // Record XP and daily active streak in Room DB
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val profile = db.profileDao().getByUserId(userId)
                if (profile != null) {
                    XpRules.recordDailyActivity(profile, db.profileDao())
                    XpRules.addXp(profile, db.profileDao(), result.xpEarned)
                }
            }
        }

        // Show Results Overlay
        layoutGameContent.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE

        tvResultTitle.text = if (result.correctCount >= 10) {
            getString(R.string.game_result_congrats)
        } else {
            getString(R.string.game_result_game_over)
        }

        tvResultXp.text = getString(R.string.game_result_xp_earned, result.xpEarned)
        tvResultScore.text = getString(R.string.game_result_score_label, result.score)
        tvResultCorrect.text = getString(R.string.game_result_correct_label, result.correctCount, result.totalAnswered)
        tvResultMaxCombo.text = getString(R.string.game_result_max_combo, result.maxCombo)
    }

    private fun showQuitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.game_quit_confirm_title)
            .setMessage(R.string.game_quit_confirm_message)
            .setPositiveButton(R.string.game_quit_exit) { _, _ ->
                isGameActive = false
                countDownTimer?.cancel()
                finish()
            }
            .setNegativeButton(R.string.game_quit_stay, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        countDownTimer = null
    }
}

