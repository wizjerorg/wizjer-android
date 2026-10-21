package org.wizjer.android.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.wizjer.android.R
import org.wizjer.android.ui.components.ArrowUpIcon
import org.wizjer.android.ui.components.WaveIcon

/**
 * The message box. Its round button changes with what you are doing:
 * empty → start a voice call, text typed → send.
 */
@Composable
fun Composer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onCall: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hasText = value.text.isNotBlank()

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
            cursorBrush = SolidColor(colors.onBackground),
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            stringResource(R.string.composer_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))

        val sendLabel = stringResource(R.string.composer_send)
        val callLabel = stringResource(R.string.composer_call)
        AnimatedContent(
            targetState = hasText,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.7f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.7f))
            },
            label = "composerAction",
        ) { send ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(role = Role.Button, onClick = if (send) onSend else onCall)
                    .semantics { contentDescription = if (send) sendLabel else callLabel },
                contentAlignment = Alignment.Center,
            ) {
                if (send) ArrowUpIcon(colors.onPrimary) else WaveIcon(colors.onPrimary)
            }
        }
    }
}
