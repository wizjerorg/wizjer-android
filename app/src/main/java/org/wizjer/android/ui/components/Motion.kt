package org.wizjer.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Fades and lifts element [index] of [count] in as [progress] runs 0→1. Each element takes
 * 55% of the timeline and they start evenly spaced, so they overlap into one continuous motion.
 */
fun Modifier.staggered(progress: Animatable<Float, *>, index: Int, count: Int) = graphicsLayer {
    val span = 0.55f
    val step = if (count > 1) (1f - span) / (count - 1) else 0f
    val local = ((progress.value - index * step) / span).coerceIn(0f, 1f)
    val p = EmphasizedEasing.transform(local)
    alpha = p
    translationY = (1f - p) * 24.dp.toPx()
}
