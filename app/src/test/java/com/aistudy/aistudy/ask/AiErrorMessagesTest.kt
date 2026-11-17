package com.aistudy.aistudy.ask

import com.aistudy.aistudy.R
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test

class AiErrorMessagesTest {
    @Test fun structuredReasonsOverrideGenericHttpStatus() {
        val expected = mapOf(
            GeminiClient.ErrorReason.API_KEY_INVALID to R.string.ask_error_key_invalid,
            GeminiClient.ErrorReason.API_KEY_EXPIRED to R.string.ask_error_key_invalid,
            GeminiClient.ErrorReason.API_KEY_RESTRICTED to R.string.ask_error_access,
            GeminiClient.ErrorReason.SERVICE_DISABLED to R.string.ask_error_access,
            GeminiClient.ErrorReason.BILLING_DISABLED to R.string.ask_error_billing,
            GeminiClient.ErrorReason.RATE_LIMITED to R.string.ask_error_rate_limit,
            GeminiClient.ErrorReason.QUOTA_EXHAUSTED to R.string.ask_error_quota_exhausted
        )
        for ((reason, message) in expected) {
            assertEquals(reason.name, message,
                AiErrorMessages.message(GeminiClient.RequestException(400, reason), R.string.quiz_error))
        }
    }

    @Test fun unknownReasonUsesHttpStatusIncludingNeutral429() {
        val expected = mapOf(
            400 to R.string.ask_error_request, 422 to R.string.ask_error_request,
            401 to R.string.ask_error_key_invalid, 403 to R.string.ask_error_access,
            404 to R.string.ask_error_model, 408 to R.string.ask_error_timeout,
            504 to R.string.ask_error_timeout, 413 to R.string.ask_error_too_large,
            429 to R.string.ask_error_quota, 500 to R.string.ask_error_service,
            502 to R.string.ask_error_service, 503 to R.string.ask_error_service,
            418 to R.string.ask_error_request
        )
        for ((status, message) in expected) {
            assertEquals("HTTP $status", message,
                AiErrorMessages.message(GeminiClient.RequestException(status), R.string.quiz_error))
        }
    }

    @Test fun timeoutIsNotReportedAsGenericNetworkFailure() {
        assertEquals(R.string.ask_error_timeout,
            AiErrorMessages.message(SocketTimeoutException("private fixture"), R.string.quiz_error))
        assertEquals(R.string.ask_error_network,
            AiErrorMessages.message(IOException("private fixture"), R.string.quiz_error))
    }

    @Test fun invalidApiAndJsonResponsesHaveAConsistentMessage() {
        for (error in listOf(GeminiClient.InvalidResponseException(), JSONException("private fixture"))) {
            assertEquals(R.string.ask_error_response, AiErrorMessages.message(error, R.string.answer_extras_error))
        }
    }

    @Test fun allCallersShareApiMessagesButKeepTheirDomainFallbacks() {
        val fallbacks = listOf(R.string.ask_error_save, R.string.answer_error_save_more,
            R.string.answer_extras_error, R.string.quiz_error)
        for (fallback in fallbacks) {
            assertEquals(R.string.ask_error_key_invalid,
                AiErrorMessages.message(GeminiClient.RequestException(401), fallback))
            assertEquals(R.string.ask_error_quota_exhausted,
                AiErrorMessages.message(GeminiClient.RequestException(429, GeminiClient.ErrorReason.QUOTA_EXHAUSTED), fallback))
            assertEquals(fallback, AiErrorMessages.message(IllegalStateException("private fixture"), fallback))
            assertEquals(fallback, AiErrorMessages.message(IllegalArgumentException("domain validation"), fallback))
        }
    }
}
