package com.aistudy.aistudy

import android.app.Application
import com.aistudy.aistudy.platform.ServiceLocator

/**
 * Khoi tao DI container truoc khi `MainActivity` dung gi den.
 *
 * Khong co logic gi khac o day nen dung `Application` rong + [onCreate] goi
 * [ServiceLocator.init] la du.
 */
class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
