package com.aistudy.aistudy

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList

class AIStudyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS_APP_LOCALE, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_DEFAULT_APPLIED, false)) {
            prefs.edit().putBoolean(KEY_DEFAULT_APPLIED, true).apply()
            val localeManager = getSystemService(LocaleManager::class.java)
            if (localeManager != null && localeManager.applicationLocales.isEmpty) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(DEFAULT_LANGUAGE_TAG)
            }
        }
    }

    companion object {
        const val DEFAULT_LANGUAGE_TAG = "en"
        const val VIETNAMESE_LANGUAGE_TAG = "vi"
        private const val PREFS_APP_LOCALE = "app_locale"
        private const val KEY_DEFAULT_APPLIED = "default_applied"

        internal fun isVietnameseLocaleTag(
            applicationLocaleTags: String?,
            configurationLocaleTags: String?
        ): Boolean {
            val activeTag = applicationLocaleTags?.trim()?.takeIf { it.isNotEmpty() }
                ?: configurationLocaleTags?.trim().orEmpty()
            val primary = activeTag.substringBefore(',').substringBefore('-').substringBefore('_')
            return primary.equals(VIETNAMESE_LANGUAGE_TAG, ignoreCase = true)
        }
    }
}
