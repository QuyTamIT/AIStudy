package com.aistudy.aistudy.ask

import android.os.Looper
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GeminiClient {
    val CANDIDATE_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-3.1-flash-lite",
        "gemini-3.6-flash"
    )
    private const val MAX_RETRIES = 2
    private const val INITIAL_BACKOFF_MS = 1_000L
    private const val MAX_RETRY_DELAY_MS = 30_000L
    private val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    private val RETRY_DURATION = Regex("[0-9]+(?:\\.[0-9]{1,9})?s")
    private val CAMEL_WORD = Regex("([a-z])([A-Z])")
    private val QUOTA_WINDOW = Regex("(?:^|[^a-z])per[_-](minute|day)(?=$|[^a-z])")

    enum class ErrorReason {
        API_KEY_INVALID, API_KEY_EXPIRED, API_KEY_RESTRICTED,
        SERVICE_DISABLED, BILLING_DISABLED, RATE_LIMITED, QUOTA_EXHAUSTED, UNKNOWN
    }

    class RequestException(
        val statusCode: Int,
        val reason: ErrorReason = ErrorReason.UNKNOWN,
        val retryAfterMillis: Long? = null
    ) : IOException("Gemini HTTP $statusCode (${reason.name})")
    class InvalidResponseException : IOException("Gemini did not return a complete answer")


    // Blocking REST call: callers must use a background executor.
    fun ask(
        apiKey: String,
        prompt: String,
        responseSchema: JSONObject,
        systemInstruction: String,
        trace: AiRequestTrace,
        imageBase64: String? = null,
        mimeType: String? = null
    ): String {
        require(apiKey.isNotBlank()) { "Gemini API key is missing" }
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Gemini requests must run on a background thread"
        }
        return execute(apiKey, prompt, responseSchema, systemInstruction, trace, imageBase64, mimeType)
    }

    internal fun execute(
        apiKey: String,
        prompt: String,
        responseSchema: JSONObject,
        systemInstruction: String,
        trace: AiRequestTrace,
        imageBase64: String? = null,
        mimeType: String? = null,
        candidateModels: List<String> = CANDIDATE_MODELS,
        connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
        wait: (Long) -> Unit = Thread::sleep,
        wallClock: () -> Long = System::currentTimeMillis,
        errorJson: (String) -> JSONObject = ::readGeminiErrorJson
    ): String {
        trace.beginGeneration()
        try {
            var retries = 0
            var modelIndex = 0
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val currentModel = candidateModels.getOrElse(modelIndex) { candidateModels.last() }
                try {
                    return askOnce(apiKey, prompt, responseSchema, systemInstruction, trace,
                        imageBase64, mimeType, connectionFactory, wallClock, errorJson, currentModel)
                } catch (error: RequestException) {
                    if (error.reason == ErrorReason.QUOTA_EXHAUSTED ||
                        error.statusCode !in RETRYABLE_STATUS_CODES || retries >= MAX_RETRIES) throw error
                    val backoffMillis = (INITIAL_BACKOFF_MS * (1L shl retries))
                        .coerceAtMost(MAX_RETRY_DELAY_MS)
                    val delayMillis = maxOf(backoffMillis, error.retryAfterMillis ?: 0L)
                    if (delayMillis > MAX_RETRY_DELAY_MS) throw error
                    retries++
                    if (modelIndex + 1 < candidateModels.size) {
                        modelIndex++
                    }
                    wait(delayMillis)
                }
            }
        } finally {
            trace.endGeneration()
        }
    }

    private fun askOnce(
        apiKey: String,
        prompt: String,
        responseSchema: JSONObject,
        systemInstruction: String,
        trace: AiRequestTrace,
        imageBase64: String?,
        mimeType: String?,
        connectionFactory: (URL) -> HttpURLConnection,
        wallClock: () -> Long,
        errorJson: (String) -> JSONObject,
        model: String = CANDIDATE_MODELS.first()
    ): String {
        trace.beginAttempt()
        var connection: HttpURLConnection? = null
        var status: Int? = null
        var outcome = AiRequestTrace.Outcome.REQUEST_FAILED
        var reason = AiRequestTrace.Reason.NETWORK_ERROR
        try {
            val request = connectionFactory(URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"))
            connection = request
            request.requestMethod = "POST"
            request.connectTimeout = 30_000
            request.readTimeout = 90_000
            request.instanceFollowRedirects = false
            request.doOutput = true
            request.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            request.setRequestProperty("x-goog-api-key", apiKey)
            val generationConfig = JSONObject().put("responseMimeType", "application/json")
                .put("responseJsonSchema", responseSchema)
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            if (!imageBase64.isNullOrEmpty()) {
                parts.put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", mimeType?.takeIf { it.isNotEmpty() } ?: "image/jpeg")
                    .put("data", imageBase64)))
            }
            val payload = JSONObject()
                .put("systemInstruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", generationConfig)
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            request.setFixedLengthStreamingMode(bytes.size)
            request.outputStream.use { it.write(bytes) }

            val code = request.responseCode
            status = code
            if (code !in 200..299) {
                // A failed error-body read must still follow the original HTTP retry policy.
                val body = try {
                    request.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                } catch (_: IOException) { "" }
                val envelope = try { errorJson(body) }
                    catch (_: JSONException) { null }
                    catch (_: IOException) { null }
                    catch (_: NumberFormatException) { null }
                if (envelope != null) trace.captureResponse(envelope)
                throw readRequestError(request, code, envelope, wallClock)
            }
            val body = request.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = JSONObject(body)
            trace.captureResponse(response)
            outcome = AiRequestTrace.Outcome.INVALID_RESPONSE
            val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            reason = AiRequestTrace.Reason.MISSING_CONTENT
            if (candidate == null) throw InvalidResponseException()
            reason = AiRequestTrace.Reason.NON_STOP
            if (candidate.optString("finishReason") != "STOP") throw InvalidResponseException()
            reason = AiRequestTrace.Reason.MISSING_CONTENT
            val candidateParts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw InvalidResponseException()
            val answer = buildString {
                for (index in 0 until candidateParts.length()) {
                    val part = candidateParts.optJSONObject(index) ?: continue
                    if (!part.optBoolean("thought", false)) append(part.optString("text", ""))
                }
            }.trim()
            reason = AiRequestTrace.Reason.EMPTY_CONTENT
            if (answer.isBlank()) throw InvalidResponseException()
            outcome = AiRequestTrace.Outcome.SUCCESS
            reason = AiRequestTrace.Reason.NONE
            return answer
        } catch (error: Exception) {
            when (error) {
                is RequestException -> {
                    outcome = AiRequestTrace.Outcome.REQUEST_FAILED
                    reason = if (error.reason == ErrorReason.UNKNOWN) AiRequestTrace.Reason.HTTP_ERROR
                        else AiRequestTrace.Reason.valueOf(error.reason.name)
                }
                is JSONException -> {
                    outcome = AiRequestTrace.Outcome.INVALID_RESPONSE
                    reason = AiRequestTrace.Reason.INVALID_ENVELOPE
                }
                is InterruptedException -> {
                    outcome = AiRequestTrace.Outcome.CANCELLED
                    reason = AiRequestTrace.Reason.INTERRUPTED
                }
            }
            throw error
        } finally {
            try { connection?.disconnect() } finally { trace.endAttempt(status, outcome, reason, model) }
        }
    }

    private fun readRequestError(
        connection: HttpURLConnection,
        code: Int,
        envelope: JSONObject?,
        wallClock: () -> Long
    ): RequestException {
        var retryAfterMillis = parseRetryAfter(connection.getHeaderField("Retry-After"), wallClock())
        val details = envelope?.optJSONObject("error")?.optJSONArray("details") ?: JSONArray()
        for (index in 0 until details.length()) {
            val detail = details.optJSONObject(index) ?: continue
            if (detail.optString("@type") == "type.googleapis.com/google.rpc.RetryInfo") {
                parseRetryDelay(detail.opt("retryDelay"))?.let {
                    retryAfterMillis = maxOf(retryAfterMillis ?: 0L, it)
                }
            }
        }
        for (index in 0 until details.length()) {
            val reason = when (details.optJSONObject(index)?.optString("reason")) {
                "API_KEY_INVALID" -> ErrorReason.API_KEY_INVALID
                "API_KEY_EXPIRED" -> ErrorReason.API_KEY_EXPIRED
                "API_KEY_SERVICE_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED",
                "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_ANDROID_APP_BLOCKED",
                "API_KEY_IOS_APP_BLOCKED", "API_KEY_RESTRICTION_VIOLATED" -> ErrorReason.API_KEY_RESTRICTED
                "SERVICE_DISABLED" -> ErrorReason.SERVICE_DISABLED
                "BILLING_DISABLED" -> ErrorReason.BILLING_DISABLED
                else -> ErrorReason.UNKNOWN
            }
            if (reason != ErrorReason.UNKNOWN) return RequestException(code, reason, retryAfterMillis)
        }
        val errorMessage = envelope?.optJSONObject("error")?.optString("message").orEmpty()
        if (code == 403 && (errorMessage.contains("billing", ignoreCase = true) || errorMessage.contains("paid tier", ignoreCase = true))) {
            return RequestException(code, ErrorReason.BILLING_DISABLED, retryAfterMillis)
        }
        val reason = if (code == 429) quotaReason(details) else ErrorReason.UNKNOWN
        return RequestException(code, reason, retryAfterMillis)
    }

    private fun quotaReason(details: JSONArray): ErrorReason {
        var hasViolation = false
        var allPerMinute = true
        for (index in 0 until details.length()) {
            val detail = details.optJSONObject(index) ?: continue
            if (detail.optString("@type") != "type.googleapis.com/google.rpc.QuotaFailure") continue
            val violations = detail.optJSONArray("violations")
            if (violations == null || violations.length() == 0) allPerMinute = false
            if (violations == null) continue
            for (item in 0 until violations.length()) {
                hasViolation = true
                val violation = violations.optJSONObject(item)
                val windows = listOf("quotaId", "quotaMetric").flatMap { field ->
                    val label = (violation?.opt(field) as? String).orEmpty()
                        .replace(CAMEL_WORD, "$1_$2").lowercase(Locale.ROOT)
                    QUOTA_WINDOW.findAll(label).map { it.groupValues[1] }.toList()
                }
                val rawQuotaValue = violation?.opt("quotaValue")
                val quotaValue = parseQuotaValue(rawQuotaValue)
                // Avoid immediate retries when a daily limit or unavailable quota is reported.
                if (quotaValue == 0L || "day" in windows) {
                    return ErrorReason.QUOTA_EXHAUSTED
                }
                val invalidValue = rawQuotaValue != null && rawQuotaValue != JSONObject.NULL && quotaValue == null
                if ("minute" !in windows || invalidValue) allPerMinute = false
            }
        }
        return if (hasViolation && allPerMinute) ErrorReason.RATE_LIMITED else ErrorReason.UNKNOWN
    }

    private fun parseQuotaValue(value: Any?): Long? {
        if (value !is Number && value !is String) return null
        return try {
            BigDecimal(value.toString()).longValueExact().takeIf { it >= 0L }
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun parseRetryDelay(value: Any?): Long? {
        if (value !is String || !RETRY_DURATION.matches(value)) return null
        return BigDecimal(value.dropLast(1)).movePointRight(3).setScale(0, RoundingMode.CEILING)
            .min(BigDecimal.valueOf(Long.MAX_VALUE)).toLong()
    }

    private fun parseRetryAfter(value: String?, nowMillis: Long): Long? {
        val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (header.all { it in '0'..'9' }) {
            val seconds = header.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
        }
        return try {
            val retryAt = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            val delay = Duration.between(Instant.ofEpochMilli(nowMillis), retryAt)
            if (delay.isNegative) 0L else delay.toMillis()
        } catch (_: DateTimeParseException) {
            null
        } catch (_: ArithmeticException) {
            // Preserve the HTTP error and treat an unrepresentable wait as beyond the retry budget.
            Long.MAX_VALUE
        }
    }
}
