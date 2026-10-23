package org.wizjer.android.ui.components

/*
 * "Beam" avatars from Boring Avatars (https://boringavatars.com), ported from its React source
 * (utilities.ts and avatar-beam.tsx). MIT License, Copyright (c) 2021 boringdesigners.
 * Drawn on the phone: the name never goes to their service.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import org.wizjer.android.ui.theme.BrandMint
import org.wizjer.android.ui.theme.BrandMintLight
import org.wizjer.android.ui.theme.BrandTeal
import org.wizjer.android.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.roundToInt

/** The original's canvas: everything below is in these units. */
private const val SIZE = 36f

/** Brand tones from deep teal to pale mint, far enough apart that the face always stands out. */
private val Palette = listOf(BrandTeal, Color(0xFF1F6F5C), BrandMint, Color(0xFF9BE3C6), BrandMintLight)

/** A friendly face generated from [name], the same every time it's drawn. */
@Composable
fun BeamAvatar(name: String, modifier: Modifier = Modifier) {
    val beam = remember(name) { beam(name) }
    Canvas(modifier.clip(CircleShape)) {
        withTransform({ scale(size.width / SIZE, size.height / SIZE, pivot = Offset.Zero) }) { drawBeam(beam) }
    }
}

/** What a beam face looks like for one name (avatar-beam.tsx's generateData). */
internal data class Beam(
    val background: Color,
    val wrapper: Color,
    val face: Color,
    val wrapperTranslateX: Float,
    val wrapperTranslateY: Float,
    val wrapperRotate: Float,
    val wrapperScale: Float,
    val isMouthOpen: Boolean,
    val isCircle: Boolean,
    val eyeSpread: Float,
    val mouthSpread: Float,
    val faceRotate: Float,
    val faceTranslateX: Float,
    val faceTranslateY: Float,
)

internal fun beam(name: String, colors: List<Color> = Palette): Beam {
    val n = nameHash(name)
    val range = colors.size
    val wrapper = colors[(n % range).toInt()]
    val preTranslateX = unit(n, 10, 1)
    val wrapperTranslateX = if (preTranslateX < 5) preTranslateX + SIZE / 9 else preTranslateX
    val preTranslateY = unit(n, 10, 2)
    val wrapperTranslateY = if (preTranslateY < 5) preTranslateY + SIZE / 9 else preTranslateY
    return Beam(
        background = colors[((n + 13) % range).toInt()],
        wrapper = wrapper,
        face = contrast(wrapper),
        wrapperTranslateX = wrapperTranslateX,
        wrapperTranslateY = wrapperTranslateY,
        wrapperRotate = unit(n, 360),
        wrapperScale = 1 + unit(n, (SIZE / 12).toInt()) / 10,
        isMouthOpen = digit(n, 2) % 2 == 0L,
        isCircle = digit(n, 1) % 2 == 0L,
        eyeSpread = unit(n, 5),
        mouthSpread = unit(n, 3),
        faceRotate = unit(n, 10, 3),
        faceTranslateX = if (wrapperTranslateX > SIZE / 6) wrapperTranslateX / 2 else unit(n, 8, 1),
        faceTranslateY = if (wrapperTranslateY > SIZE / 6) wrapperTranslateY / 2 else unit(n, 7, 2),
    )
}

private fun DrawScope.drawBeam(beam: Beam) {
    drawRect(beam.background, size = Size(SIZE, SIZE))
    // SVG's "translate rotate scale": the calls below apply in the same order.
    withTransform({
        translate(beam.wrapperTranslateX, beam.wrapperTranslateY)
        rotate(beam.wrapperRotate, pivot = Offset(SIZE / 2, SIZE / 2))
        scale(beam.wrapperScale, beam.wrapperScale, pivot = Offset.Zero)
    }) {
        drawRoundRect(beam.wrapper, size = Size(SIZE, SIZE), cornerRadius = CornerRadius(if (beam.isCircle) SIZE / 2 else SIZE / 6))
    }
    withTransform({
        translate(beam.faceTranslateX, beam.faceTranslateY)
        rotate(beam.faceRotate, pivot = Offset(SIZE / 2, SIZE / 2))
    }) {
        val y = 19 + beam.mouthSpread
        if (beam.isMouthOpen) {
            // A smile: "M15 y c2 1 4 1 6 0".
            val mouth = Path().apply {
                moveTo(15f, y)
                cubicTo(17f, y + 1, 19f, y + 1, 21f, y)
            }
            drawPath(mouth, beam.face, style = Stroke(width = 1f, cap = StrokeCap.Round))
        } else {
            // An open grin: "M13,y a1,0.75 0 0,0 10,0", the lower half of an ellipse 10 wide.
            val mouth = Path().apply {
                moveTo(13f, y)
                arcTo(Rect(13f, y - 3.75f, 23f, y + 3.75f), 180f, -180f, false)
                close()
            }
            drawPath(mouth, beam.face)
        }
        drawOval(beam.face, topLeft = Offset(14 - beam.eyeSpread, 14f), size = Size(1.5f, 2f))
        drawOval(beam.face, topLeft = Offset(20 + beam.eyeSpread, 14f), size = Size(1.5f, 2f))
    }
}

/** JavaScript's 32-bit string hash from the original, made positive. */
internal fun nameHash(name: String): Long {
    var hash = 0
    for (c in name) hash = (hash shl 5) - hash + c.code
    return abs(hash.toLong())
}

/** The [place]th decimal digit of [number], counting from 0 at the ones. */
private fun digit(number: Long, place: Int): Long {
    var n = number
    repeat(place) { n /= 10 }
    return n % 10
}

/** [number] modulo [range], negated when the digit at [index] (if given) is even. */
private fun unit(number: Long, range: Int, index: Int = 0): Float {
    val value = (number % range).toFloat()
    return if (index != 0 && digit(number, index) % 2 == 0L) -value else value
}

/** Dark ink on light colours, white on dark ones (YIQ brightness, as in the original). */
private fun contrast(color: Color): Color {
    fun channel(value: Float) = (value * 255).roundToInt()
    val yiq = (channel(color.red) * 299 + channel(color.green) * 587 + channel(color.blue) * 114) / 1000.0
    return if (yiq >= 128) Ink else Color.White
}
