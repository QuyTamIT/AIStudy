package com.aistudy.aistudy.domain.ai

/**
 * Loi AI phai gan vao `Result.failure()` de UI hien thi dung.
 *
 * Khong dung enum vi moi provider tra ve cau loi khac nhau; UI chi can phan biet
 * "user sai" voi "he thong sai" de quyet dinh co hien hop hay khong.
 */
sealed class AiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Chua cau hinh API key. UI: mo huong dan nhap key. */
    data class NotConfigured(override val message: String) : AiException(message)

    /** API key sai / het quota / bi chan. UI: hien thi nguyen nhan + huong dan. */
    data class Provider(override val message: String) : AiException(message)

    /** Khong co mang. UI: hien thi offline, giu nguyen cau hoi cho thu lai. */
    data class Network(override val message: String) : AiException(message)

    /** Response khong parse duoc. Co the do model tra ve khong dung format. */
    data class Malformed(override val message: String) : AiException(message)
}
