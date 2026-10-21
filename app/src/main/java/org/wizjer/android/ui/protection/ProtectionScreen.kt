package org.wizjer.android.ui.protection

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.wizjer.android.R
import org.wizjer.android.protection.AppPermission
import org.wizjer.android.protection.ProtectionState
import org.wizjer.android.protection.ProtectionStore
import org.wizjer.android.protection.WizjerNotificationListener
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.ChevronLeftIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.LockIcon
import org.wizjer.android.ui.components.MessageIcon
import org.wizjer.android.ui.components.StackIcon
import org.wizjer.android.ui.components.WizjerSwitch
import org.wizjer.android.ui.components.relativeTime
import org.wizjer.android.ui.components.rememberWizjerMarkState
import org.wizjer.android.ui.permissions.PermissionsState
import org.wizjer.android.ui.permissions.rememberPermissionsState

/** Overall state of background protection, as shown to the user. */
enum class ProtectionStatus(@StringRes val title: Int, @StringRes val body: Int) {
    On(R.string.protection_status_on, R.string.protection_status_on_body),
    Starting(R.string.protection_status_starting, R.string.protection_status_starting_body),
    MissingPermissions(R.string.protection_status_missing, R.string.protection_status_missing_body),
    Paused(R.string.protection_status_paused, R.string.protection_status_paused_body),
}

/** Permissions the current switches need that aren't granted yet. */
fun missingPermissions(state: ProtectionState, permissions: PermissionsState): List<AppPermission> =
    AppPermission.entries.filter { permission ->
        val needed = when (permission) {
            AppPermission.Alerts -> true
            AppPermission.NotificationAccess -> state.notificationsEnabled
            AppPermission.Sms -> state.smsEnabled
        }
        needed && permissions.granted[permission] != true
    }

fun protectionStatus(state: ProtectionState, permissions: PermissionsState): ProtectionStatus = when {
    !state.enabled -> ProtectionStatus.Paused
    missingPermissions(state, permissions).isNotEmpty() -> ProtectionStatus.MissingPermissions
    state.notificationsEnabled && !state.listenerConnected -> ProtectionStatus.Starting
    else -> ProtectionStatus.On
}

/** "Ochrona w tle": status, the main switch, statistics and what is being checked. */
@Composable
fun ProtectionScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val store = remember { ProtectionStore.get(context) }
    val state by store.state.collectAsStateWithLifecycle()
    val permissions = rememberPermissionsState()
    val missing = missingPermissions(state, permissions)
    val status = protectionStatus(state, permissions)

    // Keeps relative times ("Przed chwilą" → "1 minutę temu") and "today" fresh while open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
            store.refreshStats()
        }
    }
    // Access is granted but the listener hasn't connected: ask the system to bind it again.
    LaunchedEffect(status) {
        if (status == ProtectionStatus.Starting) {
            delay(1_500)
            NotificationListenerService.requestRebind(ComponentName(context, WizjerNotificationListener::class.java))
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(onBack, stringResource(R.string.action_back)) { ChevronLeftIcon(colors.onBackground) }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.drawer_protection),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            StatusCard(
                status = status,
                enabled = state.enabled,
                onEnabledChange = store::setEnabled,
                onFix = { missing.firstOrNull()?.let(permissions::request) },
            )

            SectionLabel(stringResource(R.string.protection_stats))
            StatsGrid(state, now)

            SectionLabel(stringResource(R.string.protection_sources))
            SourceRow(
                icon = { MessageIcon(it) },
                title = stringResource(R.string.permission_sms_title),
                body = stringResource(R.string.source_sms_body),
                checked = state.smsEnabled,
                granted = permissions.granted[AppPermission.Sms] == true,
                active = state.enabled,
                onCheckedChange = store::setSmsEnabled,
                onAllow = { permissions.request(AppPermission.Sms) },
            )
            HorizontalDivider(color = colors.outlineVariant)
            SourceRow(
                icon = { StackIcon(it) },
                title = stringResource(R.string.source_notifications),
                body = stringResource(R.string.source_notifications_body),
                checked = state.notificationsEnabled,
                granted = permissions.granted[AppPermission.NotificationAccess] == true,
                active = state.enabled,
                onCheckedChange = store::setNotificationsEnabled,
                onAllow = { permissions.request(AppPermission.NotificationAccess) },
            )

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.Top) {
                LockIcon(colors.onSurfaceVariant, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The big card on top: the logo (eye open while protecting), what's going on, and the main switch. */
@Composable
private fun StatusCard(
    status: ProtectionStatus,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onFix: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val mark = rememberWizjerMarkState()
    LaunchedEffect(status) {
        when (status) {
            ProtectionStatus.Paused -> mark.closeEye()
            ProtectionStatus.On -> {
                mark.openEye()
                mark.idle()
            }
            else -> mark.openEye()
        }
    }
    val background by animateColorAsState(
        if (status == ProtectionStatus.On) colors.secondaryContainer else colors.surfaceContainer,
        tween(400),
        label = "statusBackground",
    )
    val text = if (status == ProtectionStatus.On) colors.onSecondaryContainer else colors.onBackground

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(Modifier.width(52.dp), state = mark)
            Spacer(Modifier.weight(1f))
            WizjerSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(status.title), style = MaterialTheme.typography.headlineSmall, color = text)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(status.body),
            style = MaterialTheme.typography.bodyMedium,
            color = text.copy(alpha = 0.75f),
        )
        if (status == ProtectionStatus.MissingPermissions) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.permission_allow),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(role = Role.Button, onClick = onFix)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StatsGrid(state: ProtectionState, now: Long) {
    val stats = state.stats
    val lastCheck = stats.lastCheckedAt?.let { relativeTime(it, now) } ?: stringResource(R.string.stat_never)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stats.checkedToday.toString(), stringResource(R.string.stat_today), Modifier.weight(1f).fillMaxHeight())
            StatTile(
                stats.threatsFound.toString(),
                stringResource(R.string.stat_threats),
                Modifier.weight(1f).fillMaxHeight(),
                highlight = stats.threatsFound > 0,
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stats.checkedTotal.toString(), stringResource(R.string.stat_total), Modifier.weight(1f).fillMaxHeight())
            StatTile(lastCheck, stringResource(R.string.stat_last), Modifier.weight(1f).fillMaxHeight(), compact = true)
        }
    }
}

/** A number (or short text) with a label under it. [highlight] outlines it in ink to draw attention. */
@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer)
            .border(1.5.dp, if (highlight) colors.onBackground else Color.Transparent, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            value,
            style = if (compact) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** One thing Wizjer checks: its own switch, or an "Allow" button while its permission is missing. */
@Composable
private fun SourceRow(
    icon: @Composable (Color) -> Unit,
    title: String,
    body: String,
    checked: Boolean,
    granted: Boolean,
    active: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onAllow: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(colors.onBackground) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            WizjerSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = active)
        } else {
            Text(
                stringResource(R.string.permission_allow),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onBackground,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, colors.onBackground.copy(alpha = 0.2f), CircleShape)
                    .clickable(role = Role.Button, onClick = onAllow)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 32.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}
