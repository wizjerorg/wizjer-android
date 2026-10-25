package org.wizjer.android.data.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * A stand-in for the Wizjer API's `/ai` endpoints on localhost, for tests: routes answer with
 * JSON or with a reply stream written the way the server writes it (`id:`, `event:`, `data:`,
 * heartbeats), and every request is recorded.
 */
class FakeAiServer : Closeable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val routes = CopyOnWriteArrayList<Route>()
    val requests = CopyOnWriteArrayList<Recorded>()

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    class Recorded(val method: String, val path: String, val query: String?, val headers: Map<String, String?>, val body: ByteArray) {
        val text: String get() = String(body, Charsets.UTF_8)
        fun json() = JSONObject(text)
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private class Route(val method: String, val path: Regex, val handle: (Call) -> Unit)

    init {
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            val recorded = Recorded(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery?.let { URLDecoder.decode(it, "UTF-8") },
                headers = exchange.requestHeaders.entries.associate { (key, values) -> key.lowercase() to values.firstOrNull() },
                body = exchange.requestBody.readBytes(),
            )
            requests += recorded
            val route = routes.lastOrNull { it.method == recorded.method && it.path.matches(recorded.path) }
            try {
                if (route == null) Call(exchange, recorded).json(404, """{"error":"not_found"}""") else route.handle(Call(exchange, recorded))
            } catch (_: IOException) {
                // The client went away, or a test dropped the connection on purpose.
            } finally {
                try {
                    exchange.close()
                } catch (_: IOException) {
                    // A stream cut short on purpose: the connection is closed, which is the point.
                }
            }
        }
        server.start()
    }

    /** Answers [method] requests to paths matching [path]; a later route for the same path wins. */
    fun on(method: String, path: String, handle: (Call) -> Unit) {
        routes += Route(method, Regex(path), handle)
    }

    fun requestsTo(method: String, path: String) = requests.filter { it.method == method && Regex(path).matches(it.path) }

    override fun close() {
        server.stop(0)
    }

    class Call(private val exchange: HttpExchange, val request: Recorded) {

        fun json(status: Int, body: String) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }

        fun plain(status: Int, body: String) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }

        fun bytes(status: Int, body: ByteArray, contentType: String) {
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.write(body)
        }

        /** No body (204 and the like). */
        fun empty(status: Int) {
            exchange.sendResponseHeaders(status, -1)
        }

        /**
         * A reply stream. With [dropAfter], the connection breaks after that many events (the client
         * sees an error, not an end); [between] runs before each event, e.g. to wait or pause.
         */
        fun stream(events: List<Ev>, dropAfter: Int? = null, between: (Ev) -> Unit = {}) {
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "no-store, no-transform")
            // A declared length that is never reached makes closing early a broken connection.
            exchange.sendResponseHeaders(200, if (dropAfter != null) 10_000_000L else 0L)
            val out = exchange.responseBody
            fun write(text: String) {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            write("retry: 3000\n\n")
            write(": ping\n\n")
            events.forEachIndexed { index, event ->
                if (dropAfter != null && index >= dropAfter) return
                between(event)
                write("id: ${event.id}\nevent: ${event.type}\ndata: ${event.data}\n\n")
            }
        }
    }
}

/** One event of a reply stream. */
data class Ev(val id: Int, val type: String, val data: String)

/** Builders for the events the server sends. */
object Events {
    const val TIME = "2026-10-25T18:42:10.311Z"

    fun chatJson(id: String, title: String = "Czy to oszustwo?", running: Boolean? = null): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("createdAt", TIME).put("updatedAt", TIME)
        .also { if (running != null) it.put("running", running) }

    fun start(id: Int, chatId: String, userMessageId: Long, title: String = "Czy to oszustwo?") =
        Ev(id, "start", JSONObject().put("chat", chatJson(chatId, title)).put("userMessageId", userMessageId).toString())

    fun thinking(id: Int) = Ev(id, "thinking", "{}")

    fun text(id: Int, delta: String) = Ev(id, "text", JSONObject().put("delta", delta).toString())

    fun tool(id: Int, toolId: String, name: String, status: String, input: JSONObject, summary: JSONObject? = null) = Ev(
        id, "tool",
        JSONObject().put("id", toolId).put("name", name).put("input", input).put("status", status)
            .put("summary", summary ?: JSONObject.NULL).toString(),
    )

    fun done(id: Int, status: String = "complete", messageId: Long? = 42, sources: JSONArray = JSONArray(), discarded: Boolean = false) = Ev(
        id, "done",
        JSONObject().put("status", status).put("messageId", messageId ?: JSONObject.NULL).put("sources", sources)
            .also { if (discarded) it.put("discarded", true) }.toString(),
    )

    fun error(id: Int, code: String) = Ev(id, "error", JSONObject().put("error", code).toString())

    fun user(id: Long, text: String, images: List<String> = emptyList()) = JSONObject()
        .put("id", id).put("role", "user").put("text", text).put("images", JSONArray(images)).put("language", "pl").put("createdAt", TIME)

    fun reply(id: Long, text: String, status: String = "complete") = JSONObject()
        .put("id", id).put("role", "assistant").put("text", text).put("status", status)
        .put("tools", JSONArray()).put("sources", JSONArray()).put("createdAt", TIME)

    fun detail(chatId: String, running: Boolean, vararg messages: JSONObject): String =
        chatJson(chatId, running = running).put("messages", JSONArray(messages.toList())).toString()

    fun usage(remaining: Int, limit: Int = 50) =
        """{"messages":{"limit":$limit,"used":${limit - remaining},"remaining":$remaining,"resetsAt":"2026-10-26T00:00:00.000Z"}}"""
}
