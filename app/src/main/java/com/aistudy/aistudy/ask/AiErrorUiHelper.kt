package com.aistudy.aistudy.ask

import android.os.CountDownTimer
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aistudy.aistudy.R
import com.google.android.material.button.MaterialButton

internal class AiErrorUiHelper(
    private val root: View,
    private val onRetry: () -> Unit,
    private val onGoHome: () -> Unit
) {
    private val container: View? = root.findViewById(R.id.card_ai_error)
    private val ivIcon: ImageView? = root.findViewById(R.id.iv_error_icon)
    private val tvTitle: TextView? = root.findViewById(R.id.tv_error_title)
    private val tvMessage: TextView? = root.findViewById(R.id.tv_error_message)
    private val tvHint: TextView? = root.findViewById(R.id.tv_error_hint)
    private val btnRetry: MaterialButton? = root.findViewById(R.id.btn_error_retry)
    private val btnHome: MaterialButton? = root.findViewById(R.id.btn_error_home)

    private var cooldownTimer: CountDownTimer? = null

    init {
        btnHome?.setOnClickListener {
            cancelCooldown()
            onGoHome()
        }
        btnRetry?.setOnClickListener {
            cancelCooldown()
            hide()
            onRetry()
        }
    }

    fun showError(info: AiErrorMessages.AiErrorInfo) {
        cancelCooldown()
        val context = root.context ?: return

        tvTitle?.setText(info.titleRes)
        tvMessage?.setText(info.messageRes)

        // Accent / tint based on error severity
        if (info.isQuotaError) {
            ivIcon?.setColorFilter(ContextCompat.getColor(context, R.color.streak))
            if (info.isDailyQuotaExhausted) {
                tvHint?.setText(R.string.ai_quota_daily_hint)
                tvHint?.visibility = View.VISIBLE
            } else {
                tvHint?.visibility = View.GONE
            }
        } else {
            ivIcon?.setColorFilter(ContextCompat.getColor(context, R.color.answer_error))
            tvHint?.visibility = View.GONE
        }

        // Action buttons
        if (info.canRetry) {
            btnRetry?.visibility = View.VISIBLE
            btnRetry?.isEnabled = true
            btnRetry?.setText(R.string.ai_action_retry)

            if (info.retryDelaySeconds > 0) {
                startCooldown(info.retryDelaySeconds)
            }
        } else {
            btnRetry?.visibility = View.GONE
        }

        container?.visibility = View.VISIBLE
    }

    private fun startCooldown(seconds: Int) {
        btnRetry?.isEnabled = false
        val context = root.context ?: return

        cooldownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000L).toInt() + 1
                tvHint?.text = context.getString(R.string.ai_retry_wait, remaining)
                tvHint?.visibility = View.VISIBLE
                btnRetry?.text = context.getString(R.string.ai_retry_wait, remaining)
            }

            override fun onFinish() {
                tvHint?.visibility = View.GONE
                btnRetry?.isEnabled = true
                btnRetry?.setText(R.string.ai_action_retry)
            }
        }.start()
    }

    fun hide() {
        cancelCooldown()
        container?.visibility = View.GONE
    }

    fun cancelCooldown() {
        cooldownTimer?.cancel()
        cooldownTimer = null
    }
}
