package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * FR1 - dang ky / dang nhap / dang xuat, va N1 (PBKDF2) + N3 (xoa du lieu).
 *
 * Mat khau KHONG bao gio duoc luoi - xem [com.aistudy.aistudy.data.security.PasswordHasher].
 */
interface AuthRepository {

    /** User hien tai, null khi chua dang nhap. La nguon suy cho [sessionUserId]. */
    val currentUser: Flow<User?>

    /** Id user da dang nhap, null khi chua dang nhap. UI dung de biet ai dang dung app. */
    val sessionUserId: StateFlow<Long?>

    /**
     * @param failure la `IllegalArgumentException` khi email/sai mat khau yeu, la
     *               `IllegalStateException` khi email da ton tai.
     */
    suspend fun register(email: String, password: String, displayName: String): Result<User>

    suspend fun login(email: String, password: String): Result<User>

    suspend fun logout()

    /** N3 GDPR / COPPA - xoa toan bo du lieu sinh hoat, giu tai khoan de xoa sau. */
    suspend fun deleteAccount(): Result<Unit>
}
