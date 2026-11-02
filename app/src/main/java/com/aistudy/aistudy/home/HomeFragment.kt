package com.aistudy.aistudy.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Callable

class HomeFragment : Fragment() {
    private var loadJob: Job? = null

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

        bindGreeting(view, null)
        view.findViewById<View>(R.id.home_ask).setOnClickListener { openAsk() }
        view.findViewById<View>(R.id.home_empty_ask).setOnClickListener { openAsk() }
        view.findViewById<View>(R.id.home_retry).setOnClickListener { loadDashboard(view) }
        // Stack the ledger before large text can collide in the two columns.
        if (resources.configuration.fontScale >= 1.3f || resources.configuration.screenWidthDp < 360) {
            view.findViewById<LinearLayout>(R.id.home_metrics).orientation = LinearLayout.VERTICAL
            view.findViewById<View>(R.id.home_metric_divider).visibility = View.GONE
            listOf(R.id.home_questions_metric, R.id.home_xp_metric).forEach { id ->
                view.findViewById<View>(id).layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (id == R.id.home_xp_metric) topMargin = resources.getDimensionPixelSize(R.dimen.answer_space_4)
                }
            }
        }
        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
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
        view?.let(::loadDashboard)
    }

    override fun onPause() {
        loadJob?.cancel()
        super.onPause()
    }

    private fun openAsk() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_ask
    }

    private fun loadDashboard(root: View) {
        loadJob?.cancel()
        val appContext = requireContext().applicationContext
        val preferences = appContext.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        root.findViewById<View>(R.id.home_error).visibility = View.GONE
        root.findViewById<View>(R.id.home_loading).visibility =
            if (root.findViewById<View>(R.id.home_dashboard_data).visibility == View.VISIBLE) View.GONE else View.VISIBLE
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                require(userId > 0L)
                val snapshot = withContext(Dispatchers.IO) {
                    val database = AppDatabase.getInstance(appContext)
                    database.runInTransaction(Callable {
                        // Read one consistent snapshot without materializing the answer history.
                        val questionDao = database.questionAnswerDao()
                        HomeSnapshot(database.userDao().getById(userId)?.fullName,
                            database.profileDao().getByUserId(userId), questionDao.countByUserId(userId),
                            questionDao.getLatestByUserId(userId))
                    })
                }
                if (preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId) return@launch
                bindGreeting(root, snapshot.name)
                bindProfile(root, snapshot.profile)
                val numbers = NumberFormat.getIntegerInstance(resources.configuration.locales[0])
                root.findViewById<TextView>(R.id.home_question_count).text = numbers.format(snapshot.questionCount)
                root.findViewById<TextView>(R.id.home_xp_count).text = snapshot.profile?.xp?.let { numbers.format(it) }
                    ?: getString(R.string.home_value_unavailable)
                root.findViewById<TextView>(R.id.home_journey_body).setText(
                    if (snapshot.questionCount == 0) R.string.home_journey_empty else R.string.home_journey_body)
                bindLatest(root, snapshot.latest, userId)
                root.findViewById<View>(R.id.home_dashboard_data).visibility = View.VISIBLE
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("HomeFragment", "Unable to load learning dashboard", error)
                root.findViewById<View>(R.id.home_dashboard_data).visibility = View.GONE
                root.findViewById<View>(R.id.home_profile).visibility = View.GONE
                root.findViewById<View>(R.id.home_error).visibility = View.VISIBLE
            } finally {
                root.findViewById<View>(R.id.home_loading).visibility = View.GONE
            }
        }
    }

    private fun bindGreeting(root: View, name: String?) {
        val greeting = getString(when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_morning
            in 12..17 -> R.string.home_afternoon
            else -> R.string.home_evening
        })
        root.findViewById<TextView>(R.id.home_greeting).text = name?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { getString(R.string.home_greeting_named, greeting, it) }
            ?: getString(R.string.home_greeting, greeting)
    }

    private fun bindProfile(root: View, profile: Profile?) {
        val level = when (profile?.level) {
            ProfileDefaults.LEVEL_JUNIOR -> R.string.onboarding_level_junior
            ProfileDefaults.LEVEL_HIGH -> R.string.onboarding_level_high
            ProfileDefaults.LEVEL_UNI -> R.string.onboarding_level_uni
            else -> null
        }?.let(::getString)
        val style = when (profile?.style) {
            ProfileDefaults.STYLE_SHORT -> R.string.onboarding_style_short
            ProfileDefaults.STYLE_STEPWISE -> R.string.onboarding_style_stepwise
            ProfileDefaults.STYLE_DETAILED -> R.string.onboarding_style_detailed
            else -> null
        }?.let(::getString)
        val context = if (level != null && style != null) getString(R.string.home_profile_context, level, style)
            else level ?: style.orEmpty()
        root.findViewById<TextView>(R.id.home_profile_context).apply {
            text = context
            visibility = if (context.isEmpty()) View.GONE else View.VISIBLE
        }
        val subjects = profile?.subjects.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            .distinct().mapNotNull { subjectLabel(it) }.map(::getString)
        root.findViewById<TextView>(R.id.home_interests).apply {
            text = getString(R.string.home_interests, subjects.joinToString(getString(R.string.home_subject_separator)))
            visibility = if (subjects.isEmpty()) View.GONE else View.VISIBLE
        }
        root.findViewById<View>(R.id.home_profile).visibility =
            if (context.isEmpty() && subjects.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun bindLatest(root: View, latest: QuestionAnswer?, userId: Long) {
        val row = root.findViewById<View>(R.id.home_latest)
        row.visibility = if (latest == null) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.home_continue_body).visibility = if (latest == null) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.home_empty).visibility = if (latest == null) View.VISIBLE else View.GONE
        if (latest == null) {
            row.setOnClickListener(null)
            return
        }
        val question = latest.question?.takeIf { it.isNotBlank() } ?: getString(R.string.home_saved_question)
        root.findViewById<TextView>(R.id.home_latest_question).text = question
        root.findViewById<TextView>(R.id.home_latest_subject).setText(
            subjectLabel(latest.subject) ?: R.string.onboarding_subject_other)
        root.findViewById<TextView>(R.id.home_latest_date).apply {
            text = if (latest.createdAt > 0L) getString(R.string.home_saved_date,
                DateFormat.getMediumDateFormat(context).format(Date(latest.createdAt))) else ""
            visibility = if (latest.createdAt > 0L) View.VISIBLE else View.GONE
        }
        row.contentDescription = getString(R.string.home_open_question, question)
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
        row.setOnClickListener {
            val activeId = requireContext().getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
                .getLong(LoginActivity.KEY_USER_ID, 0L)
            if (activeId == userId) startActivity(Intent(requireContext(), AnswerActivity::class.java)
                .putExtra(AnswerActivity.EXTRA_QA_ID, latest.id))
        }
    }

    private fun subjectLabel(subject: String?): Int? = when (subject) {
        ProfileDefaults.SUBJECT_MATH -> R.string.onboarding_subject_math
        ProfileDefaults.SUBJECT_SCIENCE -> R.string.onboarding_subject_science
        ProfileDefaults.SUBJECT_CODE -> R.string.onboarding_subject_code
        ProfileDefaults.SUBJECT_HISTORY -> R.string.onboarding_subject_history
        ProfileDefaults.SUBJECT_LANGUAGE -> R.string.onboarding_subject_language
        ProfileDefaults.SUBJECT_OTHER -> R.string.onboarding_subject_other
        else -> null
    }

    private data class HomeSnapshot(val name: String?, val profile: Profile?, val questionCount: Int,
                                    val latest: QuestionAnswer?)
}
