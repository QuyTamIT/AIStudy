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
        val additions = mutableListOf<AnswerStep>()
        batch.forEach { candidate ->
            // Deduplicate first: legacy IDs such as FOOD! must not make an old topic fail validation.
            val duplicate = seen.any { old ->
                same(old.topicId, candidate.topicId) || same(old.title, candidate.title) ||
                    same(old.body, candidate.body)
            }
            if (!duplicate) {
                require(candidate.body.isNotBlank()) { "Topic body must not be blank" }
                require(isCanonicalTopicId(candidate.topicId)) { "Invalid topic ID" }
                seen += candidate
                additions += candidate
            }
        }
        require(additions.size >= 3) { "Not enough distinct topics" }
        return copy(steps = steps + additions, moreCount = moreCount + 1)
    }

    fun classified(json: JSONObject): AnswerContent {
        require(json.length() == CLASSIFICATION_FIELDS.size && CLASSIFICATION_FIELDS.all { json.has(it) })
        val typeValue = json.get("answerType")
        require(typeValue is String) { "answerType must be a string" }
        val classifiedType = AnswerType.parse(typeValue)
        val redundant = json.get("finalAnswerRedundant")
        require(redundant is Boolean) { "finalAnswerRedundant must be boolean" }
        require(!redundant || classifiedType == AnswerType.TOPIC_LIST) {
            "Only topic lists may have a redundant final answer"
        }
        val ids = json.get("topicIds")
        require(ids is JSONArray && ids.length() == steps.size) { "topicIds count mismatch" }
        val parsedIds = (0 until ids.length()).map { index ->
            val id = ids.get(index)
            require(id is String) { "topicIds must contain strings" }
            if (classifiedType == AnswerType.TOPIC_LIST) {
                // Legacy items may share one canonical concept ID, but IDs remain canonical.
                require(isCanonicalTopicId(id)) { "Invalid topic ID" }
            } else {
                require(id.isEmpty()) { "Non-topic answers must have empty topic IDs" }
            }
            id
        }
        return copy(type = classifiedType, finalAnswerRedundant = redundant,
            steps = steps.mapIndexed { index, step ->
                step.copy(topicId = parsedIds[index].takeIf { it.isNotEmpty() })
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
        private val CLASSIFICATION_FIELDS = setOf("answerType", "finalAnswerRedundant", "topicIds")
        private val STEP_FIELDS = setOf("topicId", "title", "body", "expr")
        private val TEXT_EXTRAS = setOf("simplify", "alternative")
        private val LIST_EXTRAS = setOf("concepts", "mistakes", "followUps")
        private val EXTRA_FIELDS = TEXT_EXTRAS + LIST_EXTRAS
        private val TOPIC_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

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
            val modeValue = json.get("responseType")
            require(modeValue is String) { "responseType must be a string" }
            val mode = ResponseMode.entries.find { it.value == modeValue }
                ?: throw IllegalArgumentException("Unknown response type: $modeValue")
            val typeValue = json.get("answerType")
            require(typeValue is String) { "answerType must be a string" }
            val type = AnswerType.parse(typeValue)
            val redundant = json.get("finalAnswerRedundant")
            require(redundant is Boolean) { "finalAnswerRedundant must be boolean" }
            require(!redundant || type == AnswerType.TOPIC_LIST) {
                "Only topic lists may have a redundant final answer"
            }
            val rawSteps = json.get("steps")
            require(rawSteps is JSONArray) { "steps must be an array" }
            val items = parseGeneratedSteps(rawSteps)
            require(items.isNotEmpty()) { "At least one step is required" }
            val ids = items.map { it.topicId.orEmpty() }
            if (type == AnswerType.TOPIC_LIST) {
                require(ids.all(::isCanonicalTopicId)) { "Topic-list IDs must be canonical" }
                require(ids.toSet().size == ids.size) { "Topic-list IDs must be unique" }
            } else {
                require(ids.all { it.isEmpty() }) { "Non-topic answers must have empty topic IDs" }
            }
            return AnswerContent(items, type, redundant, 0, mode)
        }

        fun parseGeneratedSteps(array: JSONArray): List<AnswerStep> = (0 until array.length()).map { index ->
            val value = array.get(index)
            require(value is JSONObject) { "Each generated step must be an object" }
            require(value.length() == STEP_FIELDS.size && STEP_FIELDS.all { value.has(it) }) {
                "Generated steps must contain exactly the required fields"
            }
            val topicId = value.get("topicId")
            val title = value.get("title")
            val body = value.get("body")
            val expr = value.get("expr")
            require(topicId is String && title is String && body is String && expr is String) {
                "Generated step fields must be strings"
            }
            require(body.isNotBlank()) { "Step body must not be blank" }
            AnswerStep(title, body, expr, topicId.takeIf { it.isNotEmpty() })
        }

        fun parseSteps(array: JSONArray): List<AnswerStep> = (0 until array.length()).map { index ->
            val step = array.getJSONObject(index)
            AnswerStep((step.opt("title") as? String).orEmpty(), step.getString("body"),
                (step.opt("expr") as? String).orEmpty(),
                (step.opt("topicId") as? String)?.takeIf { it.isNotBlank() })
        }

        fun validateExtras(json: JSONObject): JSONObject {
            require(json.length() == EXTRA_FIELDS.size && EXTRA_FIELDS.all { json.has(it) }) { "Invalid extras fields" }
            for (key in TEXT_EXTRAS) {
                val value = json.get(key)
                require(value is String && value.isNotBlank()) { "Extra text must be nonblank" }
            }
            for (key in LIST_EXTRAS) {
                val value = json.get(key)
                require(value is JSONArray && value.length() > 0) { "Extra list must be nonempty" }
                for (index in 0 until value.length()) {
                    val item = value.get(index)
                    require(item is String && item.isNotBlank()) { "Extra list values must be strings" }
                }
            }
            return json
        }

        fun extraField(json: JSONObject, key: String): String? {
            val value = json.opt(key)
            if (key in TEXT_EXTRAS) return (value as? String)?.takeIf { it.isNotBlank() }
            if (key !in LIST_EXTRAS || value !is JSONArray || value.length() == 0) return null
            val result = StringBuilder()
            for (index in 0 until value.length()) {
                val item = value.opt(index) as? String ?: return null
                if (item.isBlank()) return null
                if (index > 0) result.append("\n\n")
                result.append("• ").append(item)
            }
            return result.toString()
        }

        private fun isCanonicalTopicId(id: String?): Boolean =
            id != null && TOPIC_ID.matches(id)

        private fun same(first: String?, second: String?): Boolean {
            fun normalized(value: String?) = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
            val key = normalized(first)
            return key.isNotEmpty() && key == normalized(second)
        }
    }
}
