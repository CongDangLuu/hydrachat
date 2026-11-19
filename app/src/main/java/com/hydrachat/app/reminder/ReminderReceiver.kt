package com.hydrachat.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hydrachat.app.AppContainer
import com.hydrachat.app.HydraApp
import com.hydrachat.app.bubble.BubbleService
import com.hydrachat.app.domain.HydrationMath
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Handles the reminder alarm and the quick replies of the fallback notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HydraApp).container
        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> remind(context.applicationContext, container)
                    ACTION_DRINK -> drink(context.applicationContext, container, intent)
                    ACTION_SNOOZE -> snooze(context.applicationContext, container)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun remind(context: Context, container: AppContainer) {
        val settings = container.settings.current
        if (!settings.isOnboarded) return
        val now = System.currentTimeMillis()
        if (!HydrationMath.isWithinWork(now, settings.schedule, ZoneId.systemDefault())) {
            container.scheduler.scheduleNext()
            return
        }
        if (container.repository.todayTotalMl() >= settings.targetMl) {
            container.scheduler.scheduleNext(skipToday = true)
            return
        }
        // While the app is open its chat room shows the line, so there is no bubble to defer or show.
        val isAppOpen = container.screenVisibility.isAppOpen
        val deferReason = if (isAppOpen) null else ContextDetector.deferReason(context, settings.hiddenApps)
        if (deferReason != null) {
            Log.i(TAG, "Reminder deferred: $deferReason")
            container.scheduler.scheduleInMinutes(HydrationMath.DEFER_MINUTES)
            return
        }
        val nudge = container.repository.nextNudge()
        container.repository.recordNudgeShown(nudge)
        if (!isAppOpen) BubbleService.show(context, nudge)
        container.scheduler.scheduleNext()
    }

    private suspend fun drink(context: Context, container: AppContainer, intent: Intent) {
        val amountMl = intent.getIntExtra(EXTRA_AMOUNT_ML, 0)
        ReminderNotifier.cancelReminder(context)
        if (amountMl !in 1..MAX_SINGLE_DRINK_ML) return
        container.repository.logDrink(amountMl)
    }

    private suspend fun snooze(context: Context, container: AppContainer) {
        ReminderNotifier.cancelReminder(context)
        container.repository.recordSnooze()
        container.scheduler.scheduleInMinutes(HydrationMath.SNOOZE_MINUTES)
    }

    companion object {
        const val ACTION_REMIND = "com.hydrachat.app.action.REMIND"
        const val ACTION_DRINK = "com.hydrachat.app.action.DRINK"
        const val ACTION_SNOOZE = "com.hydrachat.app.action.SNOOZE"
        const val EXTRA_AMOUNT_ML = "amount_ml"
        private const val TAG = "ReminderReceiver"
        private const val MAX_SINGLE_DRINK_ML = 2000
    }
}

/** Alarms do not survive a reboot, an app update or a clock change, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        (context.applicationContext as HydraApp).container.scheduler.restore()
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
