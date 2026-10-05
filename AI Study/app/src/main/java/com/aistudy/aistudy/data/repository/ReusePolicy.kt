package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.rules.QuestionSimilarity

/**
 * AI Cost Management - quyet dinh co tai dung ket qua da co hay phai goi AI.
 *
 * Tach rieng thanh ham thuan `object` de TC-ASK-02/03/04 chay duoc bang JUnit
 * thuan, khong can Room, khong can emulator. Phan I/O ghi DB nam o
 * `QuestionRepositoryImpl`.
 */
object ReusePolicy {

    /**
     * @param exact kiem tra hash - cung noi dung chuoi da chuan hoa.
     * @param candidates so cau gan day nhat da hoi, da doc tu DB.
     * @return cau hoi nen tai dung, hoac null khi phai goi AI that.
     */
    fun select(
        exact: QuestionAnswer?,
        candidates: List<QuestionAnswer>,
        text: String
    ): QuestionAnswer? =
        exact ?: candidates.firstOrNull { QuestionSimilarity.isSimilar(text, it.questionText) }

    /**
     * Copy cau cu sang cau moi cho user hien tai.
     *
     * Phai tao ROW MOI chu khong tra lai row cu: lich su la nhat ky rieng cua
     * tung user, va `reviewTimeSeconds` cua row goc khong duoc cong don vao row
     * moi - nguoi dung chua doc cau moi bao gio.
     */
    fun reuseAs(
        source: QuestionAnswer,
        userId: Long,
        nowMillis: Long
    ): QuestionAnswer = source.copy(
        id = 0L,
        userId = userId,
        servedFromCache = true,
        matchedQuestionId = source.id,
        reviewTimeSeconds = 0,
        isBookmarked = false,
        createdAt = nowMillis
    )
}
