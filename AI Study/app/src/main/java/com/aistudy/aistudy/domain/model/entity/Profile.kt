package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.Subject

/**
 * Bảng `profiles` — FR2 onboarding.
 *
 * `userId` có UNIQUE index: một user chỉ có một hồ sơ, chặn trùng khi người dùng
 * bấm "Xong" hai lần (TC-ONB-04).
 *
 * Cố ý KHÔNG có bảng nối profile_subjects — xem giải trình ở
 * docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.2 và câu trả lời cho D1.
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.2
 */
@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"], unique = true)]
)
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "userId")
    val userId: Long,

    @ColumnInfo(name = "educationLevel")
    val educationLevel: EducationLevel,

    @ColumnInfo(name = "preferredStyle")
    val preferredStyle: ExplanationStyle,

    /** Lưu dạng chuỗi qua Converters — Set không thứ tự nên index trên cột này không có ý nghĩa. */
    @ColumnInfo(name = "subjectCodes")
    val subjectCodes: Set<Subject>,

    /** null nghĩa là chưa onboarding ⇒ AuthGate chuyển tới Routes.ONBOARDING (TC-ONB-01). */
    @ColumnInfo(name = "onboardedAt")
    val onboardedAt: Long? = null
)
