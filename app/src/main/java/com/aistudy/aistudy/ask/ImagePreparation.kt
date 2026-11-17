package com.aistudy.aistudy.ask

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Local image bounds also apply to providers that report a missing or incorrect MIME. */
object ImagePreparation {
    const val MAX_INPUT_BYTES = 20 * 1024 * 1024
    private const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
    data class Payload(val bytes: ByteArray, val mimeType: String)

    fun copyBounded(input: InputStream, target: File, maxBytes: Int = MAX_INPUT_BYTES) {
        require(maxBytes > 0)
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw IOException("Image exceeds local size limit")
                    output.write(buffer, 0, count)
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun mimeType(file: File): String {
        if (!file.isFile || file.length() !in 1..MAX_INPUT_BYTES.toLong()) throw IOException("Invalid image size")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null) throw IOException("Invalid image")
        return bounds.outMimeType
    }

    fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        require(reqWidth > 0 && reqHeight > 0)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while ((options.outWidth.toLong() + sample - 1) / sample > reqWidth ||
            (options.outHeight.toLong() + sample - 1) / sample > reqHeight) {
            if (sample >= (1 shl 29)) return null
            sample *= 2
        }
        options.inSampleSize = sample
        options.inJustDecodeBounds = false
        return try { BitmapFactory.decodeFile(path, options) } catch (_: OutOfMemoryError) { null }
    }

    fun prepare(file: File): Payload {
        val mime = mimeType(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val bitmap = decodeSampledBitmap(file.path, 1600, 1600) ?: throw IOException("Image cannot be decoded")
        try {
            if (bounds.outWidth <= 1600 && bounds.outHeight <= 1600 && file.length() <= MAX_PAYLOAD_BYTES &&
                mime in setOf("image/jpeg", "image/png", "image/webp")) {
                return Payload(file.readBytes(), mime)
            }
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) || out.size() > MAX_PAYLOAD_BYTES) {
                    throw IOException("Prepared image exceeds payload limit")
                }
                return Payload(out.toByteArray(), "image/jpeg")
            }
        } finally { bitmap.recycle() }
    }

    /** Only disposable drafts can be removed; history lives in a different directory. */
    fun deleteDraft(file: File?, draftDirectory: File): Boolean = try {
        file != null && file.canonicalFile.parentFile == draftDirectory.canonicalFile && file.isFile && file.delete()
    } catch (_: IOException) { false }
}
