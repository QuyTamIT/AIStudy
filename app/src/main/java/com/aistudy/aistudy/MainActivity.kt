package com.aistudy.aistudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.auth.restoreSessionProfile
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.notify.NotificationScheduler
import com.aistudy.aistudy.navigation.CurvedAnimatedBottomNav
import com.aistudy.aistudy.onboarding.OnboardingActivity
import com.aistudy.aistudy.playground.PlaygroundFragment
import com.aistudy.aistudy.progress.ProgressFragment
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_QA_ID = "qa_id"
        const val TAB_HOME = "home"
        const val TAB_ASK = "ask"
        const val TAB_PLAYGROUND = "playground"
        const val TAB_HISTORY = "history"
        const val TAB_PROGRESS = "progress"
        private const val STATE_SELECTED_TAB = "selected_tab"
    }

    private lateinit var bottomNav: CurvedAnimatedBottomNav

    fun selectTab(itemId: Int) {
        bottomNav.selectedItemId = itemId
    }
    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profile = try {
            restoreSessionProfile(this)
        } catch (error: Exception) {
            AlertDialog.Builder(this)
                .setTitle(R.string.startup_database_error_title)
                .setMessage(R.string.startup_database_error_message)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { finish() }
                .show()
            return
        }
        if (profile == null || !profile.onboarded) {
            val destination = if (profile == null) LoginActivity::class.java else OnboardingActivity::class.java
            startActivity(Intent(this, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        FriendSeeder.ensureSeeded(AppDatabase.getInstance(this).friendDao(), profile.userId)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.main).setBackgroundResource(
            UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
        )

        bottomNav = findViewById(R.id.bottom_nav)
        if (savedInstanceState != null) {
            // FragmentManager restores the screen. Select its tab before navigation callbacks are attached.
            bottomNav.selectedItemId = savedInstanceState.getInt(STATE_SELECTED_TAB, View.NO_ID)
        }
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_ask -> AskFragment()
                R.id.nav_playground -> PlaygroundFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_progress -> ProgressFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            handleOpenIntent(intent)
        }

        requestNotificationPermissionIfNeeded()
        NotificationScheduler.schedule(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::bottomNav.isInitialized) outState.putInt(STATE_SELECTED_TAB, bottomNav.selectedItemId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::bottomNav.isInitialized) handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        if (intent == null) {
            bottomNav.selectedItemId = R.id.nav_home
            return
        }
        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1L)
        if (qaId > 0) {
            val answer = Intent(this, AnswerActivity::class.java)
            answer.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
            startActivity(answer)
            bottomNav.selectedItemId = R.id.nav_history
            return
        }
        bottomNav.selectedItemId = when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_ASK -> R.id.nav_ask
            TAB_PLAYGROUND -> R.id.nav_playground
            TAB_HISTORY -> R.id.nav_history
            TAB_PROGRESS -> R.id.nav_progress
            else -> R.id.nav_home
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
