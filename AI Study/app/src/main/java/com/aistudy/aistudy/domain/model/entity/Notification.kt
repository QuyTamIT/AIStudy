package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudy.aistudy.domain.model.enums.NotificationType

/**
 * Bảng `notifications` — FR10.
 *
 * [deepLink] lưu dạng route (vd. "question/42") để bấm thông báo là mở đúng
 * màn hình, khỏi truyền dữ liệu lằng nhằng qua Intent.
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.7
 */
@Entity(
    tableName = "notifications",
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
        Index(value = ["userId", "isRead"])
    ]
)
data class Notification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "userId")
    val userId: Long,

    @ColumnInfo(name = "type")
    val type: NotificationType,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "deepLink")
    val deepLink: String? = null,

    @ColumnInfo(name = "isRead", defaultValue = "0")
    val isRead: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long
)
