package org.wizjer.android.protection

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wizjer.android.detection.Analysis
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.Reason
import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.SampleMessages
import org.wizjer.android.detection.Signal
import org.wizjer.android.detection.model.ScamModel
import org.wizjer.android.protection.ProtectionPipeline.AlertLevel
import java.io.File

/** What background protection decides about incoming messages, with the bundled model. */
class ProtectionPipelineTest {

    private val detector = LocalScamDetector(model = { model })

    private fun alert(source: MessageSource, sender: String?, text: String) =
        ProtectionPipeline.alertLevel(detector.analyzeNow(IncomingMessage(source, sender, text, null, 0).toDetectorInput()))

    @Test
    fun `dangerous samples raise a warning, as SMS and as notifications`() {
        SampleMessages.all.filter { it.expected == RiskLevel.Dangerous }.forEach { sample ->
            assertEquals(sample.label, AlertLevel.Warning, alert(MessageSource.Sms, sample.sender, sample.text))
            assertEquals(sample.label, AlertLevel.Warning, alert(MessageSource.Notification, sample.sender, sample.text))
        }
    }

    @Test
    fun `a callback scam SMS raises a warning`() {
        val text = "Wykryto logowanie z Wietnamu do Twojego konta. Jesli to nie Ty, zadzwon natychmiast pod +48 22 410 18 52 REF: VN7701"
        assertEquals(AlertLevel.Warning, alert(MessageSource.Sms, "+48 512 345 678", text))
    }

    @Test
    fun `genuine messages stay quiet`() {
        assertEquals(AlertLevel.None, alert(MessageSource.Sms, "mBank", "mBank: dodano nowe urządzenie do aplikacji mobilnej. Jeśli to nie Ty, zadzwoń na 801 300 800."))
        assertEquals(AlertLevel.None, alert(MessageSource.Notification, "Anna", "Będę o 18, kupić coś na kolację?"))
        assertEquals(AlertLevel.None, alert(MessageSource.Notification, "Mama", "Нагадую: завтра о 10 у лікаря, не забудь документи"))
        SampleMessages.all.filter { it.expected == RiskLevel.Safe }.forEach { sample ->
            assertEquals(sample.label, AlertLevel.None, alert(MessageSource.Sms, sample.sender, sample.text))
        }
    }

    @Test
    fun `a parcel fee with nothing to act on gets a gentle note, not a warning`() {
        assertEquals(AlertLevel.Careful, alert(MessageSource.Sms, "+48 512 345 678", "Pilne! Dopłać 2 zł do przesyłki, inaczej wróci do nadawcy."))
    }

    @Test
    fun `"be careful" needs a concrete reason, not just the model's hunch or a link`() {
        fun suspicious(vararg signals: Signal) = Analysis(
            risk = 45, level = RiskLevel.Suspicious, confidence = 60, primaryType = null, types = emptyList(),
            reasons = signals.map { Reason(it) }, entities = emptyList(), normalizedText = "", language = "pl",
            timings = emptyList(), detectorVersion = "test",
        )
        assertEquals(AlertLevel.None, ProtectionPipeline.alertLevel(suspicious(Signal.ModelSuspicious, Signal.Link, Signal.Money)))
        assertEquals(AlertLevel.Careful, ProtectionPipeline.alertLevel(suspicious(Signal.ModelSuspicious, Signal.ShortLink)))
        assertEquals(AlertLevel.Careful, ProtectionPipeline.alertLevel(suspicious(Signal.Prize, Signal.Urgency)))
    }

    companion object {
        private val model = File("src/main/assets/${ScamModel.ASSET}").inputStream().use { ScamModel.read(it) }
    }
}
