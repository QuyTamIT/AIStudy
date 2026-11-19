package com.aistudy.aistudy

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudy.aistudy.ask.ImagePreparation
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.User
import com.aistudy.aistudy.profile.StudentProfileRepository
import com.aistudy.aistudy.profile.replaceCustomAvatar
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudentProfileInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun profileLoadsAndSavesLocallyWithoutCloudSync() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = db.userDao().insert(User().apply { fullName = "Local student" })
            val repository = StudentProfileRepository(context, db)
            assertTrue(repository.updateName(id, "Updated student").isSuccess)
            val profile = repository.getProfile(id)
            assertEquals("Updated student", profile.fullName)
            assertFalse(profile.isCloudSynced)
        } finally {
            db.close()
        }
    }

    @Test
    fun invalidAvatarKeepsPreviousPhoto() = assertRejectedAvatarPreservesPrevious {
        "not an image".byteInputStream()
    }

    @Test
    fun oversizedAvatarKeepsPreviousPhoto() = assertRejectedAvatarPreservesPrevious {
        ByteArrayInputStream(ByteArray(ImagePreparation.MAX_INPUT_BYTES + 1))
    }

    @Test
    fun inputCloseFailureKeepsPreviousPhoto() = assertRejectedAvatarPreservesPrevious {
        object : ByteArrayInputStream(photoBytes(Color.BLUE)) {
            override fun close() {
                throw IOException("Could not close selected image stream")
            }
        }
    }

    @Test
    fun validAvatarReplacesPreviousPhoto() = withExistingAvatar { target ->
        val selected = photoBytes(Color.BLUE)
        replaceCustomAvatar(target) { selected.inputStream() }
        assertArrayEquals(selected, target.readBytes())
        assertEquals(listOf(target.name), target.parentFile?.list()?.toList())
    }

    private fun assertRejectedAvatarPreservesPrevious(openInput: () -> InputStream) =
        withExistingAvatar { target ->
            val previous = target.readBytes()
            assertThrows(IOException::class.java) { replaceCustomAvatar(target, openInput) }
            assertTrue("The previous avatar file should still exist", target.isFile)
            assertArrayEquals(previous, target.readBytes())
            assertEquals(listOf(target.name), target.parentFile?.list()?.toList())
        }

    private fun withExistingAvatar(test: (File) -> Unit) {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "avatar-test-").toFile()
        try {
            val target = File(directory, "avatar_42.jpg").apply { writeBytes(photoBytes(Color.RED)) }
            test(target)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun photoBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
