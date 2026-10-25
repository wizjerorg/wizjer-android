package org.wizjer.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyTextTest {

    private fun plain(vararg spans: String) = spans.map { Span(it, bold = false) }

    @Test
    fun theOpeningBoldLineIsTheVerdict() {
        val blocks = parseReply("**To prawdopodobnie oszustwo.** Nie otwieraj linku.\n\nInPost nie prosi o dopłaty SMS-em.")
        assertEquals(
            listOf(
                ReplyBlock.Verdict(plain("To prawdopodobnie oszustwo.").map { it.copy(bold = true) }),
                ReplyBlock.Paragraph(plain("Nie otwieraj linku.")),
                ReplyBlock.Paragraph(plain("InPost nie prosi o dopłaty SMS-em.")),
            ),
            blocks,
        )
    }

    @Test
    fun listsAndBoldInsideThem() {
        val blocks = parseReply("Co zrobić:\n- Nie klikaj **linku**\n- Zadzwoń do banku\n1. Zablokuj kartę\n2) Zgłoś to")
        assertEquals(
            listOf(
                ReplyBlock.Paragraph(plain("Co zrobić:")),
                ReplyBlock.Bullet(listOf(Span("Nie klikaj ", false), Span("linku", true))),
                ReplyBlock.Bullet(plain("Zadzwoń do banku")),
                ReplyBlock.Numbered("1", plain("Zablokuj kartę")),
                ReplyBlock.Numbered("2", plain("Zgłoś to")),
            ),
            blocks,
        )
    }

    @Test
    fun anIndentedLineContinuesItsListItemAndAPlainOneStartsAParagraph() {
        val blocks = parseReply("- Pierwszy\n  ciąg dalszy\nNowy akapit")
        assertEquals(listOf(ReplyBlock.Bullet(plain("Pierwszy ciąg dalszy")), ReplyBlock.Paragraph(plain("Nowy akapit"))), blocks)
    }

    @Test
    fun whileStreamingAnOpenBoldIsBoldAndAHalfMarkerIsHidden() {
        assertEquals(listOf(ReplyBlock.Verdict(listOf(Span("To praw", true)))), parseReply("**To praw"))
        assertEquals(listOf(ReplyBlock.Paragraph(plain("Uważaj"))), parseReply("Uważaj *"))
        assertEquals(
            listOf(ReplyBlock.Paragraph(listOf(Span("Uważaj ", false), Span("na", true)))),
            parseReply("Uważaj **na"),
        )
        assertEquals(emptyList<ReplyBlock>(), parseReply("*"))
        assertEquals(emptyList<ReplyBlock>(), parseReply("- "))
    }

    @Test
    fun linesOfAParagraphStayOnTheirOwnLines() {
        assertEquals(listOf(ReplyBlock.Paragraph(plain("Linia 1\nLinia 2"))), parseReply("Linia 1\r\nLinia 2"))
    }

    @Test
    fun aStarThatIsNotABulletStaysAsWritten() {
        assertEquals(listOf(ReplyBlock.Paragraph(plain("5 * 3 = 15"))), parseReply("5 * 3 = 15"))
    }

    @Test
    fun plainTextForCopyingAndReadingAloud() {
        assertEquals(
            "To oszustwo.\n\nNie klikaj.\n• Zadzwoń do banku\n• Zablokuj kartę",
            plainReply("**To oszustwo.**\n\nNie klikaj.\n\n- Zadzwoń do **banku**\n- Zablokuj kartę"),
        )
    }

    @Test
    fun onlyMarkdownLinksToHttpsBecomeLinks() {
        val blocks = parseReply("Zgłoś stronę w [formularzu CERT](https://incydent.cert.pl). Adres inpost-doplata.top to oszustwo.")
        assertEquals(
            listOf(
                ReplyBlock.Paragraph(
                    listOf(
                        Span("Zgłoś stronę w ", false),
                        Span("formularzu CERT", false, "https://incydent.cert.pl"),
                        Span(". Adres inpost-doplata.top to oszustwo.", false),
                    ),
                ),
            ),
            blocks,
        )
        // Anything but https shows as its title, unclickable.
        assertEquals(listOf(ReplyBlock.Paragraph(plain("Zobacz ", "stronę"))), parseReply("Zobacz [stronę](http://example.pl)"))
        assertEquals(listOf(ReplyBlock.Paragraph(plain("Zobacz ", "to"))), parseReply("Zobacz [to](javascript:alert)"))
    }

    @Test
    fun linksWorkInBoldListsAndTheVerdict() {
        val blocks = parseReply("**Zgłoś to w [CERT](https://incydent.cert.pl).** Szybko.\n\n- Zadzwoń na [infolinię](https://www.knf.gov.pl)")
        assertEquals(
            ReplyBlock.Verdict(listOf(Span("Zgłoś to w ", true), Span("CERT", true, "https://incydent.cert.pl"), Span(".", true))),
            blocks[0],
        )
        assertEquals(ReplyBlock.Paragraph(plain("Szybko.")), blocks[1])
        assertEquals(ReplyBlock.Bullet(listOf(Span("Zadzwoń na ", false), Span("infolinię", false, "https://www.knf.gov.pl"))), blocks[2])
    }

    @Test
    fun copyingKeepsTheAddressAndReadingAloudSaysOnlyTheTitle() {
        val text = "Zgłoś stronę w [formularzu CERT](https://incydent.cert.pl). Adres inpost-doplata.top to oszustwo."
        assertEquals("Zgłoś stronę w formularzu CERT (https://incydent.cert.pl). Adres inpost-doplata.top to oszustwo.", plainReply(text))
        assertEquals("Zgłoś stronę w formularzu CERT. Adres inpost-doplata.top to oszustwo.", plainReply(text, links = false))
        assertEquals(listOf(15..57), linkRanges(text))
    }

    @Test
    fun codeBlocksKeepTheirLinesAsWritten() {
        val blocks = parseReply("Wpisz:\n\n```bash\nadb devices\n  **nie pogrubiaj** [x](https://a.pl)\n```\nGotowe.")
        assertEquals(
            listOf(
                ReplyBlock.Paragraph(plain("Wpisz:")),
                ReplyBlock.Code("bash", "adb devices\n  **nie pogrubiaj** [x](https://a.pl)"),
                ReplyBlock.Paragraph(plain("Gotowe.")),
            ),
            blocks,
        )
        // Still being written: the block runs to the end, and a half-typed fence isn't shown.
        assertEquals(listOf(ReplyBlock.Code("", "kod")), parseReply("```\nkod"))
        assertEquals(listOf(ReplyBlock.Paragraph(plain("Zobacz:"))), parseReply("Zobacz:\n``"))
    }

    @Test
    fun inlineCodeIsLeftAloneAndBoldRunsAcrossIt() {
        assertEquals(
            listOf(
                ReplyBlock.Paragraph(
                    listOf(Span("Kliknij ", false), Span("**Ustawienia**", false, code = true), Span(" teraz", false)),
                ),
            ),
            parseReply("Kliknij `**Ustawienia**` teraz"),
        )
        assertEquals(
            listOf(ReplyBlock.Verdict(listOf(Span("Nie wpisuj ", true), Span("1234", true, code = true), Span(" nigdzie.", true)))),
            parseReply("**Nie wpisuj `1234` nigdzie.**"),
        )
        assertEquals("Wpisz kod 1234 i:\n\nadb devices", plainReply("Wpisz kod `1234` i:\n\n```\nadb devices\n```"))
    }

    @Test
    fun polishNumbersAreSpacedOut() {
        assertEquals("+48 605 420 169", formatNumber("+48605420169"))
        assertEquals("+44 20 7946 0958", formatNumber("+44 20 7946 0958"))
        assertEquals("8080", formatNumber("8080"))
    }
}
