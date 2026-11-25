package com.aistudy.aistudy.playground

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaygroundFragment : Fragment() {

    private lateinit var tvLevelBadge: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvSpeedBestScore: TextView
    private lateinit var tvMemoryBestMoves: TextView
    private lateinit var btnPlaySpeedMath: MaterialButton
    private lateinit var btnPlayMemory: MaterialButton

    private var currentUserId = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playground, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvLevelBadge = view.findViewById(R.id.tv_level_badge)
        tvUserName = view.findViewById(R.id.tv_user_name)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvStreak = view.findViewById(R.id.tv_streak)
        tvSpeedBestScore = view.findViewById(R.id.tv_speed_best_score)
        tvMemoryBestMoves = view.findViewById(R.id.tv_memory_best_moves)
        btnPlaySpeedMath = view.findViewById(R.id.btn_play_speed_math)
        btnPlayMemory = view.findViewById(R.id.btn_play_memory)

        btnPlaySpeedMath.setOnClickListener {
            val intent = Intent(requireContext(), SpeedMathActivity::class.java)
            startActivity(intent)
        }

        btnPlayMemory.setOnClickListener {
            val intent = Intent(requireContext(), MemoryMatchActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val ctx = context ?: return
        val userId = ctx.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) return
        currentUserId = userId

        // Read Local Best Scores
        val prefs = ctx.getSharedPreferences(SpeedMathActivity.PREFS_PLAYGROUND, Context.MODE_PRIVATE)
        val bestSpeed = prefs.getInt(SpeedMathActivity.KEY_SPEED_BEST_PREFIX + userId, 0)
        val bestMemory = prefs.getInt(MemoryMatchActivity.KEY_MEMORY_BEST_PREFIX + userId, 0)

        tvSpeedBestScore.text = getString(R.string.game_speed_math_best_score, bestSpeed)
        tvMemoryBestMoves.text = if (bestMemory > 0) {
            getString(R.string.game_memory_best_moves, bestMemory)
        } else {
            getString(R.string.playground_no_best)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val (user, profile) = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(ctx.applicationContext)
                val u = db.userDao().getById(userId)
                val p = db.profileDao().getByUserId(userId)
                Pair(u, p)
            }

            if (!isAdded || view == null) return@launch

            val xp = profile?.xp ?: 0
            val streak = profile?.streak ?: 0
            val level = XpRules.levelFromXp(xp)
            val name = user?.fullName?.ifBlank { getString(R.string.profile_student_fallback) } ?: getString(R.string.profile_student_fallback)

            tvLevelBadge.text = level.toString()
            tvUserName.text = name
            tvXpTotal.text = getString(R.string.playground_total_xp, xp)
            tvStreak.text = getString(R.string.home_streak_days, streak)
        }
    }
}

