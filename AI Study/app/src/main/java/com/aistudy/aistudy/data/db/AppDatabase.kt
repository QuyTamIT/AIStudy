package com.aistudy.aistudy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aistudy.aistudy.data.db.dao.NotificationDao
import com.aistudy.aistudy.data.db.dao.ProfileDao
import com.aistudy.aistudy.data.db.dao.QuestionAnswerDao
import com.aistudy.aistudy.data.db.dao.QuizItemDao
import com.aistudy.aistudy.data.db.dao.UserBadgeDao
import com.aistudy.aistudy.data.db.dao.UserDao
import com.aistudy.aistudy.data.db.dao.XpEventDao
import com.aistudy.aistudy.domain.model.entity.Notification
import com.aistudy.aistudy.domain.model.entity.Profile
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.entity.QuizItem
import com.aistudy.aistudy.domain.model.entity.User
import com.aistudy.aistudy.domain.model.entity.UserBadge
import com.aistudy.aistudy.domain.model.entity.XpEvent

/**
 * Cơ sở dữ liệu Room — 7 bảng, xem docs/PART1_M1_SOFTWARE_DESIGN.md mục 4
 * và docs/uml/09_erd.puml.
 *
 * Lưu ý khi nâng version sau này: Room **không** tự migrate. Mỗi lần tăng
 * `version` phải viết `Migration` và thêm `addMigrations(...)` trong
 * DatabaseProvider, nếu không app sẽ crash lúc mở trên máy đã có dữ liệu cũ.
 */
@Database(
    entities = [
        User::class,
        Profile::class,
        QuestionAnswer::class,
        QuizItem::class,
        XpEvent::class,
        UserBadge::class,
        Notification::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun profileDao(): ProfileDao

    abstract fun questionAnswerDao(): QuestionAnswerDao

    abstract fun quizItemDao(): QuizItemDao

    abstract fun xpEventDao(): XpEventDao

    abstract fun notificationDao(): NotificationDao

    abstract fun userBadgeDao(): UserBadgeDao

    /**
     * Xoá toàn bộ dữ liệu sinh hoạt của một user nhưng **giữ** tài khoản và hồ sơ.
     * Dùng cho quyền được xoá dữ liệu của chủ thể dữ liệu (N3 GDPR) — TC-PRV-01, TC-PRV-02.
     */
    suspend fun clearUserData(userId: Long) {
        questionAnswerDao().deleteByUser(userId)
        quizItemDao().deleteByUser(userId)
        xpEventDao().deleteByUser(userId)
        notificationDao().deleteByUser(userId)
        userBadgeDao().deleteByUser(userId)
    }
}
