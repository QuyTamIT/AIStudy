package com.aistudy.aistudy.ask

import androidx.annotation.StringRes
import com.aistudy.aistudy.R
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONException

internal object AiErrorMessages {

    data class AiErrorInfo(
        @get:StringRes val titleRes: Int,
        @get:StringRes val messageRes: Int,
        val canRetry: Boolean,
        val isQuotaError: Boolean,
        val isDailyQuotaExhausted: Boolean = false,
        val retryDelaySeconds: Int = 0
    )

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

    fun getErrorInfo(error: Exception, @StringRes fallback: Int = R.string.ask_error_request): AiErrorInfo {
        val msg = message(error, fallback)
        return when (error) {
            is GeminiClient.RequestException -> when (error.reason) {
                GeminiClient.ErrorReason.QUOTA_EXHAUSTED -> AiErrorInfo(
                    titleRes = R.string.ai_error_title_quota_exhausted,
                    messageRes = msg,
                    canRetry = false,
                    isQuotaError = true,
                    isDailyQuotaExhausted = true
                )
                GeminiClient.ErrorReason.RATE_LIMITED -> AiErrorInfo(
                    titleRes = R.string.ai_error_title_quota,
                    messageRes = msg,
                    canRetry = true,
                    isQuotaError = true,
                    retryDelaySeconds = ((error.retryAfterMillis ?: 3000L) / 1000L).toInt().coerceIn(2, 60)
                )
                GeminiClient.ErrorReason.BILLING_DISABLED -> AiErrorInfo(
                    titleRes = R.string.ai_error_title_billing,
                    messageRes = msg,
                    canRetry = false,
                    isQuotaError = false
                )
                GeminiClient.ErrorReason.API_KEY_INVALID,
                GeminiClient.ErrorReason.API_KEY_EXPIRED,
                GeminiClient.ErrorReason.API_KEY_RESTRICTED,
                GeminiClient.ErrorReason.SERVICE_DISABLED -> AiErrorInfo(
                    titleRes = R.string.ai_error_title_auth,
                    messageRes = msg,
                    canRetry = false,
                    isQuotaError = false
                )
                GeminiClient.ErrorReason.UNKNOWN -> when (error.statusCode) {
                    401, 403 -> AiErrorInfo(
                        titleRes = R.string.ai_error_title_auth,
                        messageRes = msg,
                        canRetry = false,
                        isQuotaError = false
                    )
                    408, 504 -> AiErrorInfo(
                        titleRes = R.string.ai_error_title_timeout,
                        messageRes = msg,
                        canRetry = true,
                        isQuotaError = false,
                        retryDelaySeconds = 2
                    )
                    413 -> AiErrorInfo(
                        titleRes = R.string.ai_error_title_general,
                        messageRes = msg,
                        canRetry = false,
                        isQuotaError = false
                    )
                    429 -> AiErrorInfo(
                        titleRes = R.string.ai_error_title_quota,
                        messageRes = msg,
                        canRetry = true,
                        isQuotaError = true,
                        retryDelaySeconds = 5
                    )
                    in 500..599 -> AiErrorInfo(
                        titleRes = R.string.ai_error_title_server,
                        messageRes = msg,
                        canRetry = true,
                        isQuotaError = false,
                        retryDelaySeconds = 3
                    )
                    else -> AiErrorInfo(
                        titleRes = R.string.ai_error_title_general,
                        messageRes = msg,
                        canRetry = true,
                        isQuotaError = false
                    )
                }
            }
            is SocketTimeoutException -> AiErrorInfo(
                titleRes = R.string.ai_error_title_timeout,
                messageRes = msg,
                canRetry = true,
                isQuotaError = false,
                retryDelaySeconds = 2
            )
            is IOException -> AiErrorInfo(
                titleRes = R.string.ai_error_title_network,
                messageRes = msg,
                canRetry = true,
                isQuotaError = false,
                retryDelaySeconds = 2
            )
            is GeminiClient.InvalidResponseException, is JSONException -> AiErrorInfo(
                titleRes = R.string.ai_error_title_general,
                messageRes = msg,
                canRetry = true,
                isQuotaError = false
            )
            else -> AiErrorInfo(
                titleRes = R.string.ai_error_title_general,
                messageRes = msg,
                canRetry = true,
                isQuotaError = false
            )
        }
    }
}
