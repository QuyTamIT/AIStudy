package com.aistudy.aistudy.answer

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class AnswerType(val value: String) {
    SOLUTION("solution"), EXPLANATION("explanation"), TOPIC_LIST("topic_list");

    companion object {
        fun parse(value: String): AnswerType =
            requireNotNull(entries.find { it.value == value }) { "Unknown answer type: $value" }
    }
}

enum class ResponseMode(val value: String) {
    STEP_BY_STEP("step_by_step"),
    DIRECT("direct"),
    CREATIVE("creative"),
    CLARIFICATION("clarification"),
    LIMITATION("limitation");

    companion object {
        fun parse(value: String?): ResponseMode? =
            if (value.isNullOrBlank()) null
            else entries.find { it.value.equals(value.trim(), ignoreCase = true) }
    }
}

data class AnswerStep(val title: String, val body: String, val expr: String, val topicId: String? = null)

data class AnswerContent(
    val steps: List<AnswerStep>,
    val type: AnswerType?,
    val finalAnswerRedundant: Boolean,
    val moreCount: Int,
    val responseMode: ResponseMode = ResponseMode.STEP_BY_STEP
) {
    init { require(moreCount in 0..MAX_MORE) }

    val canExpand: Boolean get() = type == AnswerType.TOPIC_LIST && moreCount < MAX_MORE

    fun showFinalAnswer(subject: String?): Boolean =
        subject == "math" || type != AnswerType.TOPIC_LIST || !finalAnswerRedundant

    fun append(batch: List<AnswerStep>): AnswerContent {
        check(canExpand) { "No more expansions available" }
        require(batch.size in 3..5) { "Expected 3–5 topics" }
        val seen = steps.toMutableList()
        val additions = batch.filter { candidate ->
            require(!candidate.topicId.isNullOrBlank() && candidate.body.isNotBlank())
            val duplicate = seen.any { old ->
                same(old.topicId, candidate.topicId) || same(old.title, candidate.title) ||
                    same(old.body, candidate.body)
            }
            if (!duplicate) seen.add(candidate)
            !duplicate
        }
        require(additions.size >= 3) { "Not enough distinct topics" }
        return copy(steps = steps + additions, moreCount = moreCount + 1)
    }

    fun classified(json: JSONObject): AnswerContent {
        val classifiedType = AnswerType.parse(json.getString("answerType"))
        val redundant = json.opt("finalAnswerRedundant")
        require(redundant is Boolean)
        val ids = json.getJSONArray("topicIds")
        require(ids.length() == steps.size)
        val modeStr = json.optString("responseType").takeIf { it.isNotBlank() }
            ?: json.optString("responseMode").takeIf { it.isNotBlank() }
        val newMode = ResponseMode.parse(modeStr) ?: responseMode
        return copy(type = classifiedType, finalAnswerRedundant = redundant,
            responseMode = newMode,
            steps = steps.mapIndexed { index, step ->
                val id = ids.getString(index)
                if (classifiedType == AnswerType.TOPIC_LIST) require(id.isNotBlank())
                step.copy(topicId = id.takeIf { it.isNotBlank() })
            })
    }

    fun itemsJson(): JSONArray = JSONArray().apply {
        steps.forEach { step -> put(JSONObject().put("title", step.title).put("body", step.body)
            .put("expr", step.expr).put("topicId", step.topicId.orEmpty())) }
    }

    fun toJson(): String = itemsJson().apply {
        // Metadata lives inside the existing JSON array; the Room schema and old readers stay compatible.
        if (length() > 0 && type != null) getJSONObject(0).put("_answer", JSONObject()
            .put("version", 1).put("answerType", type.value)
            .put("responseType", responseMode.value)
            .put("finalAnswerRedundant", finalAnswerRedundant).put("moreCount", moreCount))
    }.toString()

    companion object {
        const val MAX_MORE = 3

        fun read(stored: String?): AnswerContent {
            val array = JSONArray(stored ?: "[]")
            val items = parseSteps(array)
            val metadata = array.optJSONObject(0)?.optJSONObject("_answer")
            if (metadata == null) {
                val inferredMode = if (items.size > 1) ResponseMode.STEP_BY_STEP else ResponseMode.DIRECT
                return AnswerContent(items,
                    if (items.isEmpty()) AnswerType.EXPLANATION else null, false, 0, inferredMode)
            }
            require(metadata.getInt("version") == 1)
            val redundant = metadata.opt("finalAnswerRedundant")
            require(redundant is Boolean)
            val answerType = AnswerType.parse(metadata.getString("answerType"))
            val modeStr = metadata.optString("responseType").takeIf { it.isNotBlank() }
                ?: metadata.optString("responseMode").takeIf { it.isNotBlank() }
            val mode = ResponseMode.parse(modeStr) ?: when (answerType) {
                AnswerType.SOLUTION -> ResponseMode.STEP_BY_STEP
                AnswerType.TOPIC_LIST -> ResponseMode.STEP_BY_STEP
                AnswerType.EXPLANATION -> if (items.size > 1) ResponseMode.STEP_BY_STEP else ResponseMode.DIRECT
            }
            return AnswerContent(items, answerType, redundant, metadata.getInt("moreCount"), mode)
        }

        fun generated(json: JSONObject): AnswerContent {
            val modeStr = json.optString("responseType").takeIf { it.isNotBlank() }
                ?: json.optString("responseMode").takeIf { it.isNotBlank() }
            val mode = ResponseMode.parse(modeStr) ?: ResponseMode.STEP_BY_STEP
            val type = if (json.has("answerType")) {
                AnswerType.parse(json.getString("answerType"))
            } else {
                when (mode) {
                    ResponseMode.STEP_BY_STEP -> AnswerType.SOLUTION
                    else -> AnswerType.EXPLANATION
                }
            }
            val redundant = json.opt("finalAnswerRedundant") as? Boolean ?: false
            val items = parseSteps(json.getJSONArray("steps"))
            require(items.isNotEmpty() && items.all { it.body.isNotBlank() })
            if (type == AnswerType.TOPIC_LIST) require(items.all { !it.topicId.isNullOrBlank() })
            return AnswerContent(items, type, redundant, 0, mode)
        }

        fun parseSteps(array: JSONArray): List<AnswerStep> = (0 until array.length()).map { index ->
            val step = array.getJSONObject(index)
            AnswerStep((step.opt("title") as? String).orEmpty(), step.getString("body"),
                (step.opt("expr") as? String).orEmpty(),
                (step.opt("topicId") as? String)?.takeIf { it.isNotBlank() })
        }

        private fun same(first: String?, second: String?): Boolean {
            fun normalized(value: String?) = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
            val key = normalized(first)
            return key.isNotEmpty() && key == normalized(second)
        }
    }
}
