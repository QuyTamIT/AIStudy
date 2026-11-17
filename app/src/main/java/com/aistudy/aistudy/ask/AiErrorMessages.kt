package com.aistudy.aistudy.ask

import androidx.annotation.StringRes
import com.aistudy.aistudy.R
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONException

internal object AiErrorMessages {
    @StringRes
    fun message(error: Exception, @StringRes fallback: Int): Int = when (error) {
        is GeminiClient.RequestException -> when (error.reason) {
            GeminiClient.ErrorReason.API_KEY_INVALID,
            GeminiClient.ErrorReason.API_KEY_EXPIRED -> R.string.ask_error_key_invalid
            GeminiClient.ErrorReason.API_KEY_RESTRICTED,
            GeminiClient.ErrorReason.SERVICE_DISABLED -> R.string.ask_error_access
            GeminiClient.ErrorReason.BILLING_DISABLED -> R.string.ask_error_billing
            GeminiClient.ErrorReason.RATE_LIMITED -> R.string.ask_error_rate_limit
            GeminiClient.ErrorReason.QUOTA_EXHAUSTED -> R.string.ask_error_quota_exhausted
            GeminiClient.ErrorReason.UNKNOWN -> when (error.statusCode) {
                401 -> R.string.ask_error_key_invalid
                403 -> R.string.ask_error_access
                404 -> R.string.ask_error_model
                408, 504 -> R.string.ask_error_timeout
                413 -> R.string.ask_error_too_large
                429 -> R.string.ask_error_quota
                in 500..599 -> R.string.ask_error_service
                else -> R.string.ask_error_request
            }
        }
        is SocketTimeoutException -> R.string.ask_error_timeout
        is GeminiClient.InvalidResponseException, is JSONException -> R.string.ask_error_response
        is IOException -> R.string.ask_error_network
        else -> fallback
    }
}
