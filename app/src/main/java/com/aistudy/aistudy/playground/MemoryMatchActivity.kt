package com.aistudy.aistudy.playground

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MemoryMatchActivity : AppCompatActivity() {

    companion object {
        const val KEY_MEMORY_BEST_PREFIX = "memory_best_moves_"
    }

    private val engine = MemoryMatchEngine()
    private lateinit var adapter: MemoryCardAdapter
    private val handler = Handler(Looper.getMainLooper())

    private var userId: Long = -1L
    private var firstFlippedIndex: Int? = null
    private var isBusyEvaluating = false
    private var isGameActive = false
    private var secondsElapsed = 0

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isGameActive) {
                secondsElapsed++
                tvTimer.text = engine.formatTime(secondsElapsed)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private lateinit var tvTimer: TextView
    private lateinit var tvMoves: TextView
    private lateinit var tvMatchedPairs: TextView
    private lateinit var rvCards: RecyclerView

    // Result Overlay views
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvStars: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultXp: TextView
    private lateinit var tvResultMoves: TextView
    private lateinit var tvResultTime: TextView
    private lateinit var btnPlayAgain: MaterialButton
    private lateinit var btnDone: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Study)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_memory_match)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)

        initViews()
        setupListeners()
        startNewGame()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isGameActive && !engine.isGameOver()) {
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
            if (isGameActive && !engine.isGameOver()) {
                showQuitConfirmation()
            } else {
                finish()
            }
        }

        tvTimer = findViewById(R.id.tv_timer)
        tvMoves = findViewById(R.id.tv_moves)
        tvMatchedPairs = findViewById(R.id.tv_matched_pairs)
        rvCards = findViewById(R.id.rv_cards)

        layoutResult = findViewById(R.id.layout_result)
        tvStars = findViewById(R.id.tv_stars)
        tvResultTitle = findViewById(R.id.tv_result_title)
        tvResultXp = findViewById(R.id.tv_result_xp)
        tvResultMoves = findViewById(R.id.tv_result_moves)
        tvResultTime = findViewById(R.id.tv_result_time)
        btnPlayAgain = findViewById(R.id.btn_play_again)
        btnDone = findViewById(R.id.btn_done)

        rvCards.layoutManager = GridLayoutManager(this, 3)
        adapter = MemoryCardAdapter(emptyList()) { position ->
            handleCardClick(position)
        }
        rvCards.adapter = adapter
    }

    private fun setupListeners() {
        btnPlayAgain.setOnClickListener {
            startNewGame()
        }

        btnDone.setOnClickListener {
            finish()
        }
    }

    private fun startNewGame() {
        handler.removeCallbacks(timerRunnable)
        secondsElapsed = 0
        firstFlippedIndex = null
        isBusyEvaluating = false
        isGameActive = true

        layoutResult.visibility = View.GONE
        rvCards.visibility = View.VISIBLE

        val cards = engine.initGame(MemoryMatchEngine.DEFAULT_PAIRS_COUNT)
        adapter.updateCards(cards)

        updateStatsUI()
        tvTimer.text = "00:00"
        handler.postDelayed(timerRunnable, 1000L)
    }

    private fun handleCardClick(position: Int) {
        if (!isGameActive || isBusyEvaluating) return
        val card = engine.getCard(position) ?: return
        if (card.isFlipped || card.isMatched) return

        card.isFlipped = true
        adapter.notifyItemChanged(position)

        val first = firstFlippedIndex
        if (first == null) {
            // First card flipped
            firstFlippedIndex = position
        } else {
            // Second card flipped
            engine.incrementMoves()
            updateStatsUI()

            val isMatch = engine.checkMatch(first, position)
            firstFlippedIndex = null

            if (isMatch) {
                adapter.notifyItemChanged(first)
                adapter.notifyItemChanged(position)
                updateStatsUI()

                if (engine.isGameOver()) {
                    onVictory()
                }
            } else {
                isBusyEvaluating = true
                handler.postDelayed({
                    val c1 = engine.getCard(first)
                    val c2 = engine.getCard(position)
                    c1?.isFlipped = false
                    c2?.isFlipped = false
                    adapter.notifyItemChanged(first)
                    adapter.notifyItemChanged(position)
                    isBusyEvaluating = false
                }, 850L)
            }
        }
    }

    private fun updateStatsUI() {
        tvMoves.text = getString(R.string.game_memory_moves, engine.getMoves())
        tvMatchedPairs.text = getString(R.string.game_memory_pairs_matched, engine.getMatchedPairsCount(), engine.getTotalPairs())
    }

    private fun onVictory() {
        isGameActive = false
        handler.removeCallbacks(timerRunnable)

        val result = engine.calculateResult(secondsElapsed)

        // Save Lowest Moves (Best score)
        if (userId != -1L) {
            val prefs = getSharedPreferences(SpeedMathActivity.PREFS_PLAYGROUND, Context.MODE_PRIVATE)
            val key = KEY_MEMORY_BEST_PREFIX + userId
            val best = prefs.getInt(key, 0)
            if (best == 0 || result.moves < best) {
                prefs.edit().putInt(key, result.moves).apply()
            }

            // Record XP and streak in Room DB
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val profile = db.profileDao().getByUserId(userId)
                if (profile != null) {
                    XpRules.recordDailyActivity(profile, db.profileDao())
                    XpRules.addXp(profile, db.profileDao(), result.xpEarned)
                }
            }
        }

        // Show Victory Overlay
        rvCards.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE

        tvStars.text = when (result.stars) {
            3 -> "⭐⭐⭐"
            2 -> "⭐⭐"
            else -> "⭐"
        }

        tvResultTitle.text = getString(R.string.game_result_victory)
        tvResultXp.text = getString(R.string.game_result_xp_earned, result.xpEarned)
        tvResultMoves.text = getString(R.string.game_result_moves_label, result.moves)
        tvResultTime.text = getString(R.string.game_result_time_label, engine.formatTime(result.timeSeconds))
    }

    private fun showQuitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.game_quit_confirm_title)
            .setMessage(R.string.game_quit_confirm_message)
            .setPositiveButton(R.string.game_quit_exit) { _, _ ->
                isGameActive = false
                handler.removeCallbacks(timerRunnable)
                finish()
            }
            .setNegativeButton(R.string.game_quit_stay, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isGameActive = false
        handler.removeCallbacks(timerRunnable)
    }
}

