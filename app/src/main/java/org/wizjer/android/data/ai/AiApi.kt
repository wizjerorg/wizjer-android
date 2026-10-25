package org.wizjer.android.data.ai

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.wizjer.android.data.api.ApiClient
import org.wizjer.android.data.api.ApiResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.time.Instant

/**
 * Why a request to the assistant failed. [code] is the API's `error` code (`daily_limit`, `busy`…)
 * or one of the app's own below.
 */
data class AiError(val code: String) {
    companion object {
        /** No internet, or the server couldn't be reached. */
        const val NO_CONNECTION = "no_connection"

        /** The session is no longer valid (401). */
        const val SIGNED_OUT = "signed_out"
        const val NOT_CONFIRMED = "not_confirmed"
        const val NOT_FOUND = "not_found"

        /** An answer the app didn't expect. */
        const val SERVER = "server"
    }
}

sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>
    data class Failed(val error: AiError) : AiResult<Nothing>
}

/** How following a reply stream ended. */
sealed interface StreamEnd {
    /**
     * The stream's final `done` or `error` event arrived (and was handed over). The stream is read on to
     * its close after that, as a new chat's title can still follow.
     */
    data object Finished : StreamEnd

    /** The connection broke before the end; follow again after [lastEventId]. */
    data class Dropped(val lastEventId: Long) : StreamEnd

    /** The server closed the stream without an end, or had nothing to follow: load the chat instead. */
    data object Gone : StreamEnd

    /** The request was turned down before a stream started. */
    data class Refused(val error: AiError) : StreamEnd
}

/** A message to send: its text, ids of images uploaded with [AiApi.upload], and the reply's language. */
data class Outgoing(val text: String, val images: List<String>, val language: String)

/**
 * The assistant's endpoints (`/ai/…`). Every call needs the account's token; [token] reads the
 * current one. Network errors become [AiError.NO_CONNECTION], never exceptions.
 */
internal class AiApi(
    private val token: () -> String?,
    private val client: ApiClient = ApiClient(),
) {

    suspend fun chats(before: Long? = null, limit: Int = PAGE): AiResult<List<AiChat>> {
        val query = "limit=$limit" + (before?.let { "&before=" + URLEncoder.encode(Instant.ofEpochMilli(it).toString(), "UTF-8") } ?: "")
        return json { client.get("/ai/chats?$query", it) }.map(AiJson::chats)
    }

    suspend fun chat(id: String): AiResult<AiChatDetail> = json { client.get("/ai/chats/$id", it) }.map(AiJson::chatDetail)

    suspend fun delete(chatId: String): AiResult<Unit> = call { client.delete("/ai/chats/$chatId", it) }.map { }

    /** Asks the reply in progress to stop; its stream then ends with `stopped`. Nothing running is fine too. */
    suspend fun stop(chatId: String): AiResult<Unit> = when (val result = call { client.post("/ai/chats/$chatId/stop", JSONObject(), it) }) {
        is AiResult.Ok -> AiResult.Ok(Unit)
        is AiResult.Failed -> if (result.error.code == "not_running") AiResult.Ok(Unit) else result
    }

    suspend fun usage(): AiResult<AiUsage> = json { client.get("/ai/usage", it) }.map(AiJson::usage)

    /** Uploads a JPEG, PNG, WebP or GIF (at most 2 MB); returns its id for [Outgoing.images]. */
    suspend fun upload(image: ByteArray, mediaType: String): AiResult<String> =
        json { client.upload("/ai/images", image, mediaType, it) }.map { it.getString("id") }

    /** Removes an image that was never sent. */
    suspend fun deleteImage(id: String): AiResult<Unit> = call { client.delete("/ai/images/$id", it) }.map { }

    /** The image's file, for showing it in the chat. */
    suspend fun image(id: String): AiResult<ByteArray> {
        val token = token() ?: return AiResult.Failed(AiError(AiError.SIGNED_OUT))
        return try {
            client.stream("/ai/images/$id", token = token) { response ->
                val body = response.body
                if (response.isSuccess && body != null) AiResult.Ok(body.readBytes()) else AiResult.Failed(errorOf(response.status, ""))
            }
        } catch (_: IOException) {
            AiResult.Failed(AiError(AiError.NO_CONNECTION))
        }
    }

    /**
     * Sends [message] to the chat [chatId] (or starts a new chat when null) and follows the reply,
     * handing each event to [onEvent] as it arrives.
     */
    suspend fun send(chatId: String?, message: Outgoing, onEvent: suspend (ReplyEvent) -> Unit): StreamEnd {
        val body = JSONObject()
            .put("text", message.text)
            .put("images", JSONArray(message.images))
            .put("language", message.language)
            .toString()
            .toByteArray(Charsets.UTF_8)
        return stream(if (chatId == null) "/ai/chats" else "/ai/chats/$chatId/messages", after = 0, body = body, onEvent = onEvent)
    }

    /** Follows the reply being written in [chatId] (or one that ended moments ago) from after event [after]. */
    suspend fun follow(chatId: String, after: Long, onEvent: suspend (ReplyEvent) -> Unit): StreamEnd =
        stream("/ai/chats/$chatId/events", after = after, body = null, onEvent = onEvent)

    private suspend fun stream(path: String, after: Long, body: ByteArray?, onEvent: suspend (ReplyEvent) -> Unit): StreamEnd {
        val token = token() ?: return StreamEnd.Refused(AiError(AiError.SIGNED_OUT))
        return withContext(Dispatchers.IO) {
            val connection = try {
                client.connect(path, token, readTimeoutMillis = STREAM_TIMEOUT_MILLIS)
            } catch (_: IOException) {
                return@withContext StreamEnd.Refused(AiError(AiError.NO_CONNECTION))
            }
            connection.disconnectOnCancel {
                // Compressed streams can be held back by proxies until a block fills up.
                connection.setRequestProperty("Accept", "text/event-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (after > 0) connection.setRequestProperty("Last-Event-ID", after.toString())
                val status = try {
                    if (body != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setFixedLengthStreamingMode(body.size)
                        connection.outputStream.use { it.write(body) }
                    }
                    connection.responseCode
                } catch (_: IOException) {
                    // A follow-up request can simply be repeated; a new message may or may not have
                    // reached the server, and there's no chat id yet to ask.
                    return@disconnectOnCancel if (body == null) StreamEnd.Dropped(after) else StreamEnd.Refused(AiError(AiError.NO_CONNECTION))
                }
                when {
                    status == HttpURLConnection.HTTP_NO_CONTENT -> StreamEnd.Gone
                    status == HttpURLConnection.HTTP_OK && connection.contentType.orEmpty().startsWith("text/event-stream") ->
                        readEvents(connection, after, onEvent)
                    else -> StreamEnd.Refused(errorOf(status, connection.errorBody()))
                }
            }
        }
    }

    private suspend fun readEvents(connection: HttpURLConnection, after: Long, onEvent: suspend (ReplyEvent) -> Unit): StreamEnd {
        val parser = SseParser()
        var lastId = after
        var ended = false
        try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: return if (ended) StreamEnd.Finished else StreamEnd.Gone
                    val sse = parser.line(line) ?: continue
                    sse.id?.toLongOrNull()?.let { lastId = it }
                    // A malformed event is skipped rather than ending the reply.
                    val event = try {
                        AiJson.event(sse.type, sse.data)
                    } catch (_: JSONException) {
                        null
                    } ?: continue
                    onEvent(event)
                    if (event is ReplyEvent.Done || event is ReplyEvent.Failed) ended = true
                }
            }
        } catch (_: IOException) {
            // After the end, a lost connection only costs the title; the chat list has it next time.
            return if (ended) StreamEnd.Finished else StreamEnd.Dropped(lastId)
        }
    }

    /** A request with the token, turning network failures and error statuses into [AiResult.Failed]. */
    private suspend fun call(request: suspend (String) -> ApiResponse): AiResult<ApiResponse> {
        val token = token() ?: return AiResult.Failed(AiError(AiError.SIGNED_OUT))
        val response = try {
            request(token)
        } catch (_: IOException) {
            return AiResult.Failed(AiError(AiError.NO_CONNECTION))
        }
        return if (response.isSuccess) AiResult.Ok(response) else AiResult.Failed(errorOf(response.status, response.body))
    }

    private suspend fun json(request: suspend (String) -> ApiResponse): AiResult<JSONObject> =
        when (val result = call(request)) {
            is AiResult.Ok -> try {
                AiResult.Ok(result.value.json())
            } catch (_: JSONException) {
                AiResult.Failed(AiError(AiError.SERVER))
            }
            is AiResult.Failed -> result
        }

    private inline fun <T, R> AiResult<T>.map(transform: (T) -> R): AiResult<R> = when (this) {
        is AiResult.Ok -> try {
            AiResult.Ok(transform(value))
        } catch (_: JSONException) {
            AiResult.Failed(AiError(AiError.SERVER))
        }
        is AiResult.Failed -> this
    }

    private fun errorOf(status: Int, body: String): AiError = AiError(
        when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> AiError.SIGNED_OUT
            else -> AiJson.errorCode(body) ?: if (status == HttpURLConnection.HTTP_NOT_FOUND) AiError.NOT_FOUND else AiError.SERVER
        }
    )

    private fun HttpURLConnection.errorBody(): String = try {
        errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    } catch (_: IOException) {
        ""
    }

    /**
     * Runs [block], which blocks on this connection, so that cancelling the coroutine closes the
     * connection (a blocked read can't be interrupted otherwise) and the read ends at once.
     */
    private suspend fun <T> HttpURLConnection.disconnectOnCancel(block: suspend () -> T): T = coroutineScope {
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                disconnect()
            }
        }
        try {
            block()
        } finally {
            watcher.cancel()
        }
    }

    companion object {
        const val PAGE = 30

        /** The server sends a heartbeat every 10 s, so a longer silence means the connection is gone. */
        private const val STREAM_TIMEOUT_MILLIS = 35_000
    }
}
