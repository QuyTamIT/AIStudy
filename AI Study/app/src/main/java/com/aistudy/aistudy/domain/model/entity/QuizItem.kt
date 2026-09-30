package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject

/**
 * Bảng `quiz_items` — FR7 AI-Generated Practice Questions.
 *
 * [isCorrect] PHẢI nullable: null = chưa làm, false = làm sai. Nếu gộp hai
 * trường hợp thì QuizItemDao.accuracy() sẽ chia sai mẫu số (TC-QUI-06, TC-QUI-07).
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.4
 */
@Entity(
    tableName = "quiz_items",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionAnswer::class,
            parentColumns = ["id"],
            childColumns = ["sourceQuestionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["subjectCode"]),
        Index(value = ["sourceQuestionId"])
    ]
)
data class QuizItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "userId")
    val userId: Long,

    /** Câu luyện tập sinh ra từ câu hỏi nào — cho phép truy vết nguồn gốc. */
    @ColumnInfo(name = "sourceQuestionId")
    val sourceQuestionId: Long? = null,

    @ColumnInfo(name = "subjectCode")
    val subjectCode: Subject,

    @ColumnInfo(name = "questionType")
    val questionType: QuestionType,

    @ColumnInfo(name = "prompt")
    val prompt: String,

    /** Chỉ dùng cho MULTIPLE_CHOICE. */
    @ColumnInfo(name = "optionA")
    val optionA: String? = null,

    @ColumnInfo(name = "optionB")
    val optionB: String? = null,

    @ColumnInfo(name = "optionC")
    val optionC: String? = null,

    @ColumnInfo(name = "optionD")
    val optionD: String? = null,

    /** So sánh sau khi chuẩn hoá: bỏ khoảng trắng thừa, hạ chữ thường (TC-QUI-09). */
    @ColumnInfo(name = "correctAnswer")
    val correctAnswer: String,

    @ColumnInfo(name = "explanation")
    val explanation: String? = null,

    @ColumnInfo(name = "isCorrect")
    val isCorrect: Boolean? = null,

    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "answeredAt")
    val answeredAt: Long? = null
)
