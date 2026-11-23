package com.aistudy.aistudy.profile

import android.system.Os
import com.aistudy.aistudy.ask.ImagePreparation
import java.io.File
import java.io.IOException
import java.io.InputStream

internal const val PROFILE_AVATAR_SIZE_PX = 168

internal fun replaceCustomAvatar(target: File, openInput: () -> InputStream) {
    val directory = requireNotNull(target.parentFile)
    directory.mkdirs()
    val staged = File.createTempFile("avatar-", ".tmp", directory)
    try {
        openInput().use { ImagePreparation.copyBounded(it, staged) }
        val bitmap = ImagePreparation.decodeSampledBitmap(
            staged.path,
            PROFILE_AVATAR_SIZE_PX,
            PROFILE_AVATAR_SIZE_PX
        ) ?: throw IOException()
        bitmap.recycle()
        Os.rename(staged.absolutePath, target.absolutePath)
    } finally {
        staged.delete()
    }
}
