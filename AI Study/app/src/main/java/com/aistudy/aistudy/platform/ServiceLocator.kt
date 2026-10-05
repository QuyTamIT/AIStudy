package com.aistudy.aistudy.platform

import android.content.Context
import com.aistudy.aistudy.data.repository.AuthRepository
import com.aistudy.aistudy.data.repository.GamificationRepository
import com.aistudy.aistudy.data.repository.NotificationRepository
import com.aistudy.aistudy.data.repository.ProfileRepository
import com.aistudy.aistudy.data.repository.ProgressRepository
import com.aistudy.aistudy.data.repository.QuestionRepository
import com.aistudy.aistudy.data.repository.QuizRepository
import com.aistudy.aistudy.data.security.ImageStore
import com.aistudy.aistudy.data.security.SessionStore
import com.aistudy.aistudy.domain.ai.AiClient

/**
 * DI container. Khong dung Hilt/Dagger theo quyet dinh D-1 trong design doc -
 * mot Gradle module, khong annotation processor cho DI, giu build don gian.
 *
 * [init] goi mot lan tu `Application.onCreate()`. Truoc `init`, moi truong co
 * `IllegalStateException` co message ro rang thay vi `UninitializedPropertyAccessException`.
 */
object ServiceLocator {

    private const val NOT_INIT =
        "ServiceLocator chua khoi tao. goi ServiceLocator.init(context) tu Application.onCreate()"

    lateinit var sessionStore: SessionStore
        internal set
    lateinit var imageStore: ImageStore
        internal set
    lateinit var aiClient: AiClient
        internal set
    lateinit var authRepository: AuthRepository
        internal set
    lateinit var profileRepository: ProfileRepository
        internal set
    lateinit var questionRepository: QuestionRepository
        internal set
    lateinit var quizRepository: QuizRepository
        internal set
    lateinit var progressRepository: ProgressRepository
        internal set
    lateinit var gamificationRepository: GamificationRepository
        internal set
    lateinit var notificationRepository: NotificationRepository
        internal set

    @Volatile
    private var initialised = false

    val isInitialised: Boolean get() = initialised

    fun init(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            ServiceGraph.install(context.applicationContext)
            initialised = true
        }
    }

    /** Noi co nghiep kiem tra trang thai khoi tao ma khong can mang `lateinit`. */
    fun checkReady() {
        check(initialised) { NOT_INIT }
    }
}
