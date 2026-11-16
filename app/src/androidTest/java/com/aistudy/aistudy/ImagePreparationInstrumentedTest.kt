package com.aistudy.aistudy

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.ask.ImagePreparation
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import com.aistudy.aistudy.ask.AskFragment
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePreparationInstrumentedTest {
    @Test fun previewBoundsBothLongAndWideImages() {
        for ((width, height) in listOf(8192 to 64, 64 to 8192, 800 to 600)) {
            val file = File.createTempFile("image-fixture-", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
            try {
                val original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
                original.recycle()
                val preview = requireNotNull(AskFragment.decodeSampledBitmap(file.path, 600, 600))
                try {
                    assertTrue("width=${preview.width}", preview.width <= 600)
                    assertTrue("height=${preview.height}", preview.height <= 600)
                } finally { preview.recycle() }
            } finally { file.delete() }
        }
    }
    @Test fun payloadMimeMatchesActualBytesAndLargePngBecomesJpeg() {
        val file = File.createTempFile("mime-fixture-", ".jpg", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            for (size in listOf(100, 2400)) {
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                val payload = ImagePreparation.prepare(file)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size, bounds)
                assertEquals(if (size == 100) "image/png" else "image/jpeg", payload.mimeType)
                assertEquals(payload.mimeType, bounds.outMimeType)
                assertTrue(bounds.outWidth <= 1600 && bounds.outHeight <= 1600)
                assertTrue(payload.bytes.size <= 2 * 1024 * 1024)
            }
        } finally { file.delete() }
    }

    @Test fun oversizedCopyDeletesPartialFileAndCorruptImageIsRejected() {
        val file = File.createTempFile("copy-fixture-", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            assertThrows(IOException::class.java) {
                ImagePreparation.copyBounded(ByteArrayInputStream(ByteArray(1025)), file, 1024)
            }
            assertFalse(file.exists())
            file.writeText("not an image")
            assertThrows(IOException::class.java) { ImagePreparation.prepare(file) }
        } finally { file.delete() }
    }

    @Test fun draftCleanupCannotDeletePersistedHistoryImage() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "image-cleanup-fixture").apply { mkdirs() }
        val draftDir = File(root, "drafts").apply { mkdirs() }
        val draft = File(draftDir, "draft.png").apply { writeText("draft") }
        val saved = File(root, "history.png").apply { writeText("saved") }
        try {
            assertFalse(ImagePreparation.deleteDraft(saved, draftDir))
            assertTrue(saved.exists())
            assertTrue(ImagePreparation.deleteDraft(draft, draftDir))
            assertFalse(draft.exists())
        } finally { root.deleteRecursively() }
    }
}
