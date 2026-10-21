package org.wizjer.android.ui.call

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import org.wizjer.android.R
import org.wizjer.android.ui.components.CloseIcon
import org.wizjer.android.ui.components.MicIcon
import org.wizjer.android.ui.components.MicOffIcon
import org.wizjer.android.ui.components.WizjerMark
import org.wizjer.android.ui.components.rememberWizjerMarkState

private val CallBackground = Color(0xFF050A0A)
private val CallInk = Color(0xFFF1F5F4)
private val CallMint = Color(0xFF3DD39C)

/**
 * Voice call with the assistant. Always dark, like a phone call: the mark breathes,
 * rings pulse while it listens, and the eye blinks and glances now and then.
 * UI only for now; audio comes with the assistant.
 */
@Composable
fun CallScreen(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    var muted by rememberSaveable { mutableStateOf(false) }
    val startedAt = rememberSaveable { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    val mark = rememberWizjerMarkState()

    LaunchedEffect(Unit) {
        while (true) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(500)
        }
    }
    LaunchedEffect(Unit) {
        delay(700)
        mark.idle()
    }
    LightSystemBarsAndKeepScreenOn()

    val breathing = rememberInfiniteTransition(label = "breathing")
    val breath by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val ringsAlpha by animateFloatAsState(if (muted) 0f else 1f, tween(400), label = "ringsAlpha")

    Box(
        modifier
            .fillMaxSize()
            .background(CallBackground),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = CallInk)
            Spacer(Modifier.height(4.dp))
            Text(
                formatDuration(elapsed),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = CallInk.copy(alpha = 0.55f),
            )

            Spacer(Modifier.weight(1f))
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                ListeningRings(Modifier.fillMaxSize().graphicsLayer { alpha = ringsAlpha })
                WizjerMark(
                    color = CallInk,
                    eyeColor = CallMint,
                    pupilColor = CallInk,
                    state = mark,
                    modifier = Modifier
                        .width(112.dp)
                        .graphicsLayer {
                            scaleX = breath
                            scaleY = breath
                        },
                )
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(
                targetState = muted,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "callStatus",
            ) { isMuted ->
                Text(
                    stringResource(if (isMuted) R.string.call_muted else R.string.call_listening),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CallInk.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                RoundControl(
                    label = stringResource(if (muted) R.string.call_unmute else R.string.call_mute),
                    filled = muted,
                    size = 68.dp,
                    onClick = { muted = !muted },
                ) { tint -> if (muted) MicOffIcon(tint) else MicIcon(tint) }
                RoundControl(
                    label = stringResource(R.string.call_end),
                    filled = true,
                    size = 76.dp,
                    onClick = onEnd,
                ) { tint -> CloseIcon(tint, size = 28.dp) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Three rings that expand and fade, one after another. */
@Composable
private fun ListeningRings(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rings")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_700, easing = LinearEasing), RepeatMode.Restart),
        label = "ringProgress",
    )
    Canvas(modifier) {
        val inner = size.minDimension * 0.24f
        val outer = size.minDimension / 2f
        repeat(3) { index ->
            val p = (progress + index / 3f) % 1f
            drawCircle(
                color = CallMint.copy(alpha = (1f - p) * 0.45f),
                radius = inner + p * (outer - inner),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun RoundControl(
    label: String,
    filled: Boolean,
    size: Dp,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val background by animateColorAsState(if (filled) CallInk else Color.Transparent, tween(200), label = "controlBg")
    val tint = if (filled) CallBackground else CallInk
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .border(1.5.dp, CallInk.copy(alpha = if (filled) 0f else 0.3f), CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            icon(tint)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = CallInk.copy(alpha = 0.7f))
    }
}

/** White status/navigation icons on the dark call screen, and no screen timeout mid-call. */
@Composable
private fun LightSystemBarsAndKeepScreenOn() {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            lightStatus?.let { controller.isAppearanceLightStatusBars = it }
            lightNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            view.keepScreenOn = false
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
