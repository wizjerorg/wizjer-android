package org.wizjer.android.data.ai

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import org.wizjer.android.data.api.ApiClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The assistant's chat logic against a fake API: sending, streaming, losing and regaining the
 * connection, failures, stopping, opening chats and deleting them. Its "main thread" is one thread.
 */
class AssistantTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A stuck test fails instead of holding up the whole run. */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = FakeAiServer()
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val api = AiApi(token = { "token-1" }, client = ApiClient(server.url))
    private val assistant by lazy { Assistant(api, AiImages(folder.newFolder("images"), api), main, undoMillis = 300) }

    init {
        server.on("GET", "/ai/usage") { it.json(200, Events.usage(remaining = 40)) }
    }

    @After
    fun tearDown() {
        assistant.close()
        server.close()
        main.close()
    }

    private fun <T> onMain(block: () -> T): T = runBlocking { withContext(main) { block() } }

    private fun await(what: String, seconds: Long = 10, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!onMain(condition)) {
            check(System.nanoTime() < deadline) { "Timed out waiting for: $what" }
            Thread.sleep(20)
        }
    }

    private val items get() = onMain { assistant.current.items.toList() }
    private val lastReply get() = items.last() as ReplyItem

    /** For [await], which already runs on the main thread. */
    private fun replyDone() = assistant.current.items.lastOrNull().let { it is ReplyItem && !it.writing }

    @Test
    fun aNewChatStreamsItsReplyAndKeepsIt() {
        server.on("POST", "/ai/chats") {
            it.stream(
                listOf(
                    Events.start(1, "c1", 41),
                    Events.thinking(2),
                    Events.tool(3, "t1", "check_phone_number", "running", JSONObject().put("number", "605420169")),
                    Events.tool(
                        4, "t1", "check_phone_number", "done", JSONObject().put("number", "605420169"),
                        JSONObject().put("number", "+48605420169").put("found", true).put("verdict", "scam").put("reportCount", 143),
                    ),
                    Events.text(5, "**To oszustwo.** "),
                    Events.text(6, "Nie oddzwaniaj."),
                    Events.done(7, sources = JSONArray().put(JSONObject().put("title", "CERT").put("url", "https://cert.pl"))),
                ),
            )
        }
        assertTrue(onMain { assistant.send("  Czy to oszustwo?  ", "pl") })
        await("the reply to end", condition = ::replyDone)

        val user = items[0] as UserItem
        assertEquals(41L, user.id)
        assertEquals("Czy to oszustwo?", user.text)
        assertNull(user.failure)
        val reply = lastReply
        assertEquals("**To oszustwo.** Nie oddzwaniaj.", reply.text)
        assertEquals(ReplyStatus.Complete, reply.status)
        assertEquals(42L, reply.id)
        assertEquals(ToolStatus.Done, reply.tools.single().status)
        assertEquals(listOf(Source("CERT", "https://cert.pl")), reply.sources)
        assertEquals("c1", onMain { assistant.current.chatId })
        assertFalse(onMain { assistant.current.replying })
        await("the chat to stop running in the list") { assistant.chats.singleOrNull()?.let { it.id == "c1" && !it.running } == true }
        await("today's allowance") { assistant.usage?.remaining == 40 }
        assertEquals("  Czy to oszustwo?  ".trim(), server.requestsTo("POST", "/ai/chats").single().json().getString("text"))
    }

    @Test
    fun aLostConnectionPicksUpWhereItBroke() {
        server.on("POST", "/ai/chats") {
            it.stream(listOf(Events.start(1, "c1", 41), Events.text(2, "Ala "), Events.text(3, "ma "), Events.text(4, "psa")), dropAfter = 3)
        }
        server.on("GET", "/ai/chats/c1/events") { call ->
            if (call.request.header("Last-Event-ID") == "3") call.stream(listOf(Events.text(4, "kota."), Events.done(5)))
            else call.json(500, "{}")
        }
        onMain { assistant.send("Hej", "pl") }
        await("the reply to end", condition = ::replyDone)
        assertEquals("Ala ma kota.", lastReply.text)
        assertFalse(lastReply.reconnecting)
        assertEquals(1, server.requestsTo("GET", "/ai/chats/c1/events").size)
    }

    @Test
    fun aReplyThatFailsInANewChatCanBeSentAgain() {
        server.on("POST", "/ai/chats") { it.stream(listOf(Events.start(1, "c1", 41), Events.thinking(2), Events.error(3, "ai_busy"))) }
        onMain { assistant.send("Pomocy", "pl") }
        await("the failure") { (assistant.current.items.singleOrNull() as? UserItem)?.failure != null }

        val failed = items.single() as UserItem
        assertEquals(AiError("ai_busy"), failed.failure)
        // The server dropped the new chat with its only message, so the app starts afresh.
        assertNull(onMain { assistant.current.chatId })
        assertTrue(onMain { assistant.chats.isEmpty() })

        server.on("POST", "/ai/chats") { it.stream(listOf(Events.start(1, "c2", 51), Events.text(2, "Już."), Events.done(3, messageId = 52))) }
        onMain { assistant.retry(failed, "pl") }
        await("the reply to the retried message", condition = ::replyDone)
        assertEquals(listOf(51L, 52L), items.map { (it as? UserItem)?.id ?: (it as ReplyItem).id })
        assertNull((items[0] as UserItem).failure)
        assertEquals("c2", onMain { assistant.current.chatId })
    }

    @Test
    fun stoppedBeforeAnyWordTheMessageGoesBackToTheComposer() {
        server.on("POST", "/ai/chats") {
            it.stream(listOf(Events.start(1, "c1", 41), Events.done(2, status = "stopped", messageId = null, discarded = true)))
        }
        onMain {
            assistant.attachments += Attachment("a", null, "0b9e5a52-6f0e-4f4e-9a54-1c7e7d2f4a10")
            assistant.send("Sprawdź to", "pl")
        }
        await("the draft") { assistant.draft == "Sprawdź to" }
        assertTrue(items.isEmpty())
        assertNull(onMain { assistant.current.chatId })
        assertTrue(onMain { assistant.chats.isEmpty() })
        assertEquals(listOf("0b9e5a52-6f0e-4f4e-9a54-1c7e7d2f4a10"), onMain { assistant.attachments.map { it.id } })
        assertEquals("0b9e5a52-6f0e-4f4e-9a54-1c7e7d2f4a10", server.requestsTo("POST", "/ai/chats").single().json().getJSONArray("images").getString(0))
    }

    @Test
    fun aRefusedMessageStaysWithTheReason() {
        server.on("POST", "/ai/chats") { it.json(429, """{"error":"daily_limit"}""") }
        onMain { assistant.send("Hej", "pl") }
        await("the failure") { (assistant.current.items.singleOrNull() as? UserItem)?.failure != null }
        assertEquals(AiError("daily_limit"), (items.single() as UserItem).failure)

        server.on("POST", "/ai/chats") { it.plain(401, "Unauthorized") }
        val failed = items.single() as UserItem
        onMain { assistant.retry(failed, "pl") }
        await("the sign-in failure") { (assistant.current.items.singleOrNull() as? UserItem)?.failure?.code == AiError.SIGNED_OUT }
    }

    @Test
    fun sendingAgainReplacesAFailedMessage() {
        server.on("POST", "/ai/chats") { it.json(503, """{"error":"ai_unavailable"}""") }
        onMain { assistant.send("Pierwsza", "pl") }
        await("the failure") { (assistant.current.items.singleOrNull() as? UserItem)?.failure != null }
        server.on("POST", "/ai/chats") { it.stream(listOf(Events.start(1, "c1", 41), Events.done(2))) }
        onMain { assistant.send("Druga", "pl") }
        await("the reply", condition = ::replyDone)
        assertEquals(listOf("Druga"), items.filterIsInstance<UserItem>().map { it.text })
    }

    @Test
    fun openingAChatWithAReplyInProgressFollowsItFromTheStart() {
        server.on("GET", "/ai/chats") { it.json(200, """{"chats":[${Events.chatJson("c1", running = true)}]}""") }
        server.on("GET", "/ai/chats/c1") { it.json(200, Events.detail("c1", true, Events.user(41, "Hej"))) }
        server.on("GET", "/ai/chats/c1/events") { it.stream(listOf(Events.start(1, "c1", 41), Events.text(2, "Cześć!"), Events.done(3))) }

        onMain { assistant.refreshChats() }
        await("the chat list") { assistant.chats.isNotEmpty() }
        onMain { assistant.open(assistant.chats.single()) }
        await("the reply", condition = ::replyDone)

        assertEquals(41L, (items[0] as UserItem).id)
        assertEquals("Cześć!", lastReply.text)
        assertEquals(2, items.size)
        assertNull(server.requestsTo("GET", "/ai/chats/c1/events").single().header("Last-Event-ID"))
        await("the list to show it finished") { !assistant.chats.single().running }
    }

    @Test
    fun whenThereIsNothingToFollowTheChatIsLoadedAgain() {
        server.on("POST", "/ai/chats") { it.stream(listOf(Events.start(1, "c1", 41), Events.text(2, "Go")), dropAfter = 2) }
        server.on("GET", "/ai/chats/c1/events") { it.empty(204) }
        server.on("GET", "/ai/chats/c1") { it.json(200, Events.detail("c1", false, Events.user(41, "Hej"), Events.reply(42, "Gotowe."))) }
        onMain { assistant.send("Hej", "pl") }
        await("the saved reply", condition = ::replyDone)
        assertEquals("Gotowe.", lastReply.text)
        assertEquals(2, items.size)
    }

    @Test
    fun stopPressedBeforeTheChatHasAnIdIsSentOnceItDoes() {
        val stopped = CountDownLatch(1)
        server.on("POST", "/ai/chats/c1/stop") {
            stopped.countDown()
            it.json(202, """{"stopping":true}""")
        }
        server.on("POST", "/ai/chats") { call ->
            call.stream(listOf(Events.start(1, "c1", 41), Events.text(2, "Częś"), Events.done(3, status = "stopped"))) { event ->
                if (event.id == 1) Thread.sleep(300)
                if (event.id == 2) stopped.await(5, TimeUnit.SECONDS)
            }
        }
        onMain {
            assistant.send("Hej", "pl")
            assistant.stop()
            assertTrue(assistant.current.stopping)
        }
        await("the stopped reply", condition = ::replyDone)
        assertEquals(ReplyStatus.Stopped, lastReply.status)
        assertEquals(1, server.requestsTo("POST", "/ai/chats/c1/stop").size)
        assertFalse(onMain { assistant.current.stopping })
    }

    @Test
    fun aDeletedChatComesBackWithUndoAndIsDeletedOtherwise() {
        server.on("GET", "/ai/chats") { it.json(200, """{"chats":[${Events.chatJson("c1", running = false)},${Events.chatJson("c2", running = false)}]}""") }
        server.on("DELETE", "/ai/chats/c[12]") { it.empty(204) }
        onMain { assistant.refreshChats() }
        await("the chat list") { assistant.chats.size == 2 }

        onMain {
            assistant.delete("c1")
            assertEquals(listOf("c2"), assistant.chats.map { it.id })
            assistant.undoDelete("c1")
            assertEquals(listOf("c1", "c2"), assistant.chats.map { it.id })
        }
        Thread.sleep(600)
        assertTrue(server.requestsTo("DELETE", "/ai/chats/c1").isEmpty())

        onMain { assistant.delete("c2") }
        await("the delete request") { server.requestsTo("DELETE", "/ai/chats/c2").isNotEmpty() }
        assertEquals(listOf("c1"), onMain { assistant.chats.map { it.id } })
    }

    @Test
    fun aDeleteThatFailsPutsTheChatBackAndSaysSo() = runBlocking {
        server.on("GET", "/ai/chats") { it.json(200, """{"chats":[${Events.chatJson("c1", running = false)}]}""") }
        server.on("DELETE", "/ai/chats/c1") { it.json(500, "{}") }
        onMain { assistant.refreshChats() }
        await("the chat list") { assistant.chats.size == 1 }
        val notice = async { withTimeout(5_000) { assistant.notices.first() } }
        Thread.sleep(50)
        onMain { assistant.delete("c1") }
        assertEquals(Notice.DeleteFailed, notice.await())
        await("the chat to come back") { assistant.chats.size == 1 }
    }

    @Test
    fun aMessageThatArrivedDespiteALostConnectionIsShownNotRepeated() {
        var loads = 0
        server.on("GET", "/ai/chats") { it.json(200, """{"chats":[${Events.chatJson("c1", running = false)}]}""") }
        server.on("GET", "/ai/chats/c1") { call ->
            loads++
            if (loads == 1) {
                call.json(200, Events.detail("c1", false, Events.user(41, "Pierwsza"), Events.reply(42, "Odp.")))
            } else {
                call.json(200, Events.detail("c1", true, Events.user(41, "Pierwsza"), Events.reply(42, "Odp."), Events.user(43, "Druga")))
            }
        }
        server.on("POST", "/ai/chats/c1/messages") { it.json(409, """{"error":"busy"}""") }
        server.on("GET", "/ai/chats/c1/events") { it.stream(listOf(Events.start(1, "c1", 43), Events.text(2, "OK"), Events.done(3, messageId = 44))) }

        onMain { assistant.refreshChats() }
        await("the chat list") { assistant.chats.size == 1 }
        onMain { assistant.open(assistant.chats.single()) }
        await("the chat") { assistant.current.items.size == 2 }
        onMain { assistant.send("Druga", "pl") }
        await("the reply to the message that arrived") { assistant.current.items.size == 4 && replyDone() }

        assertEquals(listOf(41L, 42L, 43L, 44L), items.map { (it as? UserItem)?.id ?: (it as ReplyItem).id })
        assertTrue(items.filterIsInstance<UserItem>().all { it.failure == null })
    }

    @Test
    fun aChatDeletedElsewhereTurnsIntoANewOneForTheMessage() {
        server.on("GET", "/ai/chats") { it.json(200, """{"chats":[${Events.chatJson("c1", running = false)}]}""") }
        server.on("GET", "/ai/chats/c1") { it.json(200, Events.detail("c1", false, Events.user(41, "a"), Events.reply(42, "b"))) }
        server.on("POST", "/ai/chats/c1/messages") { it.json(404, """{"error":"not_found"}""") }
        onMain { assistant.refreshChats() }
        await("the chat list") { assistant.chats.size == 1 }
        onMain { assistant.open(assistant.chats.single()) }
        await("the chat") { assistant.current.items.size == 2 }
        onMain { assistant.send("c", "pl") }
        await("the failure") { (assistant.current.items.lastOrNull() as? UserItem)?.failure != null }
        assertEquals(1, items.size)
        assertNull(onMain { assistant.current.chatId })
        assertTrue(onMain { assistant.chats.isEmpty() })
    }

    @Test
    fun photosLimitTheNextMessage() {
        onMain {
            repeat(3) { assistant.attachments += Attachment("k$it", null, "id-$it") }
            assertEquals(1, assistant.photoRoom)
            assistant.attachments += Attachment("k3", null, null)
            assertEquals(0, assistant.photoRoom)
            // A photo still uploading holds the message back.
            assertFalse(assistant.canSend("Hej"))
        }
    }
}
