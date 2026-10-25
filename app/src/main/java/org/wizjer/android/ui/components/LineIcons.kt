package org.wizjer.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Thin, round-capped line icons drawn in code to match the minimal type.

@Composable
fun ArrowForwardIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.18f, h * 0.5f); lineTo(w * 0.82f, h * 0.5f)
        moveTo(w * 0.56f, h * 0.24f); lineTo(w * 0.82f, h * 0.5f); lineTo(w * 0.56f, h * 0.76f)
    }

@Composable
fun ChevronDownIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.2f, h * 0.38f); lineTo(w * 0.5f, h * 0.66f); lineTo(w * 0.8f, h * 0.38f)
    }

@Composable
fun CheckIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.2f, h * 0.52f); lineTo(w * 0.42f, h * 0.72f); lineTo(w * 0.8f, h * 0.3f)
    }

@Composable
fun ChevronLeftIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f)
    }

@Composable
fun InfoIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addOval(Rect(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.92f))
        moveTo(w * 0.5f, h * 0.46f); lineTo(w * 0.5f, h * 0.72f)
        moveTo(w * 0.5f, h * 0.3f); lineTo(w * 0.5f, h * 0.31f)
    }

@Composable
fun BellIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.24f, h * 0.7f)
        lineTo(w * 0.27f, h * 0.44f)
        cubicTo(w * 0.29f, h * 0.29f, w * 0.38f, h * 0.18f, w * 0.5f, h * 0.18f)
        cubicTo(w * 0.62f, h * 0.18f, w * 0.71f, h * 0.29f, w * 0.73f, h * 0.44f)
        lineTo(w * 0.76f, h * 0.7f)
        close()
        moveTo(w * 0.17f, h * 0.7f); lineTo(w * 0.83f, h * 0.7f)
        moveTo(w * 0.42f, h * 0.82f); quadraticTo(w * 0.5f, h * 0.9f, w * 0.58f, h * 0.82f)
    }

/** Stacked cards: notifications from other apps. */
@Composable
fun StackIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addRoundRect(RoundRect(w * 0.16f, h * 0.36f, w * 0.84f, h * 0.82f, CornerRadius(w * 0.1f)))
        moveTo(w * 0.26f, h * 0.24f); lineTo(w * 0.74f, h * 0.24f)
        moveTo(w * 0.32f, h * 0.53f); lineTo(w * 0.68f, h * 0.53f)
        moveTo(w * 0.32f, h * 0.66f); lineTo(w * 0.54f, h * 0.66f)
    }

@Composable
fun MessageIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.28f, h * 0.2f)
        lineTo(w * 0.72f, h * 0.2f)
        quadraticTo(w * 0.84f, h * 0.2f, w * 0.84f, h * 0.32f)
        lineTo(w * 0.84f, h * 0.6f)
        quadraticTo(w * 0.84f, h * 0.72f, w * 0.72f, h * 0.72f)
        lineTo(w * 0.44f, h * 0.72f)
        lineTo(w * 0.28f, h * 0.84f)
        lineTo(w * 0.28f, h * 0.72f)
        quadraticTo(w * 0.16f, h * 0.72f, w * 0.16f, h * 0.6f)
        lineTo(w * 0.16f, h * 0.32f)
        quadraticTo(w * 0.16f, h * 0.2f, w * 0.28f, h * 0.2f)
        close()
        moveTo(w * 0.31f, h * 0.4f); lineTo(w * 0.69f, h * 0.4f)
        moveTo(w * 0.31f, h * 0.53f); lineTo(w * 0.55f, h * 0.53f)
    }

@Composable
fun MailIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addRoundRect(RoundRect(w * 0.12f, h * 0.22f, w * 0.88f, h * 0.78f, CornerRadius(w * 0.1f)))
        moveTo(w * 0.16f, h * 0.3f); lineTo(w * 0.5f, h * 0.54f); lineTo(w * 0.84f, h * 0.3f)
    }

@Composable
fun LockIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addRoundRect(RoundRect(w * 0.2f, h * 0.44f, w * 0.8f, h * 0.86f, CornerRadius(w * 0.1f)))
        moveTo(w * 0.33f, h * 0.44f)
        lineTo(w * 0.33f, h * 0.32f)
        cubicTo(w * 0.33f, h * 0.2f, w * 0.41f, h * 0.13f, w * 0.5f, h * 0.13f)
        cubicTo(w * 0.59f, h * 0.13f, w * 0.67f, h * 0.2f, w * 0.67f, h * 0.32f)
        lineTo(w * 0.67f, h * 0.44f)
        moveTo(w * 0.5f, h * 0.6f); lineTo(w * 0.5f, h * 0.7f)
    }

/** Two lines, the lower one shorter. */
@Composable
fun MenuIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.18f, h * 0.38f); lineTo(w * 0.82f, h * 0.38f)
        moveTo(w * 0.18f, h * 0.62f); lineTo(w * 0.58f, h * 0.62f)
    }

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.5f, h * 0.22f); lineTo(w * 0.5f, h * 0.78f)
        moveTo(w * 0.22f, h * 0.5f); lineTo(w * 0.78f, h * 0.5f)
    }

@Composable
fun ArrowUpIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.5f, h * 0.8f); lineTo(w * 0.5f, h * 0.2f)
        moveTo(w * 0.26f, h * 0.44f); lineTo(w * 0.5f, h * 0.2f); lineTo(w * 0.74f, h * 0.44f)
    }

/** Voice: four bars of different heights. */
@Composable
fun WaveIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.26f, h * 0.4f); lineTo(w * 0.26f, h * 0.6f)
        moveTo(w * 0.42f, h * 0.24f); lineTo(w * 0.42f, h * 0.76f)
        moveTo(w * 0.58f, h * 0.32f); lineTo(w * 0.58f, h * 0.68f)
        moveTo(w * 0.74f, h * 0.42f); lineTo(w * 0.74f, h * 0.58f)
    }

@Composable
fun MicIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) =
    LineIcon(color, modifier.size(size)) { w, h -> micPath(w, h) }

@Composable
fun MicOffIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        micPath(w, h)
        moveTo(w * 0.2f, h * 0.16f); lineTo(w * 0.8f, h * 0.84f)
    }

private fun Path.micPath(w: Float, h: Float) {
    addRoundRect(RoundRect(w * 0.38f, h * 0.14f, w * 0.62f, h * 0.58f, CornerRadius(w * 0.12f)))
    moveTo(w * 0.26f, h * 0.48f)
    quadraticTo(w * 0.26f, h * 0.74f, w * 0.5f, h * 0.74f)
    quadraticTo(w * 0.74f, h * 0.74f, w * 0.74f, h * 0.48f)
    moveTo(w * 0.5f, h * 0.74f); lineTo(w * 0.5f, h * 0.86f)
}

@Composable
fun CloseIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.28f, h * 0.28f); lineTo(w * 0.72f, h * 0.72f)
        moveTo(w * 0.72f, h * 0.28f); lineTo(w * 0.28f, h * 0.72f)
    }

@Composable
fun CopyIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addRoundRect(RoundRect(w * 0.34f, h * 0.34f, w * 0.84f, h * 0.84f, CornerRadius(w * 0.1f)))
        moveTo(w * 0.16f, h * 0.64f); lineTo(w * 0.16f, h * 0.26f)
        quadraticTo(w * 0.16f, h * 0.16f, w * 0.26f, h * 0.16f)
        lineTo(w * 0.64f, h * 0.16f)
    }

@Composable
fun SpeakerIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.14f, h * 0.4f); lineTo(w * 0.3f, h * 0.4f); lineTo(w * 0.48f, h * 0.22f)
        lineTo(w * 0.48f, h * 0.78f); lineTo(w * 0.3f, h * 0.6f); lineTo(w * 0.14f, h * 0.6f); close()
        moveTo(w * 0.62f, h * 0.38f); quadraticTo(w * 0.7f, h * 0.5f, w * 0.62f, h * 0.62f)
        moveTo(w * 0.72f, h * 0.26f); quadraticTo(w * 0.88f, h * 0.5f, w * 0.72f, h * 0.74f)
    }

/** Settings: two sliders with knobs. */
@Composable
fun SlidersIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.14f, h * 0.34f); lineTo(w * 0.52f, h * 0.34f)
        moveTo(w * 0.72f, h * 0.34f); lineTo(w * 0.86f, h * 0.34f)
        addOval(Rect(w * 0.52f, h * 0.24f, w * 0.72f, h * 0.44f))
        moveTo(w * 0.14f, h * 0.66f); lineTo(w * 0.28f, h * 0.66f)
        moveTo(w * 0.48f, h * 0.66f); lineTo(w * 0.86f, h * 0.66f)
        addOval(Rect(w * 0.28f, h * 0.56f, w * 0.48f, h * 0.76f))
    }

@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.38f, h * 0.2f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.38f, h * 0.8f)
    }

/** Opens outside the app. */
@Composable
fun ExternalIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.28f, h * 0.72f); lineTo(w * 0.72f, h * 0.28f)
        moveTo(w * 0.36f, h * 0.28f); lineTo(w * 0.72f, h * 0.28f); lineTo(w * 0.72f, h * 0.64f)
    }

@Composable
fun LogoutIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.46f, h * 0.18f); lineTo(w * 0.28f, h * 0.18f)
        quadraticTo(w * 0.18f, h * 0.18f, w * 0.18f, h * 0.28f)
        lineTo(w * 0.18f, h * 0.72f)
        quadraticTo(w * 0.18f, h * 0.82f, w * 0.28f, h * 0.82f)
        lineTo(w * 0.46f, h * 0.82f)
        moveTo(w * 0.42f, h * 0.5f); lineTo(w * 0.84f, h * 0.5f)
        moveTo(w * 0.68f, h * 0.34f); lineTo(w * 0.84f, h * 0.5f); lineTo(w * 0.68f, h * 0.66f)
    }

/** Sandbox: a lab flask. */
@Composable
fun FlaskIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.36f, h * 0.14f); lineTo(w * 0.64f, h * 0.14f)
        moveTo(w * 0.42f, h * 0.14f); lineTo(w * 0.42f, h * 0.4f)
        lineTo(w * 0.2f, h * 0.78f)
        quadraticTo(w * 0.16f, h * 0.86f, w * 0.26f, h * 0.86f)
        lineTo(w * 0.74f, h * 0.86f)
        quadraticTo(w * 0.84f, h * 0.86f, w * 0.8f, h * 0.78f)
        lineTo(w * 0.58f, h * 0.4f); lineTo(w * 0.58f, h * 0.14f)
        moveTo(w * 0.3f, h * 0.62f); lineTo(w * 0.7f, h * 0.62f)
    }

/** Attach a photo: a frame with a hill and the sun. */
@Composable
fun ImageIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addRoundRect(RoundRect(w * 0.14f, h * 0.18f, w * 0.86f, h * 0.82f, CornerRadius(w * 0.12f)))
        moveTo(w * 0.16f, h * 0.7f)
        lineTo(w * 0.38f, h * 0.5f)
        lineTo(w * 0.56f, h * 0.66f)
        lineTo(w * 0.66f, h * 0.58f)
        lineTo(w * 0.84f, h * 0.72f)
        addOval(Rect(w * 0.58f, h * 0.3f, w * 0.7f, h * 0.42f))
    }

/** Take a photo: a camera body with its lens. */
@Composable
fun CameraIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.24f, h * 0.3f)
        lineTo(w * 0.34f, h * 0.3f)
        lineTo(w * 0.4f, h * 0.2f)
        lineTo(w * 0.6f, h * 0.2f)
        lineTo(w * 0.66f, h * 0.3f)
        lineTo(w * 0.76f, h * 0.3f)
        quadraticTo(w * 0.86f, h * 0.3f, w * 0.86f, h * 0.4f)
        lineTo(w * 0.86f, h * 0.72f)
        quadraticTo(w * 0.86f, h * 0.82f, w * 0.76f, h * 0.82f)
        lineTo(w * 0.24f, h * 0.82f)
        quadraticTo(w * 0.14f, h * 0.82f, w * 0.14f, h * 0.72f)
        lineTo(w * 0.14f, h * 0.4f)
        quadraticTo(w * 0.14f, h * 0.3f, w * 0.24f, h * 0.3f)
        close()
        addOval(Rect(w * 0.37f, h * 0.41f, w * 0.63f, h * 0.67f))
    }

/** Try again: an open circle with an arrowhead. */
@Composable
fun RetryIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        arcTo(Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f), -60f, 300f, forceMoveTo = true)
        moveTo(w * 0.72f, h * 0.14f); lineTo(w * 0.68f, h * 0.27f); lineTo(w * 0.81f, h * 0.31f)
    }

@Composable
fun ArrowDownIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.5f, h * 0.8f)
        moveTo(w * 0.26f, h * 0.56f); lineTo(w * 0.5f, h * 0.8f); lineTo(w * 0.74f, h * 0.56f)
    }

/** A handset, for checking a phone number. */
@Composable
fun PhoneIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.3f, h * 0.16f)
        lineTo(w * 0.4f, h * 0.36f)
        lineTo(w * 0.32f, h * 0.44f)
        quadraticTo(w * 0.42f, h * 0.6f, w * 0.56f, h * 0.68f)
        lineTo(w * 0.64f, h * 0.6f)
        lineTo(w * 0.84f, h * 0.7f)
        lineTo(w * 0.78f, h * 0.82f)
        quadraticTo(w * 0.42f, h * 0.84f, w * 0.18f, h * 0.24f)
        close()
    }

/** A globe, for checking a website. */
@Composable
fun GlobeIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addOval(Rect(w * 0.14f, h * 0.14f, w * 0.86f, h * 0.86f))
        addOval(Rect(w * 0.34f, h * 0.14f, w * 0.66f, h * 0.86f))
        moveTo(w * 0.16f, h * 0.5f); lineTo(w * 0.84f, h * 0.5f)
    }

/** A magnifying glass, for a web search. */
@Composable
fun SearchIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addOval(Rect(w * 0.16f, h * 0.16f, w * 0.64f, h * 0.64f))
        moveTo(w * 0.58f, h * 0.58f); lineTo(w * 0.84f, h * 0.84f)
    }

/** An open book, for one of Wizjer's guides. */
@Composable
fun BookIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.5f, h * 0.3f)
        quadraticTo(w * 0.34f, h * 0.2f, w * 0.14f, h * 0.24f)
        lineTo(w * 0.14f, h * 0.76f)
        quadraticTo(w * 0.34f, h * 0.72f, w * 0.5f, h * 0.82f)
        quadraticTo(w * 0.66f, h * 0.72f, w * 0.86f, h * 0.76f)
        lineTo(w * 0.86f, h * 0.24f)
        quadraticTo(w * 0.66f, h * 0.2f, w * 0.5f, h * 0.3f)
        lineTo(w * 0.5f, h * 0.82f)
    }

/** A person, for the user's account. */
@Composable
fun PersonIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        addOval(Rect(w * 0.34f, h * 0.14f, w * 0.66f, h * 0.46f))
        moveTo(w * 0.18f, h * 0.86f)
        quadraticTo(w * 0.2f, h * 0.58f, w * 0.5f, h * 0.58f)
        quadraticTo(w * 0.8f, h * 0.58f, w * 0.82f, h * 0.86f)
    }

/** Something went wrong: a rounded triangle with an exclamation mark. */
@Composable
fun AlertIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon(color, modifier.size(size)) { w, h ->
        moveTo(w * 0.5f, h * 0.14f)
        lineTo(w * 0.88f, h * 0.82f)
        lineTo(w * 0.12f, h * 0.82f)
        close()
        moveTo(w * 0.5f, h * 0.4f); lineTo(w * 0.5f, h * 0.58f)
        moveTo(w * 0.5f, h * 0.69f); lineTo(w * 0.5f, h * 0.7f)
    }

@Composable
private fun LineIcon(color: Color, modifier: Modifier, build: Path.(Float, Float) -> Unit) {
    Canvas(modifier) {
        val path = Path().apply { build(size.width, size.height) }
        drawPath(path, color, style = iconStroke())
    }
}

private fun DrawScope.iconStroke() =
    Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
