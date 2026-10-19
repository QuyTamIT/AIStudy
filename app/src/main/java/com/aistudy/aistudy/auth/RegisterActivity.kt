package com.aistudy.aistudy.auth

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.User
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var editFullName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var buttonSubmit: MaterialButton
    private lateinit var buttonToggleMode: TextView
    private val userDao by lazy { AppDatabase.getInstance(applicationContext).userDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.auth_activity_register)

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val padding = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)

        editFullName = findViewById(R.id.edit_full_name)
        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        buttonSubmit = findViewById(R.id.btn_submit)
        buttonToggleMode = findViewById(R.id.btn_toggle_mode)

        buttonSubmit.setOnClickListener { register() }
        buttonToggleMode.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
    }

    private fun register() {
        val fullName = editFullName.text.toString().trim()
        val email = editEmail.text.toString().trim().lowercase(Locale.ROOT)
        val password = editPassword.text.toString()

        if (fullName.isBlank()) {
            showToast(R.string.auth_error_name_required)
            return
        }
        if (email.isBlank() || password.isBlank()) {
            showToast(R.string.auth_error_empty_fields)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast(R.string.auth_error_invalid_email)
            return
        }
        if (password.length < LoginActivity.MIN_PASSWORD_LENGTH) {
            showToast(R.string.auth_error_password_short)
            return
        }

        setSubmitting(true)
        lifecycleScope.launch {
            try {
                val userId = withContext(Dispatchers.IO) {
                    if (userDao.findByEmail(email) != null) {
                        return@withContext null
                    }
                    userDao.insert(User().apply {
                        this.fullName = fullName
                        this.email = email
                        this.password = password
                    })
                }
                if (userId == null) {
                    showToast(R.string.auth_error_email_taken)
                    return@launch
                }

                check(userId > 0)
                showToast(R.string.auth_success_register)
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SQLiteConstraintException) {
                showToast(R.string.auth_error_email_taken)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to register", error)
                showToast(R.string.auth_error_unknown)
            } finally {
                setSubmitting(false)
            }
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        buttonSubmit.isEnabled = !submitting
        buttonToggleMode.isEnabled = !submitting
        editFullName.isEnabled = !submitting
        editEmail.isEnabled = !submitting
        editPassword.isEnabled = !submitting
        buttonSubmit.setText(
            if (submitting) R.string.auth_submit_loading else R.string.auth_register_submit
        )
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "RegisterActivity"
    }
}
