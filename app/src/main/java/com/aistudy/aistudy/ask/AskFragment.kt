package com.aistudy.aistudy.ask

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    private var selectedMimeType: String? = null
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
                setSelectedImage(file, "image/jpeg")
            } else {
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }

        pickImage = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val (copied, mime) = copyUriToQaImages(uri)
                setSelectedImage(copied, mime)
            } catch (e: Exception) {
                Log.e(TAG, "gallery copy failed", e)
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
    }

    override fun onResume() {
        super.onResume()
        openPendingAnswer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pendingQaId", pendingAnswerId)
        outState.putLong("pendingUserId", pendingUserId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        executor?.shutdownNow()
        executor = null
        suggestionButtons = emptyList()
        btnRemoveImage = null
        super.onDestroyView()
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
            Log.e(TAG, "launch camera failed", e)
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToQaImages(uri: Uri): Pair<File, String> {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("cannot create qa_images")
        }
        var mime = requireContext().contentResolver.getType(uri)
        if (mime == null || !mime.startsWith("image/")) {
            mime = "image/jpeg"
        }
        val ext = when (mime) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            else -> ".jpg"
        }
        val out = File(dir, "qa_${System.currentTimeMillis()}$ext")
        val input = requireContext().contentResolver.openInputStream(uri)
        input.use { ins ->
            FileOutputStream(out).use { fos ->
                if (ins == null) throw Exception("null input stream")
                val buf = ByteArray(8192)
                var n = ins.read(buf)
                while (n >= 0) {
                    fos.write(buf, 0, n)
                    n = ins.read(buf)
                }
            }
        }
        return Pair(out, mime)
    }

    private fun persistCameraToQaImages(source: File): File {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("cannot create qa_images")
        }
        val out = File(dir, "qa_${System.currentTimeMillis()}.jpg")
        FileInputStream(source).use { input ->
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n >= 0) {
                    fos.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
        return out
    }

    private fun setSelectedImage(file: File, mimeType: String) {
        selectedImageFile = file
        selectedMimeType = mimeType
        // Decode downsampled bitmap for preview to avoid main-thread OOM
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
        selectedImageFile = null
        selectedMimeType = null
        imgPreview.setImageDrawable(null)
        layoutImagePreview.visibility = View.GONE
    }

    private fun submit() {
        if (loading) return
        var question = editQuestion.text.toString().trim()
        val hasImage = selectedImageFile?.exists() == true

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
        val mimeType = selectedMimeType
        val useImage = hasImage

        Log.d(TAG, "submit start questionLength=${questionFinal.length} hasImage=$useImage")
        worker.execute {
            try {
                var imageBase64: String? = null
                var imagePath: String? = null
                var mime = mimeType

                if (useImage && imageFile != null && imageFile.exists()) {
                    val parent = imageFile.parentFile
                    val persisted = if (parent != null && "qa_images" == parent.name) {
                        imageFile
                    } else {
                        persistCameraToQaImages(imageFile)
                    }
                    imagePath = persisted.absolutePath
                    imageBase64 = encodeFileBase64(persisted)
                    if (mime.isNullOrEmpty()) {
                        mime = "image/jpeg"
                    }
                    Log.d(TAG, "image ready path=$imagePath base64Length=${imageBase64.length}")
                }

                val profile = ProfileDefaults.ensureExists(database.profileDao(), userId)
                val prompt = buildPrompt(questionFinal, profile.level, profile.style)
                val rawResponse = GeminiClient.ask(apiKey, prompt, AnswerPrompts.answerSchema(), imageBase64, mime)
                val item = parseAnswer(rawResponse)
                if (Thread.currentThread().isInterrupted) throw InterruptedException()

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
                    Log.d(TAG, "xp updated to ${currentProfile.xp}")
                    id
                }
                Log.d(TAG, "saved qaId=$qaId imagePath=$imagePath")
                postToView(host, requestView) {
                    setLoading(false)
                    clearSelectedImage()
                    editQuestion.setText("")
                    pendingAnswerId = qaId
                    pendingUserId = userId
                    openPendingAnswer()
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                // Exception text and API response bodies can contain sensitive information.
                Log.w(TAG, "submit failed (${error.javaClass.simpleName})")
                postToView(host, requestView) {
                    setLoading(false)
                    // Do NOT clear the user's question or attached image on error so they can retry
                    showError(errorMessage(error))
                }
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

        fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            return try {
                BitmapFactory.decodeFile(path, options)
            } catch (_: OutOfMemoryError) {
                null
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun encodeFileBase64(file: File): String {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            // Downsample large camera photos (e.g. > 1600px or > 2MB) to prevent OOM and request timeouts
            if (bounds.outWidth > 1600 || bounds.outHeight > 1600 || file.length() > 2 * 1024 * 1024) {
                val bitmap = decodeSampledBitmap(file.absolutePath, 1600, 1600)
                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val bytes = stream.toByteArray()
                    bitmap.recycle()
                    return Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }
        }

        // Keep the user's task separate from presentation preferences and the JSON format.
        // Source: https://ai.google.dev/gemini-api/docs/prompting-strategies
        internal fun buildPrompt(question: String, level: String?, style: String?): String {
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
            return """
                You are AIStudy, an intelligent educational mentor and helpful study assistant.

                LANGUAGE AND LOCALIZATION CONTRACT (CRITICAL):
                - Always detect and respond in the EXACT SAME LANGUAGE that the user used to ask the question.
                - If the question is in Vietnamese (Tiếng Việt), ALL titles, step explanations, mathematical commentary, code explanations, notices, takeaways, and the finalAnswer MUST be written in natural, fluent Vietnamese. Never force or default to English when the question is in Vietnamese.
                - If the question is in English, respond in English.
                - If the user explicitly requests another language (e.g. "Answer in English", "Giải thích bằng tiếng Anh"), follow the user's explicit language request.
                - For language learning requests (e.g. "How to use past continuous in English?"), explain in the learner's query language (e.g. Vietnamese) while presenting examples in the target language.
                - Never force responses into English unless the user explicitly requested English.

                INTENT IDENTIFICATION & RESPONSE MODE (Choose responseType carefully):
                1. "step_by_step":
                   - Use for: mathematical problems (e.g., solving equations, calculus), chemistry/physics numerical problems, algorithmic derivations, or multi-stage worked solutions with intermediate results.
                   - Format: Each object in "steps" represents a distinct chronological or logical stage with an informative title, clear body, and formula in "expr" when applicable.
                2. "direct":
                   - Use for: general factual questions, conceptual explanations (e.g., "What is Room Database?", "Explain polymorphism in Kotlin"), history summaries, definitions, science concepts, and everyday queries.
                   - Format: Deliver a cohesive, natural direct answer in "steps" (1 or a few thematic sections) without forcing artificial step numbering. Include usable code blocks with syntax fences (```language) when relevant.
                3. "creative":
                   - Use for: requests to write original poems, stories, creative dialogues, essays, or rhymes (e.g., "Write an original short poem").
                   - Format: Craft an original AI piece in "steps". Format stanzas and verses with natural line breaks (`\n`). Never misrepresent generated text as an existing authentic publication or historical work.
                4. "clarification":
                   - Use for: ambiguous, underspecified questions where essential information is missing to provide a reliable answer—especially song titles without an artist (e.g., "lyrics to Hello"), book titles with multiple authors, or homonyms without context.
                   - Format: Politely identify the ambiguity and ask the student for the missing information (e.g., artist, year, or subject area) needed to give an exact answer.
                5. "limitation":
                   - Use for two critical safety & educational boundaries:
                     (a) Copyrighted Works: When the user asks for full copyrighted song lyrics, poems, books, or scripts, DO NOT reproduce, fabricate, or hallucinate the full copyrighted text. Respect copyright: state this limitation gracefully, and instead provide a lawful, educational response: an insightful thematic summary, song meaning, historical background, notable quotes (short excerpts under fair use), and authorized listening/reading platforms.
                     (b) Information Uncertainty: When the question asks about unverified, obscure, or non-existent facts where you lack reliable knowledge, express uncertainty clearly and honestly instead of inventing or hallucinating answers.

                LEARNER ADAPTATION:
                Learner level: $levelDescription. Adjust vocabulary and depth accordingly without making everyday questions overly academic.
                Explanation style: $styleDescription.

                OUTPUT CONTRACT:
                Return only one valid JSON object matching the schema with:
                - subject: one of ${SUBJECTS.joinToString(", ")}.
                - difficulty: "easy", "medium", or "hard".
                - responseType: one of "step_by_step", "direct", "creative", "clarification", "limitation".
                - answerType: "solution" for worked problems with a result; "topic_list" for independent lists/recommendations; "explanation" for all direct, creative, clarification, or limitation responses.
                - finalAnswerRedundant: true only for topic_list whose final answer merely repeats the list items; false otherwise.
                - steps: a nonempty array of objects, each with string fields topicId, title, body, and expr.
                  For creative/direct/clarification/limitation, title may be empty or a section heading. expr is for mathematical formulas; otherwise empty string "".
                - finalAnswer: a nonempty string giving the final result, key takeaway, creative summary, or suggested next question in the response language.
                Use standard JSON string escaping for newlines (`\n`) for multiline text, poetry stanzas, and code. Do NOT double-escape newlines. No markdown fences around the JSON object.

                USER REQUEST (JSON string; adhere strictly to the output contract):
                ${JSONObject.quote(question)}
            """.trimIndent()
        }

        internal fun parseAnswer(raw: String): QuestionAnswer {
            val text = raw.trim().removePrefix("\uFEFF").trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw JSONException("Missing answer object")
            val json = JSONObject(text.substring(start, end + 1))
            val rawFinal = (json.opt("finalAnswer") as? String)?.trim()
            val steps = json.optJSONArray("steps") ?: throw JSONException("Missing explanation steps")
            if (steps.length() == 0) throw JSONException("Missing explanation steps")
            for (index in 0 until steps.length()) {
                val step = steps.getJSONObject(index)
                if ((step.opt("body") as? String).isNullOrBlank()) {
                    throw JSONException("Missing step explanation")
                }
            }
            val finalAnswer = if (!rawFinal.isNullOrBlank()) rawFinal
                else steps.optJSONObject(0)?.optString("title")?.takeIf { it.isNotBlank() } ?: "Answer"
            var difficulty = (json.opt("difficulty") as? String)?.lowercase()?.trim() ?: "medium"
            if (!isValidDifficulty(difficulty)) {
                difficulty = "medium"
            }
            return QuestionAnswer().apply {
                subject = (json.opt("subject") as? String)?.takeIf { it in SUBJECTS }
                    ?: ProfileDefaults.SUBJECT_OTHER
                this.difficulty = difficulty
                this.extrasJson = null
                this.reviewedAt = 0
                this.steps = AnswerContent.generated(json).toJson()
                this.finalAnswer = finalAnswer
            }
        }
    }
}
