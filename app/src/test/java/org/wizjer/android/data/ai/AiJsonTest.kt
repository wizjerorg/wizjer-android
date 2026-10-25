package org.wizjer.android.data.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AiJsonTest {

    @Test
    fun readsTheDocumentedReplyStream() {
        val start = AiJson.event(
            "start",
            """{"chat":{"id":"9b1c","title":"Dostałam taki SMS","createdAt":"2026-10-25T18:42:10.311Z","updatedAt":"2026-10-25T18:42:10.311Z"},"userMessageId":41}""",
        ) as ReplyEvent.Start
        assertEquals("9b1c", start.chat.id)
        assertEquals("Dostałam taki SMS", start.chat.title)
        assertEquals(Instant.parse("2026-10-25T18:42:10.311Z").toEpochMilli(), start.chat.updatedAt)
        assertEquals(41L, start.userMessageId)

        assertEquals(ReplyEvent.Thinking, AiJson.event("thinking", "{}"))

        val running = (AiJson.event(
            "tool",
            """{"id":"toolu_01A","name":"check_phone_number","input":{"number":"+48 605 420 169"},"status":"running","summary":null}""",
        ) as ReplyEvent.Tool).step
        assertEquals(ToolStatus.Running, running.status)
        assertEquals("+48 605 420 169", running.input["number"])
        assertNull(running.summary)

        val done = (AiJson.event(
            "tool",
            """{"id":"toolu_01A","name":"check_phone_number","input":{"number":"+48 605 420 169"},"status":"done","summary":{"number":"+48605420169","found":true,"verdict":"spam","reportCount":143}}""",
        ) as ReplyEvent.Tool).step
        assertEquals(ToolStatus.Done, done.status)
        assertEquals(true, done.summary!!["found"])
        assertEquals(143, (done.summary.orEmpty()["reportCount"] as Number).toInt())

        assertEquals(ReplyEvent.Text("**To prawdopodobnie oszustwo.** "), AiJson.event("text", """{"delta":"**To prawdopodobnie oszustwo.** "}"""))

        val end = AiJson.event("done", """{"status":"complete","messageId":42,"sources":[{"title":"CERT","url":"https://cert.pl"}]}""") as ReplyEvent.Done
        assertEquals(ReplyStatus.Complete, end.status)
        assertEquals(42L, end.messageId)
        assertEquals(listOf(Source("CERT", "https://cert.pl")), end.sources)
        assertFalse(end.discarded)
    }

    @Test
    fun readsEveryWayAReplyEnds() {
        assertEquals(ReplyStatus.Stopped, (AiJson.event("done", """{"status":"stopped","messageId":7}""") as ReplyEvent.Done).status)
        assertEquals(ReplyStatus.MaxTokens, (AiJson.event("done", """{"status":"max_tokens","messageId":7}""") as ReplyEvent.Done).status)
        assertEquals(ReplyStatus.Incomplete, (AiJson.event("done", """{"status":"incomplete","messageId":7}""") as ReplyEvent.Done).status)
        val deleted = AiJson.event("done", """{"status":"deleted","messageId":null}""") as ReplyEvent.Done
        assertEquals(ReplyStatus.Deleted, deleted.status)
        assertNull(deleted.messageId)
        val discarded = AiJson.event("done", """{"status":"stopped","messageId":null,"discarded":true}""") as ReplyEvent.Done
        assertTrue(discarded.discarded)
        assertNull(discarded.messageId)
        assertEquals(ReplyEvent.Failed("refused"), AiJson.event("error", """{"error":"refused"}"""))
    }

    @Test
    fun unknownEventsAndFieldsDoNotBreakAnything() {
        assertNull(AiJson.event("something_new", """{"x":1}"""))
        val step = (AiJson.event("tool", """{"id":"t1","name":"check_iban","input":{"iban":"PL1"},"status":"error","summary":null,"extra":true}""") as ReplyEvent.Tool).step
        assertEquals("check_iban", step.name)
        assertEquals(ToolStatus.Error, step.status)
    }

    @Test
    fun sourcesWithoutAnAddressAreLeftOutAndAMissingTitleShowsTheAddress() {
        val done = AiJson.event("done", """{"status":"complete","messageId":1,"sources":[{"title":"x"},{"url":"https://a.pl"}]}""") as ReplyEvent.Done
        assertEquals(listOf(Source("https://a.pl", "https://a.pl")), done.sources)
    }

    @Test
    fun readsAChatWithItsMessages() {
        val detail = AiJson.chatDetail(
            JSONObject(Events.detail("c1", true, Events.user(41, "Hej", listOf("img-1")), Events.reply(42, "**Spokojnie.**", "stopped"))),
        )
        assertTrue(detail.chat.running)
        assertEquals(StoredMessage.User(41, "Hej", listOf("img-1")), detail.messages[0])
        val reply = detail.messages[1] as StoredMessage.Assistant
        assertEquals(ReplyStatus.Stopped, reply.status)
        assertEquals("**Spokojnie.**", reply.text)
    }

    @Test
    fun readsTheChatListAndUsage() {
        val chats = AiJson.chats(JSONObject("""{"chats":[{"id":"a","title":"","createdAt":"${Events.TIME}","updatedAt":"${Events.TIME}","running":true}]}"""))
        assertEquals("", chats.single().title)
        assertTrue(chats.single().running)

        val usage = AiJson.usage(JSONObject(Events.usage(remaining = 38)))
        assertEquals(AiUsage(50, 12, 38, Instant.parse("2026-10-26T00:00:00Z").toEpochMilli()), usage)
    }

    @Test
    fun readsErrorCodes() {
        assertEquals("daily_limit", AiJson.errorCode("""{"error":"daily_limit"}"""))
        assertNull(AiJson.errorCode("Unauthorized"))
        assertNull(AiJson.errorCode("<html>Bad gateway</html>"))
    }
}
