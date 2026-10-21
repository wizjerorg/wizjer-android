package org.wizjer.android.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.ui.components.EmphasizedEasing
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.rememberWizjerMarkState
import org.wizjer.android.ui.components.staggered
import org.wizjer.android.ui.language.LanguageChip
import org.wizjer.android.ui.language.LanguageSheet
import org.wizjer.android.ui.language.currentAppLanguage
import org.wizjer.android.ui.theme.WizjerTheme

private val MarkWidth = 64.dp
/** The intro starts with the mark at 96dp in the screen centre, matching the splash screen. */
private const val MarkStartScale = 1.5f
private const val RevealMillis = 1_100

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Survives the activity recreation caused by switching language, so the intro plays once.
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    val language = currentAppLanguage()

    val mark = rememberWizjerMarkState()
    val lift = remember { Animatable(if (introPlayed) 1f else 0f) }
    val reveal = remember { Animatable(if (introPlayed) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!introPlayed) {
            delay(250)
            mark.lookAround()
            mark.blink()
            launch { lift.animateTo(1f, tween(900, easing = EmphasizedEasing)) }
            delay(300)
            reveal.animateTo(1f, tween(RevealMillis, easing = LinearEasing))
            introPlayed = true
        }
        mark.idle()
    }

    var rootHeight by remember { mutableIntStateOf(0) }
    var markCenterY by remember { mutableFloatStateOf(Float.NaN) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .onSizeChanged { rootHeight = it.height }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .staggered(reveal, index = 3, count = 4),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanguageChip(language, onClick = { showLanguages = true })
            }

            Spacer(Modifier.weight(1f))

            BrandMark(
                state = mark,
                modifier = Modifier
                    .width(MarkWidth)
                    .onGloballyPositioned {
                        markCenterY = it.positionInRoot().y + it.size.height / 2f
                    }
                    .graphicsLayer {
                        if (markCenterY.isNaN() || rootHeight == 0) {
                            alpha = 0f // not measured yet; the splash is still covering us
                            return@graphicsLayer
                        }
                        val t = lift.value
                        translationY = (rootHeight / 2f - markCenterY) * (1f - t)
                        val s = lerp(MarkStartScale, 1f, t)
                        scaleX = s
                        scaleY = s
                    },
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
                    .staggered(reveal, index = 0, count = 4),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.welcome_body),
                // Balanced lines read better than a ragged paragraph when centred.
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .staggered(reveal, index = 1, count = 4),
            )

            Spacer(Modifier.weight(1.3f))

            Column(Modifier.staggered(reveal, index = 2, count = 4)) {
                PrimaryButton(
                    text = stringResource(R.string.welcome_cta),
                    onClick = onGetStarted,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onLogin),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.welcome_login),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLanguages) {
        LanguageSheet(current = language, onDismiss = { showLanguages = false })
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun WelcomeScreenPreview() {
    WizjerTheme { WelcomeScreen(onGetStarted = {}, onLogin = {}) }
}
