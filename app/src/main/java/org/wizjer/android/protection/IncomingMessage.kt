package org.wizjer.android.protection

import org.wizjer.android.detection.DetectorInput

/** Where a message reached the phone from. [Email] is a mail app's notification: the subject and the start of the text. */
enum class MessageSource { Sms, Notification, Email }

/**
 * One incoming message, whichever way it arrived. Lives in memory only, never in logs; only the
 * last few that raised an alert are kept on the phone ([AlertLog]) so the user can look at them again.
 *
 * @property sender phone number (SMS) or the notification's title (often the contact or app name)
 * @property appPackage the app that posted the notification; null for SMS
 * @property subject an e-mail's subject, kept apart from [text] as the AI model was trained
 */
data class IncomingMessage(
    val source: MessageSource,
    val sender: String?,
    val text: String,
    val appPackage: String?,
    val receivedAt: Long,
    val subject: String? = null,
)

/** What the detector checks: the text and its sender kept apart, as the AI model was trained. */
fun IncomingMessage.toDetectorInput() = DetectorInput(text = text, source = source, sender = sender, subject = subject)
