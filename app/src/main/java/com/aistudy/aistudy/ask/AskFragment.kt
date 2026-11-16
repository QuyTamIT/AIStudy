package com.aistudy.aistudy.ask

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.answer.AnswerContent
import com.aistudy.aistudy.answer.AnswerPrompts
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONException
import org.json.JSONObject

class AskFragment : Fragment() {
    private lateinit var editQuestion: EditText
    private lateinit var buttonSend: MaterialButton
    private lateinit var buttonCamera: MaterialButton
    private lateinit var buttonGallery: MaterialButton
    private lateinit var layoutImagePreview: LinearLayout
    private lateinit var imgPreview: ImageView
    private var btnRemoveImage: View? = null
    private lateinit var progressLoading: View
    private lateinit var textError: TextView

    private var suggestionButtons = emptyList<MaterialButton>()
    private var executor: ExecutorService? = null
    private var loading = false
    private var pendingAnswerId = 0L
    private var pendingUserId = 0L

    // Phase 10: Selected image state and ActivityResult launchers
    private var selectedImageFile: File? = null
    private var cameraFile: File? = null

    private lateinit var requestCameraPermission: ActivityResultLauncher<String>
    private lateinit var requestGalleryPermission: ActivityResultLauncher<String>
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private lateinit var pickImage: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(requireContext(), R.string.ask_permission_camera, Toast.LENGTH_SHORT).show()
            }
        }

        requestGalleryPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                pickImage.launch("image/*")
            } else {
                Toast.makeText(requireContext(), R.string.ask_permission_gallery, Toast.LENGTH_SHORT).show()
            }
        }

        takePicture = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val file = cameraFile
            if (success && file != null && file.exists()) {
                setSelectedImage(file)
            } else {
                deleteDraft(file)
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
            cameraFile = null
        }

        pickImage = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                setSelectedImage(copyUriToQaImages(uri))
            } catch (e: Exception) {
                Log.w(TAG, "gallery copy failed (${e.javaClass.simpleName})")
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.cloneInContext(ContextThemeWrapper(requireContext(), R.style.Theme_AIStudy_Study))
        .inflate(R.layout.fragment_ask, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editQuestion = view.findViewById(R.id.edit_question)
        buttonSend = view.findViewById(R.id.btn_send)
        buttonCamera = view.findViewById(R.id.btn_camera)
        buttonGallery = view.findViewById(R.id.btn_gallery)
        layoutImagePreview = view.findViewById(R.id.layout_image_preview)
        imgPreview = view.findViewById(R.id.img_preview)
        btnRemoveImage = view.findViewById(R.id.btn_remove_image)
        progressLoading = view.findViewById(R.id.progress_loading)
        textError = view.findViewById(R.id.tv_ask_error)

        editQuestion.doAfterTextChanged {
            view.findViewById<TextInputLayout>(R.id.input_question).error = null
            textError.visibility = View.GONE
        }

        // Main already handles system bars. Only the remaining IME overlap belongs to Ask.
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val navigation = activity?.findViewById<View>(R.id.bottom_nav)?.height ?: 0
            root.setPadding(0, 0, 0, (ime - systemBar - navigation).coerceAtLeast(0))
            insets
        }
        ViewCompat.requestApplyInsets(view)

        buttonCamera.setOnClickListener { ensureCameraPermission() }
        buttonGallery.setOnClickListener { ensureGalleryPermission() }
        btnRemoveImage?.setOnClickListener { clearSelectedImage() }

        suggestionButtons = listOf(
            R.id.btn_suggestion_1, R.id.btn_suggestion_2,
            R.id.btn_suggestion_3, R.id.btn_suggestion_4
        ).map { view.findViewById<MaterialButton>(it) }
        val suggestions = listOf(
            R.string.ask_suggestion_1, R.string.ask_suggestion_2,
            R.string.ask_suggestion_3, R.string.ask_suggestion_4
        )
        suggestionButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                editQuestion.setText(suggestions[index])
                editQuestion.setSelection(editQuestion.text.length)
                editQuestion.requestFocus()
                textError.visibility = View.GONE
                WindowCompat.getInsetsController(requireActivity().window, editQuestion).show(WindowInsetsCompat.Type.ime())
            }
        }
        pendingAnswerId = savedInstanceState?.getLong("pendingQaId") ?: 0L
        pendingUserId = savedInstanceState?.getLong("pendingUserId") ?: 0L
        executor = Executors.newSingleThreadExecutor()
        buttonSend.setOnClickListener { submit() }
        setLoading(false)
        savedInstanceState?.getString("selectedImagePath")?.let { path ->
            val file = File(path)
            if (isDraft(file) && file.exists()) setSelectedImage(file)
        }
        savedInstanceState?.getString("cameraImagePath")?.let { path ->
            File(path).takeIf { isDraft(it) }?.let { cameraFile = it }
        }
    }

    override fun onResume() {
        super.onResume()
        openPendingAnswer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pendingQaId", pendingAnswerId)
        outState.putLong("pendingUserId", pendingUserId)
        outState.putString("selectedImagePath", selectedImageFile?.path)
        outState.putString("cameraImagePath", cameraFile?.path)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        executor?.shutdownNow()
        executor = null
        suggestionButtons = emptyList()
        btnRemoveImage = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (activity?.isChangingConfigurations != true) {
            deleteDraft(selectedImageFile)
            deleteDraft(cameraFile)
        }
        super.onDestroy()
    }

    // Phase 10: Camera & Gallery helpers
    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun ensureGalleryPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pickImage.launch("image/*")
        } else {
            requestGalleryPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(requireContext().cacheDir, "camera")
            if (!dir.exists() && !dir.mkdirs()) {
                throw Exception("cannot create camera cache")
            }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                file
            )
            takePicture.launch(uri)
        } catch (e: Exception) {
            Log.w(TAG, "launch camera failed (${e.javaClass.simpleName})")
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToQaImages(uri: Uri): File {
        val dir = File(requireContext().cacheDir, "qa_image_drafts").apply { mkdirs() }
        val out = File.createTempFile("qa_", ".image", dir)
        try {
            val input = requireContext().contentResolver.openInputStream(uri) ?: throw IOException("No image stream")
            input.use { ImagePreparation.copyBounded(it, out) }
            ImagePreparation.mimeType(out)
            return out
        } catch (error: Exception) {
            out.delete()
            throw error
        }
    }

    private fun isDraft(file: File): Boolean = try {
        val cache = requireContext().cacheDir
        val parent = file.canonicalFile.parentFile
        parent == File(cache, "qa_image_drafts").canonicalFile || parent == File(cache, "camera").canonicalFile
    } catch (_: IOException) { false }

    private fun deleteDraft(file: File?) {
        val cache = context?.cacheDir ?: return
        ImagePreparation.deleteDraft(file, File(cache, "qa_image_drafts"))
        ImagePreparation.deleteDraft(file, File(cache, "camera"))
    }

    private fun setSelectedImage(file: File) {
        try { ImagePreparation.mimeType(file) } catch (_: IOException) {
            deleteDraft(file)
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            return
        }
        val previous = selectedImageFile
        selectedImageFile = file
        if (previous != file) deleteDraft(previous)
        val previewBitmap = decodeSampledBitmap(file.absolutePath, 600, 600)
        if (previewBitmap != null) {
            imgPreview.setImageBitmap(previewBitmap)
            layoutImagePreview.visibility = View.VISIBLE
        } else {
            clearSelectedImage()
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearSelectedImage() {
        deleteDraft(selectedImageFile)
        selectedImageFile = null
        imgPreview.setImageDrawable(null)
        layoutImagePreview.visibility = View.GONE
    }

    private fun submit() {
        if (loading) return
        val inputQuestion = editQuestion.text.toString().trim()
        var question = inputQuestion
        val hasImage = selectedImageFile?.exists() == true
        val isImageOnly = inputQuestion.isBlank() && hasImage

        if (question.isBlank() && !hasImage) {
            view?.findViewById<TextInputLayout>(R.id.input_question)?.error = getString(R.string.ask_empty_both)
            editQuestion.requestFocus()
            return
        }

        if (question.isBlank()) {
            question = getString(R.string.ask_image_default_question)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_", ignoreCase = true)) {
            showError(R.string.ask_error_key_missing)
            return
        }
        val host = activity ?: return
        val requestView = view ?: return
        val worker = executor ?: return
        val preferences = host.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = preferences.getLong(LoginActivity.KEY_USER_ID, 0L)
        if (userId <= 0L) {
            startActivity(Intent(host, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            host.finish()
            return
        }
        val database = AppDatabase.getInstance(host.applicationContext)
        textError.visibility = View.GONE
        view?.findViewById<TextInputLayout>(R.id.input_question)?.error = null
        setLoading(true)

        val questionFinal = question
        val imageFile = selectedImageFile
        val useImage = hasImage

        val fallbackLanguageTag = AnswerPrompts.fallbackLanguageTag(resources)
        val trace = AiRequestTrace(if (useImage) AiRequestTrace.Feature.ASK_IMAGE else AiRequestTrace.Feature.ASK_TEXT)
        worker.execute {
            var persistedImage: File? = null
            var imageSaved = false
            var outcome = AiRequestTrace.Outcome.REQUEST_FAILED
            try {
                var imageBase64: String? = null
                var imagePath: String? = null
                var mime: String? = null

                if (useImage && imageFile != null && imageFile.exists()) {
                    val payload = ImagePreparation.prepare(imageFile)
                    imageBase64 = Base64.encodeToString(payload.bytes, Base64.NO_WRAP)
                    mime = payload.mimeType
                }

                val profile = ProfileDefaults.ensureExists(database.profileDao(), userId)
                val prompt = buildPrompt(inputQuestion, profile.level, profile.style, isImageOnly, fallbackLanguageTag)
                val rawResponse = GeminiClient.ask(apiKey, prompt, AnswerPrompts.answerSchema(),
                    AnswerPrompts.answerInstruction, trace, imageBase64, mime)
                outcome = AiRequestTrace.Outcome.INVALID_RESPONSE
                val item = trace.validate { parseAnswer(rawResponse) }
                outcome = AiRequestTrace.Outcome.SAVE_FAILED
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                if (useImage && imageFile != null) {
                    val dir = File(host.filesDir, "qa_images").apply { mkdirs() }
                    val persisted = File.createTempFile("qa_", ".image", dir)
                    persistedImage = persisted
                    imageFile.inputStream().use { ImagePreparation.copyBounded(it, persisted) }
                    imagePath = persisted.path
                }

                item.userId = userId
                item.question = questionFinal
                item.imagePath = imagePath
                item.reviewedSeconds = 0
                item.createdAt = System.currentTimeMillis()

                val qaId = database.runInTransaction<Long> {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (preferences.getLong(LoginActivity.KEY_USER_ID, 0L) != userId) {
                        throw SessionChangedException()
                    }
                    val currentProfile = ProfileDefaults.ensureExists(database.profileDao(), userId)
                    val id = database.questionAnswerDao().insert(item)
                    XpRules.recordDailyActivity(currentProfile, database.profileDao())
                    XpRules.addXp(currentProfile, database.profileDao(), XpRules.ASK)
                    id
                }
                imageSaved = true
                outcome = AiRequestTrace.Outcome.SUCCESS
                postToView(host, requestView) {
                    setLoading(false)
                    clearSelectedImage()
                    editQuestion.setText("")
                    pendingAnswerId = qaId
                    pendingUserId = userId
                    openPendingAnswer()
                }
            } catch (error: InterruptedException) {
                outcome = AiRequestTrace.Outcome.CANCELLED
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                if (error is SessionChangedException) outcome = AiRequestTrace.Outcome.SESSION_CHANGED
                else if (outcome == AiRequestTrace.Outcome.REQUEST_FAILED &&
                    (error is GeminiClient.InvalidResponseException || error is JSONException)) {
                    outcome = AiRequestTrace.Outcome.INVALID_RESPONSE
                }
                // Exception text and API response bodies can contain sensitive information.
                Log.w(TAG, "submit failed (${error.javaClass.simpleName})")
                postToView(host, requestView) {
                    setLoading(false)
                    // Do NOT clear the user's question or attached image on error so they can retry
                    showError(errorMessage(error))
                }
            }
            finally {
                try { if (!imageSaved) persistedImage?.delete() } finally { trace.finish(outcome) }
            }
        }
    }

    private fun postToView(host: Activity, requestView: View, action: () -> Unit) {
        host.runOnUiThread {
            if (isAdded && view === requestView && !host.isFinishing && !host.isDestroyed) action()
        }
    }

    private fun openPendingAnswer() {
        if (!isResumed || pendingAnswerId <= 0L) return
        val host = activity ?: return
        val userId = host.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, 0L)
        val qaId = pendingAnswerId
        pendingAnswerId = 0L
        if (userId != pendingUserId) return
        startActivity(Intent(host, AnswerActivity::class.java)
            .putExtra(AnswerActivity.EXTRA_QA_ID, qaId))
    }

    private fun setLoading(value: Boolean) {
        loading = value
        editQuestion.isEnabled = !value
        view?.findViewById<TextInputLayout>(R.id.input_question)?.isEnabled = !value
        buttonSend.isEnabled = !value
        buttonCamera.isEnabled = !value
        buttonGallery.isEnabled = !value
        btnRemoveImage?.isEnabled = !value
        suggestionButtons.forEach { it.isEnabled = !value }
        progressLoading.visibility = if (value) View.VISIBLE else View.GONE
        buttonSend.setText(if (value) R.string.ask_sending else R.string.ask_send)
        buttonSend.setIconResource(if (value) 0 else R.drawable.ic_study_arrow)
    }

    private fun showError(message: Int) {
        textError.setText(message)
        textError.visibility = View.VISIBLE
    }

    private fun errorMessage(error: Exception): Int = when (error) {
        is SessionChangedException -> R.string.ask_error_session
        is GeminiClient.RequestException -> when (error.reason) {
            GeminiClient.ErrorReason.API_KEY_INVALID,
            GeminiClient.ErrorReason.API_KEY_EXPIRED -> R.string.ask_error_key_invalid
            GeminiClient.ErrorReason.API_KEY_RESTRICTED,
            GeminiClient.ErrorReason.SERVICE_DISABLED -> R.string.ask_error_access
            GeminiClient.ErrorReason.BILLING_DISABLED -> R.string.ask_error_billing
            GeminiClient.ErrorReason.UNKNOWN -> when (error.statusCode) {
                400, 422 -> R.string.ask_error_request
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
        is GeminiClient.InvalidResponseException, is JSONException, is IllegalArgumentException -> R.string.ask_error_response
        is SocketTimeoutException -> R.string.ask_error_timeout
        is IOException -> R.string.ask_error_network
        else -> R.string.ask_error_save
    }

    private class SessionChangedException : IllegalStateException()

    companion object {
        private const val TAG = "AskFragment"
        private val SUBJECTS = setOf(
            ProfileDefaults.SUBJECT_MATH, ProfileDefaults.SUBJECT_SCIENCE,
            ProfileDefaults.SUBJECT_CODE, ProfileDefaults.SUBJECT_HISTORY,
            ProfileDefaults.SUBJECT_LANGUAGE, ProfileDefaults.SUBJECT_OTHER
        )

        private fun isValidDifficulty(difficulty: String): Boolean {
            return difficulty == "easy" || difficulty == "medium" || difficulty == "hard"
        }

        fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? =
            ImagePreparation.decodeSampledBitmap(path, reqWidth, reqHeight)

        internal fun buildPrompt(
            question: String,
            level: String?,
            style: String?,
            isImageOnly: Boolean = false,
            fallbackLanguageTag: String = "en"
        ): String {
            val levelDescription = when (level) {
                ProfileDefaults.LEVEL_JUNIOR -> "middle school (grades 6-9)"
                ProfileDefaults.LEVEL_UNI -> "university"
                else -> "high school (grades 10-12)"
            }
            val styleDescription = when (style) {
                ProfileDefaults.STYLE_SHORT -> "Be concise while retaining the requested items and useful examples."
                ProfileDefaults.STYLE_DETAILED -> "Give fuller explanations and relevant examples within the requested topic."
                else -> "Use clear, ordered sections or direct explanation; use procedural steps only when the task needs them."
            }
            return JSONObject().put("question", question).put("isImageOnly", isImageOnly)
                .put("fallbackLanguageTag", fallbackLanguageTag)
                .put("learnerLevel", levelDescription).put("explanationStyle", styleDescription).toString()
        }

        internal fun parseAnswer(raw: String): QuestionAnswer {
            val text = raw.trim().removePrefix("\uFEFF").trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw JSONException("Missing answer object")
            val json = JSONObject(text.substring(start, end + 1))
            val subject = json.opt("subject") as? String
            require(subject in SUBJECTS) { "Invalid answer subject" }
            val difficulty = json.opt("difficulty") as? String
            require(difficulty != null && isValidDifficulty(difficulty)) { "Invalid answer difficulty" }
            val finalAnswer = (json.opt("finalAnswer") as? String)?.trim()
            require(!finalAnswer.isNullOrBlank()) { "Missing final answer" }
            val content = AnswerContent.generated(json)
            return QuestionAnswer().apply {
                this.subject = subject
                this.difficulty = difficulty
                this.extrasJson = null
                this.reviewedAt = 0
                this.steps = content.toJson()
                this.finalAnswer = finalAnswer
            }
        }
    }
}
