package org.wizjer.android.data.ai

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** A row of a chat. [key] stays the same while the row changes (e.g. while a reply is written). */
sealed interface ChatItem {
    val key: String
}

/**
 * The user's message. [id] is the server's, null until it's saved. [failure]: it couldn't be sent, or its
 * reply failed and the server dropped it; the app offers to send it again.
 */
@Immutable
data class UserItem(
    override val key: String,
    val id: Long?,
    val text: String,
    val images: List<String>,
    val failure: AiError? = null,
) : ChatItem

/**
 * Wizjer's reply. [status] is null while it's being written. [thinking]: the model is thinking (nothing new
 * to show yet). [reconnecting]: the connection broke and the app is getting it back; [interrupted]: it gave
 * up, and the reply may well have finished on the server.
 */
@Immutable
data class ReplyItem(
    override val key: String,
    val id: Long? = null,
    val text: String = "",
    val tools: List<ToolStep> = emptyList(),
    val sources: List<Source> = emptyList(),
    val status: ReplyStatus? = null,
    val thinking: Boolean = true,
    val reconnecting: Boolean = false,
    val interrupted: Boolean = false,
) : ChatItem {
    val writing: Boolean get() = status == null

    /** This reply with [event] added. */
    fun apply(event: ReplyEvent): ReplyItem = when (event) {
        is ReplyEvent.Start, is ReplyEvent.Failed, is ReplyEvent.Title -> this
        ReplyEvent.Thinking -> copy(thinking = true, reconnecting = false)
        is ReplyEvent.Tool -> copy(thinking = false, reconnecting = false, tools = tools.upsert(event.step))
        is ReplyEvent.Text -> copy(thinking = false, reconnecting = false, text = text + event.delta)
        is ReplyEvent.Done -> copy(
            thinking = false, reconnecting = false, interrupted = false,
            id = event.messageId, status = event.status, sources = event.sources,
        )
    }

    private fun List<ToolStep>.upsert(step: ToolStep): List<ToolStep> {
        val index = indexOfFirst { it.id == step.id }
        return if (index < 0) this + step else toMutableList().also { it[index] = step }
    }
}

/** Whether a chat's messages are on screen yet. */
sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data class Failed(val error: AiError) : LoadState
}

/**
 * One chat: its messages and the reply being written in it. A new chat has no [chatId] until the
 * server starts it. Changed on the main thread only; work runs in the [Assistant]'s scope, so a
 * reply carries on while the user looks at another chat.
 */
@Stable
class Conversation internal constructor(private val assistant: Assistant, chatId: String?) {

    var chatId: String? by mutableStateOf(chatId)
        private set

    val items: SnapshotStateList<ChatItem> = mutableStateListOf()

    var loadState: LoadState by mutableStateOf(if (chatId == null) LoadState.Ready else LoadState.Loading)
        private set

    /** Stop was pressed and the reply hasn't ended yet. */
    var stopping: Boolean by mutableStateOf(false)
        private set

    /** The reply being written here, if any (also one the app lost track of). */
    val reply: ReplyItem? get() = (items.lastOrNull() as? ReplyItem)?.takeIf { it.writing }

    /** A reply is being written and followed: the composer offers Stop instead of Send. */
    val replying: Boolean get() = reply?.interrupted == false

    /** Images already sent in this chat (the server allows [Assistant.IMAGES_PER_CHAT]). */
    val imageCount: Int get() = items.sumOf { if (it is UserItem && it.failure == null) it.images.size else 0 }

    private var job: Job? = null

    /** A finished reply's stream, still open for the new chat's title. */
    private var tail: Job? = null
    private var stopRequested = false

    /** Keys of the user message whose reply is being written, and of that reply. */
    private var liveUser: String? = null
    private var liveReply: String? = null

    /** Sends [user] and follows the reply. */
    internal fun start(user: UserItem, language: String) {
        // The last reply is over but its stream may still be waiting for the chat's title: let it be.
        if (items.none { it is ReplyItem && it.writing }) {
            tail = job?.takeIf { it.isActive }
            job = null
        }
        val sent = user.copy(id = null, failure = null)
        val reply = ReplyItem(newKey())
        items += sent
        items += reply
        liveUser = sent.key
        liveReply = reply.key
        stopRequested = false
        stopping = false
        assistant.spendOne()
        launchJob {
            // Until the title is in, the server counts the chat as busy; it usually is by now.
            tail?.let { previous -> withTimeoutOrNull(TITLE_WAIT_MILLIS) { previous.join() } ?: previous.cancel() }
            tail = null
            when (val end = assistant.api.send(chatId, Outgoing(sent.text, sent.images, language), ::onEvent)) {
                StreamEnd.Finished -> Unit
                is StreamEnd.Dropped -> if (chatId != null) follow(end.lastEventId) else fail(AiError(AiError.NO_CONNECTION), sent.key)
                StreamEnd.Gone -> if (chatId != null) refresh() else fail(AiError(AiError.SERVER), sent.key)
                is StreamEnd.Refused -> refused(end.error, sent)
            }
        }
    }

    /** Loads the chat from the server, and follows its reply if one is being written. */
    internal fun load() = launchJob { refresh() }

    /** Picks up a reply the app lost the connection to. */
    internal fun resume() {
        if (reply?.interrupted == true || loadState is LoadState.Failed) load()
    }

    internal fun stop() {
        if (!replying || stopping) return
        stopping = true
        val id = chatId
        if (id == null) {
            // The server hasn't named the chat yet; stop as soon as it does.
            stopRequested = true
            return
        }
        assistant.scope.launch {
            if (assistant.api.stop(id) is AiResult.Failed) {
                stopping = false
                assistant.notify(Notice.StopFailed)
            }
        }
    }

    /** Takes a failed message out of the chat (to send it again, edit it or move it elsewhere). */
    internal fun take(item: UserItem): Boolean {
        if (replying) return false
        return items.remove(item)
    }

    /** Takes out messages that failed to send (replaced by whatever the user sends next) and returns them. */
    internal fun dropFailed(): List<UserItem> {
        val failed = items.filterIsInstance<UserItem>().filter { it.failure != null }
        items.removeAll(failed)
        return failed
    }

    internal fun cancel() {
        job?.cancel()
        job = null
    }

    /** Runs [block] as this chat's only job. */
    private fun launchJob(block: suspend () -> Unit) {
        job?.cancel()
        job = assistant.scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never leave the chat waiting for a reply nobody follows any more.
                stopping = false
                markInterrupted()
                loadState = if (items.isEmpty()) LoadState.Failed(AiError(AiError.SERVER)) else LoadState.Ready
            }
        }
    }

    private suspend fun onEvent(event: ReplyEvent) = withContext(assistant.main) { apply(event) }

    private fun apply(event: ReplyEvent) {
        when (event) {
            is ReplyEvent.Start -> started(event)
            is ReplyEvent.Failed -> return fail(AiError(event.error), liveUser)
            is ReplyEvent.Title -> return chatId?.let { assistant.retitle(it, event.title) } ?: Unit
            else -> Unit
        }
        updateReply { it.apply(event) }
        if (event is ReplyEvent.Done) finished(event)
    }

    private fun started(event: ReplyEvent.Start) {
        val id = event.chat.id
        chatId = id
        updateUser { it.copy(id = event.userMessageId) }
        assistant.register(this, event.chat)
        if (stopRequested) {
            stopRequested = false
            assistant.scope.launch { assistant.api.stop(id) }
        }
    }

    private fun finished(done: ReplyEvent.Done) {
        stopping = false
        val id = chatId
        when {
            done.discarded -> {
                // Stopped before anything was written: nothing was saved, so the message goes back to the composer.
                val user = items.firstOrNull { it.key == liveUser } as? UserItem
                items.removeAll { it.key == liveUser || it.key == liveReply }
                if (user != null) {
                    if (assistant.current === this) assistant.restoreDraft(user.text, user.images)
                    else items += user.copy(id = null, failure = AiError(STOPPED))
                }
                serverDroppedChat()
            }
            done.status == ReplyStatus.Deleted -> if (id != null) assistant.forgetChat(id)
            else -> if (id != null) assistant.chatFinished(id)
        }
        assistant.refreshUsage()
    }

    /** The reply failed: the server saved nothing, so [userKey]'s message is offered to be sent again. */
    private fun fail(error: AiError, userKey: String?) {
        markFailed(error, userKey)
        serverDroppedChat()
        assistant.refreshUsage()
    }

    private fun markFailed(error: AiError, userKey: String?) {
        stopping = false
        items.removeAll { it.key == liveReply }
        val index = items.indexOfFirst { it.key == userKey }
        if (index >= 0) items[index] = (items[index] as UserItem).copy(id = null, failure = error)
    }

    /** A chat whose only message was dropped is deleted on the server too; this one starts afresh. */
    private fun serverDroppedChat() {
        val id = chatId ?: return
        if (items.none { it.isSaved }) {
            chatId = null
            assistant.forgetChat(id, keep = this)
        } else {
            assistant.chatFinished(id)
        }
    }

    /** The request was turned down before a reply began; the chat on the server is as it was. */
    private suspend fun refused(error: AiError, user: UserItem) {
        // Maybe the message did arrive (the connection broke after sending) or is being answered.
        if ((error.code == AiError.NO_CONNECTION || error.code == BUSY) && arrived(user)) return
        if (error.code == AiError.NOT_FOUND) {
            // The chat was deleted elsewhere: what's left is a new chat with this one message.
            chatId?.let { assistant.forgetChat(it, keep = this) }
            chatId = null
            items.removeAll { it.key != user.key && it.key != liveReply }
        }
        markFailed(error, user.key)
        assistant.refreshUsage()
    }

    /** Whether [user]'s message reached the server after all; if so the chat is shown as the server has it. */
    private suspend fun arrived(user: UserItem): Boolean {
        val id = chatId ?: return false
        val detail = (assistant.api.chat(id) as? AiResult.Ok)?.value ?: return false
        val seen = items.mapNotNullTo(HashSet()) { (it as? UserItem)?.id }
        val last = detail.messages.lastOrNull { it is StoredMessage.User } as? StoredMessage.User ?: return false
        if (last.id in seen || last.text != user.text || last.images != user.images) return false
        show(detail, depth = 0)
        return true
    }

    private suspend fun refresh(depth: Int = 0) {
        val id = chatId ?: return
        if (items.isEmpty()) loadState = LoadState.Loading
        when (val result = assistant.api.chat(id)) {
            is AiResult.Ok -> show(result.value, depth)
            is AiResult.Failed -> when {
                result.error.code == AiError.NOT_FOUND -> assistant.forgetChat(id)
                items.isEmpty() -> loadState = LoadState.Failed(result.error)
                else -> {
                    loadState = LoadState.Ready
                    markInterrupted()
                }
            }
        }
    }

    /** Shows the chat as the server has it, keeping rows' keys, and follows a reply being written. */
    private suspend fun show(detail: AiChatDetail, depth: Int) {
        val keys = items.mapNotNull { item -> item.savedId?.let { it to item.key } }.toMap()
        val stored = detail.messages.map { message ->
            when (message) {
                is StoredMessage.User -> UserItem(keys["u${message.id}"] ?: newKey(), message.id, message.text, message.images)
                is StoredMessage.Assistant -> ReplyItem(
                    key = keys["r${message.id}"] ?: newKey(),
                    id = message.id,
                    text = message.text,
                    tools = message.tools,
                    sources = message.sources,
                    status = message.status,
                    thinking = false,
                )
            }
        }
        items.clear()
        items.addAll(stored)
        loadState = LoadState.Ready
        stopping = false
        assistant.chatUpdated(detail.chat)
        if (detail.chat.running && depth < MAX_RELOADS) {
            val reply = ReplyItem(newKey())
            liveUser = stored.lastOrNull { it is UserItem }?.key
            liveReply = reply.key
            items += reply
            follow(after = 0, depth = depth + 1)
        }
    }

    /** Follows the reply from after event [after], reconnecting with a growing pause when the connection breaks. */
    private suspend fun follow(after: Long, depth: Int = 0) {
        val id = chatId ?: return
        var last = after
        var attempt = 0
        while (true) {
            when (val end = assistant.api.follow(id, last, ::onEvent)) {
                StreamEnd.Finished -> return
                StreamEnd.Gone -> return refresh(depth)
                is StreamEnd.Dropped -> {
                    if (end.lastEventId > last) attempt = 0
                    last = end.lastEventId
                }
                is StreamEnd.Refused -> when (end.error.code) {
                    AiError.NO_CONNECTION, AiError.SERVER, TOO_MANY_REQUESTS -> Unit
                    AiError.NOT_FOUND -> return assistant.forgetChat(id)
                    else -> return refresh(depth)
                }
            }
            attempt++
            if (attempt > RECONNECT_DELAYS.size) return markInterrupted()
            updateReply { it.copy(reconnecting = true) }
            delay(RECONNECT_DELAYS[attempt - 1])
        }
    }

    /** Gave up following the reply (it may well have finished on the server); the user can load it again. */
    private fun markInterrupted() {
        stopping = false
        updateReply { if (it.writing) it.copy(interrupted = true, reconnecting = false, thinking = false) else it }
    }

    private fun updateReply(change: (ReplyItem) -> ReplyItem) {
        val index = items.indexOfFirst { it.key == liveReply }
        if (index >= 0) items[index] = change(items[index] as ReplyItem)
    }

    private fun updateUser(change: (UserItem) -> UserItem) {
        val index = items.indexOfFirst { it.key == liveUser }
        if (index >= 0) items[index] = change(items[index] as UserItem)
    }

    private val ChatItem.isSaved: Boolean
        get() = when (this) {
            is UserItem -> id != null && failure == null
            is ReplyItem -> id != null
        }

    /** "u41" / "r42": the server's id of a saved row, to keep its key when the chat is reloaded. */
    private val ChatItem.savedId: String?
        get() = when (this) {
            is UserItem -> id?.takeIf { failure == null }?.let { "u$it" }
            is ReplyItem -> id?.let { "r$it" }
        }

    companion object {
        /** A message stopped before Wizjer wrote anything, while the user was in another chat. */
        const val STOPPED = "stopped"
        private const val BUSY = "busy"
        private const val TOO_MANY_REQUESTS = "too_many_requests"
        private const val MAX_RELOADS = 3
        private const val TITLE_WAIT_MILLIS = 15_000L

        /** Pauses between reconnection attempts; after the last one the reply is marked interrupted. */
        private val RECONNECT_DELAYS = longArrayOf(1_000, 2_000, 3_000, 5_000, 8_000, 10_000, 15_000)

        internal fun newKey(): String = UUID.randomUUID().toString()
    }
}
