package com.aistudy.aistudy.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Nơi duy nhất tạo [AppDatabase].
 *
 * Dùng double-checked locking vì `get()` có thể được gọi từ nhiều coroutine cùng lúc
 * trong lúc app khởi động. Room đã tự quản lý kết nối nên không cần synchronized quanh
 * mọi lời gọi DAO, chỉ cần quanh lần khởi tạo.
 */
object DatabaseProvider {

    private const val DATABASE_NAME = "ai_study.db"

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Khoá ngoại khai báo CASCADE nên Room không bật sẵn. Bật thủ công ở đây
                    // để xoá user xoá luôn profile/câu hỏi, đúng N3 GDPR.
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            // TODO(Phase 13): thêm .addMigrations(...) ở đây khi version > 1.
            // Không có migration thì Room chỉ xoá sạch bảng — mất dữ liệu người dùng.
            .build()

    /** Chỉ dùng cho test: gắn database in-memory. */
    fun overrideForTest(database: AppDatabase?) {
        instance = database
    }
}
