package com.aistudy.aistudy.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * N1 - session luu bang `EncryptedSharedPreferences`, khong dung SharedPreferences
 * tho. Design doc D-6.
 *
 * Chi luu `user_id` va `saved_at`. Khong luu token, khong luu mat khau: khi
 * can xac thuc lai, app dung `PasswordHasher` va `UserDao`, khong phai so sanh
 * secret nao da luoi.
 */
class EncryptedPrefsSessionStore(
    context: Context
) : SessionStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override val userId: Flow<Long?> = callbackFlow {
        trySend(currentUserId())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_USER_ID) trySend(currentUserId())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Khong `awaitClose` thi listener giu tham chieu toi prefs va khong bao
        // gio, gia doi voi moi collector la mot listener moi.
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun currentUserId(): Long? = prefs.getLong(KEY_USER_ID, NO_USER).takeIf { it != NO_USER }

    override fun save(userId: Long) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_SAVED_AT)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "ai_study_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_SAVED_AT = "saved_at"
        const val NO_USER = -1L
    }
}
