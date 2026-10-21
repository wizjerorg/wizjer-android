package org.wizjer.android.protection

import android.content.Context

/**
 * Where every incoming SMS and notification ends up. Stage 1 (now): respect the user's
 * switches and count what was checked. Stage 2 will analyse [IncomingMessage.text] on the
 * device and raise a warning when something looks dangerous.
 */
object ProtectionPipeline {

    fun onIncoming(context: Context, message: IncomingMessage) {
        if (message.text.isBlank()) return
        val store = ProtectionStore.get(context)
        if (!store.isActive(message.source)) return

        // Stage 2: on-device analysis of message.text goes here.

        store.recordChecked(message.source, message.receivedAt)
    }
}
