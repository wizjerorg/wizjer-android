package org.wizjer.android.detection.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.wizjer.android.detection.DetectorInput
import org.wizjer.android.detection.EntityKind
import org.wizjer.android.detection.EntityNote
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.Signal
import java.net.IDN

class SiteIndexTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun index(vararg entries: Pair<SiteList, String>): SiteIndex {
        val builder = SiteIndex.Builder()
        entries.forEach { (list, domain) -> builder.add(list, domain) }
        val file = folder.newFile()
        builder.writeTo(file, updatedAt = 1_700_000_000_000)
        return checkNotNull(SiteIndex.open(file))
    }

    @Test
    fun `written lists read back`() {
        val index = index(
            SiteList.Cert to "nordandfolk.com",
            SiteList.Cert to "paypay-pl.eu.cc",
            SiteList.Cert to "nordandfolk.com", // duplicates count once
            SiteList.Gambling to "zzzzgame.com",
        )
        assertEquals(1_700_000_000_000, index.updatedAt)
        assertEquals(2, index.size(SiteList.Cert))
        assertEquals(1, index.size(SiteList.Gambling))
        assertEquals(0, index.size(SiteList.Piracy))
        assertEquals(setOf(SiteList.Cert), index.listsFor("nordandfolk.com"))
        assertEquals(setOf(SiteList.Gambling), index.listsFor("zzzzgame.com"))
        assertTrue(index.listsFor("example.com").isEmpty())
    }

    @Test
    fun `subdomains of a listed site match, but not its parents or siblings`() {
        val index = index(SiteList.Cert to "paypay-pl.eu.cc", SiteList.Cert to "webmail-x.pages.dev")
        assertEquals(setOf(SiteList.Cert), index.listsFor("login.paypay-pl.eu.cc"))
        assertTrue(index.listsFor("pages.dev").isEmpty())
        assertTrue(index.listsFor("other.pages.dev").isEmpty())
    }

    @Test
    fun `domains are compared in one normal form`() {
        val index = index(SiteList.Cert to "WWW.Klydren.SHOP.", SiteList.Piracy to "żółw-filmy.pl")
        assertEquals(setOf(SiteList.Cert), index.listsFor("klydren.shop"))
        assertEquals(setOf(SiteList.Piracy), index.listsFor("żółw-filmy.pl"))
        assertEquals(setOf(SiteList.Piracy), index.listsFor(IDN.toASCII("żółw-filmy.pl")))
        assertNull(SiteIndex.normalize("not a domain"))
    }

    @Test
    fun `a damaged file is ignored`() {
        val file = folder.newFile()
        file.writeBytes(ByteArray(40) { 7 })
        assertNull(SiteIndex.open(file))
        assertNull(SiteIndex.open(folder.root.resolve("missing.idx")))
    }

    @Test
    fun `the detector uses every downloaded index`() {
        val main = index(SiteList.Cert to "nordandfolk.com", SiteList.Shortener to "t.2merkato.com")
        val gamblingOnly = index(SiteList.Gambling to "zzzzgame.com")
        val withLists = LocalScamDetector { listOf(main, gamblingOnly) }
        val withoutLists = LocalScamDetector()
        val text = "Hej, zobacz nordandfolk.com"

        val listed = withLists.analyzeNow(DetectorInput(text))
        assertTrue(listed.listsUsed)
        assertEquals(RiskLevel.Dangerous, listed.level)
        assertTrue(listed.reasons.any { it.signal == Signal.CertList })
        assertTrue(listed.entities.any { it.kind == EntityKind.Link && EntityNote.CertList in it.notes })

        val unlisted = withoutLists.analyzeNow(DetectorInput(text))
        assertFalse(unlisted.listsUsed)
        assertEquals(RiskLevel.Safe, unlisted.level)

        val gambling = withLists.analyzeNow(DetectorInput("Bonus czeka: https://zzzzgame.com"))
        assertTrue(gambling.reasons.any { it.signal == Signal.GamblingSite })

        val shortened = withLists.analyzeNow(DetectorInput("Zobacz https://t.2merkato.com/abc"))
        assertTrue(shortened.reasons.any { it.signal == Signal.ShortLink })
    }
}
