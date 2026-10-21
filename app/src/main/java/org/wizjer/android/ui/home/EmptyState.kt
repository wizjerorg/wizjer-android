package org.wizjer.android.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wizjer.android.R
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.ChevronRightIcon
import org.wizjer.android.ui.components.LifebuoyDuoIcon
import org.wizjer.android.ui.components.LinkDuoIcon
import org.wizjer.android.ui.components.MessageDuoIcon
import org.wizjer.android.ui.components.PhoneCheckDuoIcon
import org.wizjer.android.ui.components.ShieldIcon
import org.wizjer.android.ui.components.TwoToneHeading
import org.wizjer.android.ui.components.rememberWizjerMarkState
import org.wizjer.android.ui.components.staggered
import org.wizjer.android.ui.protection.ProtectionStatus

/**
 * Starting points for people who don't know what to ask. Some fill the message box
 * (the user still has to paste or type something), others send a ready question right away.
 */
enum class Suggestion(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val text: Int,
    val sendsImmediately: Boolean,
    val icon: @Composable () -> Unit,
) {
    CheckMessage(R.string.suggest_message_title, R.string.suggest_message_body, R.string.suggest_message_prefill, false, { MessageDuoIcon() }),
    CheckLink(R.string.suggest_link_title, R.string.suggest_link_body, R.string.suggest_link_prefill, false, { LinkDuoIcon() }),
    CheckNumber(R.string.suggest_call_title, R.string.suggest_call_body, R.string.suggest_call_prompt, false, { PhoneCheckDuoIcon() }),
    AfterScam(R.string.suggest_help_title, R.string.suggest_help_body, R.string.suggest_help_prompt, true, { LifebuoyDuoIcon() }),
}

/** New chat: a personal greeting and large, clear starting points. */
@Composable
fun EmptyState(
    firstName: String,
    protectionStatus: ProtectionStatus,
    onFixProtection: () -> Unit,
    onSuggestion: (Suggestion, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mark = rememberWizjerMarkState()
    val reveal = remember { Animatable(0f) }
    val count = Suggestion.entries.size + 2

    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = LinearEasing)) }
    LaunchedEffect(Unit) {
        delay(1_200)
        mark.idle()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        BrandMark(Modifier.width(44.dp).staggered(reveal, 0, count), state = mark)
        Spacer(Modifier.height(20.dp))
        TwoToneHeading(
            lead = if (firstName.isNotBlank()) stringResource(R.string.home_greeting, firstName)
            else stringResource(R.string.home_greeting_plain),
            rest = stringResource(R.string.home_prompt),
            separator = "\n",
            modifier = Modifier.staggered(reveal, 1, count),
        )
        Spacer(Modifier.height(28.dp))
        // Only shown when something is wrong; when protection works, nothing nags.
        if (protectionStatus == ProtectionStatus.Paused || protectionStatus == ProtectionStatus.MissingPermissions) {
            ProtectionOffCard(protectionStatus, onFixProtection, Modifier.staggered(reveal, 1, count))
            Spacer(Modifier.height(16.dp))
        }
        Suggestion.entries.forEachIndexed { index, suggestion ->
            val text = stringResource(suggestion.text)
            SuggestionRow(suggestion, { onSuggestion(suggestion, text) }, Modifier.staggered(reveal, 2 + index, count))
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Background protection isn't working: say why plainly and offer the fix right there. */
@Composable
private fun ProtectionOffCard(status: ProtectionStatus, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, colors.onBackground, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldIcon(colors.onBackground)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(status.title), style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(status.body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.protection_fix),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onFix)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** One starting point: our two-tone icon standing free (no circle behind it), title and a short line. */
@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestion.icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(suggestion.title), style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(suggestion.body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        ChevronRightIcon(colors.onSurfaceVariant)
    }
}
