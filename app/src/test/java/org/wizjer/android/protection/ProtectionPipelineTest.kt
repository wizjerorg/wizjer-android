package org.wizjer.android.protection

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.SampleMessages
import org.wizjer.android.detection.model.ScamModel
import java.io.File

/** What background protection decides about incoming messages, with the bundled model. */
class ProtectionPipelineTest {

    private val detector = LocalScamDetector(model = { model })

    private fun alerts(source: MessageSource, sender: String?, text: String) =
        ProtectionPipeline.isThreat(detector.analyzeNow(IncomingMessage(source, sender, text, null, 0).toDetectorInput()))

    @Test
    fun `dangerous samples raise an alert, as SMS and as notifications`() {
        SampleMessages.all.filter { it.expected == RiskLevel.Dangerous }.forEach { sample ->
            assertEquals(sample.label, true, alerts(MessageSource.Sms, sample.sender, sample.text))
            assertEquals(sample.label, true, alerts(MessageSource.Notification, sample.sender, sample.text))
        }
    }

    @Test
    fun `a callback scam SMS raises an alert`() {
        val text = "Wykryto logowanie z Wietnamu do Twojego konta. Jesli to nie Ty, zadzwon natychmiast pod +48 22 410 18 52 REF: VN7701"
        assertEquals(true, alerts(MessageSource.Sms, "+48 512 345 678", text))
    }

    @Test
    fun `genuine messages stay quiet`() {
        assertEquals(false, alerts(MessageSource.Sms, "mBank", "mBank: dodano nowe urządzenie do aplikacji mobilnej. Jeśli to nie Ty, zadzwoń na 801 300 800."))
        assertEquals(false, alerts(MessageSource.Notification, "Anna", "Będę o 18, kupić coś na kolację?"))
        assertEquals(false, alerts(MessageSource.Notification, "Mama", "Нагадую: завтра о 10 у лікаря, не забудь документи"))
        SampleMessages.all.filter { it.expected == RiskLevel.Safe }.forEach { sample ->
            assertEquals(sample.label, false, alerts(MessageSource.Sms, sample.sender, sample.text))
        }
    }

    companion object {
        private val model = File("src/main/assets/${ScamModel.ASSET}").inputStream().use { ScamModel.read(it) }
    }
}
