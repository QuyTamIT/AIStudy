package com.aistudy.aistudy.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aistudy.aistudy.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Android stores the app locale and recreates open activities with translated resources. */
object AppLanguage {
    private val languageTags = listOf("vi", "en")

    fun showPicker(activity: AppCompatActivity) {
        val currentLanguage = activity.resources.configuration.locales[0].language
        val selectedIndex = languageTags.indexOf(currentLanguage).takeIf { it >= 0 } ?: 1
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.profile_language_title)
            .setSingleChoiceItems(
                arrayOf(activity.getString(R.string.profile_language_vietnamese), activity.getString(R.string.profile_language_english)),
                selectedIndex
            ) { dialog, index ->
                dialog.dismiss()
                val tag = languageTags[index]
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
            }
            .setNegativeButton(R.string.logout_cancel, null)
            .show()
    }
}
