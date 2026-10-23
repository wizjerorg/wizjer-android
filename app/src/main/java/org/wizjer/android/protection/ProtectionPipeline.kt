package org.wizjer.android.protection

import android.content.Context
import org.wizjer.android.data.lists.ThreatLists
import org.wizjer.android.data.model.ModelStore
import org.wizjer.android.detection.Analysis
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.RiskLevel
import java.util.concurrent.Executors

/**
 * Where every incoming SMS and notification ends up: respects the user's switches, checks
 * [IncomingMessage.text] on the device (rules, threat lists and the AI model), counts what was
 * checked and raises an alert when a message looks dangerous. Nothing is sent anywhere.
 */
object ProtectionPipeline {

    /** One message at a time, off the main thread, in the order they arrived. */
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "wizjer-protection") }
    private val repeats = RepeatFilter()

    /** Built on first use, on the worker thread (reading the model takes a moment). */
    private var detector: LocalScamDetector? = null

    /** Checks [message] in the background, then calls [done] (e.g. to finish a broadcast). */
    fun onIncoming(context: Context, message: IncomingMessage, done: () -> Unit = {}) {
        val checkable = message.text.isNotBlank() &&
            ProtectionStore.get(context).isActive(message.source) &&
            !repeats.isRepeat(message.text) // an SMS usually arrives again as the SMS app's notification
        if (!checkable) return done()

        val app = context.applicationContext
        worker.execute {
            try {
                check(app, message)
            } catch (_: Exception) {
                // A failed check must never crash the app in the background.
            } finally {
                done()
            }
        }
    }

    private fun check(context: Context, message: IncomingMessage) {
        val analysis = detector(context).analyzeNow(message.toDetectorInput())
        val threat = isThreat(analysis)
        ProtectionStore.get(context).recordChecked(message.source, message.receivedAt, threat)
        if (threat) ScamAlerts.show(context, AlertLog.add(message, analysis))
    }

    /** Only a firm verdict raises an alert: a false alarm costs more trust than a "be careful" earns. */
    internal fun isThreat(analysis: Analysis) = analysis.level == RiskLevel.Dangerous

    private fun detector(context: Context) = detector ?: LocalScamDetector(
        model = { ModelStore.get(context) },
        sites = { ThreatLists.get(context).loaded.value.values },
    ).also { detector = it }
}
