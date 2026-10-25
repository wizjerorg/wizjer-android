package org.wizjer.android.protection

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.wizjer.android.MainActivity
import org.wizjer.android.R
import org.wizjer.android.detection.RiskLevel

/**
 * What background protection shows about a flagged message: a warning that pops up for a dangerous
 * one, or a quieter "be careful" note in the notification shade. Either only says so and opens the
 * message in the app: it never repeats the text, which keeps it out of notification history, and
 * doesn't guess the kind of scam before the user has looked.
 */
object ScamAlerts {
    private const val CHANNEL = "scam_alerts"
    private const val CAREFUL_CHANNEL = "careful_notes"

    /** Extra with the [FlaggedMessage.id] to open. */
    const val EXTRA_ALERT_ID = "org.wizjer.android.ALERT_ID"

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is false without POST_NOTIFICATIONS
    fun show(context: Context, flagged: FlaggedMessage) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val warning = flagged.analysis.level == RiskLevel.Dangerous
        // Separate channels, so people can quieten the gentle notes without losing the warnings.
        manager.createNotificationChannel(
            if (warning) {
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.alert_channel))
                    .setDescription(context.getString(R.string.alert_channel_body))
            } else {
                NotificationChannelCompat.Builder(CAREFUL_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.alert_careful_channel))
                    .setDescription(context.getString(R.string.alert_careful_channel_body))
            }.build(),
        )

        // Reaches the running app (onNewIntent) instead of starting a second copy.
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ALERT_ID, flagged.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val body = context.getString(if (warning) R.string.alert_body else R.string.alert_careful_body)
        val notification = NotificationCompat.Builder(context, if (warning) CHANNEL else CAREFUL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (warning) R.string.alert_title else R.string.alert_careful_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (warning) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(PendingIntent.getActivity(context, flagged.id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        manager.notify(flagged.id, notification)
    }
}
