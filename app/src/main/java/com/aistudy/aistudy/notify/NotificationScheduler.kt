package com.aistudy.aistudy.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    const val UNIQUE_DAILY = "aistudy_daily_learning"
    const val UNIQUE_FORCE = "aistudy_force_learning"
    const val PREFS_NOTIF = "notify_settings"
    const val KEY_NOTIF_ENABLED = "daily_notif_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_ENABLED, true)

    fun schedule(context: Context) {
        if (!isEnabled(context)) {
            cancel(context)
            return
        }

        NotificationHelper.ensureChannel(context)

        val delayMs = millisUntilHour(20)
        val daily = PeriodicWorkRequest.Builder(
            DailyLearningWorker::class.java,
            1,
            TimeUnit.DAYS
        )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            daily
        )
    }

    fun enqueueNow(context: Context): Boolean {
        if (!isEnabled(context)) return false

        NotificationHelper.ensureChannel(context)
        context.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        val once = OneTimeWorkRequest.Builder(DailyLearningWorker::class.java).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_FORCE,
            ExistingWorkPolicy.REPLACE,
            once
        )
        return true
    }

    private fun millisUntilHour(hourOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.set(Calendar.HOUR_OF_DAY, hourOfDay)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_DAILY)
        workManager.cancelUniqueWork(UNIQUE_FORCE)
    }
}

