package com.aistudy.aistudy.platform

import android.content.Context
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.data.db.DatabaseProvider
import com.aistudy.aistudy.data.repository.AuthRepositoryImpl
import com.aistudy.aistudy.data.repository.GamificationRepositoryImpl
import com.aistudy.aistudy.data.repository.NotificationRepositoryImpl
import com.aistudy.aistudy.data.repository.ProfileRepositoryImpl
import com.aistudy.aistudy.data.repository.ProgressRepositoryImpl
import com.aistudy.aistudy.data.repository.QuestionRepositoryImpl
import com.aistudy.aistudy.data.repository.QuizRepositoryImpl
import com.aistudy.aistudy.data.security.AesGcmImageStore
import com.aistudy.aistudy.data.security.EncryptedPrefsSessionStore
import com.aistudy.aistudy.domain.ai.AiClient
import com.aistudy.aistudy.domain.ai.FakeAiClient
import com.aistudy.aistudy.domain.ai.GeminiAiClient

/**
 * Noi lang ServiceLocator lap cac implementation that cua interface trong
 * `data/repository`, `data/security` va `domain/ai`.
 *
 * Khong dung Hilt/Dagger theo quyet dinh D-1 trong design doc: mot Gradle
 * module, khong annotation processor cho DI, giu build don gian.
 */
internal object ServiceGraph {

    fun install(context: Context) {
        val database = DatabaseProvider.get(context)
        val imageStore = AesGcmImageStore()
        val sessionStore = EncryptedPrefsSessionStore(context)

        val aiClient: AiClient = if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            GeminiAiClient(
                apiKey = BuildConfig.GEMINI_API_KEY,
                model = BuildConfig.GEMINI_MODEL
            )
        } else {
            // Khong co key thi app van chay duoc day du bang noi dung mo
            // phong - xem `FakeAiClient` vi sao day khong phai stub de lam nhanh.
            FakeAiClient()
        }

        val profileRepository = ProfileRepositoryImpl(database.profileDao())
        val gamificationRepository = GamificationRepositoryImpl(
            userDao = database.userDao(),
            xpEventDao = database.xpEventDao(),
            userBadgeDao = database.userBadgeDao(),
            questionAnswerDao = database.questionAnswerDao(),
            quizItemDao = database.quizItemDao()
        )
        val notificationRepository = NotificationRepositoryImpl(
            notificationDao = database.notificationDao(),
            xpEventDao = database.xpEventDao()
        )
        val questionRepository = QuestionRepositoryImpl(
            questionAnswerDao = database.questionAnswerDao(),
            notificationRepository = notificationRepository,
            gamificationRepository = gamificationRepository,
            profileRepository = profileRepository,
            aiClient = aiClient,
            imageStore = imageStore,
            appContext = context.applicationContext
        )
        val quizRepository = QuizRepositoryImpl(
            quizItemDao = database.quizItemDao(),
            questionAnswerDao = database.questionAnswerDao(),
            profileRepository = profileRepository,
            gamificationRepository = gamificationRepository,
            aiClient = aiClient
        )

        ServiceLocator.sessionStore = sessionStore
        ServiceLocator.imageStore = imageStore
        ServiceLocator.aiClient = aiClient
        ServiceLocator.profileRepository = profileRepository
        ServiceLocator.gamificationRepository = gamificationRepository
        ServiceLocator.notificationRepository = notificationRepository
        ServiceLocator.questionRepository = questionRepository
        ServiceLocator.quizRepository = quizRepository
        ServiceLocator.progressRepository = ProgressRepositoryImpl(
            questionAnswerDao = database.questionAnswerDao(),
            quizItemDao = database.quizItemDao(),
            xpEventDao = database.xpEventDao(),
            notificationDao = database.notificationDao()
        )
        ServiceLocator.authRepository = AuthRepositoryImpl(
            database = database,
            userDao = database.userDao(),
            sessionStore = sessionStore
        )
    }
}
