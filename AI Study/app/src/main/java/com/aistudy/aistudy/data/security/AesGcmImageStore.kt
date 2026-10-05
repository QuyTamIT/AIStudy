package com.aistudy.aistudy.data.security

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * FR3 + N1 - anh bai tap duoc ma hoa AES-GCM truoc khi ghi vao `filesDir`.
 *
 * Vì sao phai ma hoa: `QuestionAnswer.questionImagePath` tro thang vao mot
 * file trong bo nho rieng cua app. Android 10+ chan app khac doc, nhung anh
 * bai tap cua hoc sinh van la du lieu nhay cam. Ma hoa o day (khong phai o
 * repository) de moi duong ghi deu di qua mot cho.
 *
 * Khoa sinh trong `AndroidKeyStore` nen khong bao gio duoc xuat khoa thu.
 */
class AesGcmImageStore : ImageStore {

    override fun store(context: Context, uri: Uri): StoredImage? = runCatching {
        val plaintext = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (plaintext.isEmpty()) return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        val dir = File(context.filesDir, QUESTION_DIR).apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}-${plaintext.size}.bin")
        file.writeBytes(ciphertext)

        StoredImage(
            path = file.absolutePath,
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }.getOrNull()

    override fun read(context: Context, path: String, iv: String?): ByteArray? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val keyBytes = Base64.decode(iv ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, keyBytes))
        cipher.doFinal(file.readBytes())
    }.getOrNull()

    override fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KEY_ALGORITHM, PROVIDER)
        generator.init(KeyGenParameterSpecCompat())
        return generator.generateKey()
    }

    private fun KeyGenParameterSpecCompat() =
        android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALGORITHM = "AES"
        const val KEY_ALIAS = "ai_study_image_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val QUESTION_DIR = "questions"

        /** 128 bit la kich thuoc IV chuan cua GCM. */
        const val TAG_BITS = 128
    }
}
