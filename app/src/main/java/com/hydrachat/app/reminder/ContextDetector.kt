package com.hydrachat.app.reminder

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Process
import android.util.DisplayMetrics
import android.view.Display

/** Decides whether a reminder would interrupt the user (DND, landscape video/game, hidden apps). */
object ContextDetector {
    private const val FOREGROUND_LOOKBACK_MS = 60 * 60 * 1000L

    fun deferReason(context: Context, hiddenApps: Set<String>): String? = when {
        isDoNotDisturbOn(context) -> "do-not-disturb"
        isLandscape(context) -> "landscape"
        isHiddenAppInForeground(context, hiddenApps) -> "hidden-app"
        else -> null
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // checkOpNoThrow works on every API level and is the non-deprecated variant again since API 36.
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isDoNotDisturbOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = manager.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun isLandscape(context: Context): Boolean {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels > metrics.heightPixels
    }

    private fun isHiddenAppInForeground(context: Context, hiddenApps: Set<String>): Boolean {
        if (hiddenApps.isEmpty() || !hasUsageAccess(context)) return false
        return foregroundPackage(context) in hiddenApps
    }

    private fun foregroundPackage(context: Context): String? {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var lastForeground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForeground = event.packageName
            }
        }
        return lastForeground
    }
}
