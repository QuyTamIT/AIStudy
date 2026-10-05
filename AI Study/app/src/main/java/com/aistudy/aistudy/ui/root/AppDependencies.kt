package com.aistudy.aistudy.ui.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aistudy.aistudy.data.repository.AuthRepository
import com.aistudy.aistudy.data.repository.GamificationRepository
import com.aistudy.aistudy.data.repository.NotificationRepository
import com.aistudy.aistudy.data.repository.ProfileRepository
import com.aistudy.aistudy.data.repository.ProgressRepository
import com.aistudy.aistudy.data.repository.QuestionRepository
import com.aistudy.aistudy.data.repository.QuizRepository
import com.aistudy.aistudy.domain.ai.AiClient
import com.aistudy.aistudy.platform.ServiceLocator

/**
 * God do moi repository, resolve MOT LAN o composition root roi truyen xuong.
 *
 * ViewModel nhan constructor injection tu day - `ServiceLocator` khong bao gio
 * duoc goi trong ViewModel, nho do ViewModel van test duoc voi mock.
 */
data class AppDependencies(
    val authRepository: AuthRepository,
    val profileRepository: ProfileRepository,
    val questionRepository: QuestionRepository,
    val quizRepository: QuizRepository,
    val progressRepository: ProgressRepository,
    val gamificationRepository: GamificationRepository,
    val notificationRepository: NotificationRepository,
    val aiClient: AiClient
) {
    companion object {
        fun fromServiceLocator(): AppDependencies {
            ServiceLocator.checkReady()
            return AppDependencies(
                authRepository = ServiceLocator.authRepository,
                profileRepository = ServiceLocator.profileRepository,
                questionRepository = ServiceLocator.questionRepository,
                quizRepository = ServiceLocator.quizRepository,
                progressRepository = ServiceLocator.progressRepository,
                gamificationRepository = ServiceLocator.gamificationRepository,
                notificationRepository = ServiceLocator.notificationRepository,
                aiClient = ServiceLocator.aiClient
            )
        }
    }
}

/** `remember` de khong tao lai data class moi o moi lan recomposition. */
@Composable
fun rememberAppDependencies(): AppDependencies = remember { AppDependencies.fromServiceLocator() }