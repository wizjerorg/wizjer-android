package org.wizjer.android.protection

/** Where a message reached the phone from. */
enum class MessageSource { Sms, Notification }

/**
 * One incoming message, whichever way it arrived. Lives in memory only: the content is
 * never written to storage or logs.
 *
 * @property sender phone number (SMS) or the notification's title (often the contact or app name)
 * @property appPackage the app that posted the notification; null for SMS
 */
data class IncomingMessage(
    val source: MessageSource,
    val sender: String?,
    val text: String,
    val appPackage: String?,
    val receivedAt: Long,
)
