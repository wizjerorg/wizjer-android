package org.wizjer.android.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyItemTest {

    private fun step(status: ToolStatus, summary: Map<String, Any?>? = null) =
        ToolStep("t1", "check_domain", status, mapOf("value" to "inpost-dostawa.top"), summary)

    @Test
    fun buildsAReplyFromItsEventsInOrder() {
        val events = listOf(
            ReplyEvent.Thinking,
            ReplyEvent.Tool(step(ToolStatus.Running)),
            ReplyEvent.Tool(step(ToolStatus.Done, mapOf("domain" to "inpost-dostawa.top", "flagged" to true, "lists" to listOf("cert")))),
            ReplyEvent.Text("**To oszustwo.** "),
            ReplyEvent.Text("\n\n"),
            ReplyEvent.Text("Nie klikaj."),
            ReplyEvent.Done(ReplyStatus.Complete, 42, listOf(Source("CERT", "https://cert.pl")), discarded = false),
        )
        val reply = events.fold(ReplyItem("k")) { item, event -> item.apply(event) }

        assertEquals("**To oszustwo.** \n\nNie klikaj.", reply.text)
        assertEquals(1, reply.tools.size)
        assertEquals(ToolStatus.Done, reply.tools.single().status)
        assertEquals(42L, reply.id)
        assertEquals(ReplyStatus.Complete, reply.status)
        assertFalse(reply.writing)
        assertFalse(reply.thinking)
        assertEquals("k", reply.key)
    }

    @Test
    fun thinkingShowsUntilSomethingArrives() {
        val waiting = ReplyItem("k")
        assertTrue(waiting.thinking)
        assertTrue(waiting.writing)
        assertFalse(waiting.apply(ReplyEvent.Text("A")).thinking)
        assertTrue(waiting.apply(ReplyEvent.Text("A")).apply(ReplyEvent.Thinking).thinking)
    }

    @Test
    fun stepsKeepTheirPlaceWhenTheyEnd() {
        val other = ToolStep("t2", "web_search", ToolStatus.Running, mapOf("query" to "x"), null)
        val reply = ReplyItem("k")
            .apply(ReplyEvent.Tool(step(ToolStatus.Running)))
            .apply(ReplyEvent.Tool(other))
            .apply(ReplyEvent.Tool(step(ToolStatus.Error)))
        assertEquals(listOf("t1", "t2"), reply.tools.map { it.id })
        assertEquals(ToolStatus.Error, reply.tools[0].status)
    }

    @Test
    fun anEndClearsReconnectingAndInterrupted() {
        val lost = ReplyItem("k", reconnecting = true, interrupted = true)
        val ended = lost.apply(ReplyEvent.Done(ReplyStatus.Stopped, 9, emptyList(), discarded = false))
        assertFalse(ended.reconnecting)
        assertFalse(ended.interrupted)
        assertEquals(ReplyStatus.Stopped, ended.status)
        assertNull(ReplyItem("k").apply(ReplyEvent.Failed("ai_error")).status)
    }
}
