package com.aistudy.aistudy.ask

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiErrorParsingInstrumentedTest {
    private class Connection(private val body: String, private val status: Int, private val retryAfter: String?) :
        HttpURLConnection(URL("https://fixture.invalid/")) {
        override fun connect() { }
        override fun usingProxy() = false
        override fun disconnect() { }
        override fun getResponseCode() = status
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getHeaderField(name: String?): String? = if (name == "Retry-After") retryAfter else null
    }

    private fun quotaBody(rawValue: String): String = """
        {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
          "violations":[{"quotaId":"RequestsPerMinute","quotaValue":$rawValue}]}]}}
    """.trimIndent()

    private fun failure(
        body: String,
        status: Int = 429,
        retryAfter: String? = null
    ): Pair<GeminiClient.RequestException, List<Long>> {
        val waits = mutableListOf<Long>()
        val trace = AiRequestTrace(AiRequestTrace.Feature.QUIZ, sink = { })
        val error = assertThrows(GeminiClient.RequestException::class.java) {
            GeminiClient.execute("fixture", "{}", JSONObject(), "fixture", trace,
                connectionFactory = { Connection(body, status, retryAfter) }, wait = { waits.add(it) })
        }
        trace.finish(AiRequestTrace.Outcome.REQUEST_FAILED)
        return error to waits
    }

    @Test fun tinyFractionDoesNotUnderflowIntoZeroQuota() {
        val (error, waits) = failure(quotaBody("1e-400"))
        assertEquals(GeminiClient.ErrorReason.UNKNOWN, error.reason)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test fun fractionDoesNotRoundIntoAnIntegerMinuteLimit() {
        val (error, waits) = failure(quotaBody("1.0000000000000001"))
        assertEquals(GeminiClient.ErrorReason.UNKNOWN, error.reason)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test fun validZeroFormatsNeverRetry() {
        for (value in listOf("0", "0.0", "0e0", "\"0e0\"")) {
            val (error, waits) = failure(quotaBody(value))
            assertEquals(value, GeminiClient.ErrorReason.QUOTA_EXHAUSTED, error.reason)
            assertTrue(waits.isEmpty())
        }
    }

    @Test fun positiveIntegerFormatsAndNullKeepTheMinuteLimit() {
        for (value in listOf("1.0", "1e2", "\"9223372036854775807\"", "null")) {
            val (error, waits) = failure(quotaBody(value))
            assertEquals(value, GeminiClient.ErrorReason.RATE_LIMITED, error.reason)
            assertEquals(listOf(1_000L, 2_000L), waits)
        }
    }

    @Test fun overflowingHttpDatePreservesKeyError() {
        val body = """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}"""
        val (error, waits) = failure(body, status = 401, retryAfter = "Fri, 31 Dec 999999999 23:59:59 GMT")
        assertEquals(401, error.statusCode)
        assertEquals(GeminiClient.ErrorReason.API_KEY_INVALID, error.reason)
        assertEquals(Long.MAX_VALUE, error.retryAfterMillis)
        assertTrue(waits.isEmpty())
    }

    @Test fun oversizedNumericTokensDoNotHideKnownQuotaOrAccessReasons() {
        for (value in listOf("1e1000", "1e2147483648")) {
            for (known in listOf("""{"quotaId":"RequestsPerDay"}""", """{"quotaValue":0}""")) {
                val body = """
                    {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
                      "violations":[{"quotaId":"RequestsPerMinute","quotaValue":$value},$known]}]}}
                """.trimIndent()
                val (error, waits) = failure(body)
                assertEquals(GeminiClient.ErrorReason.QUOTA_EXHAUSTED, error.reason)
                assertTrue(waits.isEmpty())
            }
            val body = """
                {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
                  "violations":[{"quotaValue":$value}]},{"reason":"API_KEY_INVALID"}]}}
            """.trimIndent()
            val (error, waits) = failure(body, status = 401)
            assertEquals(GeminiClient.ErrorReason.API_KEY_INVALID, error.reason)
            assertTrue(waits.isEmpty())
        }
    }
}
