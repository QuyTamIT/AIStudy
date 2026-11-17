package com.aistudy.aistudy.ask

import com.aistudy.aistudy.R
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiErrorHandlingTest {

    @Test
    fun quotaExhaustedHasVietnameseMessageAndDisablesRetry() {
        val error = GeminiClient.RequestException(
            statusCode = 429,
            reason = GeminiClient.ErrorReason.QUOTA_EXHAUSTED
        )
        val info = AiErrorMessages.getErrorInfo(error)

        assertEquals(R.string.ai_error_title_quota_exhausted, info.titleRes)
        assertEquals(R.string.ask_error_quota_exhausted, info.messageRes)
        assertTrue(info.isQuotaError)
        assertTrue(info.isDailyQuotaExhausted)
        assertFalse("Quá hạn ngạch theo ngày không được thử lại tự động để tránh spam", info.canRetry)
    }

    @Test
    fun rateLimitedHasVietnameseMessageAndEnablesSafeRetryWithBackoff() {
        val error = GeminiClient.RequestException(
            statusCode = 429,
            reason = GeminiClient.ErrorReason.RATE_LIMITED,
            retryAfterMillis = 5_000L
        )
        val info = AiErrorMessages.getErrorInfo(error)

        assertEquals(R.string.ai_error_title_quota, info.titleRes)
        assertEquals(R.string.ask_error_rate_limit, info.messageRes)
        assertTrue(info.isQuotaError)
        assertFalse(info.isDailyQuotaExhausted)
        assertTrue(info.canRetry)
        assertEquals(5, info.retryDelaySeconds)
    }

    @Test
    fun billingErrorDisablesRetryAndExplainsIssue() {
        val error = GeminiClient.RequestException(
            statusCode = 403,
            reason = GeminiClient.ErrorReason.BILLING_DISABLED
        )
        val info = AiErrorMessages.getErrorInfo(error)

        assertEquals(R.string.ai_error_title_billing, info.titleRes)
        assertEquals(R.string.ask_error_billing, info.messageRes)
        assertFalse(info.isQuotaError)
        assertFalse(info.canRetry)
    }

    @Test
    fun authAndApiKeyErrorsDisableRetry() {
        for (reason in listOf(
            GeminiClient.ErrorReason.API_KEY_INVALID,
            GeminiClient.ErrorReason.API_KEY_EXPIRED,
            GeminiClient.ErrorReason.API_KEY_RESTRICTED,
            GeminiClient.ErrorReason.SERVICE_DISABLED
        )) {
            val error = GeminiClient.RequestException(401, reason)
            val info = AiErrorMessages.getErrorInfo(error)

            assertEquals(R.string.ai_error_title_auth, info.titleRes)
            assertFalse("Lỗi khóa API không được thử lại khi chưa cấu hình lại", info.canRetry)
            assertFalse(info.isQuotaError)
        }
    }

    @Test
    fun networkAndTimeoutErrorsAreRetryable() {
        val timeoutInfo = AiErrorMessages.getErrorInfo(SocketTimeoutException("Read timed out"))
        assertEquals(R.string.ai_error_title_timeout, timeoutInfo.titleRes)
        assertEquals(R.string.ask_error_timeout, timeoutInfo.messageRes)
        assertTrue(timeoutInfo.canRetry)

        val networkInfo = AiErrorMessages.getErrorInfo(IOException("Failed to connect to host"))
        assertEquals(R.string.ai_error_title_network, networkInfo.titleRes)
        assertEquals(R.string.ask_error_network, networkInfo.messageRes)
        assertTrue(networkInfo.canRetry)
    }

    @Test
    fun server5xxErrorsAreRetryable() {
        for (status in listOf(500, 502, 503)) {
            val error = GeminiClient.RequestException(status, GeminiClient.ErrorReason.UNKNOWN)
            val info = AiErrorMessages.getErrorInfo(error)

            assertEquals(R.string.ai_error_title_server, info.titleRes)
            assertEquals(R.string.ask_error_service, info.messageRes)
            assertTrue(info.canRetry)
        }
    }

    @Test
    fun generic429WithoutViolationUsesQuotaWarningAndSafeRetry() {
        val error = GeminiClient.RequestException(429, GeminiClient.ErrorReason.UNKNOWN)
        val info = AiErrorMessages.getErrorInfo(error)

        assertEquals(R.string.ai_error_title_quota, info.titleRes)
        assertEquals(R.string.ask_error_quota, info.messageRes)
        assertTrue(info.isQuotaError)
        assertTrue(info.canRetry)
        assertEquals(5, info.retryDelaySeconds)
    }
}
