package com.hydrachat.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hydrachat.app.R
import com.hydrachat.app.ai.AI_SOURCE_LABEL
import com.hydrachat.app.ai.NudgeContent
import com.hydrachat.app.bubble.BubbleActivity
import com.hydrachat.app.bubble.PersonaIcon
import com.hydrachat.app.bubble.putNudge
import com.hydrachat.app.data.UserSettings
import com.hydrachat.app.domain.HydrationMath
import com.hydrachat.app.domain.StoreCatalog
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
    private const val REQUEST_BUBBLE = 204
    private const val BUBBLE_HEIGHT_DP = 420
    private const val SHORTCUT_PREFIX = "persona_"
    private const val USER_NAME = "Bạn"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminderChannel = NotificationChannel(CHANNEL_REMINDER, "Nhắc uống nước", NotificationManager.IMPORTANCE_HIGH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) reminderChannel.setAllowBubbles(true)
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Bong bóng chat đang hiển thị", NotificationManager.IMPORTANCE_MIN),
        )
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** True when every conversation of the app may bubble; "selected conversations" cannot be checked up front. */
    fun canBubble(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                manager.bubblePreference == NotificationManager.BUBBLE_PREFERENCE_ALL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                @Suppress("DEPRECATION")
                manager.areBubblesAllowed()
            else -> false
        }
    }

    /**
     * Used when the overlay cannot be shown. On Android 11+ it is a conversation notification that can
     * expand into a system bubble; where bubbles are off it stays a notification with the same quick replies.
     */
    fun showReminder(context: Context, settings: UserSettings, nudge: NudgeContent) {
        if (!canPost(context)) return
        val persona = settings.persona
        val icon = PersonaIcon.create(persona, StoreCatalog.skin(settings.skinId))
        val personaPerson = Person.Builder().setName(persona.displayName).setIcon(icon).setKey(persona.id).build()
        val shortcutId = publishConversationShortcut(context, personaPerson, icon)
        // One message, not two: the bubble preview shows only the latest message, which must be the nudge.
        val messageText = listOfNotNull(
            nudge.chatText,
            AI_SOURCE_LABEL.takeIf { nudge.isFromAi },
        ).joinToString("\n")
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(USER_NAME).build())
            .addMessage(messageText, System.currentTimeMillis(), personaPerson)

        val cupMl = settings.cupMl
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("${persona.emoji} ${persona.displayName}")
            .setContentText(nudge.chatText)
            .setStyle(style)
            .setShortcutId(shortcutId)
            .addPerson(personaPerson)
            .setBubbleMetadata(bubbleMetadata(context, nudge, icon))
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

    /** Bubbles only attach to conversations, which Android ties to a long-lived sharing shortcut. */
    private fun publishConversationShortcut(context: Context, person: Person, icon: IconCompat): String {
        val shortcutId = SHORTCUT_PREFIX + person.key
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(person.name ?: USER_NAME)
            .setIcon(icon)
            .setPerson(person)
            .setLongLived(true)
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        return shortcutId
    }

    private fun bubbleMetadata(context: Context, nudge: NudgeContent, icon: IconCompat): NotificationCompat.BubbleMetadata {
        val intent = Intent(context, BubbleActivity::class.java).putNudge(nudge)
        // Android 12+ requires a mutable PendingIntent for the bubble's expanded activity.
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_BUBBLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.BubbleMetadata.Builder(pendingIntent, icon)
            .setDesiredHeight(BUBBLE_HEIGHT_DP)
            .build()
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
