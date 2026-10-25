package org.wizjer.android.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {

    private fun parse(stream: String): List<SseEvent> {
        val parser = SseParser()
        return stream.split("\n").mapNotNull(parser::line)
    }

    @Test
    fun readsEventsAsTheServerWritesThem() {
        val events = parse("retry: 3000\n\nid: 1\nevent: start\ndata: {\"a\":1}\n\nid: 2\nevent: text\ndata: {\"delta\":\"Hej\"}\n\n")
        assertEquals(listOf(SseEvent("1", "start", "{\"a\":1}"), SseEvent("2", "text", "{\"delta\":\"Hej\"}")), events)
    }

    @Test
    fun skipsHeartbeatsCommentsAndUnknownFields() {
        val events = parse(": ping\n\nfoo: bar\n: another\nid: 7\nevent: thinking\ndata: {}\n\n: ping\n\n")
        assertEquals(listOf(SseEvent("7", "thinking", "{}")), events)
    }

    @Test
    fun joinsDataLinesAndKeepsSpacesAfterTheFirst() {
        val events = parse("event: text\ndata: one\ndata:  two\ndata:three\n\n")
        assertEquals("one\n two\nthree", events.single().data)
    }

    @Test
    fun anEventWithoutDataIsDroppedAndTheTypeDoesNotLeakIntoTheNext() {
        val events = parse("event: lonely\n\ndata: x\n\n")
        assertEquals(listOf(SseEvent(null, "message", "x")), events)
    }

    @Test
    fun theLastIdCarriesOverToLaterEvents() {
        val events = parse("id: 5\ndata: a\n\ndata: b\n\n")
        assertEquals(listOf("5", "5"), events.map { it.id })
    }

    @Test
    fun nothingIsReturnedUntilTheBlankLine() {
        val parser = SseParser()
        assertNull(parser.line("id: 1"))
        assertNull(parser.line("event: text"))
        assertNull(parser.line("data: {}"))
        assertEquals(SseEvent("1", "text", "{}"), parser.line(""))
    }
}
