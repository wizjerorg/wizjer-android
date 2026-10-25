package org.wizjer.android.protection

import android.content.Context
import org.wizjer.android.data.lists.ThreatLists
import org.wizjer.android.data.model.ModelStore
import org.wizjer.android.detection.Analysis
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.Signal
import java.util.concurrent.Executors

/**
 * Where every incoming SMS and notification ends up: respects the user's switches, checks
 * [IncomingMessage.text] on the device (rules, threat lists and the AI model), counts what was
 * checked, and raises a warning when a message looks dangerous or a gentle note when it deserves a
 * second look. Nothing is sent anywhere.
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
        val alert = alertLevel(analysis)
        ProtectionStore.get(context).recordChecked(message.source, message.receivedAt, threat = alert == AlertLevel.Warning)
        if (alert != AlertLevel.None) ScamAlerts.show(context, AlertLog.add(context, message, analysis))
    }

    /** How a checked message is brought to the user's attention. */
    enum class AlertLevel { None, Careful, Warning }

    /**
     * A firm verdict raises a warning. A "be careful" verdict raises a gentle note only when the rules
     * name something concrete to watch out for (a payment request, a short link, a prize…): the AI
     * model's hunch or a plain link alone would make it a daily nuisance that people stop reading.
     */
    internal fun alertLevel(analysis: Analysis): AlertLevel = when (analysis.level) {
        RiskLevel.Dangerous -> AlertLevel.Warning
        RiskLevel.Suspicious ->
            if (analysis.reasons.any { it.contribution > 0 && it.signal !in BACKGROUND }) AlertLevel.Careful else AlertLevel.None
        RiskLevel.Safe -> AlertLevel.None
    }

    /** Signals that only set the scene; on their own they're no reason for a note. */
    private val BACKGROUND = setOf(
        Signal.Link, Signal.Money, Signal.Parcel, Signal.Utility, Signal.Authority, Signal.NoHttps, Signal.ModelScam, Signal.ModelSuspicious,
    )

    private fun detector(context: Context) = detector ?: LocalScamDetector(
        model = { ModelStore.get(context) },
        sites = { ThreatLists.get(context).loaded.value.values },
    ).also { detector = it }
}
