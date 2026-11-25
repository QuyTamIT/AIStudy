package com.aistudy.aistudy.ask

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.ProfileDefaults

internal object AskSuggestions {
    data class Question(val text: String, @DrawableRes val icon: Int)
    private data class Pool(@ArrayRes val questions: Int, @DrawableRes val icon: Int)

    private val subjectPools = mapOf(
        ProfileDefaults.SUBJECT_MATH to Pool(R.array.ask_questions_math, R.drawable.ic_study_math),
        ProfileDefaults.SUBJECT_SCIENCE to Pool(R.array.ask_questions_science, R.drawable.ic_study_science),
        ProfileDefaults.SUBJECT_CODE to Pool(R.array.ask_questions_code, R.drawable.ic_study_code),
        ProfileDefaults.SUBJECT_HISTORY to Pool(R.array.ask_questions_history, R.drawable.ic_nav_history),
        ProfileDefaults.SUBJECT_LANGUAGE to Pool(R.array.ask_questions_language, R.drawable.ic_study_language)
    )
    private val general = Pool(R.array.ask_questions_general, R.drawable.ic_answer_book)

    fun forSubjects(resources: Resources, subjects: String?, limit: Int): List<Question> {
        val selected = subjects.orEmpty().split(',').map { it.trim() }.distinct().mapNotNull(subjectPools::get)
        // Give every selected subject a chance when there are more subjects than buttons.
        val pools = if (selected.size > limit) selected.shuffled().take(limit) else selected.ifEmpty { listOf(general) }
        val questions = pools.map { resources.getStringArray(it.questions) }
        return List(limit) { slot ->
            val pool = slot % pools.size
            Question(questions[pool][slot / pools.size], pools[pool].icon)
        }
    }
}
