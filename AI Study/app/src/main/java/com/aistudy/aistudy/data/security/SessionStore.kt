package com.aistudy.aistudy.data.security

import kotlinx.coroutines.flow.Flow

/**
 * N1 - luu session bang `EncryptedSharedPreferences`, khong dung SharedPreferences
 * tho (design doc D-6). Chi luu `userId` va thoi diem dang nhap, khong luu token
 * hay mat khau.
 */
interface SessionStore {

    val userId: Flow<Long?>

    fun currentUserId(): Long?

    fun save(userId: Long)

    fun clear()
}
