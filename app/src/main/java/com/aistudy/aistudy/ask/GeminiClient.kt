package com.aistudy.aistudy.ask

import android.os.Looper
import android.util.Log
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
    private const val TAG = "GeminiClient"
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

    fun ask(apiKey: String, prompt: String): String =
        ask(apiKey, prompt, responseSchema = null, imageBase64 = null, mimeType = null)

    fun ask(apiKey: String, prompt: String, responseSchema: JSONObject?): String =
        ask(apiKey, prompt, responseSchema, imageBase64 = null, mimeType = null)

    fun ask(apiKey: String, prompt: String, imageBase64: String?, mimeType: String?): String =
        ask(apiKey, prompt, responseSchema = null, imageBase64 = imageBase64, mimeType = mimeType)

    // Blocking REST call: callers must use a background executor.
    fun ask(
        apiKey: String,
        prompt: String,
        responseSchema: JSONObject? = null,
        imageBase64: String? = null,
        mimeType: String? = null
    ): String {
        require(apiKey.isNotBlank()) { "Gemini API key is missing" }
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Gemini requests must run on a background thread"
        }
        var retries = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            try {
                return askOnce(apiKey, prompt, retries, responseSchema, imageBase64, mimeType)
            } catch (error: RequestException) {
                if (error.statusCode !in RETRYABLE_STATUS_CODES || retries >= MAX_RETRIES) {
                    throw error
                }
                val backoffMillis = (INITIAL_BACKOFF_MS * (1L shl retries))
                    .coerceAtMost(MAX_RETRY_DELAY_MS)
                val delayMillis = maxOf(backoffMillis, error.retryAfterMillis ?: 0L)
                // Never retry earlier than Retry-After, or keep the user waiting indefinitely.
                if (delayMillis > MAX_RETRY_DELAY_MS) {
                    Log.w(TAG, "retry skipped httpCode=${error.statusCode} retry=$retries/$MAX_RETRIES reason=retry_after_exceeds_limit")
                    throw error
                }
                retries++
                Log.d(TAG, "retry scheduled httpCode=${error.statusCode} retry=$retries/$MAX_RETRIES delayMs=$delayMillis")
                // shutdownNow() in AskFragment interrupts this wait when its view is destroyed.
                Thread.sleep(delayMillis)
            }
        }
    }

    private fun askOnce(
        apiKey: String,
        prompt: String,
        retries: Int,
        responseSchema: JSONObject?,
        imageBase64: String?,
        mimeType: String?
    ): String {
        val hasImage = !imageBase64.isNullOrEmpty()
        Log.d(TAG, "request start model=$MODEL promptLength=${prompt.length} hasImage=$hasImage retry=$retries/$MAX_RETRIES")

        val connection = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        ).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 90_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Keep credentials out of URLs, logs, and exception messages.
            connection.setRequestProperty("x-goog-api-key", apiKey)
            val generationConfig = JSONObject().put("responseMimeType", "application/json")
            if (responseSchema != null) generationConfig.put("responseJsonSchema", responseSchema)

            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))

            if (hasImage) {
                val mime = if (mimeType.isNullOrEmpty()) "image/jpeg" else mimeType
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", mime)
                            .put("data", imageBase64)
                    )
                )
            }

            val payload = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", parts)
                    )
                )
                .put("generationConfig", generationConfig)

            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            Log.d(TAG, "response httpCode=$code retry=$retries/$MAX_RETRIES")
            if (code !in 200..299) {
                val error = readRequestError(connection, code)
                // Only allowlisted reason codes are logged; never the raw response body.
                Log.w(TAG, "request failed httpCode=$code retry=$retries/$MAX_RETRIES reason=${error.reason.name}")
                throw error
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val candidate = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
                ?: throw InvalidResponseException()
            if (candidate.optString("finishReason") != "STOP") throw InvalidResponseException()
            val candidateParts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw InvalidResponseException()
            val answer = buildString {
                for (index in 0 until candidateParts.length()) {
                    val part = candidateParts.optJSONObject(index) ?: continue
                    if (!part.optBoolean("thought", false)) append(part.optString("text", ""))
                }
            }.trim()
            if (answer.isBlank()) throw InvalidResponseException()
            return answer
        } finally {
            connection.disconnect()
        }
    }

    private fun readRequestError(connection: HttpURLConnection, code: Int): RequestException {
        val retryAfterMillis = parseRetryAfter(connection.getHeaderField("Retry-After"))
        val genericError = RequestException(code, retryAfterMillis = retryAfterMillis)
        val body = try {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (_: IOException) {
            return genericError
        }
        val error = try {
            JSONObject(body).optJSONObject("error")
        } catch (_: JSONException) {
            null
        } ?: return genericError
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

    private fun parseRetryAfter(value: String?): Long? {
        val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (header.all { it in '0'..'9' }) {
            val seconds = header.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
        }
        return try {
            val retryAt = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
            (retryAt - System.currentTimeMillis()).coerceAtLeast(0L)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
