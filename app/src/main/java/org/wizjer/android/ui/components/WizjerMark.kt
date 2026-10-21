package org.wizjer.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import org.wizjer.android.ui.theme.BrandMintLight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.delay
import kotlin.random.Random

// Logo geometry, measured from media/logo.png (1000x1000 units).
private const val BubblePath =
    "M262.13,666.04 A307,307 0 1,1 408.79,765.14 L195.12,838.85 Q185.67,842.11 189.65,832.93 Z"
private val EyeCenter = Offset(500f, 472f)
private const val EyeRadius = 145.3f
private val PupilRest = Offset(447.5f, 419.5f)
private const val PupilRadius = 36.43f
private val Bounds = Rect(185f, 165f, 807f, 843f)

internal val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Drives the mark's eye: where the pupil looks and how open the eye is. */
@Stable
class WizjerMarkState {
    /** Pupil offset from its resting spot, in logo units. */
    internal val pupil = Animatable(Offset.Zero, Offset.VectorConverter)
    internal val openness = Animatable(1f)

    suspend fun lookAround() {
        look(Offset(125f, 50f), 380)   // right
        delay(160)
        look(Offset(-20f, 55f), 460)   // left
        delay(140)
        look(Offset.Zero, 380)         // back at the viewer
    }

    suspend fun openEye() {
        openness.animateTo(1f, tween(500, easing = EmphasizedEasing))
    }

    /** Slowly shuts the eye and keeps it shut ("Wizjer can't see"). */
    suspend fun closeEye() {
        pupil.animateTo(Offset.Zero, tween(200))
        openness.animateTo(0.06f, tween(700, easing = EmphasizedEasing))
    }

    suspend fun blink() {
        openness.animateTo(0.06f, tween(90, easing = FastOutLinearInEasing))
        openness.animateTo(1f, tween(170, easing = LinearOutSlowInEasing))
    }

    /** Occasional blinks and glances so the mark feels alive; never returns. */
    suspend fun idle() {
        while (true) {
            delay(Random.nextLong(3_500, 7_000))
            if (Random.nextFloat() < 0.3f) {
                look(Offset(if (Random.nextBoolean()) 110f else -15f, 50f), 420)
                delay(700)
                look(Offset.Zero, 420)
            } else {
                blink()
            }
        }
    }

    private suspend fun look(target: Offset, durationMillis: Int) {
        pupil.animateTo(target, tween(durationMillis, easing = EmphasizedEasing))
    }
}

@Composable
fun rememberWizjerMarkState(): WizjerMarkState = remember { WizjerMarkState() }

/** The logo in the brand colours of the current theme: teal bubble, mint eye, light highlight. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    state: WizjerMarkState = rememberWizjerMarkState(),
) {
    val colors = MaterialTheme.colorScheme
    WizjerMark(
        color = colors.primary,
        modifier = modifier,
        state = state,
        eyeColor = colors.secondary,
        pupilColor = BrandMintLight,
    )
}

/**
 * The Wizjer logo: a bubble with the eye cut out and a pupil inside. With only [color] it is
 * single-colour; with [eyeColor] and [pupilColor] the eye is filled like the app icon.
 */
@Composable
fun WizjerMark(
    color: Color,
    modifier: Modifier = Modifier,
    state: WizjerMarkState = rememberWizjerMarkState(),
    eyeColor: Color? = null,
    pupilColor: Color = color,
) {
    val bubble = remember { PathParser().parsePathString(BubblePath).toPath() }
    val eye = remember { Path() }
    val body = remember { Path() }

    Canvas(modifier.aspectRatio(Bounds.width / Bounds.height)) {
        val scale = size.width / Bounds.width
        val open = state.openness.value
        eye.rewind()
        eye.addOval(
            Rect(
                left = EyeCenter.x - EyeRadius,
                top = EyeCenter.y - EyeRadius * open,
                right = EyeCenter.x + EyeRadius,
                bottom = EyeCenter.y + EyeRadius * open,
            )
        )
        body.rewind()
        body.fillType = PathFillType.EvenOdd
        body.addPath(bubble)
        body.addPath(eye)

        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(-Bounds.left, -Bounds.top)
        }) {
            drawPath(body, color)
            if (eyeColor != null) drawPath(eye, eyeColor)
            clipPath(eye) {
                drawCircle(if (eyeColor != null) pupilColor else color, PupilRadius, PupilRest + state.pupil.value)
            }
        }
    }
}
