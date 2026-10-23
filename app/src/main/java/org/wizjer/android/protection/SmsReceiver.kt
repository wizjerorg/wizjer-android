package org.wizjer.android.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Receives incoming SMS (needs the RECEIVE_SMS permission) and hands each one to
 * [ProtectionPipeline]. The broadcast stays open until the check is done, so the app isn't
 * stopped halfway.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val message = incomingMessage(intent) ?: return
        val pending = goAsync()
        ProtectionPipeline.onIncoming(context, message) { pending.finish() }
    }

    companion object {
        /** The SMS in an SMS_RECEIVED broadcast; long messages arrive in parts and are joined back together. */
        fun incomingMessage(intent: Intent): IncomingMessage? {
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return null
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (parts.isNullOrEmpty()) return null
            return IncomingMessage(
                source = MessageSource.Sms,
                sender = parts.first().displayOriginatingAddress,
                text = parts.joinToString("") { it.displayMessageBody.orEmpty() },
                appPackage = null,
                receivedAt = parts.first().timestampMillis,
            )
        }
    }
}
