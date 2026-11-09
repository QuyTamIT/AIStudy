package com.aistudy.aistudy.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
    }

    private lateinit var tvLevelBadge: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXpLabel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvGreeting: TextView
    private lateinit var btnAsk: MaterialButton
    private lateinit var btnPracticeRecent: MaterialButton

    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var sectionRecent: LinearLayout
    private lateinit var tvViewAll: TextView
    private lateinit var cardRecent: LinearLayout
    private lateinit var tvRecentQuestion: TextView
    private lateinit var tvRecentSubject: TextView

    private lateinit var homeProfile: LinearLayout
    private lateinit var homeProfileContext: TextView
    private lateinit var homeInterests: TextView

    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView

    private var latestQaId = -1L
    private var latestSubject: String? = null
    private var reviewSubject: String? = null
    private var currentUserId = -1L

    private data class DashboardSnapshot(
        val fullName: String,
        val xp: Int,
        val streak: Int,
        val themeId: String?,
        val level: String?,
        val style: String?,
        val subjects: String?,
        val latest: QuestionAnswer?,
        val latestSubjectCount: Int,
        val topSubject: String?,
        val topSubjectCount: Int,
        val countMath: Int,
        val countScience: Int,
        val countCode: Int,
        val countHistory: Int,
        val countLanguage: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvLevelBadge = view.findViewById(R.id.tv_level_badge)
        tvStreak = view.findViewById(R.id.tv_streak)
        tvXpLabel = view.findViewById(R.id.tv_xp_label)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)
        tvGreeting = view.findViewById(R.id.tv_greeting)
        btnAsk = view.findViewById(R.id.btn_ask)
        btnPracticeRecent = view.findViewById(R.id.btn_practice_recent)

        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        sectionRecent = view.findViewById(R.id.section_recent)
        tvViewAll = view.findViewById(R.id.tv_view_all)
        cardRecent = view.findViewById(R.id.card_recent)
        tvRecentQuestion = view.findViewById(R.id.tv_recent_question)
        tvRecentSubject = view.findViewById(R.id.tv_recent_subject)

        homeProfile = view.findViewById(R.id.home_profile)
        homeProfileContext = view.findViewById(R.id.home_profile_context)
        homeInterests = view.findViewById(R.id.home_interests)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)

        view.findViewById<View>(R.id.btn_logout)?.setOnClickListener { showLogoutConfirmation() }

        btnAsk.setOnClickListener { selectTab(R.id.nav_ask) }
        btnPracticeRecent.setOnClickListener {
            latestSubject?.let { openPractice(it) }
        }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
        tvViewAll.setOnClickListener { selectTab(R.id.nav_history) }
        cardRecent.setOnClickListener {
            if (latestQaId != -1L) {
                val intent = Intent(requireContext(), AnswerActivity::class.java)
                intent.putExtra(AnswerActivity.EXTRA_QA_ID, latestQaId)
                startActivity(intent)
            }
        }

        view.findViewById<View>(R.id.card_subject_math).setOnClickListener { openPractice("math") }
        view.findViewById<View>(R.id.card_subject_science).setOnClickListener { openPractice("science") }
        view.findViewById<View>(R.id.card_subject_code).setOnClickListener { openPractice("code") }
        view.findViewById<View>(R.id.card_subject_history).setOnClickListener { openPractice("history") }
        view.findViewById<View>(R.id.card_subject_language).setOnClickListener { openPractice("language") }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
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

    private fun loadDashboard() {
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
                val profile = db.profileDao().getByUserId(userId)
                val qaDao = db.questionAnswerDao()
                val latest = qaDao.getLatestByUserId(userId)
                val latestSubjectCount = latest?.subject?.let { qaDao.countBySubject(userId, it) } ?: 0
                val topSubject = qaDao.getTopSubjectByUserId(userId)
                val topSubjectCount = if (topSubject != null) qaDao.countBySubject(userId, topSubject) else 0

                DashboardSnapshot(
                    fullName = user?.fullName.orEmpty(),
                    xp = profile?.xp ?: 0,
                    streak = profile?.streak ?: 0,
                    themeId = profile?.themeId,
                    level = profile?.level,
                    style = profile?.style,
                    subjects = profile?.subjects,
                    latest = latest,
                    latestSubjectCount = latestSubjectCount,
                    topSubject = topSubject,
                    topSubjectCount = topSubjectCount,
                    countMath = qaDao.countBySubject(userId, "math"),
                    countScience = qaDao.countBySubject(userId, "science"),
                    countCode = qaDao.countBySubject(userId, "code"),
                    countHistory = qaDao.countBySubject(userId, "history"),
                    countLanguage = qaDao.countBySubject(userId, "language")
                )
            }

            if (!isAdded || view == null) return@launch

            bindDashboard(snapshot)
        }
    }

    private fun bindDashboard(data: DashboardSnapshot) {
        val level = XpRules.levelFromXp(data.xp)
        val xpPct = XpRules.progressPercentToNext(data.xp)
        val nextXp = XpRules.nextLevelThreshold(data.xp)

        tvLevelBadge.text = level.toString()
        tvStreak.text = getString(R.string.home_streak_days, data.streak)
        tvXpLabel.text = getString(R.string.home_xp_progress, data.xp, nextXp)
        tvXpPercent.text = getString(R.string.home_xp_percent, xpPct)
        progressXp.progress = xpPct
        tvGreeting.text = getString(R.string.home_greeting, data.fullName)

        if (data.themeId != null) {
            activity?.findViewById<View>(R.id.main)?.setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(data.themeId))
            )
        }

        // Profile preferences context
        val contextParts = mutableListOf<String>()
        when (data.level) {
            ProfileDefaults.LEVEL_JUNIOR -> contextParts.add(getString(R.string.onboarding_level_junior))
            ProfileDefaults.LEVEL_HIGH -> contextParts.add(getString(R.string.onboarding_level_high))
            ProfileDefaults.LEVEL_UNI -> contextParts.add(getString(R.string.onboarding_level_uni))
        }
        when (data.style) {
            ProfileDefaults.STYLE_SHORT -> contextParts.add(getString(R.string.onboarding_style_short))
            ProfileDefaults.STYLE_STEPWISE -> contextParts.add(getString(R.string.onboarding_style_stepwise))
            ProfileDefaults.STYLE_DETAILED -> contextParts.add(getString(R.string.onboarding_style_detailed))
        }

        if (contextParts.isNotEmpty()) {
            homeProfileContext.text = contextParts.joinToString(" · ")
            if (!data.subjects.isNullOrEmpty()) {
                val subjectNames = data.subjects.split(",")
                    .map { subjectLabel(it.trim()) }
                    .filter { it.isNotEmpty() }
                if (subjectNames.isNotEmpty()) {
                    homeInterests.text = getString(R.string.home_interests, subjectNames.joinToString(", "))
                    homeInterests.visibility = View.VISIBLE
                } else {
                    homeInterests.visibility = View.GONE
                }
            } else {
                homeInterests.visibility = View.GONE
            }
            homeProfile.visibility = View.VISIBLE
        } else {
            homeProfile.visibility = View.GONE
        }

        val latest = data.latest
        if (latest != null) {
            latestQaId = latest.id
            latestSubject = latest.subject
            sectionRecent.visibility = View.VISIBLE
            tvRecentQuestion.text = latest.question
            tvRecentSubject.text = subjectLabel(latest.subject)
            if (data.latestSubjectCount >= MIN_PRACTICE_QA) {
                btnPracticeRecent.visibility = View.VISIBLE
                btnPracticeRecent.text = getString(R.string.quiz_practice_on, subjectLabel(latest.subject))
            } else {
                btnPracticeRecent.visibility = View.GONE
            }
        } else {
            latestQaId = -1L
            latestSubject = null
            sectionRecent.visibility = View.GONE
            btnPracticeRecent.visibility = View.GONE
        }

        val topSubject = data.topSubject
        if (topSubject != null && data.topSubjectCount >= MIN_PRACTICE_QA) {
            reviewSubject = topSubject
            tvReviewSubtitle.text = getString(R.string.home_review_subtitle, subjectLabel(topSubject))
            cardReviewSuggest.visibility = View.VISIBLE
        } else {
            reviewSubject = null
            cardReviewSuggest.visibility = View.GONE
        }

        tvCountMath.text = getString(R.string.home_subject_count, data.countMath)
        tvCountScience.text = getString(R.string.home_subject_count, data.countScience)
        tvCountCode.text = getString(R.string.home_subject_count, data.countCode)
        tvCountHistory.text = getString(R.string.home_subject_count, data.countHistory)
        tvCountLanguage.text = getString(R.string.home_subject_count, data.countLanguage)
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

    private fun selectTab(menuItemId: Int) {
        val act = activity
        if (act is MainActivity) {
            act.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = menuItemId
        }
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
}
