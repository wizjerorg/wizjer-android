package org.wizjer.android.protection

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.wizjer.android.detection.LocalScamDetector

/** Saved alerts read back as they were (org.json, so on a device). Touches no storage. */
@RunWith(AndroidJUnit4::class)
class AlertLogDeviceTest {

    @Test
    fun savedAlertsReadBackAsTheyWere() {
        val sms = IncomingMessage(MessageSource.Sms, "+48 512 345 678", "InPost: dopłać 1,99 zł: https://inpost-dostawa.top/pl", null, 1_792_800_000_000)
        val email = IncomingMessage(MessageSource.Email, "Allegro", "Zaloguj się, aby potwierdzić dane.", "com.google.android.gm", 1_792_800_100_000, subject = "Twoje konto zostało zawieszone")
        val detector = LocalScamDetector()
        val alerts = listOf(sms, email).mapIndexed { i, m -> FlaggedMessage(i + 1, m, detector.analyzeNow(m.toDetectorInput())) }

        val back = AlertLog.read(AlertLog.write(alerts))

        assertEquals(alerts.map { it.id to it.message }, back.map { it.id to it.message })
        assertEquals(alerts.map { it.analysis.level to it.analysis.reasons }, back.map { it.analysis.level to it.analysis.reasons })
        assertEquals(alerts.map { it.analysis.primaryType }, back.map { it.analysis.primaryType })
    }

    @Test
    fun unknownSignalsFromAnotherVersionAreSkipped() {
        val json = """[{"id":1,"source":"Sms","text":"x","receivedAt":1,"level":"Dangerous","risk":90,"confidence":80,
            "language":"pl","detector":"rules-0.2","modelUsed":true,"listsUsed":false,"reasons":[{"signal":"SomethingNew"}]}]"""
        assertEquals(emptyList<FlaggedMessage>(), AlertLog.read(json))
    }
}
