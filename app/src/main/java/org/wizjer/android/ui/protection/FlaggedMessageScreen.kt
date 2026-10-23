package org.wizjer.android.ui.protection

import android.content.Context
import android.provider.Telephony
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.wizjer.android.R
import org.wizjer.android.detection.Reason
import org.wizjer.android.detection.Signal
import org.wizjer.android.protection.FlaggedMessage
import org.wizjer.android.protection.IncomingMessage
import org.wizjer.android.protection.MessageSource
import org.wizjer.android.ui.components.ChevronLeftIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.relativeTime

/**
 * What a scam alert opens: the message and why it looks suspicious, with a button to ask Wizjer
 * for an in-depth analysis. [flagged] is null when the message is no longer in memory (the app was
 * closed since the alert). For a [senior] (60+) the reasons are fewer and in plainer words.
 */
@Composable
fun FlaggedMessageScreen(
    flagged: FlaggedMessage?,
    senior: Boolean,
    onBack: () -> Unit,
    onAskWizjer: (FlaggedMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
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
                stringResource(R.string.flagged_title),
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
            if (flagged == null) {
                Text(stringResource(R.string.flagged_gone), style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
            } else {
                Details(flagged, senior)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (flagged != null) {
            PrimaryButton(
                text = stringResource(R.string.flagged_analyze),
                onClick = { onAskWizjer(flagged) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Details(flagged: FlaggedMessage, senior: Boolean) {
    val colors = MaterialTheme.colorScheme
    val message = flagged.message
    val analysis = flagged.analysis

    // In ink, like the sandbox's high-risk card: the one thing on the screen to read first.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.onBackground)
            .padding(22.dp),
    ) {
        Text(stringResource(R.string.flagged_headline), style = MaterialTheme.typography.headlineSmall, color = colors.background)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.flagged_body), style = MaterialTheme.typography.bodyMedium, color = colors.background.copy(alpha = 0.75f))
    }

    SectionLabel(stringResource(R.string.flagged_message))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                message.sender?.takeIf { it.isNotBlank() } ?: sourceLabel(message, senior),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(relativeTime(message.receivedAt), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (!message.sender.isNullOrBlank()) {
            Text(sourceLabel(message, senior), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
        }
    }

    SectionLabel(stringResource(R.string.flagged_why))
    shownReasons(analysis.reasons, senior).forEach { reason ->
        Bullet(stringResource(if (senior) reason.signal.simpleLabel else reason.signal.label, reason.detail.orEmpty()))
    }
}

/**
 * The warning signs worth showing, strongest first. A [senior] gets the three clearest: the AI
 * model's opinion only repeats the headline, so it's left out when the rules found something.
 */
internal fun shownReasons(reasons: List<Reason>, senior: Boolean): List<Reason> {
    val signs = reasons.filter { it.contribution > 0 }
    if (!senior) return signs
    val named = signs.filterNot { it.signal == Signal.ModelScam || it.signal == Signal.ModelSuspicious }
    return named.ifEmpty { signs }.take(3)
}

/** "SMS", or the app a notification came from: just its name, or "Notification from: X" for a [senior]. */
@Composable
internal fun sourceLabel(message: IncomingMessage, senior: Boolean): String {
    val context = LocalContext.current
    return when (message.source) {
        MessageSource.Sms -> stringResource(R.string.flagged_via_sms)
        MessageSource.Notification -> {
            val app = remember(message.appPackage) { message.appPackage?.let { appLabel(context, it) } }
            when {
                app == null -> stringResource(R.string.flagged_via_notification)
                senior -> stringResource(R.string.flagged_via_app, app)
                else -> app
            }
        }
    }
}

private fun appLabel(context: Context, packageName: String): String? = runCatching {
    context.packageManager.run { getApplicationLabel(getApplicationInfo(packageName, 0)).toString() }
}.getOrNull()

/** The icon of the app a message came from (the SMS app for texts), or null if it can't be read. */
@Composable
internal fun rememberSourceIcon(message: IncomingMessage, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    return remember(message.source, message.appPackage, pixels) {
        val app = when (message.source) {
            MessageSource.Sms -> Telephony.Sms.getDefaultSmsPackage(context)
            MessageSource.Notification -> message.appPackage
        }
        app?.let { runCatching { context.packageManager.getApplicationIcon(it).toBitmap(pixels, pixels).asImageBitmap() }.getOrNull() }
    }
}

@Composable
private fun Bullet(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.onBackground),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, modifier = Modifier.weight(1f))
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
