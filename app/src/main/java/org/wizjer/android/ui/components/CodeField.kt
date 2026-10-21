package org.wizjer.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wizjer.android.ui.theme.WizjerFont

private val CodeDigitStyle = TextStyle(
    fontFamily = WizjerFont,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/**
 * One-time-code input drawn as [length] separate cells over a single hidden text field,
 * so typing, pasting and keyboard/autofill code suggestions all just work.
 * Increment [shakeKey] to shake the cells (e.g. after a wrong code).
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    focusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true,
    shakeKey: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        shake.animateTo(0f, keyframes {
            durationMillis = 420
            -14f at 60
            12f at 130
            -9f at 200
            6f at 270
            -3f at 340
        })
    }

    BasicTextField(
        // Cursor pinned to the end: cells fill left to right.
        value = TextFieldValue(value, TextRange(value.length)),
        onValueChange = {
            val digits = it.text.filter(Char::isDigit).take(length)
            if (digits != value) {
                onValueChange(digits)
                if (digits.length == length) onComplete(digits)
            }
        },
        enabled = enabled,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentType = ContentType.SmsOtpCode },
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onComplete(value) }),
        decorationBox = {
            Row(
                Modifier.graphicsLayer { translationX = shake.value.dp.toPx() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(length) { index ->
                    CodeCell(
                        digit = value.getOrNull(index),
                        active = focused && index == value.length.coerceAtMost(length - 1),
                        colors = colors,
                    )
                }
            }
        },
    )
}

@Composable
private fun CodeCell(digit: Char?, active: Boolean, colors: ColorScheme) {
    val lineColor by animateColorAsState(
        when {
            active -> colors.onBackground
            digit != null -> colors.onBackground.copy(alpha = 0.6f)
            else -> colors.outlineVariant
        },
        tween(200),
        label = "codeLine",
    )
    val lineHeight by animateDpAsState(if (active) 2.dp else 1.dp, tween(200), label = "codeLineHeight")

    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
            if (digit != null) {
                Text(digit.toString(), style = CodeDigitStyle, color = colors.onBackground)
            } else if (active) {
                Caret(colors.onBackground)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(40.dp)
                .height(lineHeight)
                .background(lineColor, CircleShape)
        )
    }
}

@Composable
private fun Caret(color: Color) {
    val blink = rememberInfiniteTransition(label = "caret")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    Box(
        Modifier
            .width(2.dp)
            .height(28.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape)
    )
}
