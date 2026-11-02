package com.aistudy.aistudy.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDefaults
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import java.util.Calendar
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
    }

    private lateinit var tvLevelBadge: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXpLabel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvGreeting: TextView
    private lateinit var btnAsk: MaterialButton
    private var btnLogout: View? = null

    // Phase 6: Recent Question & Subject Stats
    private lateinit var sectionRecent: View
    private lateinit var tvViewAll: TextView
    private lateinit var cardRecent: View
    private lateinit var tvRecentQuestion: TextView
    private lateinit var tvRecentSubject: TextView

    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView

    private var latestQaId = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val themedInflater = inflater.cloneInContext(ContextThemeWrapper(requireContext(), R.style.Theme_AIStudy_Study))
        return themedInflater.inflate(R.layout.fragment_home, container, false)
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
        btnLogout = view.findViewById(R.id.btn_logout)

        // Phase 6: Bind Views
        sectionRecent = view.findViewById(R.id.section_recent)
        tvViewAll = view.findViewById(R.id.tv_view_all)
        cardRecent = view.findViewById(R.id.card_recent)
        tvRecentQuestion = view.findViewById(R.id.tv_recent_question)
        tvRecentSubject = view.findViewById(R.id.tv_recent_subject)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)

        // Clicks & Navigation
        btnAsk.setOnClickListener { selectTab(R.id.nav_ask) }
        tvViewAll.setOnClickListener { selectTab(R.id.nav_history) }
        cardRecent.setOnClickListener {
            if (latestQaId != -1L) {
                val intent = Intent(requireContext(), AnswerActivity::class.java).apply {
                    putExtra(AnswerActivity.EXTRA_QA_ID, latestQaId)
                }
                startActivity(intent)
            }
        }

        btnLogout?.setOnClickListener {
            val activity = requireActivity()
            activity.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .remove(LoginActivity.KEY_USER_ID)
                .apply()

            startActivity(Intent(activity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            activity.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        val currentContext = context ?: return

        val userId = currentContext
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId <= 0L) {
            return
        }

        val db = AppDatabase.getInstance(currentContext)
        val user = db.userDao().getById(userId)
        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()

        val fullName = user?.fullName?.trim().orEmpty()
        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())

        // Phase 5: Metrics & Greeting
        tvLevelBadge.text = level.toString()
        tvStreak.text = getString(R.string.home_streak_days, streak)
        tvXpLabel.text = getString(R.string.home_xp_progress, xp, XP_MAX)
        tvXpPercent.text = getString(R.string.home_xp_percent, xpPct)
        progressXp.progress = xpPct

        tvGreeting.text = if (fullName.isNotEmpty()) {
            getString(R.string.home_greeting, fullName)
        } else {
            val greetingTime = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> getString(R.string.home_morning)
                in 12..17 -> getString(R.string.home_afternoon)
                else -> getString(R.string.home_evening)
            }
            "$greetingTime."
        }

        bindProfile(profile)

        // Phase 6: Recent Question
        val latest = qaDao.getLatestByUserId(userId)
        if (latest != null) {
            latestQaId = latest.id
            sectionRecent.visibility = View.VISIBLE
            tvRecentQuestion.text = latest.question
            tvRecentSubject.text = getQaSubjectLabel(latest.subject)
        } else {
            latestQaId = -1L
            sectionRecent.visibility = View.GONE
        }

        // Phase 6: Subject Counts
        tvCountMath.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "math"))
        tvCountScience.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "science"))
        tvCountCode.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "code"))
        tvCountHistory.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "history"))
    }

    private fun selectTab(menuItemId: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = menuItemId
        }
    }

    private fun getQaSubjectLabel(subject: String?): String {
        return when (subject) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }

    private fun bindProfile(profile: Profile?) {
        val root = view ?: return
        val homeProfile = root.findViewById<View>(R.id.home_profile) ?: return
        val homeProfileContext = root.findViewById<TextView>(R.id.home_profile_context) ?: return
        val homeInterests = root.findViewById<TextView>(R.id.home_interests) ?: return

        val levelStr = when (profile?.level) {
            ProfileDefaults.LEVEL_JUNIOR -> R.string.onboarding_level_junior
            ProfileDefaults.LEVEL_HIGH -> R.string.onboarding_level_high
            ProfileDefaults.LEVEL_UNI -> R.string.onboarding_level_uni
            else -> null
        }?.let(::getString)

        val styleStr = when (profile?.style) {
            ProfileDefaults.STYLE_SHORT -> R.string.onboarding_style_short
            ProfileDefaults.STYLE_STEPWISE -> R.string.onboarding_style_stepwise
            ProfileDefaults.STYLE_DETAILED -> R.string.onboarding_style_detailed
            else -> null
        }?.let(::getString)

        val contextStr = if (levelStr != null && styleStr != null) {
            getString(R.string.home_profile_context, levelStr, styleStr)
        } else {
            levelStr ?: styleStr.orEmpty()
        }

        homeProfileContext.text = contextStr
        homeProfileContext.visibility = if (contextStr.isEmpty()) View.GONE else View.VISIBLE

        val subjects = profile?.subjects.orEmpty().split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .mapNotNull { profileSubjectLabel(it) }
            .map(::getString)

        homeInterests.text = getString(R.string.home_interests, subjects.joinToString(getString(R.string.home_subject_separator)))
        homeInterests.visibility = if (subjects.isEmpty()) View.GONE else View.VISIBLE

        homeProfile.visibility = if (contextStr.isEmpty() && subjects.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun profileSubjectLabel(subject: String?): Int? = when (subject) {
        ProfileDefaults.SUBJECT_MATH -> R.string.onboarding_subject_math
        ProfileDefaults.SUBJECT_SCIENCE -> R.string.onboarding_subject_science
        ProfileDefaults.SUBJECT_CODE -> R.string.onboarding_subject_code
        ProfileDefaults.SUBJECT_HISTORY -> R.string.onboarding_subject_history
        ProfileDefaults.SUBJECT_LANGUAGE -> R.string.onboarding_subject_language
        ProfileDefaults.SUBJECT_OTHER -> R.string.onboarding_subject_other
        else -> null
    }
}
