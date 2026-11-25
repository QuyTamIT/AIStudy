package com.aistudy.aistudy.auth

import android.content.Context
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDefaults

/** Resolve a saved login before creating or loading any user-owned data. */
internal fun restoreSessionProfile(context: Context): Profile? {
    val preferences = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
    val savedId = preferences.all[LoginActivity.KEY_USER_ID] ?: return null
    if (savedId is Long && savedId > 0L) {
        val database = AppDatabase.getInstance(context)
        if (database.userDao().accountExists(savedId)) {
            return ProfileDefaults.ensureExists(database.profileDao(), savedId)
        }
    }

    preferences.edit().remove(LoginActivity.KEY_USER_ID).apply()
    return null
}
