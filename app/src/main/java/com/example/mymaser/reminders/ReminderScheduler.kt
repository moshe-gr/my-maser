package com.example.mymaser.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mymaser.settings.reminderMinuteOfDayFlow
import com.example.mymaser.settings.reminderSettingsFlow
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

object ReminderScheduler {
    private const val WORK_NAME = "daily_reminders"

    /** Delay from [now] until the next [minuteOfDay]. */
    fun delayUntil(now: LocalDateTime, minuteOfDay: Int): Duration {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /**
     * Matches the scheduled work to the current settings. [rescheduleTime] replaces existing work,
     * needed when the reminder time changed; otherwise existing work is kept.
     */
    suspend fun sync(context: Context, rescheduleTime: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        if (!context.reminderSettingsFlow().first().enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = delayUntil(LocalDateTime.now(), context.reminderMinuteOfDayFlow().first())
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (rescheduleTime) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Runs the reminder check once, right away. Used by the debug button in Settings. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }
}
