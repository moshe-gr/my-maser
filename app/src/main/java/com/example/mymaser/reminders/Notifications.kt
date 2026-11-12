package com.example.mymaser.reminders

import android.Manifest
import android.app.NotificationChannel
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.formatPercent
import com.example.mymaser.history.toPrefill

private const val CHANNEL_REMINDERS = "reminders"
private const val CHANNEL_SUMMARIES = "summaries"

fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannels(
        listOf(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_reminders_description) },
            NotificationChannel(
                CHANNEL_SUMMARIES,
                context.getString(R.string.channel_summaries),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_summaries_description) }
        )
    )
}

private fun notificationId(reminder: Reminder): Int = reminder.key.hashCode()

private fun Context.openIntent(link: DeepLink, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        link.toIntent(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

/** Whether the app may post notifications right now (permission on Android 13+, and not blocked). */
fun canPostNotifications(context: Context): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Whether turning reminders on should first ask for the Android 13+ notification permission. */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications(context)

private const val EMAIL_IMPORT_NOTIFICATION_ID = 0x6d61696c

/** Tells the user [emails] emails hold income or donations waiting for review. */
fun postEmailImports(context: Context, emails: Int) {
    if (!canPostNotifications(context)) return
    val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
        .setSmallIcon(R.drawable.ic_stat_maser)
        .setContentTitle(context.resources.getQuantityString(R.plurals.notify_email_title, emails, emails))
        .setContentText(context.getString(R.string.notify_email_text))
        .setContentIntent(context.openIntent(DeepLink.Open(AppDestination.Home), EMAIL_IMPORT_NOTIFICATION_ID))
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(EMAIL_IMPORT_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
        // Permission was revoked between the check and the post.
    }
}

/** Posts [reminder] if the user allows notifications; returns whether it was shown. */
fun postReminder(context: Context, reminder: Reminder): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!canPostNotifications(context)) return false
    val id = notificationId(reminder)

    val builder = when (reminder) {
        is Reminder.ExpectedEntry -> {
            val prediction = reminder.prediction
            val amount = formatCurrency(prediction.amount.toDouble(), currencyCode = prediction.currency)
            val open = context.openIntent(DeepLink.OpenForm(prediction.toPrefill()), id)
            val skip = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_SKIP)
                    .putExtra(ReminderReceiver.EXTRA_PREDICTION_KEY, prediction.key)
                    .putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setContentTitle(prediction.name)
                .setContentText(
                    context.getString(
                        if (prediction.isDonation) R.string.notify_expected_donation else R.string.notify_expected_income,
                        prediction.expectedDate.dayOfMonth,
                        amount
                    )
                )
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.notify_action_log), open)
                .addAction(0, context.getString(R.string.due_skip), skip)
        }

        is Reminder.Balance -> {
            val amount = formatCurrency(reminder.amount, true, reminder.currency)
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setContentTitle(
                    context.getString(
                        if (reminder.yearEnd) R.string.notify_balance_year_title else R.string.notify_balance_title,
                        amount
                    )
                )
                .setContentText(
                    context.getString(
                        if (reminder.yearEnd) R.string.notify_balance_year_text else R.string.notify_balance_text
                    )
                )
                .setContentIntent(context.openIntent(DeepLink.Open(AppDestination.Donation), id))
        }

        is Reminder.YearInReview -> {
            val total = formatCurrency(reminder.total, true, reminder.currency)
            val text = if (reminder.previousTotal > 0) {
                val change = (reminder.total - reminder.previousTotal) / reminder.previousTotal
                context.getString(
                    R.string.notify_review_text_change,
                    total,
                    formatPercent(change, signed = true),
                    reminder.year - 1
                )
            } else {
                context.getString(R.string.notify_review_text, total)
            }
            NotificationCompat.Builder(context, CHANNEL_SUMMARIES)
                .setContentTitle(context.getString(R.string.notify_review_title, reminder.year))
                .setContentText(text)
                .setContentIntent(context.openIntent(DeepLink.Open(AppDestination.Home), id))
        }
    }

    val notification = builder
        .setSmallIcon(R.drawable.ic_stat_maser)
        .setAutoCancel(true)
        .setStyle(NotificationCompat.BigTextStyle())
        .build()
    return try {
        manager.notify(id, notification)
        true
    } catch (_: SecurityException) {
        false
    }
}
