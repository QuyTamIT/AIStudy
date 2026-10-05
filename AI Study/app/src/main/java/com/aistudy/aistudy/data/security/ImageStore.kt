package com.aistudy.aistudy.data.security

import android.net.Uri
import android.content.Context

/**
 * FR3 + N1 - anh bai tap nhieu chon duoc ma hoa AES-GCM roi luu vao `filesDir`,
 * khong ghi thang vao shared storage (Android 10+ chan ghi resoruce cua app khac).
 *
 * [QuestionRepository.askWithImage] nhan dung `path` + `iv` tra ve tu day.
 */
interface ImageStore {

    /** @return duong dan file da ghi trong `filesDir` + IV da ma hoa, hoac null neu doc that bai. */
    fun store(context: Context, uri: Uri): StoredImage?

    fun read(context: Context, path: String, iv: String?): ByteArray?

    fun delete(path: String?)
}

data class StoredImage(val path: String, val iv: String)
