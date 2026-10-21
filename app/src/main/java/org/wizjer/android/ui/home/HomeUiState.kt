package org.wizjer.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.res.stringResource
import org.wizjer.android.R
import java.util.UUID

enum class Risk { Safe, Suspicious, Dangerous }

data class VerdictUi(val risk: Risk, val reasons: List<String>)

data class MessageUi(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val verdict: VerdictUi? = null,
)

data class ChatUi(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<MessageUi>,
)

/**
 * UI state of the home screen: the chat list and the open chat. In memory only;
 * the assistant, storage and sync will plug in here later.
 */
@Stable
class HomeUiState(initialChats: List<ChatUi>) {
    val chats = initialChats.toMutableStateList()
    var currentId by mutableStateOf<String?>(null)
        private set

    val current: ChatUi? get() = chats.firstOrNull { it.id == currentId }

    fun newChat() {
        currentId = null
    }

    fun open(id: String) {
        currentId = id
    }

    /** Removes a chat and returns what [restore] needs to bring it back (for "Undo"). */
    fun delete(id: String): Pair<Int, ChatUi>? {
        val index = chats.indexOfFirst { it.id == id }
        if (index < 0) return null
        val chat = chats.removeAt(index)
        if (currentId == id) currentId = null
        return index to chat
    }

    fun restore(removed: Pair<Int, ChatUi>) {
        val (index, chat) = removed
        chats.add(index.coerceAtMost(chats.size), chat)
    }

    /** Adds the user's message to the open chat (or starts one). Replies come with the assistant. */
    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val message = MessageUi(UUID.randomUUID().toString(), fromUser = true, text = content)
        val now = System.currentTimeMillis()
        val chat = current?.let { it.copy(messages = it.messages + message, updatedAt = now) }
            ?: ChatUi(UUID.randomUUID().toString(), content.lineSequence().first().take(48), now, listOf(message))
        chats.removeAll { it.id == chat.id }
        chats.add(0, chat)
        currentId = chat.id
    }
}

/** Two example conversations so every part of the UI can be seen before the assistant exists. */
@Composable
fun rememberHomeUiState(): HomeUiState {
    val parcelTitle = stringResource(R.string.sample_parcel_title)
    val parcelUser = stringResource(R.string.sample_parcel_user)
    val parcelReply = stringResource(R.string.sample_parcel_reply)
    val reasons = listOf(
        stringResource(R.string.sample_parcel_reason_link),
        stringResource(R.string.sample_parcel_reason_payment),
        stringResource(R.string.sample_parcel_reason_urgency),
    )
    val bankTitle = stringResource(R.string.sample_bank_title)
    val bankUser = stringResource(R.string.sample_bank_user)
    val bankReply = stringResource(R.string.sample_bank_reply)

    return remember {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        HomeUiState(
            listOf(
                ChatUi(
                    id = "sample-parcel",
                    title = parcelTitle,
                    updatedAt = now - 2 * 60 * 60 * 1000L,
                    messages = listOf(
                        MessageUi("sample-parcel-1", fromUser = true, text = parcelUser),
                        MessageUi(
                            "sample-parcel-2", fromUser = false, text = parcelReply,
                            verdict = VerdictUi(Risk.Dangerous, reasons),
                        ),
                    ),
                ),
                ChatUi(
                    id = "sample-bank",
                    title = bankTitle,
                    updatedAt = now - day,
                    messages = listOf(
                        MessageUi("sample-bank-1", fromUser = true, text = bankUser),
                        MessageUi("sample-bank-2", fromUser = false, text = bankReply),
                    ),
                ),
            )
        )
    }
}
