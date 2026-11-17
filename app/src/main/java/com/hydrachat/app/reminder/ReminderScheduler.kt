package com.hydrachat.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hydrachat.app.data.SettingsStore
import com.hydrachat.app.domain.HydrationMath
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context, private val settings: SettingsStore) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleNext(skipToday: Boolean = false) {
        val current = settings.current
        if (!current.isOnboarded) return
        val triggerAt = HydrationMath.nextReminderAt(
            nowMillis = System.currentTimeMillis(),
            schedule = current.schedule,
            intervalMinutes = current.effectiveIntervalMinutes,
            zone = ZoneId.systemDefault(),
            skipToday = skipToday,
        )
        scheduleAt(triggerAt)
    }

    /** Re-arms the stored reminder after a reboot or clock change instead of pushing it back. */
    fun restore() {
        val storedAt = settings.current.nextReminderAt
        if (!settings.current.isOnboarded) return
        if (storedAt > System.currentTimeMillis()) scheduleAt(storedAt) else scheduleNext()
    }

    fun scheduleInMinutes(minutes: Int) {
        scheduleAt(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong()))
    }

    fun scheduleAt(triggerAtMillis: Long) {
        val pendingIntent = reminderIntent()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        settings.update { it.copy(nextReminderAt = triggerAtMillis) }
        enqueuePrefetch(triggerAtMillis)
    }

    private fun enqueuePrefetch(triggerAtMillis: Long) {
        if (settings.current.apiKey.isBlank()) return
        val delayMillis = (triggerAtMillis - PREFETCH_LEAD_MS - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<PrefetchWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(PrefetchWorker.KEY_TRIGGER_AT to triggerAtMillis))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(PREFETCH_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun reminderIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REMIND,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_REMIND = 100
        const val PREFETCH_WORK_NAME = "prefetch_nudge"
        const val PREFETCH_LEAD_MS = 5 * 60 * 1000L
    }
}
