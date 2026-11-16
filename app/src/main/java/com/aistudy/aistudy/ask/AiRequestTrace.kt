package com.aistudy.aistudy.ask

import android.util.Log
import com.aistudy.aistudy.BuildConfig
import java.math.BigDecimal
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One logical AI operation; no user data or persistent state belongs in this trace. */
class AiRequestTrace(
    val feature: Feature,
    private val clock: () -> Long = System::nanoTime,
    private val sink: (JSONObject) -> Unit = { event ->
        if (BuildConfig.DEBUG) Log.d("AIStudyAI", event.toString())
    }
) {
    enum class Feature { ASK_TEXT, ASK_IMAGE, ANSWER_CLASSIFY, MORE_TOPICS, LEARNING_EXTRAS, QUIZ }
    enum class Outcome {
        SUCCESS, REQUEST_FAILED, INVALID_RESPONSE, SAVE_FAILED, SESSION_CHANGED,
        CONTENT_CHANGED, CANCELLED, INSUFFICIENT_SOURCES, KEY_MISSING
    }
    enum class Validation { VALID, INVALID, NOT_RUN }
    enum class Reason {
        NONE, REQUEST_FAILED, HTTP_ERROR, INVALID_ENVELOPE, NON_STOP, MISSING_CONTENT, EMPTY_CONTENT,
        NETWORK_ERROR, INTERRUPTED, INVALID_JSON, INVALID_CONTENT, SESSION_CHANGED,
        CONTENT_CHANGED, SAVE_FAILED, INSUFFICIENT_SOURCES, KEY_MISSING,
        API_KEY_INVALID, API_KEY_EXPIRED, API_KEY_RESTRICTED, SERVICE_DISABLED,
        BILLING_DISABLED, UNKNOWN
    }

    val taskId: String = UUID.randomUUID().toString()
    var attempts: Int = 0
        private set
    private val startedAt = clock()
    private var generationStartedAt: Long? = null
    private var generationNanos = 0L
    private var attemptStartedAt: Long? = null
    private var validationRecorded = false
    internal var contentValidated = false
        private set
    private var finished = false
    private var usage: JSONObject? = null
    private var finishReason: String? = null
    private var blockReason: String? = null
    private var requestReason = Reason.REQUEST_FAILED

    private val attemptId: String? get() = if (attempts == 0) null else "$taskId:$attempts"

    internal fun beginGeneration() { generationStartedAt = clock() }

    internal fun endGeneration() {
        generationStartedAt?.let { generationNanos += (clock() - it).coerceAtLeast(0L) }
        generationStartedAt = null
    }

    internal fun beginAttempt() {
        attempts++
        attemptStartedAt = clock()
        usage = null
        finishReason = null
        blockReason = null
        requestReason = Reason.REQUEST_FAILED
    }

    internal fun captureResponse(response: JSONObject) {
        usage = usageSnapshot(response.optJSONObject("usageMetadata"))
        finishReason = allowed(response.optJSONArray("candidates")?.optJSONObject(0)?.opt("finishReason"), FINISH_REASONS)
        blockReason = allowed(response.optJSONObject("promptFeedback")?.opt("blockReason"), BLOCK_REASONS)
    }

    internal fun endAttempt(status: Int?, outcome: Outcome, reason: Reason) {
        val start = attemptStartedAt ?: return
        attemptStartedAt = null
        requestReason = reason
        emit(base("attempt_end")
            .put("ordinal", attempts).put("model", "gemini-3.6-flash")
            .put("httpStatus", status ?: JSONObject.NULL)
            .put("outcome", outcome.name.lowercase()).put("reason", reason.name.lowercase())
            .put("finishReason", finishReason ?: JSONObject.NULL)
            .put("blockReason", blockReason ?: JSONObject.NULL)
            .put("usage", usage ?: usageSnapshot(null)).put("attemptLatencyMs", elapsedMillis(start)))
    }

    fun validation(outcome: Validation, reason: Reason = Reason.NONE) {
        if (validationRecorded) return
        validationRecorded = true
        contentValidated = outcome == Validation.VALID
        emit(base("validation").put("outcome", outcome.name.lowercase()).put("reason", reason.name.lowercase()))
    }

    fun <T> validate(block: () -> T): T {
        try {
            val result = block()
            validation(Validation.VALID)
            return result
        } catch (error: Exception) {
            validation(Validation.INVALID, if (error is JSONException) Reason.INVALID_JSON else Reason.INVALID_CONTENT)
            throw error
        }
    }

    /** The owner calls this once in finally, after validation and persistence. */
    fun finish(outcome: Outcome) {
        if (finished) return
        finished = true
        if (!validationRecorded) {
            val reason = when (outcome) {
                Outcome.SESSION_CHANGED -> Reason.SESSION_CHANGED
                Outcome.CONTENT_CHANGED -> Reason.CONTENT_CHANGED
                Outcome.CANCELLED -> Reason.INTERRUPTED
                Outcome.INSUFFICIENT_SOURCES -> Reason.INSUFFICIENT_SOURCES
                Outcome.KEY_MISSING -> Reason.KEY_MISSING
                else -> requestReason
            }
            validation(Validation.NOT_RUN, reason)
        }
        emit(base("task_end").put("attempts", attempts)
            .put("generationLatencyMs", generationNanos / 1_000_000L)
            .put("taskLatencyMs", elapsedMillis(startedAt)).put("outcome", outcome.name.lowercase()))
    }

    private fun elapsedMillis(start: Long) = (clock() - start).coerceAtLeast(0L) / 1_000_000L

    private fun base(event: String) = JSONObject().put("event", event).put("feature", feature.name)
        .put("taskId", taskId).put("attemptId", attemptId ?: JSONObject.NULL)

    private fun emit(event: JSONObject) {
        // Diagnostics, including a faulty test/observer sink, must never change the task outcome.
        try { sink(event) } catch (_: Throwable) { }
    }

    companion object {
        private val COUNTS = listOf("promptTokenCount", "cachedContentTokenCount", "candidatesTokenCount",
            "toolUsePromptTokenCount", "thoughtsTokenCount", "totalTokenCount")
        private val DETAILS = listOf("promptTokensDetails", "cacheTokensDetails", "candidatesTokensDetails",
            "toolUsePromptTokensDetails")
        private val MODALITIES = setOf("TEXT", "IMAGE", "VIDEO", "AUDIO", "DOCUMENT")
        private val TIERS = setOf("unspecified", "standard", "flex", "priority")
        private val FINISH_REASONS = setOf("FINISH_REASON_UNSPECIFIED", "STOP", "MAX_TOKENS", "SAFETY",
            "RECITATION", "LANGUAGE", "OTHER", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "MALFORMED_FUNCTION_CALL", "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_OTHER", "NO_IMAGE",
            "IMAGE_RECITATION", "UNEXPECTED_TOOL_CALL", "TOO_MANY_TOOL_CALLS", "MISSING_THOUGHT_SIGNATURE",
            "MALFORMED_RESPONSE", "ESCALATION", "PUP_LIMITED_DISABLED", "CONTINUATION")
        private val BLOCK_REASONS = setOf("BLOCK_REASON_UNSPECIFIED", "SAFETY", "OTHER", "BLOCKLIST",
            "PROHIBITED_CONTENT", "IMAGE_SAFETY")

        private fun allowed(value: Any?, values: Set<String>): String? = when (value) {
            null, JSONObject.NULL -> null
            is String -> if (value in values) value else "unknown"
            else -> "unknown"
        }

        private fun count(value: Any?): Long? {
            if (value !is Number) return null
            if (value is Byte || value is Short || value is Int || value is Long) {
                return value.toLong().takeIf { it >= 0L }
            }
            return try {
                BigDecimal(value.toString()).longValueExact().takeIf { it >= 0L }
            } catch (_: ArithmeticException) { null } catch (_: NumberFormatException) { null }
        }

        internal fun usageSnapshot(source: JSONObject?): JSONObject {
            val result = JSONObject()
            var observed = 0
            var complete = source != null
            for (field in COUNTS) {
                val value = count(source?.opt(field))
                if (value != null) observed++ else complete = false
                result.put(field, value ?: JSONObject.NULL)
            }
            for (field in DETAILS) {
                val raw = source?.opt(field)
                if (raw == null || raw == JSONObject.NULL) continue
                val details = raw as? JSONArray
                if (details == null) { complete = false; continue }
                val filtered = JSONArray()
                for (index in 0 until details.length()) {
                    val item = details.optJSONObject(index)
                    val modality = item?.opt("modality") as? String
                    val tokens = count(item?.opt("tokenCount"))
                    if (modality in MODALITIES && tokens != null) {
                        filtered.put(JSONObject().put("modality", modality).put("tokenCount", tokens))
                        observed++
                    } else complete = false
                }
                result.put(field, filtered)
            }
            val tier = source?.opt("serviceTier") as? String
            if (tier in TIERS) { result.put("serviceTier", tier); observed++ }
            else if (source?.has("serviceTier") == true) complete = false
            result.put("state", if (observed == 0) "unknown" else if (complete) "observed" else "partial")
            return result
        }
    }
}
