package org.wizjer.android.ui.home

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.data.account.ProfileStore
import org.wizjer.android.data.ai.Assistant
import org.wizjer.android.data.ai.CameraPhotos
import org.wizjer.android.data.ai.Conversation
import org.wizjer.android.data.ai.LoadState
import org.wizjer.android.data.ai.Notice
import org.wizjer.android.data.ai.ReplyItem
import org.wizjer.android.data.ai.UserItem
import org.wizjer.android.protection.ProtectionStore
import org.wizjer.android.ui.components.ArrowDownIcon
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.MenuIcon
import org.wizjer.android.ui.components.PlusIcon
import org.wizjer.android.ui.language.currentAppLanguage
import org.wizjer.android.ui.permissions.rememberPermissionsState
import org.wizjer.android.ui.protection.ProtectionStatus
import org.wizjer.android.ui.protection.protectionStatus

/**
 * Main screen: a chat with Wizjer. Swipe from the left (or tap the menu) for past chats
 * and settings. An empty chat shows a greeting and starting points.
 */
@Composable
fun HomeScreen(
    profile: ProfileStore,
    assistant: Assistant,
    onOpenSettings: () -> Unit,
    onOpenProtection: () -> Unit,
    onStartCall: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSandbox: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val protection by remember { ProtectionStore.get(context) }.state.collectAsStateWithLifecycle()
    val protectionStatus = protectionStatus(protection, rememberPermissionsState())
    val language = currentAppLanguage().tag
    val readAloud = rememberReadAloud()
    val conversation = assistant.current
    val deletedLabel = stringResource(R.string.chat_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    var composer by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    // The text was cut to the length one message can hold (e.g. a long paste).
    var shortened by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val composerFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Coming back to the app: new chats from elsewhere, today's allowance, replies whose connection was lost.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { assistant.onForeground() }

    // A message stopped before its reply began, or one to edit, comes back into the composer.
    val draft = assistant.draft
    LaunchedEffect(draft) {
        if (draft != null) {
            composer = TextFieldValue(draft, TextRange(draft.length))
            assistant.draftTaken()
        }
    }

    // Another chat on screen: stop reading the previous one aloud.
    LaunchedEffect(conversation) { readAloud.stop() }

    val notices = rememberUpdatedState(noticeTexts())
    LaunchedEffect(assistant) {
        assistant.notices.collect { notice ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(notices.value.getValue(notice))
        }
    }

    val pickOne = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { assistant.attach(context, listOf(it)) }
    }
    val room = assistant.photoRoom
    val pickSeveral = rememberLauncherForActivityResult(remember(room) { PickMultipleVisualMedia(room.coerceAtLeast(2)) }) { uris ->
        if (uris.isNotEmpty()) assistant.attach(context, uris)
    }

    // The camera app saves into a file of ours; its address survives the app being recreated meanwhile.
    var cameraPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(TakePicture()) { saved ->
        val uri = cameraPhoto?.let(Uri::parse)
        cameraPhoto = null
        if (saved && uri != null) assistant.attach(context, listOf(uri))
    }
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    val noCamera = stringResource(R.string.ai_notice_no_camera)

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun openGallery() {
        val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
        if (room >= 2) pickSeveral.launch(request) else if (room == 1) pickOne.launch(request)
    }

    fun openCamera() {
        val uri = CameraPhotos.newPhoto(context)
        cameraPhoto = uri.toString()
        try {
            takePhoto.launch(uri)
        } catch (_: ActivityNotFoundException) {
            cameraPhoto = null
            say(noCamera)
        }
    }

    // Camera or gallery; with no room left, say which limit it is instead.
    fun attach() {
        if (room > 0) {
            choosingSource = true
        } else {
            val notice = if (assistant.attachments.size >= Assistant.IMAGES_PER_MESSAGE) Notice.PhotosPerMessage else Notice.PhotosPerChat
            say(notices.value.getValue(notice))
        }
    }

    fun send() {
        if (assistant.send(composer.text, language)) {
            composer = TextFieldValue()
            shortened = false
            // Out of the way, so the whole reply can be read as it comes.
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    // Delete right away and offer Undo, instead of asking "are you sure?" first.
    fun delete(id: String) {
        assistant.delete(id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(deletedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) assistant.undoDelete(id)
        }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.background,
                drawerContentColor = colors.onBackground,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                windowInsets = WindowInsets(0),
                modifier = Modifier.width(320.dp),
            ) {
                ChatDrawer(
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    email = profile.email,
                    chats = assistant.chats,
                    chatsState = assistant.chatsState,
                    canLoadMore = assistant.canLoadMore,
                    currentId = conversation.chatId,
                    protectionStatus = protectionStatus,
                    onNewChat = { closeDrawerThen(assistant::newChat) },
                    onOpenChat = { chat -> closeDrawerThen { assistant.open(chat) } },
                    // Deleting chats is switched off for now; set ALLOW_DELETING_CHATS to bring it back.
                    onDeleteChat = if (ALLOW_DELETING_CHATS) ::delete else null,
                    onLoadMore = assistant::loadMoreChats,
                    onRetry = assistant::refreshChats,
                    onProtection = { closeDrawerThen(onOpenProtection) },
                    onSettings = { closeDrawerThen(onOpenSettings) },
                    onSandbox = onOpenSandbox?.let { open -> { closeDrawerThen(open) } },
                )
            }
        },
    ) {
        Column(
            modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        ) {
            HomeTopBar(
                chatOpen = conversation.chatId != null || conversation.items.isNotEmpty(),
                onMenu = { scope.launch { drawerState.open() } },
                onNewChat = assistant::newChat,
            )

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = conversation,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "chat",
                ) { shown ->
                    ChatContent(
                        conversation = shown,
                        firstName = profile.firstName,
                        protectionStatus = protectionStatus,
                        resetsAt = assistant.usage?.resetsAt,
                        speaking = readAloud.speaking,
                        onFixProtection = onOpenProtection,
                        onSuggestion = { suggestion, text ->
                            if (suggestion.sendsImmediately) {
                                assistant.send(text, language)
                            } else {
                                // Leave room for the user to paste the message, link or number (or add a photo).
                                val prefill = "$text\n"
                                composer = TextFieldValue(prefill, TextRange(prefill.length))
                                composerFocus.requestFocus()
                            }
                        },
                        onOpenPhoto = { viewing = it },
                        onReadAloud = { reply -> readAloud.toggle(reply.key, reply.text, language) },
                        onRetry = { assistant.retry(it, language) },
                        onEdit = assistant::edit,
                        onMoveToNewChat = { assistant.moveToNewChat(it, language) },
                        onSignIn = onSignIn,
                        onReload = assistant::reload,
                    )
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { UndoBar(it) }
            }

            ComposerNotes(
                length = composer.text.length,
                shortened = shortened,
                remaining = assistant.usage?.remaining,
                limitReached = assistant.limitReached,
                resetsAt = assistant.usage?.resetsAt,
            )
            val hasContent = composer.text.isNotBlank() || assistant.attachments.isNotEmpty()
            Composer(
                value = composer,
                onValueChange = { value ->
                    shortened = value.text.length > Assistant.MAX_TEXT || (shortened && value.text.length >= Assistant.MAX_TEXT)
                    composer = if (value.text.length > Assistant.MAX_TEXT) {
                        val text = value.text.take(Assistant.MAX_TEXT)
                        TextFieldValue(text, TextRange(text.length))
                    } else {
                        value
                    }
                },
                attachments = assistant.attachments,
                action = when {
                    conversation.replying -> if (conversation.stopping) ComposerAction.Stopping else ComposerAction.Stop
                    hasContent -> if (assistant.canSend(composer.text)) ComposerAction.Send else ComposerAction.Wait
                    else -> ComposerAction.Call
                },
                onAttach = ::attach,
                onRemovePhoto = assistant::remove,
                onRetryPhoto = assistant::retryUpload,
                onSend = ::send,
                onStop = assistant::stop,
                onCall = onStartCall,
                focusRequester = composerFocus,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }

    viewing?.let { PhotoViewer(it, onDismiss = { viewing = null }) }
    if (choosingSource) {
        PhotoSourceSheet(onCamera = ::openCamera, onGallery = ::openGallery, onDismiss = { choosingSource = false })
    }
}

/** The open chat: loading, failed to load, the greeting of a new chat, or its messages. */
@Composable
private fun ChatContent(
    conversation: Conversation,
    firstName: String,
    protectionStatus: ProtectionStatus,
    resetsAt: Long?,
    speaking: String?,
    onFixProtection: () -> Unit,
    onSuggestion: (Suggestion, String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onReadAloud: (ReplyItem) -> Unit,
    onRetry: (UserItem) -> Unit,
    onEdit: (UserItem) -> Unit,
    onMoveToNewChat: (UserItem) -> Unit,
    onSignIn: () -> Unit,
    onReload: () -> Unit,
) {
    val state = conversation.loadState
    when {
        conversation.items.isNotEmpty() -> MessageList(
            conversation, resetsAt, speaking, onOpenPhoto, onReadAloud, onRetry, onEdit, onMoveToNewChat, onSignIn, onReload,
        )
        state == LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.secondary, trackColor = Color.Transparent, strokeWidth = 2.5.dp)
        }
        state is LoadState.Failed -> LoadFailed(onReload)
        else -> EmptyState(
            firstName = firstName,
            protectionStatus = protectionStatus,
            onFixProtection = onFixProtection,
            onSuggestion = onSuggestion,
        )
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.ai_load_failed),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ai_error_offline_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.ai_action_retry),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun MessageList(
    conversation: Conversation,
    resetsAt: Long?,
    speaking: String?,
    onOpenPhoto: (String) -> Unit,
    onReadAloud: (ReplyItem) -> Unit,
    onRetry: (UserItem) -> Unit,
    onEdit: (UserItem) -> Unit,
    onMoveToNewChat: (UserItem) -> Unit,
    onSignIn: () -> Unit,
    onReload: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val items = conversation.items
    // Newest message at the bottom; the list starts there, and goes back there when a message is sent.
    // While a reply grows, the list stays pinned to its end unless the user scrolled up to read.
    LaunchedEffect(items.size) { listState.animateScrollToItem(0) }
    // Messages fade in but never slide: a reply growing line by line mustn't drag the rest along.
    val awayFromEnd by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.asReversed(), key = { it.key }) { item ->
                when (item) {
                    is UserItem -> Column(Modifier.animateItem(placementSpec = null)) {
                        UserMessage(item, onOpenPhoto)
                        item.failure?.let { error ->
                            Spacer(Modifier.height(12.dp))
                            FailureCard(
                                error = error,
                                resetsAt = resetsAt,
                                onRetry = { onRetry(item) },
                                onEdit = { onEdit(item) },
                                onNewChat = { onMoveToNewChat(item) },
                                onSignIn = onSignIn,
                            )
                        }
                    }
                    is ReplyItem -> ReplyMessage(
                        item,
                        speaking = speaking == item.key,
                        onReadAloud = { onReadAloud(item) },
                        onReload = onReload,
                        modifier = Modifier.animateItem(placementSpec = null),
                    )
                }
            }
        }

        val scope = rememberCoroutineScope()
        val label = stringResource(R.string.ai_scroll_down)
        AnimatedVisibility(
            visible = awayFromEnd,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(6.dp, CircleShape)
                    .background(colors.background)
                    .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(0) } }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                ArrowDownIcon(colors.onBackground, size = 18.dp)
            }
        }
    }
}

/** A quiet line above the composer: messages left today, and the length of a long message. */
@Composable
private fun ComposerNotes(length: Int, shortened: Boolean, remaining: Int?, limitReached: Boolean, resetsAt: Long?) {
    val colors = MaterialTheme.colorScheme
    val note = when {
        limitReached && resetsAt != null -> stringResource(R.string.ai_usage_none, clock(resetsAt))
        shortened -> stringResource(R.string.ai_text_shortened, Assistant.MAX_TEXT)
        remaining != null && remaining in 1..USAGE_NOTE_FROM -> pluralStringResource(R.plurals.ai_usage_left, remaining, remaining)
        else -> null
    }
    val counter = length >= Assistant.MAX_TEXT - 500
    AnimatedVisibility(note != null || counter, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                note.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (limitReached) colors.onBackground else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (counter) {
                Spacer(Modifier.width(12.dp))
                val full = length >= Assistant.MAX_TEXT
                Text(
                    "$length / ${Assistant.MAX_TEXT}",
                    style = MaterialTheme.typography.labelMedium.let { if (full) it.copy(fontWeight = FontWeight.Bold) else it },
                    color = if (full) colors.onBackground else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Whether a long press on a chat in the menu deletes it (with Undo). Off for now, by the user's choice. */
private const val ALLOW_DELETING_CHATS = false

/** Below this many messages left today, the composer says how many remain. */
private const val USAGE_NOTE_FROM = 5

@Composable
private fun noticeTexts(): Map<Notice, String> = mapOf(
    Notice.DeleteFailed to stringResource(R.string.ai_notice_delete_failed),
    Notice.StopFailed to stringResource(R.string.ai_notice_stop_failed),
    Notice.ChatGone to stringResource(R.string.ai_notice_chat_gone),
    Notice.PhotosPerMessage to stringResource(R.string.ai_notice_photos_per_message, Assistant.IMAGES_PER_MESSAGE),
    Notice.PhotosPerChat to stringResource(R.string.ai_notice_photos_per_chat, Assistant.IMAGES_PER_CHAT),
    Notice.PhotoUnreadable to stringResource(R.string.ai_notice_photo_unreadable),
)

/** Menu on the left, new chat on the right; the logo and name appear in the middle once a chat is open. */
@Composable
private fun HomeTopBar(chatOpen: Boolean, onMenu: () -> Unit, onNewChat: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconCircleButton(onMenu, stringResource(R.string.home_menu), Modifier.align(Alignment.CenterStart)) {
            MenuIcon(colors.onBackground)
        }
        AnimatedVisibility(
            visible = chatOpen,
            enter = fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.9f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.width(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            }
        }
        IconCircleButton(
            onNewChat,
            stringResource(R.string.home_new_chat),
            Modifier.align(Alignment.CenterEnd),
            enabled = chatOpen,
        ) {
            PlusIcon(colors.onBackground)
        }
    }
}

/** "Chat deleted · Undo" pill (or a short notice) shown for a few seconds. */
@Composable
private fun UndoBar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.primary)
            .heightIn(min = 44.dp)
            .padding(start = 20.dp, end = if (data.visuals.actionLabel != null) 6.dp else 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimary, modifier = Modifier.weight(1f, fill = false))
        data.visuals.actionLabel?.let { label ->
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { data.performAction() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
