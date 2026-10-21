package org.wizjer.android.ui.home

import androidx.annotation.StringRes
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wizjer.android.R
import org.wizjer.android.ui.components.Avatar
import org.wizjer.android.ui.components.ChevronRightIcon
import org.wizjer.android.ui.components.FlaskIcon
import org.wizjer.android.ui.components.PlusIcon
import org.wizjer.android.ui.components.ShieldIcon
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
    chats: List<ChatUi>,
    currentId: String?,
    protectionStatus: ProtectionStatus,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
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
            icon = { ShieldIcon(it) },
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
            Text(
                stringResource(R.string.drawer_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Spacer(Modifier.weight(1f))
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
                            onClick = { onOpenChat(chat.id) },
                            onLongClick = { onDeleteChat(chat.id) },
                        )
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

/** Tap to open; long-press to delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: ChatUi, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Text(
        chat.title,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

private fun groupByDay(chats: List<ChatUi>): List<Pair<Int, List<ChatUi>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    @StringRes
    fun bucket(chat: ChatUi): Int {
        val day = Instant.ofEpochMilli(chat.updatedAt).atZone(zone).toLocalDate()
        return when {
            day == today -> R.string.drawer_today
            day == today.minusDays(1) -> R.string.drawer_yesterday
            day.isAfter(today.minusDays(7)) -> R.string.drawer_week
            else -> R.string.drawer_older
        }
    }
    return chats.sortedByDescending { it.updatedAt }.groupBy(::bucket).toList()
}
