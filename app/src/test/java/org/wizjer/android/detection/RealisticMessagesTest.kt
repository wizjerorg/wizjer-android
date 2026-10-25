package org.wizjer.android.detection

import org.junit.Assert.assertTrue
import org.junit.Test
import org.wizjer.android.detection.RealisticMessages.Expect
import org.wizjer.android.detection.model.ScamModel
import org.wizjer.android.protection.ProtectionPipeline
import org.wizjer.android.protection.ProtectionPipeline.AlertLevel
import java.io.File

/** Everyday messages stay quiet and scams raise alerts, with the rules and the AI model together. */
class RealisticMessagesTest {

    private val model = File("src/main/assets/${ScamModel.ASSET}").inputStream().use { ScamModel.read(it) }
    private val detector = LocalScamDetector(model = { model })

    @Test
    fun `genuine messages never get a warning and scams are always flagged`() {
        val wrong = (RealisticMessages.genuine + RealisticMessages.scams).mapNotNull { case ->
            val analysis = detector.analyzeNow(DetectorInput(case.text, case.source, case.sender, case.subject))
            val alert = ProtectionPipeline.alertLevel(analysis)
            val right = when (case.expect) {
                Expect.Quiet -> alert == AlertLevel.None
                Expect.Careful -> alert != AlertLevel.Warning
                Expect.Note -> alert != AlertLevel.None
                Expect.Alert -> alert == AlertLevel.Warning
            }
            if (right) null else "${case.expect} → $alert (${analysis.level} ${analysis.risk}) ${analysis.reasons.map { it.signal }}: ${case.text}"
        }
        assertTrue(wrong.joinToString("\n", prefix = "\n"), wrong.isEmpty())
    }

    @Test
    fun `the model itself sees payment links between friends as ordinary`() {
        val caution = model.thresholds("pl", "notification")!!.caution
        listOf("https://paypal.me/kubanowak/25", "Zrzutka na prezent dla Ani: https://paypal.me/olawis", "https://revolut.me/marek12")
            .forEach { text ->
                val score = model.score(text, "notification", "Kuba")
                assertTrue("$score ≥ $caution: $text", score < caution)
            }
    }

    @Test
    fun `scam types need their own telltale`() {
        val paypal = detector.analyzeNow(DetectorInput("https://paypal.me/kubanowak/25"))
        assertTrue("${paypal.primaryType}", paypal.primaryType == null)
        val marketplace = RealisticMessages.scams.filter { "olx" in it.text.lowercase() || "vinted" in it.text.lowercase() }
        marketplace.forEach { case ->
            val analysis = detector.analyzeNow(DetectorInput(case.text, case.source, case.sender, case.subject))
            assertTrue("${analysis.primaryType}: ${case.text}", analysis.primaryType == ScamType.Marketplace)
        }
    }
}
