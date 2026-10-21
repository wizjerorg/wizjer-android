package org.wizjer.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.wizjer.android.R

/** Number of steps shown in the onboarding progress (name, birth date, permissions, account). */
const val OnboardingSteps = 4

/**
 * Frame shared by the onboarding steps: back button and progress on top, scrollable
 * [content], and a [bottom] area that rides above the keyboard.
 */
@Composable
fun OnboardingStepLayout(
    step: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        OnboardingTopBar(step, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
        Column {
            bottom()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun OnboardingTopBar(step: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backLabel = stringResource(R.string.action_back)
        Box(
            Modifier
                .offset(x = (-12).dp)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            ChevronLeftIcon(colors.onBackground)
        }
        Spacer(Modifier.weight(1f))
        if (step >= 0) StepProgress(step, OnboardingSteps) // step < 0: screens outside the numbered flow
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(36.dp))
    }
}

/** Pills for each step; the current one stretches, finished ones stay inked. */
@Composable
private fun StepProgress(current: Int, total: Int) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.step_progress, current + 1, total)
    Row(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            val width by animateDpAsState(
                if (index == current) 24.dp else 6.dp, tween(450, easing = EmphasizedEasing), label = "stepWidth",
            )
            // Current step in mint, finished ones in teal, upcoming ones as hairlines.
            val color by animateColorAsState(
                when {
                    index == current -> colors.secondary
                    index < current -> colors.primary
                    else -> colors.outlineVariant
                },
                tween(450),
                label = "stepColor",
            )
            Box(
                Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Heading with an inked [lead] followed by a muted [rest], e.g. "Poznajmy się. Jak masz na imię?"
 * [separator] goes between them: a space to flow as one sentence, or "\n" to stack them.
 */
@Composable
fun TwoToneHeading(lead: String, rest: String, modifier: Modifier = Modifier, separator: String = " ") {
    val colors = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            if (lead.isNotEmpty()) {
                withStyle(SpanStyle(color = colors.onBackground)) { append(lead) }
                append(separator)
            }
            withStyle(SpanStyle(color = if (lead.isEmpty()) colors.onBackground else colors.onSurfaceVariant)) {
                append(rest)
            }
        },
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Quiet "ⓘ Why do we ask?" link. */
@Composable
fun InfoLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .offset(x = (-10).dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoIcon(muted)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = muted)
    }
}
