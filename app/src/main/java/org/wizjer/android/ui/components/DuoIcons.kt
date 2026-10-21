package org.wizjer.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Wizjer's own two-tone icons: teal lines with one solid mint detail, echoing the logo's eye.
// Drawn on a unit grid (0..1) so they stay crisp at any size.

// The three "Sprawdź…" icons share one recipe: a teal object with a mint ✓ badge = "checked".

/** A message bubble with a mint ✓ badge. */
@Composable
fun MessageDuoIcon(modifier: Modifier = Modifier, size: Dp = 30.dp) = DuoIcon(modifier, size) { line, accent, paper ->
    val w = this.size.width
    val bubble = Path().apply {
        addRoundRect(RoundRect(w * 0.08f, w * 0.12f, w * 0.78f, w * 0.62f, CornerRadius(w * 0.14f)))
        moveTo(w * 0.24f, w * 0.62f); lineTo(w * 0.18f, w * 0.8f); lineTo(w * 0.4f, w * 0.62f)
    }
    drawPath(bubble, line, style = stroke())
    drawLine(line, Offset(w * 0.24f, w * 0.32f), Offset(w * 0.6f, w * 0.32f), strokeWidth = strokePx(), cap = StrokeCap.Round)
    drawLine(line, Offset(w * 0.24f, w * 0.45f), Offset(w * 0.46f, w * 0.45f), strokeWidth = strokePx(), cap = StrokeCap.Round)
    checkBadge(Offset(w * 0.72f, w * 0.72f), line, accent, paper)
}

/** The classic link glyph (two open loops joined by a bar), tilted like 🔗, with a mint ✓ badge. */
@Composable
fun LinkDuoIcon(modifier: Modifier = Modifier, size: Dp = 30.dp) = DuoIcon(modifier, size) { line, accent, paper ->
    val w = this.size.width
    val cx = w * 0.44f
    val cy = w * 0.44f
    val h = w * 0.16f      // half the loop height
    val reach = w * 0.38f  // from the centre to the far end of each loop
    val gap = w * 0.06f    // half the opening between the loops
    val link = Path().apply {
        // Left loop, open towards the middle.
        moveTo(cx - gap, cy - h)
        lineTo(cx - reach + h, cy - h)
        arcTo(Rect(cx - reach, cy - h, cx - reach + 2 * h, cy + h), -90f, -180f, false)
        lineTo(cx - gap, cy + h)
        // Right loop, mirrored.
        moveTo(cx + gap, cy - h)
        lineTo(cx + reach - h, cy - h)
        arcTo(Rect(cx + reach - 2 * h, cy - h, cx + reach, cy + h), -90f, 180f, false)
        lineTo(cx + gap, cy + h)
        // The bar joining them.
        moveTo(cx - w * 0.17f, cy)
        lineTo(cx + w * 0.17f, cy)
    }
    rotate(-45f, pivot = Offset(cx, cy)) { drawPath(link, line, style = stroke()) }
    checkBadge(Offset(w * 0.76f, w * 0.76f), line, accent, paper)
}

/** A phone with a mint ✓ badge: "this number is verified". */
@Composable
fun PhoneCheckDuoIcon(modifier: Modifier = Modifier, size: Dp = 30.dp) = DuoIcon(modifier, size) { line, accent, paper ->
    val w = this.size.width
    drawRoundRect(
        line,
        topLeft = Offset(w * 0.2f, w * 0.08f),
        size = Size(w * 0.42f, w * 0.78f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = stroke(),
    )
    drawLine(line, Offset(w * 0.36f, w * 0.18f), Offset(w * 0.46f, w * 0.18f), strokeWidth = strokePx(), cap = StrokeCap.Round)
    checkBadge(Offset(w * 0.7f, w * 0.72f), line, accent, paper)
}

/** Mint disc with a teal tick, cut out from what's behind it by a thin gap. */
private fun DrawScope.checkBadge(center: Offset, line: Color, accent: Color, paper: Color) {
    val w = size.width
    drawCircle(paper, radius = w * 0.24f, center = center)
    drawCircle(accent, radius = w * 0.19f, center = center)
    val check = Path().apply {
        moveTo(center.x - w * 0.08f, center.y)
        lineTo(center.x - w * 0.02f, center.y + w * 0.06f)
        lineTo(center.x + w * 0.09f, center.y - w * 0.06f)
    }
    drawPath(check, line, style = stroke())
}

/** A lifebuoy with mint stripes, like the real thing. */
@Composable
fun LifebuoyDuoIcon(modifier: Modifier = Modifier, size: Dp = 30.dp) = DuoIcon(modifier, size) { line, accent, _ ->
    val w = this.size.width
    val center = Offset(w / 2, w / 2)
    val outer = w * 0.4f
    val inner = w * 0.18f
    val ringRadius = (outer + inner) / 2
    val ringWidth = outer - inner
    // Four mint stripes across the ring.
    for (start in listOf(-112.5f, -22.5f, 67.5f, 157.5f)) {
        drawArc(
            accent,
            startAngle = start,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2, ringRadius * 2),
            style = Stroke(width = ringWidth),
        )
    }
    drawCircle(line, radius = outer, center = center, style = stroke())
    drawCircle(line, radius = inner, center = center, style = stroke())
}

@Composable
private fun DuoIcon(
    modifier: Modifier,
    size: Dp,
    draw: DrawScope.(line: Color, accent: Color, paper: Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The "paper" colour cuts a small gap around badges so they read as separate shapes.
    val paper = colors.surfaceContainer
    Canvas(modifier.size(size)) { draw(colors.primary, colors.secondary, paper) }
}

private fun DrawScope.strokePx() = 1.9.dp.toPx()

private fun DrawScope.stroke() = Stroke(width = strokePx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
