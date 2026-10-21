package org.wizjer.android.ui.permissions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wizjer.android.R
import org.wizjer.android.protection.AppPermission
import org.wizjer.android.ui.components.BellIcon
import org.wizjer.android.ui.components.CheckIcon
import org.wizjer.android.ui.components.EmphasizedEasing
import org.wizjer.android.ui.components.LockIcon
import org.wizjer.android.ui.components.MessageIcon
import org.wizjer.android.ui.components.OnboardingTopBar
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.StackIcon
import org.wizjer.android.ui.components.staggered

private const val RevealCount = 7

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    state: PermissionsState = rememberPermissionsState(),
) {
    val colors = MaterialTheme.colorScheme
    var confirmSkip by rememberSaveable { mutableStateOf(false) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!revealed) {
            reveal.animateTo(1f, tween(1_000, delayMillis = 150, easing = LinearEasing))
            revealed = true
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        OnboardingTopBar(step = 2, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground,
                modifier = Modifier
                    .semantics { heading() }
                    .staggered(reveal, 0, RevealCount),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.permissions_body),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.staggered(reveal, 1, RevealCount),
            )
            Spacer(Modifier.height(16.dp))

            PermissionItems.forEachIndexed { index, item ->
                Column(Modifier.staggered(reveal, 2 + index, RevealCount)) {
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    PermissionRow(
                        item = item,
                        granted = state.granted[item.permission] == true,
                        onAllow = { state.request(item.permission) },
                    )
                }
            }
            PrivacyNote(Modifier.staggered(reveal, 5, RevealCount))
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.staggered(reveal, 6, RevealCount)) {
            // Stays in the layout when hidden so the main button never jumps.
            val skipAlpha by animateFloatAsState(if (state.allGranted) 0f else 1f, tween(300), label = "skip")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer { alpha = skipAlpha }
                    .clip(CircleShape)
                    .clickable(enabled = !state.allGranted, role = Role.Button) { confirmSkip = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.permissions_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = onContinue,
                enabled = state.allGranted,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmSkip) {
        SkipPermissionsSheet(
            // Take the user straight to the first missing permission rather than back to the list.
            onGrant = { AppPermission.entries.firstOrNull { state.granted[it] != true }?.let(state::request) },
            onSkip = onSkip,
            onDismiss = { confirmSkip = false },
        )
    }
}

@Composable
private fun PrivacyNote(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.secondaryContainer)
            .padding(16.dp),
    ) {
        LockIcon(colors.onSecondaryContainer, Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSecondaryContainer,
        )
    }
}

internal class PermissionItem(
    val permission: AppPermission,
    val title: Int,
    val body: Int,
    val icon: @Composable (Color) -> Unit,
)

internal val PermissionItems = listOf(
    PermissionItem(AppPermission.Alerts, R.string.permission_alerts_title, R.string.permission_alerts_body) { BellIcon(it) },
    PermissionItem(AppPermission.NotificationAccess, R.string.permission_access_title, R.string.permission_access_body) { StackIcon(it) },
    PermissionItem(AppPermission.Sms, R.string.permission_sms_title, R.string.permission_sms_body) { MessageIcon(it) },
)

@Composable
internal fun PermissionRow(item: PermissionItem, granted: Boolean, onAllow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            item.icon(colors.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            // Action sits on the title line so the description gets the full width below.
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(item.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                PermissionAction(granted, onAllow)
            }
            Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** Outlined "Allow" pill that turns into a filled check once granted. */
@Composable
private fun PermissionAction(granted: Boolean, onAllow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val grantedLabel = stringResource(R.string.permission_granted)
    AnimatedContent(
        targetState = granted,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(tween(360, easing = EmphasizedEasing), initialScale = 0.5f))
                .togetherWith(fadeOut(tween(120)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "permissionAction",
    ) { isGranted ->
        if (isGranted) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.secondary)
                    .semantics { contentDescription = grantedLabel },
                contentAlignment = Alignment.Center,
            ) {
                CheckIcon(colors.onSecondary, size = 18.dp)
            }
        } else {
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .height(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.onBackground.copy(alpha = 0.2f), CircleShape)
                    .clickable(role = Role.Button, onClick = onAllow)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.permission_allow),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBackground,
                )
            }
        }
    }
}
