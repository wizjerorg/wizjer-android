package org.wizjer.android.data.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A conversation with the assistant, as the chat list shows it. [title] is empty for one that began with only images. */
data class AiChat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val running: Boolean = false,
)

enum class ToolStatus { Running, Done, Error }

/**
 * A step the assistant took while writing (checking a number or a link, searching the web…).
 * [input] and [summary] are the API's JSON as maps; [summary] is null while running and for a failed step.
 */
data class ToolStep(
    val id: String,
    val name: String,
    val status: ToolStatus,
    val input: Map<String, Any?>,
    val summary: Map<String, Any?>?,
)

/** A web page a reply cites. */
data class Source(val title: String, val url: String)

/** How a reply ended. */
enum class ReplyStatus { Complete, Stopped, MaxTokens, Incomplete, Deleted }

/** A message as the API stores it. */
sealed interface StoredMessage {
    val id: Long

    data class User(override val id: Long, val text: String, val images: List<String>) : StoredMessage

    data class Assistant(
        override val id: Long,
        val text: String,
        val status: ReplyStatus,
        val tools: List<ToolStep>,
        val sources: List<Source>,
    ) : StoredMessage
}

/** A chat with its messages; [running] means a reply is being written right now. */
data class AiChatDetail(val chat: AiChat, val messages: List<StoredMessage>)

/** Today's allowance: [resetsAt] is when [remaining] goes back to [limit]. */
data class AiUsage(val limit: Int, val used: Int, val remaining: Int, val resetsAt: Long)

/** One event of a reply stream. */
sealed interface ReplyEvent {
    /** Always first: the chat (a new chat's id comes from here) and the id of the user's message. */
    data class Start(val chat: AiChat, val userMessageId: Long) : ReplyEvent

    data object Thinking : ReplyEvent

    /** A step started or ended; steps keep their id. */
    data class Tool(val step: ToolStep) : ReplyEvent

    /** More of the reply's text, to append exactly as it comes. */
    data class Text(val delta: String) : ReplyEvent

    /**
     * The reply ended and was saved as [messageId]. [discarded]: it was stopped before anything was
     * written and nothing was saved, not even the user's message.
     */
    data class Done(val status: ReplyStatus, val messageId: Long?, val sources: List<Source>, val discarded: Boolean) : ReplyEvent

    /** The reply failed and nothing was saved. */
    data class Failed(val error: String) : ReplyEvent
}

/** Reads the API's JSON. Unknown event types and fields are skipped, so newer servers don't break the app. */
internal object AiJson {

    fun chat(json: JSONObject) = AiChat(
        id = json.getString("id"),
        title = json.optString("title"),
        createdAt = time(json.optString("createdAt")),
        updatedAt = time(json.optString("updatedAt")),
        running = json.optBoolean("running"),
    )

    fun chats(json: JSONObject): List<AiChat> = json.getJSONArray("chats").objects().map(::chat)

    fun chatDetail(json: JSONObject) = AiChatDetail(
        chat = chat(json),
        messages = json.optJSONArray("messages")?.objects()?.mapNotNull(::message).orEmpty(),
    )

    fun usage(json: JSONObject): AiUsage {
        val messages = json.getJSONObject("messages")
        return AiUsage(
            limit = messages.getInt("limit"),
            used = messages.getInt("used"),
            remaining = messages.getInt("remaining"),
            resetsAt = time(messages.optString("resetsAt")),
        )
    }

    /** The event of a stream's `event:` type with its `data:`; null for an event type this app doesn't know. */
    fun event(type: String, data: String): ReplyEvent? {
        val json = JSONObject(data.ifBlank { "{}" })
        return when (type) {
            "start" -> ReplyEvent.Start(chat(json.getJSONObject("chat")), json.getLong("userMessageId"))
            "thinking" -> ReplyEvent.Thinking
            "tool" -> ReplyEvent.Tool(tool(json))
            "text" -> ReplyEvent.Text(json.getString("delta"))
            "done" -> ReplyEvent.Done(
                status = status(json.optString("status")),
                messageId = if (json.isNull("messageId")) null else json.optLong("messageId"),
                sources = sources(json.optJSONArray("sources")),
                discarded = json.optBoolean("discarded"),
            )
            "error" -> ReplyEvent.Failed(json.optString("error").ifEmpty { "ai_error" })
            else -> null
        }
    }

    /** The `error` code of an error body, if it has one. */
    fun errorCode(body: String): String? = runCatching { JSONObject(body).optString("error").ifEmpty { null } }.getOrNull()

    private fun message(json: JSONObject): StoredMessage? = when (json.optString("role")) {
        "user" -> StoredMessage.User(
            id = json.getLong("id"),
            text = json.optString("text"),
            images = json.optJSONArray("images")?.strings().orEmpty(),
        )
        "assistant" -> StoredMessage.Assistant(
            id = json.getLong("id"),
            text = json.optString("text"),
            status = status(json.optString("status")),
            tools = json.optJSONArray("tools")?.objects()?.map(::tool).orEmpty(),
            sources = sources(json.optJSONArray("sources")),
        )
        else -> null
    }

    private fun tool(json: JSONObject) = ToolStep(
        id = json.getString("id"),
        name = json.optString("name"),
        status = when (json.optString("status")) {
            "running" -> ToolStatus.Running
            "done" -> ToolStatus.Done
            else -> ToolStatus.Error
        },
        input = json.optJSONObject("input")?.toMap().orEmpty(),
        summary = json.optJSONObject("summary")?.toMap(),
    )

    private fun sources(array: JSONArray?): List<Source> = array?.objects()
        ?.mapNotNull { source -> source.optString("url").takeIf { it.isNotEmpty() }?.let { Source(source.optString("title").ifEmpty { it }, it) } }
        .orEmpty()

    private fun status(value: String) = when (value) {
        "stopped" -> ReplyStatus.Stopped
        "max_tokens" -> ReplyStatus.MaxTokens
        "incomplete" -> ReplyStatus.Incomplete
        "deleted" -> ReplyStatus.Deleted
        else -> ReplyStatus.Complete
    }

    private fun time(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).ifEmpty { null } }

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { plain(opt(it)) }

    private fun plain(value: Any?): Any? = when (value) {
        JSONObject.NULL, null -> null
        is JSONObject -> value.toMap()
        is JSONArray -> (0 until value.length()).map { plain(value.opt(it)) }
        else -> value
    }
}
