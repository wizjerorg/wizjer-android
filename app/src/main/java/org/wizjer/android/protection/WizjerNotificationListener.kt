package org.wizjer.android.protection

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Receives other apps' notifications once the user grants notification access (bound and
 * kept alive by the system) and hands each new one to [ProtectionPipeline].
 */
class WizjerNotificationListener : NotificationListenerService() {

    /** Content fingerprints seen recently: updates of the same notification arrive again. */
    private val recent = object : LinkedHashMap<Int, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Unit>?) = size > RECENT_LIMIT
    }

    override fun onListenerConnected() {
        ProtectionStore.get(this).setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        ProtectionStore.get(this).setListenerConnected(false)
        requestRebind(ComponentName(this, WizjerNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val message = toIncomingMessage(sbn) ?: return
        val fingerprint = (sbn.key + message.text).hashCode()
        synchronized(recent) {
            if (recent.put(fingerprint, Unit) != null) return
        }
        ProtectionPipeline.onIncoming(this, message)
    }

    /** Null for notifications that aren't messages: our own, ongoing ones, group summaries. */
    private fun toIncomingMessage(sbn: StatusBarNotification): IncomingMessage? {
        val notification = sbn.notification
        val flags = notification.flags
        if (sbn.packageName == packageName || sbn.isOngoing) return null
        if (flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return null

        val text = readableText(notification) ?: return null
        val sender = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (sbn.packageName in MAIL_APPS) {
            // Mail apps show the subject, then the start of the e-mail.
            val subject = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val body = subject?.let { text.removePrefix(it).trim() }?.takeIf { it.isNotEmpty() && it != text.trim() }
            return IncomingMessage(MessageSource.Email, sender, body ?: text, sbn.packageName, sbn.postTime, subject = subject.takeIf { body != null })
        }
        return IncomingMessage(MessageSource.Notification, sender, text, sbn.packageName, sbn.postTime)
    }

    /** The message a notification shows: the latest chat message, the expanded text, or its lines. */
    private fun readableText(notification: Notification): String? {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.messages?.lastOrNull()?.text?.toString()
            ?.let { return it }
        val extras = notification.extras
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        return (text?.toString() ?: lines)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val RECENT_LIMIT = 200

        /** Mail apps: their notifications are e-mails (sender, subject, start of the text), judged as such. */
        val MAIL_APPS = setOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android", "com.readdle.spark", "com.samsung.android.email.provider", "com.android.email",
            "com.fsck.k9", "net.thunderbird.android",
        )
    }
}
