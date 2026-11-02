package com.aistudy.aistudy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.onboarding.OnboardingActivity
import com.aistudy.aistudy.progress.ProgressFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L)
        if (userId <= 0L) {
            navigateTo(LoginActivity::class.java)
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val main = findViewById<View>(R.id.main)
        main.visibility = View.INVISIBLE
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    ProfileDefaults.ensureExists(
                        AppDatabase.getInstance(applicationContext).profileDao(), userId
                    )
                }
                lifecycle.withResumed {
                    if (!profile.onboarded) {
                        navigateTo(OnboardingActivity::class.java)
                    } else {
                        showMainContent()
                        main.visibility = View.VISIBLE
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("MainActivity", "Unable to load profile", error)
                Toast.makeText(this@MainActivity, R.string.onboarding_error_load, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showMainContent() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNavigation.setOnItemSelectedListener { item ->
            showFragment(item.itemId)
        }

        // FragmentManager restores the current tab when the Activity is recreated.
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            showFragment(bottomNavigation.selectedItemId)
        }
    }

    private fun showFragment(itemId: Int): Boolean {
        val fragment: Fragment = when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_ask -> AskFragment()
            R.id.nav_history -> HistoryFragment()
            R.id.nav_progress -> ProgressFragment()
            else -> return false
        }

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
            .commit()
        return true
    }
}
