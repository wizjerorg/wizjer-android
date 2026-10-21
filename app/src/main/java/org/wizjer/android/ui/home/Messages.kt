package org.wizjer.android.ui.home

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.ui.components.CopyIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.SpeakerIcon
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.rememberWizjerMarkState

/** The user's own message: a black bubble on the right. */
@Composable
fun UserBubble(message: MessageUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        SelectionContainer {
            Text(
                message.text,
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

/** Wizjer's reply: plain text without a bubble, an optional risk card, and quiet actions. */
@Composable
fun AssistantMessage(message: MessageUi, onReadAloud: (MessageUi) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedLabel = stringResource(R.string.message_copied)

    Column(modifier.fillMaxWidth()) {
        message.verdict?.let {
            VerdictCard(it)
            Spacer(Modifier.height(14.dp))
        }
        SelectionContainer {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.offset(x = (-12).dp)) {
            IconCircleButton(
                onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Wizjer", message.text))) }
                    // Android 13+ shows its own confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                    }
                },
                contentDescription = stringResource(R.string.message_copy),
            ) { CopyIcon(colors.onSurfaceVariant) }
            IconCircleButton(
                onClick = { onReadAloud(message) },
                contentDescription = stringResource(R.string.message_read),
            ) { SpeakerIcon(colors.onSurfaceVariant) }
        }
    }
}

/**
 * Risk summary above a reply. The stronger the risk, the heavier the card:
 * soft mint when safe, grey when suspicious, solid ink when dangerous.
 */
@Composable
fun VerdictCard(verdict: VerdictUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content, border) = when (verdict.risk) {
        Risk.Safe -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, Color.Transparent)
        Risk.Suspicious -> Triple(colors.surfaceContainer, colors.onBackground, colors.onBackground.copy(alpha = 0.08f))
        Risk.Dangerous -> Triple(colors.onBackground, colors.background, Color.Transparent)
    }
    val label = stringResource(
        when (verdict.risk) {
            Risk.Safe -> R.string.verdict_safe
            Risk.Suspicious -> R.string.verdict_suspicious
            Risk.Dangerous -> R.string.verdict_dangerous
        }
    )

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = content, modifier = Modifier.weight(1f))
            RiskMeter(verdict.risk, content)
        }
        if (verdict.reasons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                verdict.reasons.forEach { reason ->
                    Row {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(content.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

/** Three segments: one filled for safe, two for suspicious, three for dangerous. */
@Composable
private fun RiskMeter(risk: Risk, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Box(
                Modifier
                    .width(16.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (index <= risk.ordinal) color else color.copy(alpha = 0.2f))
            )
        }
    }
}

/**
 * Shown while waiting for the assistant's answer: the eye looks around.
 * Not used yet; it goes at the bottom of the message list once replies come from the assistant.
 */
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
