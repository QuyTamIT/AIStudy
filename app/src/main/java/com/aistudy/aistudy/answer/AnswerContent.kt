package com.aistudy.aistudy.answer

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class AnswerType(val value: String) {
    SOLUTION("solution"), EXPLANATION("explanation"), TOPIC_LIST("topic_list");

    companion object {
        fun parse(value: String): AnswerType =
            requireNotNull(entries.find { it.value == value }) { "Unknown answer type" }
    }
}

data class AnswerStep(val title: String, val body: String, val expr: String, val topicId: String? = null)

data class AnswerContent(
    val steps: List<AnswerStep>,
    val type: AnswerType?,
    val finalAnswerRedundant: Boolean,
    val moreCount: Int
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
        return copy(type = classifiedType, finalAnswerRedundant = redundant,
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
            .put("finalAnswerRedundant", finalAnswerRedundant).put("moreCount", moreCount))
    }.toString()

    companion object {
        const val MAX_MORE = 3

        fun read(stored: String?): AnswerContent {
            val array = JSONArray(stored ?: "[]")
            val items = parseSteps(array)
            val metadata = array.optJSONObject(0)?.optJSONObject("_answer")
            if (metadata == null) return AnswerContent(items,
                if (items.isEmpty()) AnswerType.EXPLANATION else null, false, 0)
            require(metadata.getInt("version") == 1)
            val redundant = metadata.opt("finalAnswerRedundant")
            require(redundant is Boolean)
            return AnswerContent(items, AnswerType.parse(metadata.getString("answerType")),
                redundant, metadata.getInt("moreCount"))
        }

        fun generated(json: JSONObject): AnswerContent {
            val type = AnswerType.parse(json.getString("answerType"))
            val redundant = json.opt("finalAnswerRedundant")
            require(redundant is Boolean)
            val items = parseSteps(json.getJSONArray("steps"))
            require(items.isNotEmpty() && items.all { it.body.isNotBlank() })
            if (type == AnswerType.TOPIC_LIST) require(items.all { !it.topicId.isNullOrBlank() })
            return AnswerContent(items, type, redundant, 0)
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
