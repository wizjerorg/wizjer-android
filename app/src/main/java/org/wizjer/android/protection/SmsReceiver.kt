package org.wizjer.android.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Receives incoming SMS (needs the RECEIVE_SMS permission) and hands each one to
 * [ProtectionPipeline]. Long messages arrive in parts and are joined back together.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        ProtectionPipeline.onIncoming(
            context,
            IncomingMessage(
                source = MessageSource.Sms,
                sender = parts.first().displayOriginatingAddress,
                text = parts.joinToString("") { it.displayMessageBody.orEmpty() },
                appPackage = null,
                receivedAt = parts.first().timestampMillis,
            ),
        )
    }
}
