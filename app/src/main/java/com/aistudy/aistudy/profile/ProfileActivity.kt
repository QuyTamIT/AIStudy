package com.aistudy.aistudy.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.settings.AppLanguage
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.ask.ImagePreparation
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.notify.NotificationScheduler
import com.aistudy.aistudy.xp.UnlockCatalog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: ProfileViewModel
    private var userId: Long = -1L

    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutError: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton
    private lateinit var scrollContent: View

    // Hero views
    private lateinit var tvAvatar: TextView
    private lateinit var ivProfileAvatarCustom: ShapeableImageView
    private lateinit var btnChangeAvatar: View
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvLevelBadge: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var pbXp: ProgressBar
    private lateinit var tvXpHint: TextView

    private val pickCustomAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            saveAndSetCustomAvatar(uri)
        }
    }

    private fun saveAndSetCustomAvatar(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val avatarsDir = File(filesDir, "avatars")
                if (!avatarsDir.exists()) {
                    avatarsDir.mkdirs()
                }
                val avatarFile = File(avatarsDir, "avatar_${userId}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    ImagePreparation.copyBounded(input, avatarFile, ImagePreparation.MAX_INPUT_BYTES)
                } ?: throw IOException(getString(R.string.profile_image_unreadable))

                val customAvatarId = "custom:" + avatarFile.absolutePath
                withContext(Dispatchers.Main) {
                    viewModel.updateAvatar(userId, customAvatarId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProfileActivity,
                        getString(R.string.profile_image_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Stats
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStarred: TextView
    private lateinit var tvStatReviewTime: TextView
    private lateinit var tvStatAccuracy: TextView

    // Preferences
    private lateinit var tvPrefLevel: TextView
    private lateinit var tvPrefStyle: TextView
    private lateinit var cgFavSubjects: ChipGroup

    // History
    private lateinit var layoutHistoryItems: LinearLayout
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var btnHistoryAll: TextView

    // Settings
    private lateinit var tvSyncStatus: TextView
    private lateinit var btnSyncFirebase: MaterialButton
    private lateinit var rowChangePassword: View
    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var btnLogout: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AIStudy_Study)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val padding = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)

        if (userId <= 0L) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        bindViews()
        setupListeners()

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        viewModel.uiState.observe(this, ::renderUiState)
        viewModel.loadProfile(userId)
    }

    private fun bindViews() {
        progressLoading = findViewById(R.id.progress_profile_loading)
        layoutError = findViewById(R.id.layout_profile_error)
        tvErrorMessage = findViewById(R.id.tv_profile_error)
        btnRetry = findViewById(R.id.btn_profile_retry)
        scrollContent = findViewById(R.id.profile_scroll)

        tvAvatar = findViewById(R.id.tv_profile_avatar)
        ivProfileAvatarCustom = findViewById(R.id.iv_profile_avatar_custom)
        btnChangeAvatar = findViewById(R.id.btn_change_avatar)
        tvName = findViewById(R.id.tv_profile_name)
        tvEmail = findViewById(R.id.tv_profile_email)
        tvLevelBadge = findViewById(R.id.tv_profile_level_badge)
        tvStreak = findViewById(R.id.tv_profile_streak)
        tvXpTotal = findViewById(R.id.tv_profile_xp_total)
        tvXpPercent = findViewById(R.id.tv_profile_xp_percent)
        pbXp = findViewById(R.id.pb_profile_xp)
        tvXpHint = findViewById(R.id.tv_profile_xp_hint)

        tvStatQuestions = findViewById(R.id.tv_stat_questions_val)
        tvStatStarred = findViewById(R.id.tv_stat_starred_val)
        tvStatReviewTime = findViewById(R.id.tv_stat_review_time_val)
        tvStatAccuracy = findViewById(R.id.tv_stat_accuracy_val)

        tvPrefLevel = findViewById(R.id.tv_pref_level_val)
        tvPrefStyle = findViewById(R.id.tv_pref_style_val)
        cgFavSubjects = findViewById(R.id.cg_profile_fav_subjects)

        layoutHistoryItems = findViewById(R.id.layout_profile_history_items)
        tvHistoryEmpty = findViewById(R.id.tv_profile_history_empty)
        btnHistoryAll = findViewById(R.id.btn_profile_history_all)

        tvSyncStatus = findViewById(R.id.tv_sync_status)
        btnSyncFirebase = findViewById(R.id.btn_sync_firebase)
        rowChangePassword = findViewById(R.id.row_change_password)
        switchNotifications = findViewById(R.id.switch_notifications)
        btnLogout = findViewById(R.id.btn_profile_logout)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_language).setOnClickListener { AppLanguage.showPicker(this) }
        findViewById<View>(R.id.btn_profile_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_edit_profile_action).setOnClickListener { showEditProfileDialog() }
        btnRetry.setOnClickListener { viewModel.loadProfile(userId) }

        btnChangeAvatar.setOnClickListener { showSelectAvatarDialog() }
        btnHistoryAll.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HISTORY)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        btnSyncFirebase.setOnClickListener {
            viewModel.syncFirebase(userId)
        }

        rowChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        val prefs = getSharedPreferences(NotificationScheduler.PREFS_NOTIF, MODE_PRIVATE)
        val notifEnabled = prefs.getBoolean(NotificationScheduler.KEY_NOTIF_ENABLED, true)
        switchNotifications.isChecked = notifEnabled
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(NotificationScheduler.KEY_NOTIF_ENABLED, isChecked).apply()
            if (isChecked) {
                NotificationScheduler.schedule(this)
            } else {
                NotificationScheduler.cancel(this)
            }
            Toast.makeText(
                this,
                if (isChecked) R.string.profile_reminders_enabled else R.string.profile_reminders_disabled,
                Toast.LENGTH_SHORT
            ).show()
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun renderUiState(state: ProfileUiState) {
        if (state.isLoading) {
            progressLoading.visibility = View.VISIBLE
            layoutError.visibility = View.GONE
            scrollContent.visibility = View.GONE
            return
        }

        progressLoading.visibility = View.GONE

        if (state.errorMessage != null && state.profile == null) {
            layoutError.visibility = View.VISIBLE
            tvErrorMessage.text = state.errorMessage
            scrollContent.visibility = View.GONE
            return
        }

        layoutError.visibility = View.GONE
        scrollContent.visibility = View.VISIBLE

        state.profile?.let { bindProfileData(it) }

        state.successMessage?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }

        if (state.errorMessage != null && state.profile != null) {
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }

        btnSyncFirebase.isEnabled = !state.isSyncing
        btnSyncFirebase.setText(if (state.isSyncing) R.string.profile_cloud_synced else R.string.profile_cloud_sync_now)
    }

    private fun bindProfileData(data: StudentProfileData) {
        if (UnlockCatalog.isCustomAvatar(data.avatarId)) {
            val path = UnlockCatalog.getCustomAvatarPath(data.avatarId)
            val file = if (path != null) File(path) else null
            if (file != null && file.exists()) {
                val bitmap = ImagePreparation.decodeSampledBitmap(file.absolutePath, 168, 168)
                if (bitmap != null) {
                    ivProfileAvatarCustom.setImageBitmap(bitmap)
                    ivProfileAvatarCustom.visibility = View.VISIBLE
                    tvAvatar.visibility = View.GONE
                } else {
                    ivProfileAvatarCustom.visibility = View.GONE
                    tvAvatar.visibility = View.VISIBLE
                    tvAvatar.text = "👤"
                }
            } else {
                ivProfileAvatarCustom.visibility = View.GONE
                tvAvatar.visibility = View.VISIBLE
                tvAvatar.text = "👤"
            }
        } else {
            ivProfileAvatarCustom.visibility = View.GONE
            tvAvatar.visibility = View.VISIBLE
            tvAvatar.text = data.avatarEmoji
        }

        tvName.text = data.fullName
        tvEmail.text = data.email

        tvLevelBadge.text = getString(R.string.profile_level_title, data.levelNumber, data.levelTitle)
        tvStreak.text = getString(R.string.home_streak_days, data.streak)

        tvXpTotal.text = getString(R.string.home_xp_progress, data.xp, data.nextLevelThreshold)
        tvXpPercent.text = getString(R.string.home_xp_percent, data.xpProgressPercent)
        pbXp.progress = data.xpProgressPercent

        if (data.xpToNextLevel > 0) {
            tvXpHint.text = getString(R.string.profile_xp_next_level, data.xpToNextLevel, data.levelNumber + 1)
            tvXpHint.visibility = View.VISIBLE
        } else {
            tvXpHint.visibility = View.GONE
        }

        // Stats
        tvStatQuestions.text = data.totalQuestions.toString()
        tvStatStarred.text = data.totalStarred.toString()
        tvStatReviewTime.text = formatReviewTime(data.totalReviewSeconds)
        tvStatAccuracy.text = getString(R.string.profile_stat_accuracy_format, data.quizAccuracy)

        // Preferences
        tvPrefLevel.text = when (data.gradeLevel) {
            ProfileDefaults.LEVEL_JUNIOR -> getString(R.string.onboarding_level_junior)
            ProfileDefaults.LEVEL_UNI -> getString(R.string.onboarding_level_uni)
            else -> getString(R.string.onboarding_level_high)
        }

        tvPrefStyle.text = when (data.learningStyle) {
            ProfileDefaults.STYLE_SHORT -> getString(R.string.onboarding_style_short)
            ProfileDefaults.STYLE_DETAILED -> getString(R.string.onboarding_style_detailed)
            else -> getString(R.string.onboarding_style_stepwise)
        }

        cgFavSubjects.removeAllViews()
        for (subj in data.favoriteSubjects) {
            val chip = Chip(this).apply {
                text = subjectDisplayName(subj)
                isClickable = false
                isCheckable = false
                setChipBackgroundColorResource(R.color.answer_result_surface)
                setTextColor(ContextCompat.getColor(context, R.color.answer_primary))
                chipStrokeWidth = 0f
            }
            cgFavSubjects.addView(chip)
        }

        // Recent questions
        layoutHistoryItems.removeAllViews()
        if (data.recentQuestions.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            layoutHistoryItems.visibility = View.GONE
        } else {
            tvHistoryEmpty.visibility = View.GONE
            layoutHistoryItems.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(this)
            val dateFormat = SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault())

            for (qa in data.recentQuestions) {
                val itemView = inflater.inflate(R.layout.item_profile_history, layoutHistoryItems, false)
                val tvSubject = itemView.findViewById<TextView>(R.id.tv_item_subject)
                val tvDiff = itemView.findViewById<TextView>(R.id.tv_item_difficulty)
                val tvDate = itemView.findViewById<TextView>(R.id.tv_item_date)
                val tvQuestion = itemView.findViewById<TextView>(R.id.tv_item_question)

                tvSubject.text = subjectDisplayName(qa.subject)
                tvDiff.text = when (qa.difficulty?.lowercase()) {
                    "easy" -> getString(R.string.answer_difficulty_easy)
                    "hard" -> getString(R.string.answer_difficulty_hard)
                    else -> getString(R.string.answer_difficulty_medium)
                }
                tvDate.text = dateFormat.format(Date(qa.createdAt))
                tvQuestion.text = qa.question.orEmpty()

                itemView.setOnClickListener {
                    val intent = Intent(this, AnswerActivity::class.java).apply {
                        putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
                    }
                    startActivity(intent)
                }
                layoutHistoryItems.addView(itemView)
            }
        }
    }

    private fun showSelectAvatarDialog() {
        val profile = viewModel.uiState.value?.profile ?: return
        val currentLevel = profile.levelNumber
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_avatar, null)

        val cardCustom = dialogView.findViewById<MaterialCardView>(R.id.card_avatar_custom)
        val ivCustomPreview = dialogView.findViewById<ShapeableImageView>(R.id.iv_custom_avatar_preview)
        val layoutCustomPlaceholder = dialogView.findViewById<View>(R.id.layout_custom_icon_placeholder)
        val tvStatusCustom = dialogView.findViewById<TextView>(R.id.tv_status_custom)
        val btnPickPhoto = dialogView.findViewById<MaterialButton>(R.id.btn_pick_photo)

        val cardFox = dialogView.findViewById<MaterialCardView>(R.id.card_avatar_fox)
        val cardOwl = dialogView.findViewById<MaterialCardView>(R.id.card_avatar_owl)
        val cardCat = dialogView.findViewById<MaterialCardView>(R.id.card_avatar_cat)
        val cardRocket = dialogView.findViewById<MaterialCardView>(R.id.card_avatar_rocket)

        val tvStatusOwl = dialogView.findViewById<TextView>(R.id.tv_status_owl)
        val tvStatusCat = dialogView.findViewById<TextView>(R.id.tv_status_cat)
        val tvStatusRocket = dialogView.findViewById<TextView>(R.id.tv_status_rocket)

        // Owl Level 2
        val owlUnlocked = currentLevel >= 2
        tvStatusOwl.text = if (owlUnlocked) getString(R.string.profile_avatar_unlocked) else getString(R.string.profile_avatar_locked, 2)
        tvStatusOwl.setTextColor(ContextCompat.getColor(this, if (owlUnlocked) R.color.answer_primary else R.color.answer_foreground_secondary))

        // Cat Level 3
        val catUnlocked = currentLevel >= 3
        tvStatusCat.text = if (catUnlocked) getString(R.string.profile_avatar_unlocked) else getString(R.string.profile_avatar_locked, 3)
        tvStatusCat.setTextColor(ContextCompat.getColor(this, if (catUnlocked) R.color.answer_primary else R.color.answer_foreground_secondary))

        // Rocket Level 4
        val rocketUnlocked = currentLevel >= 4
        tvStatusRocket.text = if (rocketUnlocked) getString(R.string.profile_avatar_unlocked) else getString(R.string.profile_avatar_locked, 4)
        tvStatusRocket.setTextColor(ContextCompat.getColor(this, if (rocketUnlocked) R.color.answer_primary else R.color.answer_foreground_secondary))

        // Highlight selected
        val strokeSelected = ContextCompat.getColor(this, R.color.answer_primary)
        val strokeDefault = ContextCompat.getColor(this, R.color.answer_divider)
        cardFox.strokeColor = if (profile.avatarId == UnlockCatalog.AVATAR_FOX) strokeSelected else strokeDefault
        cardOwl.strokeColor = if (profile.avatarId == UnlockCatalog.AVATAR_OWL) strokeSelected else strokeDefault
        cardCat.strokeColor = if (profile.avatarId == UnlockCatalog.AVATAR_CAT) strokeSelected else strokeDefault
        cardRocket.strokeColor = if (profile.avatarId == UnlockCatalog.AVATAR_ROCKET) strokeSelected else strokeDefault

        val isCurrentCustom = UnlockCatalog.isCustomAvatar(profile.avatarId)
        val customPath = UnlockCatalog.getCustomAvatarPath(profile.avatarId)
            ?: File(filesDir, "avatars/avatar_${userId}.jpg").let { if (it.exists()) it.absolutePath else null }

        if (customPath != null) {
            val previewBitmap = ImagePreparation.decodeSampledBitmap(customPath, 96, 96)
            if (previewBitmap != null) {
                ivCustomPreview.setImageBitmap(previewBitmap)
                ivCustomPreview.visibility = View.VISIBLE
                layoutCustomPlaceholder.visibility = View.GONE
            } else {
                ivCustomPreview.visibility = View.GONE
                layoutCustomPlaceholder.visibility = View.VISIBLE
            }
        } else {
            ivCustomPreview.visibility = View.GONE
            layoutCustomPlaceholder.visibility = View.VISIBLE
        }

        if (isCurrentCustom) {
            cardCustom.strokeColor = strokeSelected
            tvStatusCustom.setText(R.string.profile_custom_avatar_active)
            tvStatusCustom.setTextColor(ContextCompat.getColor(this, R.color.answer_primary))
        } else {
            cardCustom.strokeColor = strokeDefault
            tvStatusCustom.setText(if (customPath != null) R.string.profile_custom_avatar_reuse else R.string.profile_custom_avatar_choose)
            tvStatusCustom.setTextColor(ContextCompat.getColor(this, R.color.answer_foreground_secondary))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton(R.string.logout_cancel, null)
            .create()

        fun select(avatarId: String, unlocked: Boolean, reqLevel: Int) {
            if (!unlocked) {
                Toast.makeText(this, getString(R.string.profile_avatar_locked, reqLevel), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.updateAvatar(userId, avatarId)
            dialog.dismiss()
        }

        cardFox.setOnClickListener { select(UnlockCatalog.AVATAR_FOX, true, 1) }
        cardOwl.setOnClickListener { select(UnlockCatalog.AVATAR_OWL, owlUnlocked, 2) }
        cardCat.setOnClickListener { select(UnlockCatalog.AVATAR_CAT, catUnlocked, 3) }
        cardRocket.setOnClickListener { select(UnlockCatalog.AVATAR_ROCKET, rocketUnlocked, 4) }

        val onPickCustom = {
            dialog.dismiss()
            pickCustomAvatarLauncher.launch("image/*")
        }

        cardCustom.setOnClickListener {
            if (!isCurrentCustom && customPath != null) {
                viewModel.updateAvatar(userId, "custom:$customPath")
                dialog.dismiss()
            } else {
                onPickCustom()
            }
        }
        btnPickPhoto.setOnClickListener {
            onPickCustom()
        }

        dialog.show()
    }

    private fun showEditProfileDialog() {
        val profile = viewModel.uiState.value?.profile ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_profile_name)
        val cgLevel = dialogView.findViewById<ChipGroup>(R.id.cg_grade_level)
        val cgStyle = dialogView.findViewById<ChipGroup>(R.id.cg_style)
        val cgSubj = dialogView.findViewById<ChipGroup>(R.id.cg_subjects)

        etName.setText(profile.fullName)

        when (profile.gradeLevel) {
            ProfileDefaults.LEVEL_JUNIOR -> dialogView.findViewById<Chip>(R.id.chip_level_junior).isChecked = true
            ProfileDefaults.LEVEL_UNI -> dialogView.findViewById<Chip>(R.id.chip_level_uni).isChecked = true
            else -> dialogView.findViewById<Chip>(R.id.chip_level_high).isChecked = true
        }

        when (profile.learningStyle) {
            ProfileDefaults.STYLE_SHORT -> dialogView.findViewById<Chip>(R.id.chip_style_short).isChecked = true
            ProfileDefaults.STYLE_DETAILED -> dialogView.findViewById<Chip>(R.id.chip_style_detailed).isChecked = true
            else -> dialogView.findViewById<Chip>(R.id.chip_style_stepwise).isChecked = true
        }

        dialogView.findViewById<Chip>(R.id.chip_subject_math).isChecked = profile.favoriteSubjects.contains("math")
        dialogView.findViewById<Chip>(R.id.chip_subject_science).isChecked = profile.favoriteSubjects.contains("science")
        dialogView.findViewById<Chip>(R.id.chip_subject_code).isChecked = profile.favoriteSubjects.contains("code")
        dialogView.findViewById<Chip>(R.id.chip_subject_history).isChecked = profile.favoriteSubjects.contains("history")
        dialogView.findViewById<Chip>(R.id.chip_subject_language).isChecked = profile.favoriteSubjects.contains("language")

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.profile_btn_save) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, R.string.profile_error_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedLevel = when (cgLevel.checkedChipId) {
                    R.id.chip_level_junior -> ProfileDefaults.LEVEL_JUNIOR
                    R.id.chip_level_uni -> ProfileDefaults.LEVEL_UNI
                    else -> ProfileDefaults.LEVEL_HIGH
                }

                val selectedStyle = when (cgStyle.checkedChipId) {
                    R.id.chip_style_short -> ProfileDefaults.STYLE_SHORT
                    R.id.chip_style_detailed -> ProfileDefaults.STYLE_DETAILED
                    else -> ProfileDefaults.STYLE_STEPWISE
                }

                val selectedSubjects = mutableListOf<String>()
                if (dialogView.findViewById<Chip>(R.id.chip_subject_math).isChecked) selectedSubjects.add("math")
                if (dialogView.findViewById<Chip>(R.id.chip_subject_science).isChecked) selectedSubjects.add("science")
                if (dialogView.findViewById<Chip>(R.id.chip_subject_code).isChecked) selectedSubjects.add("code")
                if (dialogView.findViewById<Chip>(R.id.chip_subject_history).isChecked) selectedSubjects.add("history")
                if (dialogView.findViewById<Chip>(R.id.chip_subject_language).isChecked) selectedSubjects.add("language")
                if (selectedSubjects.isEmpty()) selectedSubjects.add("math")

                viewModel.updateName(userId, newName)
                viewModel.updatePreferences(userId, selectedLevel, selectedStyle, selectedSubjects)
            }
            .setNegativeButton(R.string.logout_cancel, null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val etOld = dialogView.findViewById<TextInputEditText>(R.id.et_old_password)
        val etNew = dialogView.findViewById<TextInputEditText>(R.id.et_new_password)
        val etConfirm = dialogView.findViewById<TextInputEditText>(R.id.et_confirm_password)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_dialog_password_title)
            .setView(dialogView)
            .setPositiveButton(R.string.profile_change_password) { _, _ ->
                val oldPass = etOld.text.toString()
                val newPass = etNew.text.toString()
                val confirmPass = etConfirm.text.toString()

                if (oldPass.isBlank() || newPass.isBlank() || confirmPass.isBlank()) {
                    Toast.makeText(this, R.string.auth_error_empty_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass.length < 6) {
                    Toast.makeText(this, R.string.profile_password_too_short, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, R.string.profile_password_mismatch, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.changePassword(userId, oldPass, newPass) { success, msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.logout_cancel, null)
            .show()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_dialog_title)
            .setMessage(R.string.logout_dialog_message)
            .setPositiveButton(R.string.logout_confirm) { _, _ -> performLogout() }
            .setNegativeButton(R.string.logout_cancel, null)
            .show()
    }

    private fun performLogout() {
        getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun formatReviewTime(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMins = minutes % 60
            getString(R.string.profile_stat_time_hours_minutes, hours, remMins)
        } else {
            getString(R.string.profile_stat_time_minutes, minutes)
        }
    }

    private fun subjectDisplayName(subject: String?): String {
        return when (subject) {
            "math", ProfileDefaults.SUBJECT_MATH -> getString(R.string.onboarding_subject_math)
            "science", ProfileDefaults.SUBJECT_SCIENCE -> getString(R.string.onboarding_subject_science)
            "code", ProfileDefaults.SUBJECT_CODE -> getString(R.string.onboarding_subject_code)
            "history", ProfileDefaults.SUBJECT_HISTORY -> getString(R.string.onboarding_subject_history)
            "language", ProfileDefaults.SUBJECT_LANGUAGE -> getString(R.string.onboarding_subject_language)
            else -> getString(R.string.onboarding_subject_other)
        }
    }
}
