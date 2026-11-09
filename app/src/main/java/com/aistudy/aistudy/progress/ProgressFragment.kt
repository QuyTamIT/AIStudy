package com.aistudy.aistudy.progress

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Friend
import com.aistudy.aistudy.database.FriendDao
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.notify.NotificationScheduler
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
        private const val MASTERY_MIN = 5
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val WINDOW_MS = 30L * DAY_MS
        private val SUBJECTS = arrayOf(
            "math", "science", "code", "history", "language"
        )
    }

    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvQuizAccuracy: TextView
    private lateinit var tvReviewTime: TextView
    private lateinit var tvInsight1: TextView
    private lateinit var tvInsight2: TextView
    private lateinit var tvInsightsEmpty: TextView
    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var rowAvatars: LinearLayout
    private lateinit var rowThemes: LinearLayout
    private lateinit var layoutLeaderboard: LinearLayout

    private lateinit var rowMath: LinearLayout
    private lateinit var rowScience: LinearLayout
    private lateinit var rowCode: LinearLayout
    private lateinit var rowHistory: LinearLayout
    private lateinit var rowLanguage: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView
    private lateinit var barMath: ProgressBar
    private lateinit var barScience: ProgressBar
    private lateinit var barCode: ProgressBar
    private lateinit var barHistory: ProgressBar
    private lateinit var barLanguage: ProgressBar
    private lateinit var tvSubjectsEmpty: TextView

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeStars: LinearLayout
    private lateinit var badgeStreak: LinearLayout
    private lateinit var badgePerfect: LinearLayout
    private lateinit var badgeMastery: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var currentUserId = -1L
    private var reviewSubject: String? = null
    private var currentProfile: Profile? = null
    private var currentLevel = 1

    private data class ProgressSnapshot(
        val email: String,
        val fullName: String?,
        val profile: Profile?,
        val level: Int,
        val xp: Int,
        val streak: Int,
        val xpPct: Int,
        val totalQuestions: Int,
        val starredCount: Int,
        val attempted: Int,
        val correct: Int,
        val reviewSeconds: Int,
        val topSubject: String?,
        val topSubjectCount: Int,
        val improvedSubject: String?,
        val friends: List<Friend>,
        val countMath: Int,
        val countScience: Int,
        val countCode: Int,
        val countHistory: Int,
        val countLanguage: Int,
        val perfectCount: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)
        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvQuizAccuracy = view.findViewById(R.id.tv_quiz_accuracy)
        tvReviewTime = view.findViewById(R.id.tv_review_time)
        tvInsight1 = view.findViewById(R.id.tv_insight_1)
        tvInsight2 = view.findViewById(R.id.tv_insight_2)
        tvInsightsEmpty = view.findViewById(R.id.tv_insights_empty)
        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        rowAvatars = view.findViewById(R.id.row_avatars)
        rowThemes = view.findViewById(R.id.row_themes)
        layoutLeaderboard = view.findViewById(R.id.layout_leaderboard)

        rowMath = view.findViewById(R.id.row_math)
        rowScience = view.findViewById(R.id.row_science)
        rowCode = view.findViewById(R.id.row_code)
        rowHistory = view.findViewById(R.id.row_history)
        rowLanguage = view.findViewById(R.id.row_language)
        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
        barMath = view.findViewById(R.id.bar_math)
        barScience = view.findViewById(R.id.bar_science)
        barCode = view.findViewById(R.id.bar_code)
        barHistory = view.findViewById(R.id.bar_history)
        barLanguage = view.findViewById(R.id.bar_language)
        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeStars = view.findViewById(R.id.badge_stars)
        badgeStreak = view.findViewById(R.id.badge_streak)
        badgePerfect = view.findViewById(R.id.badge_perfect)
        badgeMastery = view.findViewById(R.id.badge_mastery)
        badgeScholar = view.findViewById(R.id.badge_scholar)

        btnLogout.setOnClickListener { showLogoutConfirmation() }
        view.findViewById<View>(R.id.tv_progress_title).setOnLongClickListener {
            NotificationScheduler.enqueueNow(requireContext())
            Toast.makeText(requireContext(), R.string.progress_force_notify, Toast.LENGTH_SHORT).show()
            true
        }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun showLogoutConfirmation() {
        val ctx = context ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.logout_dialog_title)
            .setMessage(R.string.logout_dialog_message)
            .setPositiveButton(R.string.logout_confirm) { _, _ -> performLogout() }
            .setNegativeButton(R.string.logout_cancel, null)
            .show()
    }

    private fun performLogout() {
        val ctx = context ?: return
        ctx.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()

        val intent = Intent(ctx, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        activity?.finish()
    }

    private fun loadProgress() {
        val ctx = context ?: return
        val userId = ctx
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) {
            return
        }
        currentUserId = userId

        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(ctx.applicationContext)
                val user = db.userDao().getById(userId)
                val profileDao = db.profileDao()
                val profile = profileDao.getByUserId(userId)
                if (profile != null) {
                    XpRules.ensureWeek(profile)
                    if (profile.avatarId.isNullOrEmpty()) {
                        profile.avatarId = UnlockCatalog.AVATAR_FOX
                    }
                    if (profile.themeId.isNullOrEmpty()) {
                        profile.themeId = UnlockCatalog.THEME_DEFAULT
                    }
                    profileDao.update(profile)
                }

                val qaDao = db.questionAnswerDao()
                val friendDao = db.friendDao()
                val quizItemDao = db.quizItemDao()
                val sessionDao = db.quizSessionDao()

                FriendSeeder.ensureSeeded(friendDao, userId)

                val xp = profile?.xp ?: 0
                val streak = profile?.streak ?: 0
                val level = XpRules.levelFromXp(xp)
                val xpPct = XpRules.progressPercentToNext(xp)

                val countQuestions = qaDao.countByUserId(userId)
                val starredCount = qaDao.countStarredByUserId(userId)
                val attempted = quizItemDao.countAttemptedByUserId(userId)
                val correct = quizItemDao.countCorrectByUserId(userId)
                val reviewSeconds = qaDao.sumReviewedSecondsByUserId(userId)

                val topSubject = qaDao.getTopSubjectByUserId(userId)
                val topSubjectCount = if (topSubject != null) qaDao.countBySubject(userId, topSubject) else 0
                val improved = findImprovedSubject(quizItemDao, userId)

                val friendsList = friendDao.getByOwner(userId)
                val countMath = qaDao.countBySubject(userId, "math")
                val countScience = qaDao.countBySubject(userId, "science")
                val countCode = qaDao.countBySubject(userId, "code")
                val countHistory = qaDao.countBySubject(userId, "history")
                val countLanguage = qaDao.countBySubject(userId, "language")
                val perfect = sessionDao.countPerfectByUserId(userId)

                ProgressSnapshot(
                    email = user?.email.orEmpty(),
                    fullName = user?.fullName,
                    profile = profile,
                    level = level,
                    xp = xp,
                    streak = streak,
                    xpPct = xpPct,
                    totalQuestions = countQuestions,
                    starredCount = starredCount,
                    attempted = attempted,
                    correct = correct,
                    reviewSeconds = reviewSeconds,
                    topSubject = topSubject,
                    topSubjectCount = topSubjectCount,
                    improvedSubject = improved,
                    friends = friendsList,
                    countMath = countMath,
                    countScience = countScience,
                    countCode = countCode,
                    countHistory = countHistory,
                    countLanguage = countLanguage,
                    perfectCount = perfect
                )
            }

            if (!isAdded || view == null) return@launch
            bindProgress(snapshot)
        }
    }

    private fun bindProgress(data: ProgressSnapshot) {
        currentProfile = data.profile
        currentLevel = data.level

        tvUserEmail.text = data.email
        tvXpTotal.text = data.xp.toString()
        tvLevel.text = getString(R.string.progress_level_scholar, data.level)
        tvXpPercent.text = getString(R.string.progress_xp_percent, data.xpPct)
        progressXp.progress = data.xpPct
        tvStatStreak.text = getString(R.string.progress_streak_value, data.streak)
        tvStatQuestions.text = data.totalQuestions.toString()
        tvStatStars.text = data.starredCount.toString()

        if (data.attempted > 0) {
            val accuracyPct = ((data.correct * 100f) / data.attempted).roundToInt()
            tvQuizAccuracy.text = getString(
                R.string.progress_quiz_accuracy_value, accuracyPct, data.correct, data.attempted
            )
        } else {
            tvQuizAccuracy.setText(R.string.progress_quiz_accuracy_empty)
        }

        tvReviewTime.text = formatReviewTime(data.reviewSeconds)

        bindInsights(data.topSubject, data.topSubjectCount, data.improvedSubject)
        bindReviewSuggest(data.topSubject, data.topSubjectCount)
        bindAvatars()
        bindThemes()
        bindLeaderboard(data.fullName, data.profile?.weeklyXp ?: 0, data.friends)

        val subjectTotal = data.countMath + data.countScience + data.countCode + data.countHistory + data.countLanguage
        val barDenom = if (subjectTotal > 0) subjectTotal else 1

        bindSubjectRow(rowMath, tvCountMath, barMath, data.countMath, barDenom)
        bindSubjectRow(rowScience, tvCountScience, barScience, data.countScience, barDenom)
        bindSubjectRow(rowCode, tvCountCode, barCode, data.countCode, barDenom)
        bindSubjectRow(rowHistory, tvCountHistory, barHistory, data.countHistory, barDenom)
        bindSubjectRow(rowLanguage, tvCountLanguage, barLanguage, data.countLanguage, barDenom)
        tvSubjectsEmpty.visibility = if (subjectTotal == 0) View.VISIBLE else View.GONE

        val maxSubjectCount = maxOf(data.countMath, data.countScience, data.countCode, data.countHistory, data.countLanguage)
        setBadge(badgeStarter, data.totalQuestions >= 1)
        setBadge(badgeStars, data.starredCount >= 5)
        setBadge(badgeStreak, data.streak >= 3)
        setBadge(badgePerfect, data.perfectCount >= 1)
        setBadge(badgeMastery, maxSubjectCount >= MASTERY_MIN)
        setBadge(badgeScholar, data.xp >= 1000)

        applyThemeBackground()
    }

    private fun bindAvatars() {
        rowAvatars.removeAllViews()
        val profile = currentProfile
        if (context == null || profile == null) {
            return
        }
        val selected = UnlockCatalog.normalizeAvatarId(profile.avatarId)
        UnlockCatalog.AVATARS.forEachIndexed { i, avatarId ->
            val unlocked = UnlockCatalog.isAvatarUnlocked(avatarId, currentLevel)
            val isSelected = avatarId == selected
            val chip = buildChip(
                UnlockCatalog.emojiForAvatar(avatarId),
                unlocked,
                isSelected,
                UnlockCatalog.requiredLevelForAvatar(avatarId),
                i > 0
            )
            chip.setOnClickListener { onAvatarClicked(avatarId) }
            rowAvatars.addView(chip)
        }
    }

    private fun bindThemes() {
        rowThemes.removeAllViews()
        val profile = currentProfile
        if (context == null || profile == null) {
            return
        }
        val selected = UnlockCatalog.normalizeThemeId(profile.themeId)
        UnlockCatalog.THEMES.forEachIndexed { i, themeId ->
            val unlocked = UnlockCatalog.isThemeUnlocked(themeId, currentLevel)
            val isSelected = themeId == selected
            val chip = buildChip(
                UnlockCatalog.labelForTheme(themeId),
                unlocked,
                isSelected,
                UnlockCatalog.requiredLevelForTheme(themeId),
                i > 0
            )
            chip.setOnClickListener { onThemeClicked(themeId) }
            rowThemes.addView(chip)
        }
    }

    private fun buildChip(
        label: String,
        unlocked: Boolean,
        selected: Boolean,
        requiredLevel: Int,
        withStartMargin: Boolean
    ): TextView {
        val chip = TextView(requireContext())
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (withStartMargin) {
            lp.marginStart = dp(8)
        }
        chip.layoutParams = lp
        chip.gravity = Gravity.CENTER
        chip.setPadding(dp(10), dp(12), dp(10), dp(12))
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        if (!unlocked) {
            chip.text = getString(R.string.progress_unlock_locked, requiredLevel)
            chip.alpha = 0.45f
            chip.setBackgroundResource(R.drawable.bg_unlock_chip)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_muted_foreground))
        } else {
            chip.text = label
            chip.alpha = 1f
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_unlock_chip_selected else R.drawable.bg_unlock_chip
            )
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_foreground))
        }
        return chip
    }

    private fun onAvatarClicked(avatarId: String) {
        val profile = currentProfile ?: return
        val ctx = context ?: return
        if (!UnlockCatalog.isAvatarUnlocked(avatarId, currentLevel)) {
            Toast.makeText(
                ctx,
                getString(
                    R.string.progress_unlock_toast,
                    UnlockCatalog.requiredLevelForAvatar(avatarId)
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        profile.avatarId = avatarId
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(ctx.applicationContext).profileDao().update(profile)
            }
            if (!isAdded) return@launch
            bindAvatars()
        }
    }

    private fun onThemeClicked(themeId: String) {
        val profile = currentProfile ?: return
        val ctx = context ?: return
        if (!UnlockCatalog.isThemeUnlocked(themeId, currentLevel)) {
            Toast.makeText(
                ctx,
                getString(
                    R.string.progress_unlock_toast,
                    UnlockCatalog.requiredLevelForTheme(themeId)
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        profile.themeId = themeId
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(ctx.applicationContext).profileDao().update(profile)
            }
            if (!isAdded) return@launch
            bindThemes()
            applyThemeBackground()
            activity?.findViewById<View>(R.id.main)?.setBackgroundResource(UnlockCatalog.backgroundColorRes(themeId))
            Toast.makeText(requireContext(), R.string.progress_theme_applied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyThemeBackground() {
        val root = view
        val profile = currentProfile
        if (root == null || profile == null) {
            return
        }
        val themeId = UnlockCatalog.normalizeThemeId(profile.themeId)
        root.setBackgroundResource(UnlockCatalog.backgroundColorRes(themeId))
    }

    private fun bindLeaderboard(fullName: String?, weeklyXp: Int, friends: List<Friend>) {
        layoutLeaderboard.removeAllViews()
        if (context == null) {
            return
        }

        val rows = mutableListOf<LeaderboardRow>()
        val youName = if (!fullName.isNullOrEmpty()) fullName else getString(R.string.progress_leaderboard_you)
        rows.add(LeaderboardRow(youName, weeklyXp, true))

        for (friend in friends) {
            rows.add(LeaderboardRow(friend.displayName, friend.weeklyXp, false))
        }
        rows.sortByDescending { it.weeklyXp }

        val inflater = LayoutInflater.from(requireContext())
        rows.forEachIndexed { i, row ->
            val item = inflater.inflate(R.layout.item_leaderboard_row, layoutLeaderboard, false)
            val tvRank = item.findViewById<TextView>(R.id.tv_rank)
            val tvName = item.findViewById<TextView>(R.id.tv_name)
            val tvWeeklyXp = item.findViewById<TextView>(R.id.tv_weekly_xp)
            tvRank.text = (i + 1).toString()
            tvName.text = if (row.isSelf) {
                "${getString(R.string.progress_leaderboard_you)} (${row.name})"
            } else {
                row.name
            }
            tvWeeklyXp.text = getString(R.string.progress_leaderboard_xp, row.weeklyXp)
            if (row.isSelf) {
                tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_brand))
            }
            layoutLeaderboard.addView(item)
        }
    }

    private fun bindInsights(topSubject: String?, topSubjectCount: Int, improvedSubject: String?) {
        tvInsight1.visibility = View.GONE
        tvInsight2.visibility = View.GONE

        var shown = 0
        if (topSubject != null && topSubjectCount >= 1) {
            tvInsight1.text = getString(
                R.string.progress_insight_top_subject, subjectLabel(topSubject)
            )
            tvInsight1.visibility = View.VISIBLE
            shown++
        }

        if (improvedSubject != null) {
            val target = if (shown == 0) tvInsight1 else tvInsight2
            target.text = getString(
                R.string.progress_insight_accuracy_improved, subjectLabel(improvedSubject)
            )
            target.visibility = View.VISIBLE
            shown++
        }

        tvInsightsEmpty.visibility = if (shown == 0) View.VISIBLE else View.GONE
    }

    private fun findImprovedSubject(quizItemDao: QuizItemDao, userId: Long): String? {
        val now = System.currentTimeMillis()
        val recentFrom = now - WINDOW_MS
        val olderFrom = now - (2L * WINDOW_MS)

        var bestSubject: String? = null
        var bestDelta = 0f

        for (subject in SUBJECTS) {
            val recentAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            if (recentAttempted < 2 || olderAttempted < 2) {
                continue
            }
            val recentCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            val recentAcc = recentCorrect * 1f / recentAttempted
            val olderAcc = olderCorrect * 1f / olderAttempted
            val delta = recentAcc - olderAcc
            if (delta > 0.05f && delta > bestDelta) {
                bestDelta = delta
                bestSubject = subject
            }
        }
        return bestSubject
    }

    private fun bindReviewSuggest(topSubject: String?, topSubjectCount: Int) {
        if (topSubject == null || topSubjectCount < MIN_PRACTICE_QA) {
            reviewSubject = null
            cardReviewSuggest.visibility = View.GONE
            return
        }
        reviewSubject = topSubject
        tvReviewSubtitle.text = getString(R.string.progress_review_subtitle, subjectLabel(topSubject))
        cardReviewSuggest.visibility = View.VISIBLE
    }

    private fun openPractice(subject: String) {
        val ctx = context ?: return
        if (currentUserId == -1L) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(ctx.applicationContext).questionAnswerDao().countBySubject(currentUserId, subject)
            }
            if (!isAdded) return@launch
            if (count < MIN_PRACTICE_QA) {
                Toast.makeText(requireContext(), R.string.quiz_need_more, Toast.LENGTH_LONG).show()
                return@launch
            }
            val intent = Intent(requireContext(), QuizActivity::class.java)
            intent.putExtra(QuizActivity.EXTRA_SUBJECT, subject)
            startActivity(intent)
        }
    }

    private fun formatReviewTime(totalSeconds: Int): String {
        if (totalSeconds < 60) {
            return getString(R.string.progress_review_time_seconds, totalSeconds)
        }
        if (totalSeconds < 3600) {
            return getString(R.string.progress_review_time_minutes, totalSeconds / 60)
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return getString(R.string.progress_review_time_hours, hours, minutes)
    }

    private fun bindSubjectRow(
        row: LinearLayout,
        tvCount: TextView,
        bar: ProgressBar,
        count: Int,
        denom: Int
    ) {
        if (count <= 0) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        tvCount.text = count.toString()
        bar.progress = ((count * 100f) / denom).roundToInt()
    }

    private fun setBadge(badge: LinearLayout, earned: Boolean) {
        badge.setBackgroundResource(if (earned) R.drawable.bg_badge_earned else R.drawable.bg_badge_locked)
        badge.alpha = if (earned) 1f else 0.5f
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject) {
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

    private class LeaderboardRow(
        val name: String?,
        val weeklyXp: Int,
        val isSelf: Boolean
    )
}
