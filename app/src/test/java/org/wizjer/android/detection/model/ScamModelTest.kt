package org.wizjer.android.detection.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScamModelTest {

    private val model = File("src/main/assets/${ScamModel.ASSET}").inputStream().use { ScamModel.read(it) }

    @Test
    fun `blake2b matches RFC 7693 and Python`() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            Blake2b.digest("abc".toByteArray(), 64).toHex(),
        )
        assertEquals("d8bb14d833d59559", Blake2b.digest("abc".toByteArray(), 8).toHex())
        assertEquals("e4a6a0577479b2b4", Blake2b.digest(ByteArray(0), 8).toHex())
        val twoBlocksAndMore = ByteArray(512) { (it % 256).toByte() }
        assertEquals("e4ac345f5aeb2a0e", Blake2b.digest(twoBlocksAndMore, 8).toHex())
    }

    @Test
    fun `feature names hash to the indexes Python gives them`() {
        val python = mapOf(
            "c pa" to 882985, "wpaczka" to 733265, "bdoplata_1" to 734637, "cue.urgent" to 1026699,
            "brand.link.other" to 11569, "wпошта" to 336750, "c❤️ " to 376297, "tld.pl" to 626993,
        )
        python.forEach { (name, index) -> assertEquals(name, index, ModelFeatures.index(name)) }
    }

    @Test
    fun `scores match the training code on its test messages`() {
        // 30 test messages plus made-up ones that exercise every feature (export_model.PARITY).
        assertTrue(model.golden.size > 30)
        model.golden.forEach { g ->
            assertEquals(g.text.take(60), g.score, model.score(g.text, g.source, g.sender, g.subject), 1e-9)
        }
    }

    @Test
    fun `has thresholds per language and channel`() {
        val sms = model.thresholds("pl", "sms")
        assertNotNull(sms)
        assertTrue(sms!!.caution < sms.scam)
        assertNotNull(model.thresholds("uk", "notification"))
        assertNotNull(model.thresholds("en", "email"))
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
