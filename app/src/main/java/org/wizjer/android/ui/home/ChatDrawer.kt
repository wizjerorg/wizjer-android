package org.wizjer.android.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wizjer.android.R
import org.wizjer.android.data.ai.AiChat
import org.wizjer.android.data.ai.LoadState
import org.wizjer.android.ui.components.Avatar
import org.wizjer.android.ui.components.ChevronRightIcon
import org.wizjer.android.ui.components.FlaskIcon
import org.wizjer.android.ui.components.PlusIcon
import org.wizjer.android.ui.components.ProtectionDuoIcon
import org.wizjer.android.ui.components.SlidersIcon
import org.wizjer.android.ui.protection.ProtectionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Side menu: who you are, a new chat, protection status, past chats and settings. */
@Composable
fun ChatDrawer(
    firstName: String,
    lastName: String,
    email: String,
    chats: List<AiChat>,
    chatsState: LoadState,
    canLoadMore: Boolean,
    currentId: String?,
    protectionStatus: ProtectionStatus,
    onNewChat: () -> Unit,
    onOpenChat: (AiChat) -> Unit,
    /** Long press deletes a chat; null turns that off. */
    onDeleteChat: ((String) -> Unit)?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onProtection: () -> Unit,
    onSettings: () -> Unit,
    /** Opens the detector sandbox; null hides the row (release builds). */
    onSandbox: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(firstName, lastName)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (email.isNotBlank()) {
                    Text(
                        email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onNewChat)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlusIcon(colors.onPrimary, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_new_chat), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
        }

        Spacer(Modifier.height(8.dp))
        DrawerRow(
            label = stringResource(R.string.drawer_protection),
            // The umbrella stays open whatever the status: the dot and label beside it say on or off.
            icon = { ProtectionDuoIcon() },
            onClick = onProtection,
            trailing = {
                val active = protectionStatus == ProtectionStatus.On || protectionStatus == ProtectionStatus.Starting
                val label = when (protectionStatus) {
                    ProtectionStatus.On, ProtectionStatus.Starting -> R.string.drawer_protection_on
                    ProtectionStatus.Paused -> R.string.drawer_protection_paused
                    ProtectionStatus.MissingPermissions -> R.string.drawer_protection_off
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mint dot when protected, an empty ring when paused or something needs attention.
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.secondary else Color.Transparent)
                            .border(1.5.dp, if (active) colors.secondary else colors.onBackground, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) colors.onSurfaceVariant else colors.onBackground,
                    )
                }
            },
        )
        if (onSandbox != null) {
            DrawerRow(
                label = stringResource(R.string.drawer_sandbox),
                icon = { FlaskIcon(it) },
                onClick = onSandbox,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = colors.outlineVariant)

        if (chats.isEmpty()) {
            Column(Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 16.dp)) {
                when (chatsState) {
                    LoadState.Loading -> CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = colors.secondary,
                        trackColor = Color.Transparent,
                        strokeWidth = 2.dp,
                    )
                    is LoadState.Failed -> {
                        Text(stringResource(R.string.ai_chats_failed), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text(
                            stringResource(R.string.ai_action_retry),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onBackground,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .offset(x = (-12).dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = onRetry)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    LoadState.Ready -> Text(
                        stringResource(R.string.drawer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                groupByDay(chats).forEach { (label, group) ->
                    item(key = "header-$label") {
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                        )
                    }
                    items(group, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            selected = chat.id == currentId,
                            onClick = { onOpenChat(chat) },
                            onLongClick = onDeleteChat?.let { delete -> { delete(chat.id) } },
                        )
                    }
                }
                if (canLoadMore) {
                    item(key = "more") {
                        // Reaching the end of the list loads older chats.
                        LaunchedEffect(chats.size) { onLoadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = colors.secondary,
                                trackColor = Color.Transparent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = colors.outlineVariant)
        DrawerRow(
            label = stringResource(R.string.drawer_settings),
            icon = { SlidersIcon(it) },
            onClick = onSettings,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun DrawerRow(
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { ChevronRightIcon(MaterialTheme.colorScheme.onSurfaceVariant) },
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(colors.onBackground) }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Tap to open; long-press to delete (when allowed). A mint dot pulses while Wizjer is still replying there. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: AiChat, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val replyingLabel = stringResource(R.string.ai_chat_replying)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chat.title.ifBlank { stringResource(R.string.ai_chat_photo_title) },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chat.running) {
            val pulse = rememberInfiniteTransition(label = "replying")
            val alpha by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "replyingDot",
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(CircleShape)
                    .background(colors.secondary)
                    .semantics { contentDescription = replyingLabel },
            )
        }
    }
}

private fun groupByDay(chats: List<AiChat>): List<Pair<Int, List<AiChat>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    @StringRes
    fun bucket(chat: AiChat): Int {
        val day = Instant.ofEpochMilli(chat.updatedAt).atZone(zone).toLocalDate()
        return when {
            day == today -> R.string.drawer_today
            day == today.minusDays(1) -> R.string.drawer_yesterday
            day.isAfter(today.minusDays(7)) -> R.string.drawer_week
            else -> R.string.drawer_older
        }
    }
    // Already newest first; grouping keeps that order.
    return chats.groupBy(::bucket).toList()
}
