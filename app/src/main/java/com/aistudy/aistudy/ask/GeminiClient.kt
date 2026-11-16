package com.aistudy.aistudy.ask

import android.os.Looper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GeminiClient {
    private const val MODEL = "gemini-3.6-flash"
    private const val MAX_RETRIES = 2
    private const val INITIAL_BACKOFF_MS = 1_000L
    private const val MAX_RETRY_DELAY_MS = 30_000L
    private val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)

    enum class ErrorReason {
        API_KEY_INVALID, API_KEY_EXPIRED, API_KEY_RESTRICTED,
        SERVICE_DISABLED, BILLING_DISABLED, UNKNOWN
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
        connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
        wait: (Long) -> Unit = Thread::sleep,
        wallClock: () -> Long = System::currentTimeMillis
    ): String {
        trace.beginGeneration()
        try {
            var retries = 0
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                try {
                    return askOnce(apiKey, prompt, responseSchema, systemInstruction, trace,
                        imageBase64, mimeType, connectionFactory, wallClock)
                } catch (error: RequestException) {
                    if (error.statusCode !in RETRYABLE_STATUS_CODES || retries >= MAX_RETRIES) throw error
                    val backoffMillis = (INITIAL_BACKOFF_MS * (1L shl retries))
                        .coerceAtMost(MAX_RETRY_DELAY_MS)
                    val delayMillis = maxOf(backoffMillis, error.retryAfterMillis ?: 0L)
                    if (delayMillis > MAX_RETRY_DELAY_MS) throw error
                    retries++
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
        wallClock: () -> Long
    ): String {
        trace.beginAttempt()
        var connection: HttpURLConnection? = null
        var status: Int? = null
        var outcome = AiRequestTrace.Outcome.REQUEST_FAILED
        var reason = AiRequestTrace.Reason.NETWORK_ERROR
        try {
            val request = connectionFactory(URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"))
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
                val envelope = try { JSONObject(body) } catch (_: JSONException) { null }
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
            try { connection?.disconnect() } finally { trace.endAttempt(status, outcome, reason) }
        }
    }

    private fun readRequestError(
        connection: HttpURLConnection,
        code: Int,
        envelope: JSONObject?,
        wallClock: () -> Long
    ): RequestException {
        val retryAfterMillis = parseRetryAfter(connection.getHeaderField("Retry-After"), wallClock())
        val genericError = RequestException(code, retryAfterMillis = retryAfterMillis)
        val error = envelope?.optJSONObject("error") ?: return genericError
        val details = error.optJSONArray("details") ?: return genericError
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
        return genericError
    }

    private fun parseRetryAfter(value: String?, nowMillis: Long): Long? {
        val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (header.all { it in '0'..'9' }) {
            val seconds = header.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
        }
        return try {
            val retryAt = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
            (retryAt - nowMillis).coerceAtLeast(0L)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
