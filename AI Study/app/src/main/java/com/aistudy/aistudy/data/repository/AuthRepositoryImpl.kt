package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.data.db.AppDatabase
import com.aistudy.aistudy.data.db.dao.UserDao
import com.aistudy.aistudy.data.security.PasswordHasher
import com.aistudy.aistudy.data.security.SessionStore
import com.aistudy.aistudy.domain.model.entity.User
import com.aistudy.aistudy.domain.rules.ProfileDefaults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * FR1 + N1 + N3.
 *
 * Session duoc giu bang `EncryptedSharedPreferences` ([SessionStore]) chu khong
 * phai trong `AppDatabase`, de logout khong xoa duoc nham va mat phai tai
 * mot bang Room moi dong.
 */
class AuthRepositoryImpl(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val sessionStore: SessionStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    private val _sessionUserId = MutableStateFlow(sessionStore.currentUserId())

    override val sessionUserId: StateFlow<Long?> = _sessionUserId.asStateFlow()

    override val currentUser: Flow<User?> = _sessionUserId
        .flatMapLatest { id -> if (id == null) flowOf(null) else userDao.observeById(id) }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<User> = runIo {
        val normalisedEmail = email.trim().lowercase()
        require(normalisedEmail.matches(EMAIL_PATTERN)) { "Email khong hop le" }
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Mat khau phai co it nhat $MIN_PASSWORD_LENGTH ky tu"
        }
        require(password.any { it.isLetter() } && password.any { it.isDigit() }) {
            "Mat khau phai co ca chu va so"
        }
        require(displayName.isNotBlank()) { "Ten hien thi khong duoc de trong" }

        if (userDao.findByEmail(normalisedEmail) != null) {
            throw IllegalStateException("Email nay da duoc dung")
        }

        val hashed = PasswordHasher.hash(password)
        val id = userDao.insert(
            User(
                email = normalisedEmail,
                passwordHash = hashed.hash,
                passwordSalt = hashed.salt,
                displayName = displayName.trim(),
                createdAt = System.currentTimeMillis()
            )
        )

        // Profile mac dinh phai ton tai ngay luc dang ky: `onboardedAt == null`
        // chinh la tin hieu duy nhat AuthGate dung de chuyen sang onboarding.
        val profile = ProfileDefaults.forNewUser(id)
        database.profileDao().insert(profile)

        userDao.touchLogin(id, System.currentTimeMillis())
        persistSession(id)
        userDao.findById(id) ?: throw IllegalStateException("Khong doc lai duoc user vua tao")
    }

    override suspend fun login(email: String, password: String): Result<User> = runIo {
        val normalisedEmail = email.trim().lowercase()
        val user = userDao.findByEmail(normalisedEmail)
            ?: throw IllegalArgumentException("Email hoac mat khau khong dung")

        if (!PasswordHasher.verify(password, user.passwordHash, user.passwordSalt)) {
            throw IllegalArgumentException("Email hoac mat khau khong dung")
        }

        userDao.touchLogin(user.id, System.currentTimeMillis())
        persistSession(user.id)
        user
    }

    override suspend fun logout() {
        withContext(io) {
            sessionStore.clear()
            _sessionUserId.value = null
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = runIo {
        val userId = sessionStore.currentUserId()
            ?: throw IllegalStateException("Chua dang nhap")

        // Thu tu bat buoc: xoa du lieu sinh hoat truoc, roi moi xoa user.
        // `profiles` bi CASCADE theo `users` nen khong can xoa tay.
        database.clearUserData(userId)
        userDao.deleteById(userId)

        sessionStore.clear()
        _sessionUserId.value = null
    }

    private fun persistSession(userId: Long) {
        sessionStore.save(userId)
        _sessionUserId.value = userId
    }

    private suspend inline fun <T> runIo(crossinline block: suspend () -> T): Result<T> =
        withContext(io) {
            runCatching { block() }
        }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8

        /** Du kien `Patterns.EMAIL_ADDRESS` cua Android, tach ra de repository khong can android. */
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")
    }
}
