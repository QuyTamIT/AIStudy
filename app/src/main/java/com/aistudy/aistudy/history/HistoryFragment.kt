package com.aistudy.aistudy.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
    }

    private lateinit var etSearch: EditText
    private lateinit var chipAll: TextView
    private lateinit var chipMath: TextView
    private lateinit var chipScience: TextView
    private lateinit var chipCode: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipLanguage: TextView
    private lateinit var chipStarred: TextView
    private lateinit var btnPractice: MaterialButton
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: HistoryAdapter
    private var qaDao: QuestionAnswerDao? = null
    private var userId = -1L
    private val allItems = mutableListOf<QuestionAnswer>()

    private var subjectFilter = "all"
    private var starredOnly = false
    private var query = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val themedInflater = inflater.cloneInContext(ContextThemeWrapper(requireContext(), R.style.Theme_AIStudy_Study))
        return themedInflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etSearch = view.findViewById(R.id.et_search)
        chipAll = view.findViewById(R.id.chip_all)
        chipMath = view.findViewById(R.id.chip_math)
        chipScience = view.findViewById(R.id.chip_science)
        chipCode = view.findViewById(R.id.chip_code)
        chipHistory = view.findViewById(R.id.chip_history)
        chipLanguage = view.findViewById(R.id.chip_language)
        chipStarred = view.findViewById(R.id.chip_starred)
        btnPractice = view.findViewById(R.id.btn_practice)
        rvHistory = view.findViewById(R.id.rv_history)
        tvEmpty = view.findViewById(R.id.tv_empty)

        btnPractice.setOnClickListener {
            if (subjectFilter == "all" || starredOnly) {
                return@setOnClickListener
            }
            val currentContext = context ?: return@setOnClickListener
            if (userId <= 0L) {
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(currentContext.applicationContext)
                        .questionAnswerDao()
                        .countBySubject(userId, subjectFilter)
                }
                if (!isAdded) return@launch
                if (count < MIN_PRACTICE_QA) {
                    Toast.makeText(requireContext(), R.string.quiz_need_more, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val intent = Intent(requireContext(), QuizActivity::class.java)
                intent.putExtra(QuizActivity.EXTRA_SUBJECT, subjectFilter)
                startActivity(intent)
            }
        }

        adapter = HistoryAdapter(
            object : HistoryAdapter.Listener {
                override fun onItemClick(qa: QuestionAnswer) {
                    val intent = Intent(requireContext(), AnswerActivity::class.java).apply {
                        putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
                    }
                    startActivity(intent)
                }

                override fun onStarClick(qa: QuestionAnswer) {
                    val currentContext = context ?: return
                    val ownerId = userId
                    val preferences = currentContext.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
                    if (ownerId <= 0L || qa.userId != ownerId) return
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            if (preferences.getLong(LoginActivity.KEY_USER_ID, 0L) == ownerId) {
                                AppDatabase.getInstance(currentContext.applicationContext)
                                    .questionAnswerDao().toggleStar(qa.id, ownerId)
                            }
                        }
                        if (!isAdded || preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != ownerId) return@launch
                        loadHistory()
                    }
                }
            },
            ::subjectLabel,
            ::relativeTime
        )
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        chipAll.setOnClickListener {
            subjectFilter = "all"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipMath.setOnClickListener {
            subjectFilter = "math"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipScience.setOnClickListener {
            subjectFilter = "science"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipCode.setOnClickListener {
            subjectFilter = "code"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipHistory.setOnClickListener {
            subjectFilter = "history"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipLanguage.setOnClickListener {
            subjectFilter = "language"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipStarred.setOnClickListener {
            subjectFilter = "all"
            starredOnly = true
            refreshChips()
            applyFilter()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })

        refreshChips()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val currentContext = context ?: return
        val currentUserId = currentContext
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (currentUserId <= 0L) {
            return
        }
        userId = currentUserId

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(currentContext.applicationContext).questionAnswerDao()
                qaDao = dao
                dao.getAllByUserId(currentUserId)
            }
            if (!isAdded || view == null || currentContext.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
                    .getLong(LoginActivity.KEY_USER_ID, 0L) != currentUserId) return@launch
            allItems.clear()
            allItems.addAll(items)
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = mutableListOf<QuestionAnswer>()
        for (qa in allItems) {
            if (subjectFilter != "all" && subjectFilter != qa.subject) {
                continue
            }
            if (starredOnly && !qa.starred) {
                continue
            }
            if (query.isNotEmpty() && qa.question?.lowercase()?.contains(query.lowercase()) != true) {
                continue
            }
            filtered.add(qa)
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
        refreshPracticeButton()
    }

    private fun refreshPracticeButton() {
        val show = !starredOnly && subjectFilter != "all"
        if (!show) {
            btnPractice.visibility = View.GONE
            return
        }
        btnPractice.visibility = View.VISIBLE
        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(subjectFilter))
    }

    private fun refreshChips() {
        styleChip(chipAll, subjectFilter == "all" && !starredOnly)
        styleChip(chipMath, subjectFilter == "math" && !starredOnly)
        styleChip(chipScience, subjectFilter == "science" && !starredOnly)
        styleChip(chipCode, subjectFilter == "code" && !starredOnly)
        styleChip(chipHistory, subjectFilter == "history" && !starredOnly)
        styleChip(chipLanguage, subjectFilter == "language" && !starredOnly)
        styleChip(chipStarred, starredOnly)
    }

    private fun styleChip(chip: TextView, active: Boolean) {
        val currentContext = context ?: return
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_history_chip_active)
            chip.setTextColor(ContextCompat.getColor(currentContext, R.color.answer_on_primary))
        } else {
            chip.setBackgroundResource(R.drawable.bg_history_chip)
            chip.setTextColor(ContextCompat.getColor(currentContext, R.color.answer_foreground))
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

    private fun relativeTime(createdAt: Long): String {
        val diff = System.currentTimeMillis() - createdAt
        val hours = diff / 3_600_000L
        if (hours < 1) {
            return getString(R.string.history_just_now)
        }
        if (hours < 24) {
            return getString(R.string.history_hours_ago, hours.toInt())
        }
        return getString(R.string.history_days_ago, (hours / 24).toInt())
    }
}
