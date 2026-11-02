package com.aistudy.aistudy.answer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ask.GeminiClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import org.json.JSONException

data class AnswerUiState(
    val snapshot: AnswerSnapshot? = null,
    val busy: Boolean = false,
    val error: Int? = null,
    val loadFailed: Boolean = false
)

class AnswerViewModel : ViewModel() {
    class MissingKeyException : IllegalStateException()

    private val executor = Executors.newSingleThreadExecutor()
    private val mutableState = MutableLiveData(AnswerUiState(busy = true))
    val state: LiveData<AnswerUiState> = mutableState
    private var repository: AnswerRepository? = null

    fun start(repository: AnswerRepository) {
        if (this.repository != null) return
        this.repository = repository
        executor.execute {
            try {
                val snapshot = repository.load()
                val needsClassification = snapshot.content.type == null
                mutableState.postValue(AnswerUiState(snapshot, busy = needsClassification))
                if (needsClassification) complete(snapshot, classify = true)
            } catch (_: Exception) {
                mutableState.postValue(AnswerUiState(loadFailed = true))
            }
        }
    }

    fun showMore() {
        val current = mutableState.value ?: return
        val snapshot = current.snapshot ?: return
        if (current.busy || (snapshot.content.type != null && !snapshot.content.canExpand)) return
        mutableState.value = current.copy(busy = true, error = null)
        executor.execute { complete(snapshot, classify = snapshot.content.type == null) }
    }

    private fun complete(snapshot: AnswerSnapshot, classify: Boolean) {
        val repository = repository ?: return
        try {
            val updated = if (classify) repository.classify(snapshot) else repository.expand(snapshot)
            mutableState.postValue(AnswerUiState(updated))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            // Never log prompts, API keys, raw response bodies or exception messages.
            Log.w("AnswerViewModel", "Answer update failed (${error.javaClass.simpleName})")
            if (error is AnswerRepository.AccessException) {
                mutableState.postValue(AnswerUiState(loadFailed = true))
                return
            }
            val latest = if (error is AnswerRepository.ContentChangedException) {
                try { repository.load() } catch (_: Exception) { snapshot }
            } else snapshot
            mutableState.postValue(AnswerUiState(latest, error = errorMessage(error, classify)))
        }
    }

    private fun errorMessage(error: Exception, classify: Boolean): Int = when (error) {
        is MissingKeyException -> R.string.ask_error_key_missing
        is AnswerRepository.ContentChangedException -> R.string.answer_error_changed
        is GeminiClient.RequestException -> when (error.reason) {
            GeminiClient.ErrorReason.API_KEY_INVALID, GeminiClient.ErrorReason.API_KEY_EXPIRED -> R.string.ask_error_key_invalid
            GeminiClient.ErrorReason.API_KEY_RESTRICTED, GeminiClient.ErrorReason.SERVICE_DISABLED -> R.string.ask_error_access
            GeminiClient.ErrorReason.BILLING_DISABLED -> R.string.ask_error_billing
            GeminiClient.ErrorReason.UNKNOWN -> when (error.statusCode) {
                401 -> R.string.ask_error_key_invalid
                403 -> R.string.ask_error_access
                404 -> R.string.ask_error_model
                408, 504 -> R.string.ask_error_timeout
                413 -> R.string.ask_error_too_large
                429 -> R.string.ask_error_quota
                in 500..599 -> R.string.ask_error_service
                else -> R.string.ask_error_request
            }
        }
        is SocketTimeoutException -> R.string.ask_error_timeout
        is GeminiClient.InvalidResponseException, is JSONException -> R.string.ask_error_response
        is IllegalArgumentException -> if (classify) R.string.ask_error_response else R.string.answer_error_more_response
        is IOException -> R.string.ask_error_network
        else -> R.string.answer_error_save_more
    }

    override fun onCleared() {
        executor.shutdownNow()
        super.onCleared()
    }
}
