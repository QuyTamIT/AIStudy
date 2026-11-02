package com.aistudy.aistudy.auth

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.aistudy.aistudy.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

/** Display-only behavior shared by the two forms; authentication stays in the Activities. */
internal class AuthFormUi(private val activity: AppCompatActivity) {
    private val email = activity.findViewById<TextInputLayout>(R.id.input_email)
    private val password = activity.findViewById<TextInputLayout>(R.id.input_password)
    private val name: TextInputLayout? = activity.findViewById(R.id.input_full_name)
    private val fields = listOfNotNull(name, email, password)
    private val message = activity.findViewById<TextView>(R.id.tv_auth_error)
    private val submit = activity.findViewById<MaterialButton>(R.id.btn_submit)

    init {
        fields.forEach { field ->
            field.editText?.doAfterTextChanged {
                field.error = null
                message.visibility = View.GONE
            }
        }
        password.editText?.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                if (submit.isEnabled) submit.performClick()
                true
            } else false
        }
    }

    fun showError(messageResId: Int) {
        fields.forEach { it.error = null }
        message.visibility = View.GONE
        val targets = when (messageResId) {
            R.string.auth_error_name_required -> listOfNotNull(name)
            R.string.auth_error_empty_fields -> listOf(email, password).filter { it.editText?.text.isNullOrBlank() }
            R.string.auth_error_invalid_email, R.string.auth_error_email_taken -> listOf(email)
            R.string.auth_error_password_short -> listOf(password)
            else -> emptyList()
        }
        if (targets.isEmpty()) {
            message.setText(messageResId)
            message.visibility = View.VISIBLE
        } else {
            targets.forEach { it.error = activity.getString(messageResId) }
            targets.first().editText?.requestFocus()
        }
    }

    fun setSubmitting(submitting: Boolean) {
        fields.forEach { it.isEnabled = !submitting }
        activity.findViewById<View>(R.id.progress_auth).visibility = if (submitting) View.VISIBLE else View.GONE
        submit.setIconResource(if (submitting) 0 else R.drawable.ic_study_arrow)
    }
}
