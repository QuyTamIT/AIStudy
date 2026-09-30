package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.Subject

/**
 * Bảng `question_answers` — bảng TRUNG TÂM, phục vụ FR3, FR4, FR5, FR6.
 *
 * Cơ chế AI Cost Management mà brief bắt buộc:
 *  - [questionHash] = SHA-256 của văn bản đã chuẩn hoá ⇒ tra cache khớp chính xác.
 *  - [servedFromCache] = true khi câu trả lời được tái dùng, không tốn tiền API.
 *  - [matchedQuestionId] trỏ tới bản ghi gốc đã trả tiền khi khớp mờ (Jaccard ≥ 0.85).
 *
 * Ba ca kiểm thử quan trọng nhất của cả dự án: TC-ASK-02/03/04.
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.3
 */
@Entity(
    tableName = "question_answers",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "subjectCode"]),
        Index(value = ["userId", "isBookmarked"]),
        Index(value = ["userId", "createdAt"]),
        Index(value = ["questionHash"])
    ]
)
data class QuestionAnswer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "userId")
    val userId: Long,

    @ColumnInfo(name = "subjectCode")
    val subjectCode: Subject,

    /** Rỗng nếu học sinh chỉ gửi ảnh (TC-ASK-05). */
    @ColumnInfo(name = "questionText")
    val questionText: String,

    /** Đường dẫn file đã mã hoá trong filesDir. */
    @ColumnInfo(name = "questionImagePath")
    val questionImagePath: String? = null,

    /** IV của AES-GCM, cần cho giải mã. */
    @ColumnInfo(name = "questionImageIv")
    val questionImageIv: String? = null,

    @ColumnInfo(name = "questionHash")
    val questionHash: String,

    @ColumnInfo(name = "servedFromCache", defaultValue = "0")
    val servedFromCache: Boolean = false,

    @ColumnInfo(name = "matchedQuestionId")
    val matchedQuestionId: Long? = null,

    /** null nếu câu hỏi từ cache, không qua AI. */
    @ColumnInfo(name = "detectedDifficulty")
    val detectedDifficulty: Difficulty? = null,

    @ColumnInfo(name = "answerText")
    val answerText: String,

    // ----- FR5: Answer Improvement & Learning Support -----
    @ColumnInfo(name = "simplifiedExplanation")
    val simplifiedExplanation: String? = null,

    @ColumnInfo(name = "alternativeApproach")
    val alternativeApproach: String? = null,

    @ColumnInfo(name = "keyConcepts")
    val keyConcepts: String? = null,

    @ColumnInfo(name = "commonMistakes")
    val commonMistakes: String? = null,

    @ColumnInfo(name = "followUpQuestions")
    val followUpQuestions: String? = null,

    // ----- FR6 + FR8 -----
    @ColumnInfo(name = "isBookmarked", defaultValue = "0")
    val isBookmarked: Boolean = false,

    /** Thời gian đã dành đọc câu trả lời — nguồn cho "time spent reviewing" ở FR8. */
    @ColumnInfo(name = "reviewTimeSeconds", defaultValue = "0")
    val reviewTimeSeconds: Int = 0,

    /** Số token đã dùng — cơ sở đo AI Cost Management. */
    @ColumnInfo(name = "inputTokens", defaultValue = "0")
    val inputTokens: Int = 0,

    @ColumnInfo(name = "outputTokens", defaultValue = "0")
    val outputTokens: Int = 0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long
)
