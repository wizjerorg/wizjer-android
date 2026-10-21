package org.wizjer.android.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScamDetectorTest {

    private val detector = LocalScamDetector()
    private fun analyze(text: String, sender: String? = null) = detector.analyzeNow(DetectorInput(text, sender = sender))

    @Test
    fun `every sample gets its expected level`() {
        SampleMessages.all.forEach { sample ->
            val analysis = analyze(sample.text, sample.sender)
            assertEquals("${sample.label} (risk ${analysis.risk}): ${analysis.reasons.map { it.signal }}", sample.expected, analysis.level)
        }
    }

    @Test
    fun `parcel scam is typed and explained`() {
        val analysis = analyze(SampleMessages.all.first { it.label == "Paczka" }.text)
        assertEquals(ScamType.ParcelFee, analysis.primaryType)
        assertTrue(analysis.reasons.any { it.signal == Signal.BrandLookalike && it.detail == "InPost" })
        assertTrue(analysis.entities.any { it.kind == EntityKind.Link && EntityNote.Lookalike in it.notes })
    }

    @Test
    fun `official domain lowers the risk`() {
        val official = analyze("InPost: paczka czeka. https://inpost.pl/sledzenie")
        val fake = analyze("InPost: paczka czeka. https://inpost-sledzenie.top")
        assertTrue(official.risk < fake.risk)
        assertTrue(official.reasons.any { it.signal == Signal.OfficialDomain })
    }

    @Test
    fun `disguises are undone`() {
        val normalized = TextNormalizer.normalize("D o p ł a ć teraz: іnpost[.]pl​")
        assertTrue(normalized.obfuscated)
        assertTrue("dopłać" in normalized.clean.lowercase())
        assertTrue("inpost.pl" in normalized.clean)
    }

    @Test
    fun `extracts registrable domains, phones and accounts`() {
        assertEquals("inpost.pl", EntityExtractor.registrableDomain("sledzenie.inpost.pl"))
        assertEquals("pekao.com.pl", EntityExtractor.registrableDomain("www.pekao.com.pl"))

        val extraction = EntityExtractor.extract(
            TextNormalizer.normalize("Zadzwoń 701 234 567 lub +44 20 7946 0958. Konto: 12 1020 1026 0000 0402 0000 1234"),
        )
        assertTrue(extraction.phones.any { it.isPremium })
        assertTrue(extraction.phones.any { it.isForeign })
        assertEquals(1, extraction.bankAccounts.size)
    }

    @Test
    fun `guesses the language`() {
        assertEquals("pl", analyze("Wykryliśmy podejrzane logowanie.").language)
        assertEquals("pl", analyze("Prosze o kontakt, to pilne").language) // without diacritics
        assertEquals("uk", analyze("Ваш рахунок заблоковано.").language)
        assertEquals("en", analyze("Your account has been locked.").language)
    }

    @Test
    fun `short words are not mistaken for phrases`() {
        // "pin" must not match "pinezka", "fee" must not match "feedback".
        val analysis = analyze("Kupiłam pinezki i dałam feedback w sklepie.")
        assertTrue(analysis.reasons.none { it.signal == Signal.Credentials || it.signal == Signal.Payment })
    }
}
