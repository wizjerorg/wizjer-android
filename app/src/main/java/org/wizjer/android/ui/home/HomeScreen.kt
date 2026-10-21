package org.wizjer.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.data.account.ProfileStore
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.MenuIcon
import org.wizjer.android.ui.components.PlusIcon
import org.wizjer.android.ui.permissions.rememberPermissionsState

/**
 * Main screen: a chat with Wizjer. Swipe from the left (or tap the menu) for past chats
 * and settings. An empty chat shows a greeting and starting points.
 */
@Composable
fun HomeScreen(
    profile: ProfileStore,
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onStartCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val protectionActive = rememberPermissionsState().allGranted
    val firstName = profile.firstName
    val lastName = profile.lastName
    val email = profile.email
    val deletedLabel = stringResource(R.string.chat_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    var composer by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val composerFocus = remember { FocusRequester() }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    fun send(text: String) {
        state.send(text)
        composer = TextFieldValue()
    }

    // Delete right away and offer Undo, instead of asking "are you sure?" first.
    fun delete(id: String) {
        val removed = state.delete(id) ?: return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(deletedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) state.restore(removed)
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
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    chats = state.chats,
                    currentId = state.currentId,
                    protectionActive = protectionActive,
                    onNewChat = { closeDrawerThen(state::newChat) },
                    onOpenChat = { id -> closeDrawerThen { state.open(id) } },
                    onDeleteChat = ::delete,
                    onProtection = { closeDrawerThen(onOpenSettings) },
                    onSettings = { closeDrawerThen(onOpenSettings) },
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
                chatOpen = state.current != null,
                onMenu = { scope.launch { drawerState.open() } },
                onNewChat = state::newChat,
            )

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.currentId,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "chat",
                ) { id ->
                    val chat = state.chats.firstOrNull { it.id == id }
                    if (chat == null) {
                        EmptyState(
                            firstName = firstName,
                            protectionActive = protectionActive,
                            onFixProtection = onOpenSettings,
                            onSuggestion = { suggestion, text ->
                                if (suggestion.sendsImmediately) {
                                    send(text)
                                } else {
                                    // Leave room for the user to paste the message or link.
                                    val prefill = "$text\n"
                                    composer = TextFieldValue(prefill, TextRange(prefill.length))
                                    composerFocus.requestFocus()
                                }
                            },
                        )
                    } else {
                        key(chat.id) { MessageList(chat) }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { UndoBar(it) }
            }

            Composer(
                value = composer,
                onValueChange = { composer = it },
                onSend = { send(composer.text) },
                onCall = onStartCall,
                focusRequester = composerFocus,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }
}

@Composable
private fun MessageList(chat: ChatUi) {
    val listState = rememberLazyListState()
    // Newest message at the bottom; the list starts there and stays there.
    LaunchedEffect(chat.messages.size) { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(chat.messages.asReversed(), key = { it.id }) { message ->
            if (message.fromUser) {
                UserBubble(message, Modifier.animateItem())
            } else {
                AssistantMessage(message, onReadAloud = { /* TODO: text-to-speech */ }, modifier = Modifier.animateItem())
            }
        }
    }
}

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

/** "Chat deleted · Undo" pill shown for a few seconds after deleting. */
@Composable
private fun UndoBar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimary)
        Spacer(Modifier.width(12.dp))
        data.visuals.actionLabel?.let { label ->
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
