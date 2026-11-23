package com.aistudy.aistudy.xp

import androidx.annotation.StringRes
import com.aistudy.aistudy.R

object UnlockCatalog {

    const val AVATAR_FOX = "fox"
    const val AVATAR_OWL = "owl"
    const val AVATAR_CAT = "cat"
    const val AVATAR_ROCKET = "rocket"

    val AVATARS = arrayOf(
        AVATAR_FOX, AVATAR_OWL, AVATAR_CAT, AVATAR_ROCKET
    )

    const val THEME_DEFAULT = "default"
    const val THEME_NIGHT = "night"
    const val THEME_PASTEL = "pastel"

    val THEMES = arrayOf(
        THEME_DEFAULT, THEME_NIGHT, THEME_PASTEL
    )

    fun isCustomAvatar(avatarId: String?): Boolean {
        if (avatarId.isNullOrEmpty()) return false
        return avatarId.startsWith("custom:") || avatarId.startsWith("/") || avatarId.contains(java.io.File.separator)
    }

    fun getCustomAvatarPath(avatarId: String?): String? {
        if (avatarId == null || !isCustomAvatar(avatarId)) return null
        val path = if (avatarId.startsWith("custom:")) avatarId.removePrefix("custom:") else avatarId
        val file = java.io.File(path)
        return if (file.exists() && file.isFile) file.absolutePath else null
    }

    fun isAvatarUnlocked(avatarId: String?, level: Int): Boolean {
        if (isCustomAvatar(avatarId)) return true
        val required = requiredLevelForAvatar(avatarId)
        return required > 0 && level >= required
    }

    fun isThemeUnlocked(themeId: String?, level: Int): Boolean {
        val required = requiredLevelForTheme(themeId)
        return required > 0 && level >= required
    }

    fun requiredLevelForAvatar(avatarId: String?): Int {
        if (isCustomAvatar(avatarId)) return 1
        val index = AVATARS.indexOf(avatarId)
        return if (index >= 0) index + 1 else -1
    }

    fun requiredLevelForTheme(themeId: String?): Int {
        val index = THEMES.indexOf(themeId)
        return if (index >= 0) index + 1 else -1
    }

    fun emojiForAvatar(avatarId: String?): String {
        if (isCustomAvatar(avatarId)) return "👤"
        return when (avatarId) {
            AVATAR_OWL -> "🦉"
            AVATAR_CAT -> "🐱"
            AVATAR_ROCKET -> "🚀"
            else -> "🦊"
        }
    }

    @StringRes
    fun labelResForTheme(themeId: String?): Int = when (themeId) {
        THEME_NIGHT -> R.string.progress_theme_night
        THEME_PASTEL -> R.string.progress_theme_pastel
        else -> R.string.progress_theme_default
    }

    fun backgroundColorRes(themeId: String?): Int {
        return when (themeId) {
            THEME_NIGHT -> R.color.theme_night_bg
            THEME_PASTEL -> R.color.theme_pastel_bg
            else -> R.color.theme_default_bg
        }
    }

    fun normalizeAvatarId(avatarId: String?): String {
        if (avatarId.isNullOrEmpty()) {
            return AVATAR_FOX
        }
        if (isCustomAvatar(avatarId)) {
            return avatarId
        }
        return AVATARS.firstOrNull { it == avatarId } ?: AVATAR_FOX
    }

    fun normalizeThemeId(themeId: String?): String {
        if (themeId.isNullOrEmpty()) {
            return THEME_DEFAULT
        }
        return THEMES.firstOrNull { it == themeId } ?: THEME_DEFAULT
    }
}

