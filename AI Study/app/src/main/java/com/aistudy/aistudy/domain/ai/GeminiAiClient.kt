package com.aistudy.aistudy.domain.ai

import android.util.Base64
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * FR4 + FR7 - goi Gemini qua REST.
 *
 * Khong dung Retrofit/OkHttp theo quyet dinh D-5 trong design doc: build khong
 * can mang de tai dependency, va khong xung dot version voi AGP 9.3.3. Chi can
 * `HttpURLConnection` + `org.json` (co san trong Android) la du.
 *
 * Model duoc yeu cau tra ve JSON. Gemini hay boc JSON trong ```json fences,
 * nen [extractJson] cat phan giuaa hai dau ``` truoc khi parse.
 */
class GeminiAiClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL
) : AiClient {

    override val isConfigured: Boolean = apiKey.isNotBlank()

    override suspend fun answer(request: AiRequest): Result<AiAnswer> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().apply {
                                    put(JSONObject().put("text", answerPrompt(request)))
                                    request.imageBytes
                                        ?.let { bytes ->
                                            put(
                                                JSONObject().put(
                                                    "inline_data",
                                                    JSONObject()
                                                        .put("mime_type", "image/jpeg")
                                                        .put(
                                                            "data",
                                                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                                                        )
                                                )
                                            )
                                        }
                                }
                            )
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.3)
                        .put("maxOutputTokens", 4096)
                )
            }

            val root = post(body)
            val text = extractText(root)
            parseAnswer(text, request.subject)
        }.recoverCatching { error -> throw error.toAiError() }
    }

    override suspend fun generateQuiz(
        source: QuestionAnswer,
        count: Int,
        level: EducationLevel,
        style: ExplanationStyle
    ): Result<List<QuizDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", quizPrompt(source, count, level, style))
                                )
                            )
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 4096)
                )
            }

            val root = post(body)
            parseQuizDrafts(extractText(root))
        }.recoverCatching { error -> throw error.toAiError() }
    }

    private fun post(body: JSONObject): JSONObject {
        val maxRetries = 2
        var delayMs = 1000L

        for (attempt in 0..maxRetries) {
            val connection = (URL("$BASE_URL/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code in 200..299) {
                    android.util.Log.d("DBGASK", "code=$code rawLen=${raw.length}")
                    return JSONObject(raw)
                }

                // Temporary server busy or rate limit error (503, 429, 500, 502, 504) -> retry with exponential delay
                if ((code == 503 || code == 429 || code in 500..504) && attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {}
                    delayMs *= 2
                    continue
                }

                val msg = extractError(raw)
                val userFriendlyMessage = when (code) {
                    503 -> "Máy chủ AI hiện đang quá tải (High Demand). Vui lòng thử lại sau vài giây."
                    429 -> "Đã vượt quá giới hạn yêu cầu (Rate Limit). Vui lòng thử lại sau."
                    else -> "Gemini HTTP $code - $msg"
                }
                throw AiException.Provider(userFriendlyMessage)
            } finally {
                connection.disconnect()
            }
        }

        throw AiException.Provider("Máy chủ AI hiện đang quá tải (HTTP 503). Vui lòng thử lại sau.")
    }

    private fun extractText(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates")
            ?: throw AiException.Malformed("Gemini tra ve khong co candidates")
        val first = candidates.optJSONObject(0)
            ?: throw AiException.Malformed("Gemini tra ve candidates rong")
        if (first.optString("finishReason").uppercase() == SAFETY_FINISH_REASON) {
            throw AiException.Provider("Yeu cau bi chan boi bo loc an toan cua Gemini")
        }
        val parts = first.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException.Malformed("Gemini tra ve khong co parts")
        val text = (0 until parts.length())
            .mapNotNull { parts.optJSONObject(it)?.optString("text") }
            .joinToString("\n")
        if (text.isBlank()) throw AiException.Malformed("Gemini tra ve noi dung rong")
        android.util.Log.d("DBGASK", "finish=${first.optString("finishReason")} textLen=${text.length} tail=${text.takeLast(150)}")
        return text
    }

    private fun extractError(raw: String): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw.take(200)

    private fun answerPrompt(request: AiRequest): String {
        val levelVi = when (request.level) {
            EducationLevel.MIDDLE -> "trung hoc co so"
            EducationLevel.HIGH -> "trung hoc pho thong"
            EducationLevel.UNIVERSITY -> "dai hoc co so"
        }
        val styleVi = when (request.style) {
            ExplanationStyle.SHORT -> "ngan gon, toi da 5 dong"
            ExplanationStyle.DETAILED -> "chi tiet, kem vi du so"
            ExplanationStyle.STEP_BY_STEP -> "tung buoc, dinh so thu tu"
        }
        val hasImage = if (request.imageBytes != null) " kem anh bai tap" else ""

        return """
            Ban la gia su vien giai toan. Tra loi cau hoi sau$hasImage, muc do $levelVi, phong cach $styleVi.
            Mon hoc: ${request.subject.name}.

            Tra ve DUY NHAT mot JSON dung cu phap sau, khong them y giai hay doan ma:
            {
              "answerText": "loi giai day du, co the dung markdown",
              "difficulty": "EASY" | "MEDIUM" | "HARD",
              "subject": "MATHEMATICS" | "SCIENCE" | "PROGRAMMING" | "HISTORY" | "LANGUAGE",
              "simplifiedExplanation": "giai thich rut gon trong 2-3 cau",
              "alternativeApproach": "mot cach giai khac",
              "keyConcepts": "cac khai niem chinh, phan cach bang dau cham",
              "commonMistakes": "loi thuong gap va cach tranh",
              "followUpQuestions": "hai cau hoi nen hoi tiep, phan cach bang dau cham"
            }

            Cau hoi:
            ${request.questionText}
        """.trimIndent()
    }

    private fun quizPrompt(
        source: QuestionAnswer,
        count: Int,
        level: EducationLevel,
        style: ExplanationStyle
    ): String = """
        Tao $count cau hoi luyen tap tu bai hoc duoi day. Mon ${source.subjectCode.name}.
        Trinh do ${level.name}, phong cach ${style.name}.

        Tra ve DUY NHAT mot JSON, phan tuoi la mang chua "questions":
        {
          "questions": [
            {
              "questionType": "MULTIPLE_CHOICE" | "SHORT_ANSWER" | "FILL_BLANK",
              "prompt": "noi dung cau hoi",
              "correctAnswer": "dap an dung",
              "optionA": "chi voi MULTIPLE_CHOICE",
              "optionB": "chi voi MULTIPLE_CHOICE",
              "optionC": "chi voi MULTIPLE_CHOICE",
              "optionD": "chi voi MULTIPLE_CHOICE",
              "explanation": "giai thich ngan gon"
            }
          ]
        }

        Bai hoc:
        ${source.questionText}
        Loi giai co san: ${source.answerText.take(600)}
    """.trimIndent()

    internal fun parseAnswer(text: String, fallbackSubject: Subject): AiAnswer {
        val obj = jsonCandidates(text).firstNotNullOfOrNull { candidate ->
            runCatching { JSONObject(candidate) }
                .recoverCatching { JSONObject(escapeInnerControlChars(candidate)) }
                .getOrNull()
                ?.takeIf { it.optString("answerText").isNotBlank() }
        } ?: throw AiException.Malformed("Khong parse duoc JSON tu Gemini")

        val answerText = obj.optString("answerText").trim()
        if (answerText.isBlank()) {
            throw AiException.Malformed("Gemini tra ve answerText rong")
        }

        return AiAnswer(
            answerText = answerText,
            subject = obj.optString("subject").enumOrNull<Subject>() ?: fallbackSubject,
            difficulty = obj.optString("difficulty").enumOrNull<Difficulty>(),
            simplifiedExplanation = obj.optStringOrNull("simplifiedExplanation"),
            alternativeApproach = obj.optStringOrNull("alternativeApproach"),
            keyConcepts = obj.optStringOrNull("keyConcepts"),
            commonMistakes = obj.optStringOrNull("commonMistakes"),
            followUpQuestions = obj.optStringOrNull("followUpQuestions"),
            inputTokens = 0,
            outputTokens = 0
        )
    }

    internal fun parseQuizDrafts(text: String): List<QuizDraft> {
        // Gemini hoac tra mang thang, hoac tra object boc mang trong "questions".
        // Thu object truoc: text xung quanh JSON that co the chua ngoac cua
        // vi du code, khien nhan mang thang nham.
        val array = jsonCandidates(text).firstNotNullOfOrNull { candidate ->
            quizArrayFrom(candidate) ?: quizArrayFrom(escapeInnerControlChars(candidate))
        } ?: throw AiException.Malformed("Khong parse duoc danh sach cau hoi tu Gemini")

        if (array.length() == 0) {
            throw AiException.Malformed("Gemini tra ve khong cau hoi nao")
        }

        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index)
                ?: throw AiException.Malformed("Cau hoi thu $index khong phai object")
            val prompt = item.optString("prompt").trim()
            val correct = item.optString("correctAnswer").trim()
            if (prompt.isBlank() || correct.isBlank()) {
                throw AiException.Malformed("Cau hoi thu $index thieu prompt hoac correctAnswer")
            }
            QuizDraft(
                questionType = item.optString("questionType")
                    .enumOrNull<QuestionType>() ?: QuestionType.SHORT_ANSWER,
                prompt = prompt,
                correctAnswer = correct,
                optionA = item.optStringOrNull("optionA"),
                optionB = item.optStringOrNull("optionB"),
                optionC = item.optStringOrNull("optionC"),
                optionD = item.optStringOrNull("optionD"),
                explanation = item.optStringOrNull("explanation")
            )
        }
    }

    /**
     * Boc tach mang cau hoi: uu tien object co key "questions", roi moi thu
     * mang thang. Thu tu nay quan trong vi ngoac cua object bao ngoai luon
     * dung ngay ca khi text xung quanh co ngoac cua vi du code.
     */
    private fun quizArrayFrom(json: String): JSONArray? {
        runCatching { JSONObject(json).optJSONArray(KEY_QUESTIONS) }
            .getOrNull()?.let { return it }
        return runCatching { JSONArray(json) }.getOrNull()
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val TIMEOUT_MS = 20_000
        const val SAFETY_FINISH_REASON = "SAFETY"
        const val KEY_QUESTIONS = "questions"    }
}

/**
 * Cat JSON ra khoi text. Gemini hay boc ket qua trong ```json ... ``` va
 * thuong them mot cau mo dan ngoai, nen can ca `first {` den `last }`.
 */
internal fun extractJson(text: String): String {
    val fenced = FENCE.find(text)?.groupValues?.getOrNull(1)
    if (fenced != null && fenced.isNotBlank()) return fenced.trim()

    // Van ban co the open bang `{` hoac `[`. Phai chon dau ngoac mo ra truoc
    // thoi - neu uu tien `{` thi payload `[{...}]` se bi cat mat vo `[` `]`.
    val objectStart = text.indexOf('{')
    val arrayStart = text.indexOf('[')
    val start = when {
        objectStart < 0 -> arrayStart
        arrayStart < 0 -> objectStart
        else -> minOf(objectStart, arrayStart)
    }
    if (start < 0) throw AiException.Malformed("Khong tim thay JSON trong phan tra loi cua Gemini")

    val end = maxOf(text.lastIndexOf('}'), text.lastIndexOf(']'))
    if (end <= start) throw AiException.Malformed("JSON trong phan tra loi cua Gemini khong dong")

    return text.substring(start, end + 1)
}

/**
 * Thu thap tat ca cac doan ung cu cho JSON cua Gemini: moi code fence rieng
 * le (vi du ```javascript hay ```json), roi den khoang tu dau ngoac dau tien
 * den ngoac cuoi cung cua toan van ban. Nhanh nay quan trong vi Gemini hay
 * tra code mau ```javascript ben trai va JSON ben phai - uu tien fence cuoi
 * chua nut cua JSON bang cach thu tung fence qua get key.
 */
internal fun jsonCandidates(text: String): List<String> {
    val fenced = FENCE.findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .toList()
    val objectStart = text.indexOf('{')
    val arrayStart = text.indexOf('[')
    val start = when {
        objectStart < 0 -> arrayStart
        arrayStart < 0 -> objectStart
        else -> minOf(objectStart, arrayStart)
    }
    val end = maxOf(text.lastIndexOf('}'), text.lastIndexOf(']'))
    val whole = if (start in 0 until end) listOf(text.substring(start, end + 1)) else emptyList()
    return (fenced + whole).distinct()
}

private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

/**
 * Gemini hay de nguyen dau xuong dong / tab ben trong gia tri chuoi JSON, do
 * la khong hop le theo dac ta. Ham nay doi cac ky tu dieu khien do thanh
 * escape tuong ung, chi khi nam trong chuoi, de `JSONObject` parse duoc.
 */
internal fun escapeInnerControlChars(json: String): String {
    val out = StringBuilder(json.length)
    var inString = false
    var escaped = false
    for (ch in json) {
        when {
            escaped -> { out.append(ch); escaped = false }
            inString && ch == '\\' -> { out.append(ch); escaped = true }
            ch == '"' -> { inString = !inString; out.append(ch) }
            inString && ch == '\n' -> out.append("\\n")
            inString && ch == '\r' -> Unit
            inString && ch == '\t' -> out.append("\\t")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
    if (isBlank()) null else runCatching { enumValueOf<T>(trim().uppercase()) }.getOrNull()

/** `optString` tra ve chuoi rong cho ca khi JSON thieu field - phai phan biet. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

private fun Throwable.toAiError(): Throwable = when (this) {
    is AiException -> this
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException -> AiException.Network(message ?: "Khong ket noi duoc toi may chu AI")

    else -> AiException.Provider(message ?: "Loi khong mong doi: ${this::class.simpleName}")
}
