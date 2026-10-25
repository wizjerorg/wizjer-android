package org.wizjer.android.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.data.ai.AiError
import org.wizjer.android.data.ai.Conversation
import org.wizjer.android.data.ai.ReplyItem
import org.wizjer.android.data.ai.ReplyStatus
import org.wizjer.android.data.ai.Source
import org.wizjer.android.data.ai.ToolStatus
import org.wizjer.android.data.ai.UserItem
import org.wizjer.android.ui.components.AlertIcon
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.ConfirmSheet
import org.wizjer.android.ui.components.CopyIcon
import org.wizjer.android.ui.components.ExternalIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.InfoIcon
import org.wizjer.android.ui.components.SpeakerIcon
import org.wizjer.android.ui.components.rememberWizjerMarkState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The user's own message: photos above a teal bubble on the right. Faded, with a note, if it wasn't sent. */
@Composable
fun UserMessage(item: UserItem, onOpenPhoto: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (item.failure != null) 0.55f else 1f),
        horizontalAlignment = Alignment.End,
    ) {
        if (item.images.isNotEmpty()) {
            MessagePhotos(item.images, onOpenPhoto)
            if (item.text.isNotEmpty()) Spacer(Modifier.height(6.dp))
        }
        if (item.text.isNotEmpty()) {
            SelectionContainer {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimary,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                        .background(colors.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** What happened to a message that wasn't sent, and the way forward. */
@Composable
fun FailureCard(
    error: AiError,
    resetsAt: Long?,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onNewChat: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val copy = failureCopy(error.code)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlertIcon(colors.onBackground)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(copy.title), style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
        }
        Spacer(Modifier.height(4.dp))
        val body = when {
            error.code == DAILY_LIMIT && resetsAt != null -> stringResource(R.string.ai_error_daily_limit_body, clock(resetsAt))
            else -> stringResource(copy.body)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (copy.action) {
                FailureAction.Retry -> CardButton(stringResource(R.string.ai_action_retry), primary = true, onClick = onRetry)
                FailureAction.NewChat -> CardButton(stringResource(R.string.home_new_chat), primary = true, onClick = onNewChat)
                FailureAction.SignIn -> CardButton(stringResource(R.string.ai_action_sign_in), primary = true, onClick = onSignIn)
                FailureAction.None -> Unit
            }
            if (copy.editable) CardButton(stringResource(R.string.ai_action_edit), primary = copy.action == FailureAction.None, onClick = onEdit)
        }
    }
}

@Composable
private fun CardButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (primary) colors.onBackground else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private enum class FailureAction { Retry, NewChat, SignIn, None }

private class FailureCopy(@StringRes val title: Int, @StringRes val body: Int, val action: FailureAction, val editable: Boolean = true)

private const val DAILY_LIMIT = "daily_limit"

private fun failureCopy(code: String): FailureCopy = when (code) {
    AiError.NO_CONNECTION -> FailureCopy(R.string.ai_error_offline, R.string.ai_error_offline_body, FailureAction.Retry)
    "ai_busy" -> FailureCopy(R.string.ai_error_ai_busy, R.string.ai_error_ai_busy_body, FailureAction.Retry)
    "refused" -> FailureCopy(R.string.ai_error_refused, R.string.ai_error_refused_body, FailureAction.None)
    "empty_reply", "reply_too_long" -> FailureCopy(R.string.ai_error_unfinished, R.string.ai_error_unfinished_body, FailureAction.Retry)
    DAILY_LIMIT -> FailureCopy(R.string.ai_error_daily_limit, R.string.ai_error_daily_limit_tomorrow, FailureAction.Retry)
    "too_many_replies" -> FailureCopy(R.string.ai_error_too_many_replies, R.string.ai_error_wait_body, FailureAction.Retry)
    "too_many_requests" -> FailureCopy(R.string.ai_error_too_many_requests, R.string.ai_error_too_many_requests_body, FailureAction.Retry)
    "busy" -> FailureCopy(R.string.ai_error_busy, R.string.ai_error_wait_body, FailureAction.Retry)
    "chat_full" -> FailureCopy(R.string.ai_error_chat_full, R.string.ai_error_chat_full_body, FailureAction.NewChat)
    "too_many_images" -> FailureCopy(R.string.ai_error_too_many_images, R.string.ai_error_too_many_images_body, FailureAction.NewChat)
    "invalid_images" -> FailureCopy(R.string.ai_error_invalid_images, R.string.ai_error_invalid_images_body, FailureAction.None)
    "invalid" -> FailureCopy(R.string.ai_error_invalid, R.string.ai_error_invalid_body, FailureAction.None)
    AiError.NOT_FOUND -> FailureCopy(R.string.ai_error_not_found, R.string.ai_error_not_found_body, FailureAction.Retry)
    AiError.SIGNED_OUT -> FailureCopy(R.string.ai_error_signed_out, R.string.ai_error_signed_out_body, FailureAction.SignIn, editable = false)
    AiError.NOT_CONFIRMED -> FailureCopy(R.string.ai_error_not_confirmed, R.string.ai_error_not_confirmed_body, FailureAction.SignIn, editable = false)
    Conversation.STOPPED -> FailureCopy(R.string.ai_error_stopped, R.string.ai_error_stopped_body, FailureAction.Retry)
    else -> FailureCopy(R.string.ai_error_generic, R.string.ai_error_generic_body, FailureAction.Retry)
}

/**
 * Opens [uri] in the phone's default browser, not in an app that claims the address: in a scam app,
 * a link should land where the user expects. Falls back to whatever opens web pages.
 */
internal fun openInBrowser(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
    val browser = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    // "android" is the "open with" chooser: no default browser has been picked.
    if (browser != null && browser != "android") {
        try {
            return context.startActivity(Intent(view).setPackage(browser))
        } catch (_: ActivityNotFoundException) {
            // Fall through to any app that opens web pages.
        }
    }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone: nothing to open it with.
    }
}

/** wizjer.org or one of its subdomains (an exact match on the host, so "notwizjer.org" doesn't count). */
internal fun isWizjerSite(url: String): Boolean {
    val host = Uri.parse(url).host?.lowercase() ?: return false
    return url.startsWith("https://") && (host == "wizjer.org" || host.endsWith(".wizjer.org"))
}

/** "01:00" in the phone's time zone and format. */
@Composable
internal fun clock(time: Long): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
}

/**
 * Wizjer's reply, without a bubble: the steps it took, the text (formatted as it streams in), the pages
 * it cites and quiet actions once it's done. While nothing new shows, the eye looks around.
 */
@Composable
fun ReplyMessage(
    item: ReplyItem,
    speaking: Boolean,
    onReadAloud: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val text = rememberSmoothText(item.key, item.text, item.writing)
    // A tapped link waits here until the user has seen where it goes.
    var link by rememberSaveable { mutableStateOf<String?>(null) }
    link?.let { url ->
        val uri = Uri.parse(url)
        ConfirmSheet(
            title = stringResource(R.string.ai_link_title, uri.host.orEmpty()),
            body = stringResource(R.string.ai_link_body, url),
            confirmLabel = stringResource(R.string.ai_link_open),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { openInBrowser(context, uri) },
            onDismiss = { link = null },
        )
    }
    val toolRunning = item.tools.any { it.status == ToolStatus.Running }
    // The eye shows only while there's nothing else to look at: before the first words, or while the
    // model thinks between steps. Once words flow, they are the sign of life.
    val waiting = item.writing && !item.interrupted && !item.reconnecting && !toolRunning &&
        (text.isEmpty() || (item.thinking && text.length == item.text.length))

    Column(modifier.fillMaxWidth()) {
        if (item.tools.isNotEmpty()) {
            ToolSteps(item.tools, replyDone = !item.writing)
            if (text.isNotEmpty() || waiting) Spacer(Modifier.height(16.dp))
        }
        if (text.isNotEmpty()) {
            SelectionContainer {
                ReplyText(text, onOpenLink = { url ->
                    // Wizjer's own pages open straight away; anywhere else, the user sees where first.
                    if (isWizjerSite(url)) openInBrowser(context, Uri.parse(url)) else link = url
                })
            }
        }
        if (waiting) ThinkingRow(Modifier.padding(top = if (text.isNotEmpty()) 14.dp else 0.dp))
        if (item.writing && item.reconnecting) {
            Note(stringResource(R.string.ai_reconnecting), Modifier.padding(top = 12.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = colors.secondary, trackColor = Color.Transparent, strokeWidth = 1.8.dp)
            }
        }
        if (item.writing && item.interrupted) Interrupted(onReload, Modifier.padding(top = 12.dp))

        val status = when (item.status) {
            ReplyStatus.Stopped -> R.string.ai_status_stopped
            ReplyStatus.MaxTokens -> R.string.ai_status_too_long
            ReplyStatus.Incomplete -> R.string.ai_status_incomplete
            else -> null
        }
        status?.let { Note(stringResource(it), Modifier.padding(top = 12.dp)) { InfoIcon(colors.onSurfaceVariant) } }

        // The extras appear once the words have caught up, not while they're still flowing in.
        val shownAll = text.length == item.text.length
        if (!item.writing && shownAll && item.sources.isNotEmpty()) Sources(item.sources, Modifier.padding(top = 18.dp))
        if (!item.writing && shownAll && item.text.isNotEmpty()) Actions(item, speaking, onReadAloud)
    }
}

/**
 * The reply's text as it should look right now: words come in from the server in uneven bursts, so
 * they're let out at a steady pace (faster the further behind), frame by frame. A reply that was
 * already finished when it came on screen shows whole.
 */
@Composable
private fun rememberSmoothText(key: String, target: String, writing: Boolean): String {
    var shown by remember(key) { mutableIntStateOf(if (writing) 0 else target.length) }
    LaunchedEffect(key, target) {
        val links = linkRanges(target)
        while (shown < target.length) {
            withFrameMillis { }
            val behind = target.length - shown
            var next = (shown + maxOf(1, behind / 8)).coerceAtMost(target.length)
            // Never split a character made of two UTF-16 units (emoji and the like), nor a link's Markdown.
            if (next < target.length && target[next].isLowSurrogate()) next++
            links.firstOrNull { next > it.first && next <= it.last }?.let { next = it.last + 1 }
            shown = next
        }
    }
    return target.substring(0, shown.coerceAtMost(target.length))
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The app lost the reply's connection and stopped trying; the reply itself may well be finished. */
@Composable
private fun Interrupted(onReload: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(stringResource(R.string.ai_interrupted), style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
        CardButton(stringResource(R.string.ai_reload), primary = true, onClick = onReload)
    }
}

/** The web pages the reply cites; each opens in the browser. */
@Composable
private fun Sources(sources: List<Source>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ai_sources), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        sources.forEach { source ->
            val uri = Uri.parse(source.url)
            val web = uri.scheme == "https" || uri.scheme == "http"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainer)
                    .clickable(enabled = web, role = Role.Button) { openInBrowser(context, uri) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        source.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        uri.host?.removePrefix("www.") ?: source.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                ExternalIcon(colors.onSurfaceVariant)
            }
        }
    }
}

/** Copy and read aloud (or stop reading). */
@Composable
private fun Actions(item: ReplyItem, speaking: Boolean, onReadAloud: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val copy = rememberCopy()
    Row(
        Modifier
            .padding(top = 4.dp)
            .offset(x = (-12).dp),
    ) {
        IconCircleButton(
            onClick = { copy(plainReply(item.text)) },
            contentDescription = stringResource(R.string.message_copy),
        ) { CopyIcon(colors.onSurfaceVariant) }
        IconCircleButton(
            onClick = onReadAloud,
            contentDescription = stringResource(if (speaking) R.string.message_stop_reading else R.string.message_read),
        ) {
            if (speaking) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.onBackground),
                )
            } else {
                SpeakerIcon(colors.onSurfaceVariant)
            }
        }
    }
}

/** Copies text to the clipboard and confirms it (Android 13 and newer confirm on their own). */
@Composable
internal fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedLabel = stringResource(R.string.message_copied)
    return { text ->
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Wizjer", text))) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
    }
}

/** Shown while waiting for the assistant: the eye looks around. */
@Composable
fun ThinkingRow(modifier: Modifier = Modifier) {
    val mark = rememberWizjerMarkState()
    LaunchedEffect(Unit) {
        while (true) {
            mark.lookAround()
            mark.blink()
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(Modifier.width(26.dp), state = mark)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.assistant_thinking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
