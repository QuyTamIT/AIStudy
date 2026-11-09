package com.aistudy.aistudy.onboarding

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private var userId = 0L
    private var currentStep = 0
    private var selectedLevel = ProfileDefaults.LEVEL_HIGH
    private var selectedStyle = ProfileDefaults.STYLE_STEPWISE
    private val selectedSubjects = linkedSetOf<String>()
    private var loaded = false
    private var busy = false
    private lateinit var levelChoices: Map<Int, String>
    private lateinit var subjectChoices: Map<Int, String>
    private lateinit var styleChoices: Map<Int, String>
    private lateinit var buttonBack: MaterialButton
    private lateinit var buttonNext: MaterialButton
    private val stepBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!busy) showStep(currentStep - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Study)
        super.onCreate(savedInstanceState)
        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L)
        if (userId <= 0L) {
            navigateTo(LoginActivity::class.java)
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val padding = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
        buttonBack = findViewById(R.id.btn_back)
        buttonNext = findViewById(R.id.btn_next)
        levelChoices = linkedMapOf(
            R.id.choice_level_junior to ProfileDefaults.LEVEL_JUNIOR,
            R.id.choice_level_high to ProfileDefaults.LEVEL_HIGH,
            R.id.choice_level_uni to ProfileDefaults.LEVEL_UNI
        )
        subjectChoices = linkedMapOf(
            R.id.choice_subject_math to ProfileDefaults.SUBJECT_MATH,
            R.id.choice_subject_science to ProfileDefaults.SUBJECT_SCIENCE,
            R.id.choice_subject_code to ProfileDefaults.SUBJECT_CODE,
            R.id.choice_subject_history to ProfileDefaults.SUBJECT_HISTORY,
            R.id.choice_subject_language to ProfileDefaults.SUBJECT_LANGUAGE,
            R.id.choice_subject_other to ProfileDefaults.SUBJECT_OTHER
        )
        styleChoices = linkedMapOf(
            R.id.choice_style_short to ProfileDefaults.STYLE_SHORT,
            R.id.choice_style_stepwise to ProfileDefaults.STYLE_STEPWISE,
            R.id.choice_style_detailed to ProfileDefaults.STYLE_DETAILED
        )
        bindChoices(levelChoices, false) { selectedLevel = it }
        bindChoices(subjectChoices, true) {
            if (!selectedSubjects.add(it)) selectedSubjects.remove(it)
            findViewById<View>(R.id.tv_subject_error).visibility = View.GONE
        }
        bindChoices(styleChoices, false) { selectedStyle = it }
        buttonBack.setOnClickListener { showStep(currentStep - 1) }
        buttonNext.setOnClickListener { onNext() }
        onBackPressedDispatcher.addCallback(this, stepBack)
        showStep(0)
        setBusy(true)

        lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    ProfileDefaults.ensureExists(database.profileDao(), userId)
                }
                if (profile.onboarded) {
                    navigateTo(MainActivity::class.java)
                    return@launch
                }
                val restored = savedInstanceState?.takeIf { it.getLong("userId") == userId }
                selectedLevel = (restored?.getString("level") ?: profile.level)
                    ?.takeIf { it in levelChoices.values } ?: ProfileDefaults.LEVEL_HIGH
                selectedStyle = (restored?.getString("style") ?: profile.style)
                    ?.takeIf { it in styleChoices.values } ?: ProfileDefaults.STYLE_STEPWISE
                selectedSubjects.clear()
                val subjects = restored?.getStringArrayList("subjects")
                    ?: profile.subjects.orEmpty().split(",")
                selectedSubjects.addAll(subjects.filter { it in subjectChoices.values })
                loaded = true
                refreshChoices()
                showStep(restored?.getInt("step", 0) ?: 0)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Unable to load onboarding", error)
                Toast.makeText(this@OnboardingActivity, R.string.onboarding_error_load, Toast.LENGTH_LONG).show()
                finish()
            } finally {
                setBusy(false)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (loaded) {
            outState.putLong("userId", userId)
            outState.putInt("step", currentStep)
            outState.putString("level", selectedLevel)
            outState.putString("style", selectedStyle)
            outState.putStringArrayList("subjects", ArrayList(selectedSubjects))
        }
        super.onSaveInstanceState(outState)
    }

    private fun bindChoices(choices: Map<Int, String>, multiple: Boolean, select: (String) -> Unit) {
        choices.forEach { (id, value) ->
            val view = findViewById<View>(id)
            ViewCompat.setScreenReaderFocusable(view, true)
            ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
                @Suppress("DEPRECATION")
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = if (multiple) "android.widget.CheckBox" else "android.widget.RadioButton"
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            })
            view.setOnClickListener {
                if (!loaded || busy) return@setOnClickListener
                select(value)
                refreshChoices()
            }
        }
    }

    private fun refreshChoices() {
        fun refresh(choices: Map<Int, String>, subject: Boolean, selected: (String) -> Boolean) {
            choices.forEach { (id, value) ->
                val view = findViewById<View>(id)
                view.isSelected = selected(value)
                view.setBackgroundResource(when {
                    subject && view.isSelected -> R.drawable.bg_onboarding_subject_selected
                    subject -> R.drawable.bg_onboarding_subject
                    view.isSelected -> R.drawable.bg_onboarding_choice_selected
                    else -> R.drawable.bg_onboarding_choice
                })
            }
        }
        refresh(levelChoices, false) { it == selectedLevel }
        refresh(subjectChoices, true) { it in selectedSubjects }
        refresh(styleChoices, false) { it == selectedStyle }
    }

    private fun showStep(step: Int) {
        currentStep = step.coerceIn(0, 2)
        if (currentStep != 1) findViewById<View>(R.id.tv_subject_error).visibility = View.GONE
        val containers = listOf(R.id.step_level, R.id.step_subjects, R.id.step_style)
        containers.forEachIndexed { index, id ->
            findViewById<View>(id).visibility = if (index == currentStep) View.VISIBLE else View.GONE
        }
        listOf(R.id.progress_0, R.id.progress_1, R.id.progress_2).forEachIndexed { index, id ->
            findViewById<View>(id).setBackgroundColor(ContextCompat.getColor(
                this, if (index <= currentStep) R.color.auth_brand else R.color.auth_card
            ))
        }
        val titles = listOf(R.string.onboarding_step_level_title, R.string.onboarding_step_subjects_title, R.string.onboarding_step_style_title)
        val subtitles = listOf(R.string.onboarding_step_level_subtitle, R.string.onboarding_step_subjects_subtitle, R.string.onboarding_step_style_subtitle)
        findViewById<TextView>(R.id.tv_step_title).setText(titles[currentStep])
        findViewById<TextView>(R.id.tv_step_subtitle).setText(subtitles[currentStep])
        findViewById<TextView>(R.id.tv_step_progress).text = getString(R.string.onboarding_step_progress, currentStep + 1)
        buttonBack.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        stepBack.isEnabled = currentStep > 0
        updateNextLabel()
        findViewById<ScrollView>(R.id.onboarding_scroll).post {
            findViewById<ScrollView>(R.id.onboarding_scroll).scrollTo(0, 0)
        }
    }

    private fun onNext() {
        if (!loaded || busy) return
        if (currentStep >= 1 && selectedSubjects.isEmpty()) {
            findViewById<View>(R.id.tv_subject_error).visibility = View.VISIBLE
            Toast.makeText(this, R.string.onboarding_pick_subject, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentStep < 2) {
            showStep(currentStep + 1)
            return
        }
        val level = selectedLevel
        val subjects = subjectChoices.values.filter { it in selectedSubjects }.joinToString(",")
        val style = selectedStyle
        setBusy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.runInTransaction {
                        val profile = ProfileDefaults.ensureExists(database.profileDao(), userId)
                        profile.level = level
                        profile.subjects = subjects
                        profile.style = style
                        profile.onboarded = true
                        check(database.profileDao().update(profile) == 1)
                    }
                }
                navigateTo(MainActivity::class.java)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Unable to save onboarding", error)
                Toast.makeText(this@OnboardingActivity, R.string.onboarding_error_save, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        (levelChoices.keys + subjectChoices.keys + styleChoices.keys).forEach {
            findViewById<View>(it).isEnabled = !busy && loaded
        }
        buttonBack.isEnabled = !busy && loaded
        buttonNext.isEnabled = !busy && loaded
        updateNextLabel()
    }

    private fun updateNextLabel() {
        buttonNext.setText(when {
            busy -> R.string.auth_submit_loading
            currentStep == 2 -> R.string.onboarding_finish
            else -> R.string.onboarding_continue
        })
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    companion object {
        private const val TAG = "OnboardingActivity"
    }
}
