package com.aistudy.aistudy.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.onboarding.OnboardingActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var buttonSubmit: MaterialButton
    private lateinit var buttonToggleMode: TextView
    private lateinit var formUi: AuthFormUi
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val userDao by lazy { database.userDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Study)
        super.onCreate(savedInstanceState)
        val userId = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
            .getLong(KEY_USER_ID, 0L)
        if (userId > 0L) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.auth_activity_login)

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val padding = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)

        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        buttonSubmit = findViewById(R.id.btn_submit)
        buttonToggleMode = findViewById(R.id.btn_toggle_mode)
        formUi = AuthFormUi(this)

        buttonSubmit.setOnClickListener { login() }
        buttonToggleMode.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun login() {
        val email = editEmail.text.toString().trim().lowercase(Locale.ROOT)
        val password = editPassword.text.toString()

        if (email.isBlank() || password.isBlank()) {
            showToast(R.string.auth_error_empty_fields)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast(R.string.auth_error_invalid_email)
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            showToast(R.string.auth_error_password_short)
            return
        }

        setSubmitting(true)
        lifecycleScope.launch {
            try {
                val authenticated = withContext(Dispatchers.IO) {
                    val user = userDao.findByEmail(email)
                    if (user == null || !PasswordHasher.matches(password, user.password)) null
                    else {
                        val stored = requireNotNull(user.password)
                        if (PasswordHasher.needsUpgrade(stored)) {
                            val replaced = userDao.replacePasswordIfUnchanged(
                                user.id, stored, PasswordHasher.hash(password)
                            )
                            if (replaced == 0 && !PasswordHasher.matches(password, userDao.getById(user.id)?.password)) {
                                return@withContext null
                            }
                        }
                        user to ProfileDefaults.ensureExists(database.profileDao(), user.id)
                    }
                }
                if (authenticated == null) {
                    showToast(R.string.auth_error_invalid_credentials)
                    return@launch
                }

                val (user, profile) = authenticated
                check(user.id > 0)
                getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_USER_ID, user.id)
                    .apply()

                val destination = if (profile.onboarded) MainActivity::class.java
                    else OnboardingActivity::class.java
                startActivity(Intent(this@LoginActivity, destination).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Unable to sign in", error)
                showToast(R.string.auth_error_unknown)
            } finally {
                setSubmitting(false)
            }
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        formUi.setSubmitting(submitting)
        buttonSubmit.isEnabled = !submitting
        buttonToggleMode.isEnabled = !submitting
        editEmail.isEnabled = !submitting
        editPassword.isEnabled = !submitting
        buttonSubmit.setText(
            if (submitting) R.string.auth_submit_loading else R.string.auth_login_submit
        )
    }

    private fun showToast(messageResId: Int) {
        formUi.showError(messageResId)
    }

    companion object {
        const val PREFS_AUTH = "auth"
        const val KEY_USER_ID = "user_id"
        internal const val MIN_PASSWORD_LENGTH = 6
        private const val TAG = "LoginActivity"
    }
}
