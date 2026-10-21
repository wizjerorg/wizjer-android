package org.wizjer.android.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wizjer.android.R
import org.wizjer.android.ui.theme.WizjerFont

private val DigitStyle = TextStyle(
    fontFamily = WizjerFont,
    fontSize = 40.sp,
    lineHeight = 48.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.Center,
)

/**
 * Day / month / year as three large digit fields. Focus moves forward as each fills
 * (a lone "4" in the day becomes "04"), and backspace on an empty field steps back.
 */
@Composable
fun BirthDateField(
    day: String,
    month: String,
    year: String,
    onDayChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onDone: () -> Unit,
    dayFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val monthFocus = remember { FocusRequester() }
    val yearFocus = remember { FocusRequester() }
    val separator = stringResource(R.string.date_separator)

    Row(modifier, verticalAlignment = Alignment.Top) {
        DateSegment(
            value = day,
            placeholder = stringResource(R.string.date_day),
            maxLength = 2,
            onValueChange = { v ->
                val padded = if (v.length == 1 && v[0] > '3') "0$v" else v
                onDayChange(padded)
                if (padded.length == 2) monthFocus.requestFocus()
            },
            focusRequester = dayFocus,
            imeAction = ImeAction.Next,
            onIme = { monthFocus.requestFocus() },
            onBackspaceWhenEmpty = {},
        )
        Separator(separator)
        DateSegment(
            value = month,
            placeholder = stringResource(R.string.date_month),
            maxLength = 2,
            onValueChange = { v ->
                val padded = if (v.length == 1 && v[0] > '1') "0$v" else v
                onMonthChange(padded)
                if (padded.length == 2) yearFocus.requestFocus()
            },
            focusRequester = monthFocus,
            imeAction = ImeAction.Next,
            onIme = { yearFocus.requestFocus() },
            onBackspaceWhenEmpty = {
                onDayChange(day.dropLast(1))
                dayFocus.requestFocus()
            },
        )
        Separator(separator)
        DateSegment(
            value = year,
            placeholder = stringResource(R.string.date_year),
            maxLength = 4,
            onValueChange = onYearChange,
            focusRequester = yearFocus,
            imeAction = ImeAction.Done,
            onIme = onDone,
            onBackspaceWhenEmpty = {
                onMonthChange(month.dropLast(1))
                monthFocus.requestFocus()
            },
        )
    }
}

@Composable
private fun Separator(text: String) {
    Text(
        text,
        style = DigitStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun DateSegment(
    value: String,
    placeholder: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onIme: () -> Unit,
    onBackspaceWhenEmpty: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style = DigitStyle.copy(color = colors.onBackground)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Wide enough for the placeholder or a full set of digits, whichever is wider.
    val width = remember(placeholder, maxLength) {
        val px = maxOf(
            measurer.measure(placeholder, style).size.width,
            measurer.measure("0".repeat(maxLength), style).size.width,
        )
        with(density) { px.toDp() + 4.dp }
    }
    var focused by remember { mutableStateOf(false) }
    val lineColor by animateColorAsState(
        if (focused) colors.onBackground else colors.outlineVariant, tween(250), label = "segmentLine",
    )
    val lineHeight by animateDpAsState(if (focused) 2.dp else 1.dp, tween(250), label = "segmentLineHeight")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            // Cursor pinned to the end: these fields are typed left to right only.
            value = TextFieldValue(value, TextRange(value.length)),
            onValueChange = { onValueChange(it.text.filter(Char::isDigit).take(maxLength)) },
            modifier = Modifier
                .width(width)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Backspace && value.isEmpty()) {
                        onBackspaceWhenEmpty()
                        true
                    } else {
                        false
                    }
                }
                .semantics { contentDescription = placeholder },
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(colors.onBackground),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = style.copy(color = colors.onSurfaceVariant.copy(alpha = 0.3f)))
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(width)
                .height(lineHeight)
                .background(lineColor, CircleShape)
        )
    }
}
