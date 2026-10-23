package org.wizjer.android.ui.sandbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wizjer.android.R
import org.wizjer.android.data.lists.ListSource
import org.wizjer.android.data.lists.ThreatLists
import org.wizjer.android.data.lists.ThreatListsWorker
import org.wizjer.android.detection.Analysis
import org.wizjer.android.detection.DetectorInput
import org.wizjer.android.data.model.BundledModel
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.SampleMessages
import org.wizjer.android.detection.lists.SiteList
import org.wizjer.android.protection.MessageSource
import org.wizjer.android.ui.components.ChevronLeftIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.relativeTime
import org.wizjer.android.ui.language.AppLanguage
import java.text.NumberFormat
import kotlin.math.abs

/**
 * Developer tool (debug builds only): type or paste a message and see exactly how the
 * detector judges it — risk, confidence, scam types, reasons, extracted items and timings.
 */
@Composable
fun SandboxScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lists = remember { ThreatLists.get(context) }
    val detector = remember { LocalScamDetector(sites = { lists.loaded.value.values }, model = { BundledModel.get(context) }) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var text by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(MessageSource.Sms) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            LocalScamDetector.warmUp()
            BundledModel.get(context) // read the model now rather than on the first check
        }
    }

    fun check() {
        if (text.isBlank()) return
        focusManager.clearFocus()
        scope.launch { analysis = detector.analyze(DetectorInput(text, source, sender.ifBlank { null })) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding(),
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
                stringResource(R.string.drawer_sandbox),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                InputCard(text, { text = it }, sender, { sender = it })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.sandbox_source_sms), source == MessageSource.Sms) { source = MessageSource.Sms }
                    Pill(stringResource(R.string.sandbox_source_notification), source == MessageSource.Notification) {
                        source = MessageSource.Notification
                    }
                }
            }

            SectionLabel(stringResource(R.string.sandbox_examples), Modifier.padding(horizontal = 24.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SampleMessages.all) { sample ->
                    Pill(sample.label, selected = false) {
                        text = sample.text
                        sender = sample.sender.orEmpty()
                        check()
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.sandbox_check),
                    onClick = ::check,
                    enabled = text.isNotBlank(),
                    showArrow = false,
                )

                AnimatedVisibility(
                    visible = analysis != null,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { it / 8 },
                ) {
                    analysis?.let { Results(it) }
                }
                ListsStatus(lists)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InputCard(text: String, onText: (String) -> Unit, sender: String, onSender: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        PlainField(sender, onSender, stringResource(R.string.sandbox_sender), singleLine = true)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.outlineVariant)
        PlainField(text, onText, stringResource(R.string.sandbox_hint), singleLine = false)
    }
}

@Composable
private fun PlainField(value: String, onValueChange: (String) -> Unit, hint: String, singleLine: Boolean) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 5,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.onBackground),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                inner()
            }
        },
    )
}

@Composable
private fun Results(analysis: Analysis) {
    Column {
        Spacer(Modifier.height(24.dp))
        RiskCard(analysis)

        if (analysis.types.isNotEmpty()) {
            SectionLabel(stringResource(R.string.sandbox_types))
            analysis.types.forEach { TypeBar(stringResource(it.type.label), it.score) }
        }

        SectionLabel(stringResource(R.string.sandbox_reasons))
        if (analysis.reasons.isEmpty()) {
            Text(stringResource(R.string.sandbox_no_reasons), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        analysis.reasons.forEach { reason ->
            ReasonRow(stringResource(reason.signal.label, reason.detail.orEmpty()), reason.contribution)
        }

        if (analysis.entities.isNotEmpty()) {
            SectionLabel(stringResource(R.string.sandbox_entities))
            analysis.entities.forEach { entity ->
                val notes = entity.notes.map { stringResource(it.label, entity.noteDetail.orEmpty()) }
                EntityRow(stringResource(entity.kind.label), entity.value, notes)
            }
        }

        SectionLabel(stringResource(R.string.sandbox_technical))
        TechnicalDetails(analysis)
    }
}

/** Big gauge with the risk, the level, the confidence and the most likely scam type. */
@Composable
private fun RiskCard(analysis: Analysis) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (analysis.level) {
        RiskLevel.Safe -> colors.secondaryContainer to colors.onSecondaryContainer
        RiskLevel.Suspicious -> colors.surfaceContainer to colors.onBackground
        RiskLevel.Dangerous -> colors.onBackground to colors.background
    }
    val progress by animateFloatAsState(analysis.risk / 100f, tween(700), label = "risk")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 220.dp, height = 120.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val diameter = size.width - stroke
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(diameter, diameter)
                drawArc(content.copy(alpha = 0.15f), 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(content, 180f, 180f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.sandbox_risk), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.65f))
                Text(
                    analysis.risk.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, fontFeatureSettings = "tnum"),
                    color = content,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(analysis.level.label), style = MaterialTheme.typography.titleMedium, color = content)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Fact(stringResource(R.string.sandbox_confidence), "${analysis.confidence}%", content, Modifier.weight(1f))
            Fact(
                stringResource(R.string.sandbox_main_type),
                analysis.primaryType?.let { stringResource(it.label) } ?: "—",
                content,
                Modifier.weight(1.6f),
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun TypeBar(label: String, score: Double) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, modifier = Modifier.weight(1f))
            Text("${(score * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainer),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(score.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
    }
}

/** A reason and its weight: positive pushes towards "scam", negative (mint) towards "genuine". */
@Composable
private fun ReasonRow(text: String, contribution: Double) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            (if (contribution >= 0) "+" else "−") + "%.1f".format(abs(contribution)),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = if (contribution >= 0) colors.onSurfaceVariant else colors.secondary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun EntityRow(kind: String, value: String, notes: List<String>) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 7.dp)) {
        Text(kind, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.width(112.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = colors.onBackground)
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TechnicalDetails(analysis: Analysis) {
    val colors = MaterialTheme.colorScheme
    val notYet = stringResource(R.string.sandbox_not_yet)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.sandbox_normalized), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(
            analysis.normalizedText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = colors.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainer)
                .padding(14.dp),
        )
        DetailRow(stringResource(R.string.sandbox_language), AppLanguage.fromTag(analysis.language).nativeName)
        DetailRow(stringResource(R.string.sandbox_timing), formatMicros(analysis.timings.sumOf { it.micros }))
        analysis.timings.forEach {
            DetailRow(stringResource(it.stage.label), formatMicros(it.micros), Modifier.padding(start = 16.dp))
        }
        DetailRow(stringResource(R.string.sandbox_detector), analysis.detectorVersion)
        DetailRow(stringResource(R.string.sandbox_model), if (analysis.modelUsed) "✓" else notYet)
        DetailRow(stringResource(R.string.sandbox_lists), if (analysis.listsUsed) "✓" else stringResource(R.string.sandbox_lists_none))
    }
}

/** How many domains each list has, when it was downloaded, and a button to update everything now. */
@Composable
private fun ListsStatus(lists: ThreatLists) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val loaded by lists.loaded.collectAsStateWithLifecycle()
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ThreatListsWorker.UPDATE_NOW) }
        .collectAsStateWithLifecycle(emptyList())
    val state = work.firstOrNull()?.state
    val updating = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }

    SectionLabel(stringResource(R.string.sandbox_lists))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SiteList.entries.forEach { list ->
            // A list can come from several downloads (CERT: the full one and the recent additions).
            val indexes = ListSource.entries.filter { list in it.lists }.mapNotNull { loaded[it] }
            DetailRow(
                stringResource(list.label),
                if (indexes.isEmpty()) {
                    stringResource(R.string.sandbox_lists_none)
                } else {
                    "${numbers.format(indexes.sumOf { it.size(list) })} · ${relativeTime(indexes.maxOf { it.updatedAt })}"
                },
            )
        }
        if (state == WorkInfo.State.FAILED) {
            Text(stringResource(R.string.sandbox_lists_failed), style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
        }
    }
    Spacer(Modifier.height(14.dp))
    Pill(stringResource(if (updating) R.string.sandbox_lists_updating else R.string.sandbox_lists_update), selected = false) {
        if (!updating) ThreatListsWorker.updateNow(context)
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.onPrimary else colors.onBackground,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.onBackground.copy(alpha = 0.15f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = 28.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

private fun formatMicros(micros: Long): String =
    if (micros < 1_000) "$micros µs" else "%.1f ms".format(micros / 1_000.0)
