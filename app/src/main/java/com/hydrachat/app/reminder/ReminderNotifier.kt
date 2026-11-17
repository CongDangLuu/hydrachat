package com.hydrachat.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hydrachat.app.R
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.ui.MainActivity

/** Notification channels, the bubble service notification, and the no-overlay fallback reminder. */
object ReminderNotifier {
    const val SERVICE_NOTIFICATION_ID = 1002
    private const val REMINDER_NOTIFICATION_ID = 1001
    private const val CHANNEL_REMINDER = "reminder"
    private const val CHANNEL_SERVICE = "bubble_service"
    private const val REQUEST_OPEN_APP = 200
    private const val REQUEST_DRINK_CUP = 201
    private const val REQUEST_DRINK_SIP = 202
    private const val REQUEST_SNOOZE = 203

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Nhắc uống nước", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Bong bóng chat đang hiển thị", NotificationManager.IMPORTANCE_MIN),
        )
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Used when the overlay permission is missing: same quick replies as the bubble. */
    fun showReminder(context: Context, persona: Persona, nudge: NudgeContent, cupMl: Int) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("${persona.emoji} ${persona.displayName}")
            .setContentText(nudge.chatText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${nudge.chatText}\n🎯 ${nudge.miniQuest}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "🥤 $cupMl ml", drinkIntent(context, cupMl, REQUEST_DRINK_CUP))
            .addAction(0, "💧 ${HydrationMath.SIP_ML} ml", drinkIntent(context, HydrationMath.SIP_ML, REQUEST_DRINK_SIP))
            .addAction(0, "🤫 ${HydrationMath.SNOOZE_MINUTES}p", snoozeIntent(context))
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    fun cancelReminder(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(REMINDER_NOTIFICATION_ID)
    }

    fun serviceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("HydraChat đang nhắc bạn uống nước")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context))
            .build()

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun drinkIntent(context: Context, amountMl: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_DRINK)
            .putExtra(ReminderReceiver.EXTRA_AMOUNT_ML, amountMl)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_SNOOZE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
