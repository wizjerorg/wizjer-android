package org.wizjer.android.data.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wizjer.android.data.api.ApiClient
import java.net.ServerSocket

class AiApiTest {
    private val server = FakeAiServer()
    private val api = AiApi(token = { "token-1" }, client = ApiClient(server.url))

    @After
    fun stop() = server.close()

    @Test
    fun sendStreamsEveryEventInOrderAndEndsWithTheLast() = runBlocking {
        server.on("POST", "/ai/chats") {
            it.stream(listOf(Events.start(1, "c1", 41), Events.thinking(2), Events.text(3, "Hej"), Events.done(4)))
        }
        val events = mutableListOf<ReplyEvent>()
        val end = api.send(null, Outgoing("Czy to oszustwo?", listOf("img-1"), "pl")) { events += it }

        assertEquals(StreamEnd.Finished, end)
        assertEquals(listOf("Start", "Thinking", "Text", "Done"), events.map { it::class.simpleName })
        val request = server.requests.single()
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals("text/event-stream", request.header("Accept"))
        val body = request.json()
        assertEquals("Czy to oszustwo?", body.getString("text"))
        assertEquals("img-1", body.getJSONArray("images").getString(0))
        assertEquals("pl", body.getString("language"))
    }

    @Test
    fun aTitleAfterTheEndIsStillRead() = runBlocking {
        server.on("POST", "/ai/chats") {
            it.stream(listOf(Events.start(1, "c1", 41), Events.text(2, "Hej"), Events.done(3), Events.title(4, "Paczka InPost"))) { event ->
                if (event.id == 4) Thread.sleep(200)
            }
        }
        val events = mutableListOf<ReplyEvent>()
        assertEquals(StreamEnd.Finished, api.send(null, Outgoing("x", emptyList(), "pl")) { events += it })
        assertEquals(ReplyEvent.Title("Paczka InPost"), events.last())
    }

    @Test
    fun aConnectionLostAfterTheEndStillCountsAsTheEnd() = runBlocking {
        server.on("POST", "/ai/chats") { it.stream(listOf(Events.start(1, "c1", 41), Events.done(2), Events.title(3, "x")), dropAfter = 2) }
        assertEquals(StreamEnd.Finished, api.send(null, Outgoing("x", emptyList(), "pl")) {})
    }

    @Test
    fun aMessageToAnExistingChatGoesToItsMessages() = runBlocking {
        server.on("POST", "/ai/chats/c1/messages") { it.stream(listOf(Events.start(1, "c1", 43), Events.done(2, messageId = 44))) }
        assertEquals(StreamEnd.Finished, api.send("c1", Outgoing("Dalej", emptyList(), "en")) {})
    }

    @Test
    fun errorsBeforeTheStreamComeBackAsTheirCodes() = runBlocking {
        val cases = mapOf(
            409 to """{"error":"busy"}""" to "busy",
            429 to """{"error":"daily_limit"}""" to "daily_limit",
            403 to """{"error":"not_confirmed"}""" to AiError.NOT_CONFIRMED,
            503 to """{"error":"ai_unavailable"}""" to "ai_unavailable",
            502 to "<html>Bad gateway</html>" to AiError.SERVER,
        )
        for ((response, code) in cases) {
            server.on("POST", "/ai/chats") { it.json(response.first, response.second) }
            assertEquals(StreamEnd.Refused(AiError(code)), api.send(null, Outgoing("x", emptyList(), "pl")) {})
        }
        server.on("POST", "/ai/chats") { it.plain(401, "Unauthorized") }
        assertEquals(StreamEnd.Refused(AiError(AiError.SIGNED_OUT)), api.send(null, Outgoing("x", emptyList(), "pl")) {})
    }

    @Test
    fun noServerMeansNoConnection() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        val offline = AiApi(token = { "t" }, client = ApiClient("http://127.0.0.1:$closedPort"))
        assertEquals(StreamEnd.Refused(AiError(AiError.NO_CONNECTION)), offline.send(null, Outgoing("x", emptyList(), "pl")) {})
        assertEquals(AiResult.Failed(AiError(AiError.NO_CONNECTION)), offline.chats())
        assertEquals(StreamEnd.Dropped(5), offline.follow("c1", 5) {})
    }

    @Test
    fun withoutATokenNothingIsSent() = runBlocking {
        val signedOut = AiApi(token = { null }, client = ApiClient(server.url))
        assertEquals(StreamEnd.Refused(AiError(AiError.SIGNED_OUT)), signedOut.send(null, Outgoing("x", emptyList(), "pl")) {})
        assertEquals(AiResult.Failed(AiError(AiError.SIGNED_OUT)), signedOut.usage())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun aBrokenConnectionReportsTheLastEventReceived() = runBlocking {
        server.on("POST", "/ai/chats") {
            it.stream(listOf(Events.start(1, "c1", 41), Events.text(2, "Ala "), Events.text(3, "ma "), Events.text(4, "kota")), dropAfter = 3)
        }
        val texts = mutableListOf<String>()
        val end = api.send(null, Outgoing("x", emptyList(), "pl")) { if (it is ReplyEvent.Text) texts += it.delta }
        assertEquals(StreamEnd.Dropped(3), end)
        assertEquals(listOf("Ala ", "ma "), texts)
    }

    @Test
    fun aStreamClosedWithoutAnEndIsGone() = runBlocking {
        server.on("GET", "/ai/chats/c1/events") { it.stream(listOf(Events.text(4, "x"))) }
        assertEquals(StreamEnd.Gone, api.follow("c1", 3) {})
    }

    @Test
    fun followingAsksForTheEventsAfterTheLastOne() = runBlocking {
        server.on("GET", "/ai/chats/c1/events") { it.stream(listOf(Events.text(8, "dalej"), Events.done(9))) }
        val texts = mutableListOf<String>()
        assertEquals(StreamEnd.Finished, api.follow("c1", 7) { if (it is ReplyEvent.Text) texts += it.delta })
        assertEquals(listOf("dalej"), texts)
        assertEquals("7", server.requests.single().header("Last-Event-ID"))

        api.follow("c1", 0) {}
        assertNull("from the start, no id is sent", server.requests.last().header("Last-Event-ID"))
    }

    @Test
    fun nothingToFollowIsGone() = runBlocking {
        server.on("GET", "/ai/chats/c1/events") { it.empty(204) }
        assertEquals(StreamEnd.Gone, api.follow("c1", 3) {})
        server.on("GET", "/ai/chats/c1/events") { it.json(404, """{"error":"not_found"}""") }
        assertEquals(StreamEnd.Refused(AiError(AiError.NOT_FOUND)), api.follow("c1", 3) {})
    }

    @Test
    fun brokenOrUnknownEventsAreSkipped() = runBlocking {
        server.on("GET", "/ai/chats/c1/events") {
            it.stream(listOf(Ev(1, "text", "{not json"), Ev(2, "sparkle", "{}"), Events.text(3, "ok"), Events.done(4)))
        }
        val events = mutableListOf<ReplyEvent>()
        assertEquals(StreamEnd.Finished, api.follow("c1", 0) { events += it })
        assertEquals(listOf(ReplyEvent.Text("ok")), events.filterIsInstance<ReplyEvent.Text>())
    }

    @Test
    fun chatsPageByTheLastChatsTime() = runBlocking {
        server.on("GET", "/ai/chats") { it.json(200, """{"chats":[${Events.chatJson("c1", running = false)}]}""") }
        val first = api.chats() as AiResult.Ok
        assertEquals("c1", first.value.single().id)
        api.chats(before = first.value.single().updatedAt)
        assertEquals("limit=30&before=${Events.TIME}", server.requests.last().query)
    }

    @Test
    fun stopIsFineWhenNothingRuns() = runBlocking {
        server.on("POST", "/ai/chats/c1/stop") { it.json(409, """{"error":"not_running"}""") }
        assertEquals(AiResult.Ok(Unit), api.stop("c1"))
        server.on("POST", "/ai/chats/c1/stop") { it.json(202, """{"stopping":true}""") }
        assertEquals(AiResult.Ok(Unit), api.stop("c1"))
    }

    @Test
    fun imagesGoUpRawAndComeBackAsTheyWere() = runBlocking {
        val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
        server.on("POST", "/ai/images") { it.json(201, """{"id":"3f0c","mediaType":"image/jpeg","size":6}""") }
        server.on("GET", "/ai/images/3f0c") { it.bytes(200, photo, "image/jpeg") }
        server.on("DELETE", "/ai/images/3f0c") { it.empty(204) }

        assertEquals(AiResult.Ok("3f0c"), api.upload(photo, "image/jpeg"))
        val upload = server.requests.single()
        assertEquals("image/jpeg", upload.header("Content-Type"))
        assertArrayEquals(photo, upload.body)

        assertArrayEquals(photo, (api.image("3f0c") as AiResult.Ok).value)
        assertEquals("Bearer token-1", server.requests.last().header("Authorization"))
        assertEquals(AiResult.Ok(Unit), api.deleteImage("3f0c"))

        server.on("POST", "/ai/images") { it.json(429, """{"error":"too_many_images"}""") }
        assertEquals(AiResult.Failed(AiError("too_many_images")), api.upload(photo, "image/jpeg"))
    }

    @Test
    fun usageIsRead() = runBlocking {
        server.on("GET", "/ai/usage") { it.json(200, Events.usage(remaining = 3)) }
        assertEquals(3, (api.usage() as AiResult.Ok).value.remaining)
    }

    @Test
    fun aChatsMessagesAreRead() = runBlocking {
        server.on("GET", "/ai/chats/c1") { it.json(200, Events.detail("c1", false, Events.user(1, "a"), Events.reply(2, "b"))) }
        val detail = (api.chat("c1") as AiResult.Ok).value
        assertEquals(2, detail.messages.size)
        server.on("GET", "/ai/chats/c1") { it.json(200, JSONObject().put("oops", true).toString()) }
        assertEquals(AiResult.Failed(AiError(AiError.SERVER)), api.chat("c1"))
    }
}
