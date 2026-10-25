package org.wizjer.android.data.ai

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wizjer.android.data.account.TokenStore

/** Something to tell the user once, in a short message at the bottom of the screen. */
enum class Notice { DeleteFailed, StopFailed, ChatGone, PhotosPerMessage, PhotosPerChat, PhotoUnreadable }

/** A photo in the composer: scaled down on the phone, then uploaded so the message can carry its id. */
@Stable
class Attachment internal constructor(val key: String, image: PreparedImage?, id: String?) {
    /** The JPEG to send; null while it's being prepared, or for a photo put back from an earlier message. */
    var image: PreparedImage? by mutableStateOf(image)
        internal set

    /** The server's id once uploaded. */
    var id: String? by mutableStateOf(id)
        internal set

    /** Why the upload failed; tapping the photo tries again. */
    var error: AiError? by mutableStateOf(null)
        internal set

    val ready: Boolean get() = id != null

    internal var removed = false
}

/**
 * Wizjer's AI assistant on this phone: the chat list, the open chat (and others whose replies are still
 * being written), photos waiting in the composer and today's allowance. One for the whole app, so a reply
 * keeps coming while the user switches chats or screens. Observable from Compose; changed on the main thread.
 */
class Assistant internal constructor(
    internal val api: AiApi,
    internal val images: AiImages,
    internal val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val undoMillis: Long = UNDO_MILLIS,
) {
    internal val scope = CoroutineScope(SupervisorJob() + main)

    /** The user's chats, newest first. */
    val chats = mutableStateListOf<AiChat>()

    var chatsState: LoadState by mutableStateOf(LoadState.Loading)
        private set

    /** More (older) chats can be loaded with [loadMoreChats]. */
    var canLoadMore: Boolean by mutableStateOf(false)
        private set

    /** The chat on screen; a new, empty one to begin with. */
    var current: Conversation by mutableStateOf(Conversation(this, null))
        private set

    var usage: AiUsage? by mutableStateOf(null)
        private set

    /** Photos for the next message. */
    val attachments = mutableStateListOf<Attachment>()

    /** Text to put back in the composer (a message stopped before its reply began, or one to edit). */
    var draft: String? by mutableStateOf(null)
        private set

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 8)
    val notices: SharedFlow<Notice> = _notices

    private val conversations = HashMap<String, Conversation>()
    private val pendingDeletes = HashMap<String, PendingDelete>()
    private var loadingChats: Job? = null

    private class PendingDelete(val index: Int, val chat: AiChat, val wasOpen: Boolean, val job: Job)

    // Chats

    fun newChat() {
        if (current.chatId == null && current.items.isEmpty()) return
        current = Conversation(this, null)
    }

    fun open(chat: AiChat) {
        val conversation = conversations.getOrPut(chat.id) { Conversation(this, chat.id) }
        current = conversation
        if (conversation.replying) return
        if (conversation.loadState != LoadState.Ready || chat.running || conversation.reply?.interrupted == true) conversation.load()
    }

    /** Loads the newest chats (keeping older ones already loaded). */
    fun refreshChats() {
        if (loadingChats?.isActive == true) return
        loadingChats = scope.launch {
            if (chats.isEmpty()) chatsState = LoadState.Loading
            when (val result = api.chats()) {
                is AiResult.Ok -> {
                    val page = result.value.filter { it.id !in pendingDeletes }
                    val full = result.value.size == AiApi.PAGE
                    val oldest = result.value.lastOrNull()?.updatedAt ?: Long.MAX_VALUE
                    val older = if (full) chats.filter { chat -> chat.updatedAt < oldest && page.none { it.id == chat.id } } else emptyList()
                    chats.clear()
                    chats.addAll(page + older)
                    if (older.isEmpty()) canLoadMore = full
                    chatsState = LoadState.Ready
                }
                is AiResult.Failed -> if (chats.isEmpty()) chatsState = LoadState.Failed(result.error)
            }
        }
    }

    fun loadMoreChats() {
        val before = chats.lastOrNull()?.updatedAt ?: return
        if (!canLoadMore || loadingChats?.isActive == true) return
        loadingChats = scope.launch {
            val result = api.chats(before) as? AiResult.Ok ?: return@launch // tried again when the list is scrolled
            val known = chats.mapTo(HashSet()) { it.id }
            chats.addAll(result.value.filter { it.id !in known && it.id !in pendingDeletes })
            canLoadMore = result.value.size == AiApi.PAGE
        }
    }

    /** Hides the chat at once and deletes it a few seconds later, unless [undoDelete] comes first. */
    fun delete(chatId: String) {
        val index = chats.indexOfFirst { it.id == chatId }
        if (index < 0) return
        val chat = chats.removeAt(index)
        val wasOpen = current.chatId == chatId
        if (wasOpen) current = Conversation(this, null)
        val job = scope.launch {
            delay(undoMillis)
            pendingDeletes.remove(chatId)
            val result = api.delete(chatId)
            if (result is AiResult.Ok || (result as AiResult.Failed).error.code == AiError.NOT_FOUND) {
                // A reply still being written there ends with "deleted".
                conversations.remove(chatId)
            } else {
                restore(index, chat)
                notify(Notice.DeleteFailed)
            }
        }
        pendingDeletes[chatId] = PendingDelete(index, chat, wasOpen, job)
    }

    fun undoDelete(chatId: String) {
        val pending = pendingDeletes.remove(chatId) ?: return
        pending.job.cancel()
        restore(pending.index, pending.chat)
        if (pending.wasOpen && current.chatId == null && current.items.isEmpty()) open(pending.chat)
    }

    // Messages

    /** Whether the composer's [text] and photos can go to the open chat now. */
    fun canSend(text: String): Boolean =
        (text.isNotBlank() || attachments.isNotEmpty()) &&
            attachments.all { it.ready } &&
            text.length <= MAX_TEXT &&
            current.loadState == LoadState.Ready &&
            !current.replying &&
            !limitReached

    /** Today's messages are used up (until [AiUsage.resetsAt]). */
    val limitReached: Boolean
        get() = usage?.let { it.remaining <= 0 && System.currentTimeMillis() < it.resetsAt } == true

    /** Sends the composer's [text] and photos to the open chat; the reply is written in [language] (pl, en or uk). */
    fun send(text: String, language: String): Boolean {
        val content = text.trim()
        if (!canSend(content)) return false
        val images = attachments.mapNotNull { it.id }
        attachments.clear()
        discardFailed()
        current.start(UserItem(Conversation.newKey(), null, content, images), language)
        return true
    }

    /** Starts a new chat with [text] (e.g. a flagged message to analyse). */
    fun ask(text: String, language: String) {
        current = Conversation(this, null)
        current.start(UserItem(Conversation.newKey(), null, text.trim(), emptyList()), language)
    }

    fun retry(item: UserItem, language: String) {
        if (current.take(item)) current.start(item, language)
    }

    /** Puts a failed message back in the composer. */
    fun edit(item: UserItem) {
        if (!current.take(item)) return
        val images = if (item.failure?.code == INVALID_IMAGES) emptyList() else item.images
        restoreDraft(item.text, images)
    }

    /** Sends a failed message in a new chat (this one is full). */
    fun moveToNewChat(item: UserItem, language: String) {
        if (!current.take(item)) return
        current = Conversation(this, null)
        current.start(item, language)
    }

    fun stop() = current.stop()

    /** Loads the open chat again after it failed to load or lost its reply. */
    fun reload() = current.load()

    fun draftTaken() {
        draft = null
    }

    // Photos

    /** How many more photos the next message can take (per message and per chat). */
    val photoRoom: Int
        get() = minOf(IMAGES_PER_MESSAGE - attachments.size, IMAGES_PER_CHAT - current.imageCount - attachments.size).coerceAtLeast(0)

    fun attach(context: Context, uris: List<Uri>) {
        val room = photoRoom
        if (uris.size > room) {
            val chatRoom = IMAGES_PER_CHAT - current.imageCount - attachments.size
            notify(if (chatRoom < IMAGES_PER_MESSAGE - attachments.size) Notice.PhotosPerChat else Notice.PhotosPerMessage)
        }
        val app = context.applicationContext
        uris.take(room).forEach { uri ->
            val attachment = Attachment(Conversation.newKey(), null, null)
            attachments += attachment
            scope.launch {
                val image = withContext(Dispatchers.IO) { ImagePrep.prepare(app, uri) }
                if (attachment.removed) return@launch
                if (image == null) {
                    attachments.remove(attachment)
                    notify(Notice.PhotoUnreadable)
                } else {
                    attachment.image = image
                    upload(attachment)
                }
            }
        }
    }

    fun retryUpload(attachment: Attachment) {
        if (attachment.error == null || attachment.image == null) return
        attachment.error = null
        scope.launch { upload(attachment) }
    }

    fun remove(attachment: Attachment) {
        attachment.removed = true
        attachments.remove(attachment)
        attachment.id?.let(::deleteUnsent)
    }

    private suspend fun upload(attachment: Attachment) {
        val image = attachment.image ?: return
        when (val result = api.upload(image.bytes, image.mediaType)) {
            is AiResult.Ok -> {
                images.put(result.value, image.bytes)
                if (attachment.removed) deleteUnsent(result.value) else attachment.id = result.value
            }
            is AiResult.Failed -> attachment.error = result.error
        }
    }

    // Allowance

    fun refreshUsage() {
        scope.launch { (api.usage() as? AiResult.Ok)?.let { usage = it.value } }
    }

    /** Counts a message straight away; [refreshUsage] corrects it when the reply ends. */
    internal fun spendOne() {
        usage = usage?.let { it.copy(used = it.used + 1, remaining = (it.remaining - 1).coerceAtLeast(0)) }
    }

    /** Back in the app: catch up on what changed and pick up replies whose connection was lost. */
    fun onForeground() {
        refreshChats()
        refreshUsage()
        conversations.values.forEach { it.resume() }
        if (current.chatId == null) current.resume()
    }

    // Called by conversations

    internal fun register(conversation: Conversation, chat: AiChat) {
        conversations[chat.id] = conversation
        if (chat.id in pendingDeletes) return
        chats.removeAll { it.id == chat.id }
        chats.add(0, chat.copy(running = true))
        if (chatsState is LoadState.Failed) chatsState = LoadState.Ready
    }

    internal fun chatUpdated(chat: AiChat) {
        val index = chats.indexOfFirst { it.id == chat.id }
        if (index >= 0) chats[index] = chat
    }

    internal fun chatFinished(chatId: String) {
        val index = chats.indexOfFirst { it.id == chatId }
        if (index >= 0 && chats[index].running) chats[index] = chats[index].copy(running = false)
    }

    /** The chat no longer exists on the server; [keep] is a conversation that carries on as a new chat. */
    internal fun forgetChat(chatId: String, keep: Conversation? = null) {
        val conversation = conversations.remove(chatId)
        chats.removeAll { it.id == chatId }
        if (conversation != null && conversation === current && conversation !== keep) {
            current = Conversation(this, null)
            notify(Notice.ChatGone)
        }
    }

    internal fun restoreDraft(text: String, images: List<String>) {
        draft = text
        images.take(IMAGES_PER_MESSAGE - attachments.size).forEach { attachments += Attachment(Conversation.newKey(), null, it) }
    }

    internal fun notify(notice: Notice) {
        _notices.tryEmit(notice)
    }

    /** A failed message left in the open chat is replaced by the new one; its photos are freed on the server. */
    private fun discardFailed() {
        current.dropFailed().forEach { item -> item.images.forEach(::deleteUnsent) }
    }

    private fun deleteUnsent(imageId: String) {
        scope.launch { api.deleteImage(imageId) }
    }

    private fun restore(index: Int, chat: AiChat) {
        if (chats.none { it.id == chat.id }) chats.add(index.coerceAtMost(chats.size), chat)
    }

    internal fun close() {
        scope.cancel()
    }

    companion object {
        const val MAX_TEXT = 4000
        const val IMAGES_PER_MESSAGE = 4
        const val IMAGES_PER_CHAT = 10

        /** How long a deleted chat can be brought back (a little longer than the "Undo" message shows). */
        const val UNDO_MILLIS = 5_000L

        private const val INVALID_IMAGES = "invalid_images"

        @Volatile
        private var instance: Assistant? = null

        fun get(context: Context): Assistant = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        /** Forgets everything about the signed-out account: chats in memory, cached photos and camera shots. */
        fun signOut(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = null
            }
            AiImages.clear(context)
            CameraPhotos.clear(context)
        }

        private fun create(context: Context): Assistant {
            val tokens = TokenStore(context)
            val api = AiApi(token = { tokens.token })
            return Assistant(api, AiImages(AiImages.dir(context), api))
        }
    }
}
