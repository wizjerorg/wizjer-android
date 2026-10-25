package org.wizjer.android.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.wizjer.android.R
import org.wizjer.android.data.ai.Attachment
import kotlinx.coroutines.launch
import org.wizjer.android.ui.components.ArrowUpIcon
import org.wizjer.android.ui.components.CameraIcon
import org.wizjer.android.ui.components.CloseIcon
import org.wizjer.android.ui.components.ImageIcon
import org.wizjer.android.ui.components.WaveIcon

/** What the composer's round button does now. */
enum class ComposerAction { Call, Send, Wait, Stop, Stopping }

/**
 * The message box: photos waiting to go on top, then the photo button, the text and one round
 * button that changes with what you're doing: empty → voice call, something to send → send
 * (dimmed until photos have uploaded), Wizjer writing → stop.
 */
@Composable
fun Composer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    attachments: List<Attachment>,
    action: ComposerAction,
    onAttach: () -> Unit,
    onRemovePhoto: (Attachment) -> Unit,
    onRetryPhoto: (Attachment) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCall: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer),
    ) {
        AnimatedVisibility(attachments.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEachIndexed { index, attachment ->
                    PhotoChip(attachment, index, onRemove = { onRemovePhoto(attachment) }, onRetry = { onRetryPhoto(attachment) })
                }
            }
        }
        Row(
            Modifier.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val attachLabel = stringResource(R.string.composer_attach)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAttach)
                    .semantics { contentDescription = attachLabel },
                contentAlignment = Alignment.Center,
            ) {
                ImageIcon(colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
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
            ActionButton(action, onSend, onStop, onCall)
        }
    }
}

@Composable
private fun ActionButton(action: ComposerAction, onSend: () -> Unit, onStop: () -> Unit, onCall: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labels = mapOf(
        ComposerAction.Call to stringResource(R.string.composer_call),
        ComposerAction.Send to stringResource(R.string.composer_send),
        ComposerAction.Wait to stringResource(R.string.composer_send),
        ComposerAction.Stop to stringResource(R.string.composer_stop),
        ComposerAction.Stopping to stringResource(R.string.composer_stop),
    )
    // Send and its dimmed state are one look; only real changes of meaning animate.
    val look = when (action) {
        ComposerAction.Wait -> ComposerAction.Send
        ComposerAction.Stopping -> ComposerAction.Stop
        else -> action
    }
    AnimatedContent(
        targetState = look,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.7f))
                .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.7f))
        },
        label = "composerAction",
    ) { shown ->
        val enabled = action != ComposerAction.Wait && action != ComposerAction.Stopping
        Box(
            Modifier
                .size(44.dp)
                .alpha(if (action == ComposerAction.Wait) 0.35f else 1f)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = when (shown) {
                        ComposerAction.Stop -> onStop
                        ComposerAction.Send -> onSend
                        else -> onCall
                    },
                )
                .semantics { contentDescription = labels.getValue(action) },
            contentAlignment = Alignment.Center,
        ) {
            when (shown) {
                ComposerAction.Stop -> if (action == ComposerAction.Stopping) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, trackColor = Color.Transparent, strokeWidth = 2.dp)
                } else {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.onPrimary),
                    )
                }
                ComposerAction.Send -> ArrowUpIcon(colors.onPrimary)
                else -> WaveIcon(colors.onPrimary)
            }
        }
    }
}

/** A photo waiting to be sent: a spinner while it uploads, a retry arrow if that failed, a cross to remove it. */
@Composable
private fun PhotoChip(attachment: Attachment, index: Int, onRemove: () -> Unit, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val failed = attachment.error != null
    val retryLabel = stringResource(R.string.composer_photo_failed)
    val photoLabel = stringResource(R.string.composer_photo, index + 1)
    Box(Modifier.size(72.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp, end = 6.dp)
                .size(66.dp)
                .clip(shape)
                .background(colors.background)
                .clickable(enabled = failed, role = Role.Button, onClick = onRetry)
                .semantics { contentDescription = if (failed) retryLabel else photoLabel },
        ) {
            val image = attachment.image
            val id = attachment.id
            when {
                image != null -> PreparedPhoto(image, maxSide = 256, modifier = Modifier.matchParentSize())
                id != null -> ChatPhoto(id, maxSide = 256, modifier = Modifier.matchParentSize())
            }
            UploadOverlay(uploading = !attachment.ready && !failed, failed = failed)
        }
        val removeLabel = stringResource(R.string.composer_remove_photo)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeLabel },
            contentAlignment = Alignment.Center,
        ) {
            CloseIcon(colors.onPrimary, size = 16.dp)
        }
    }
}

/** Where a photo comes from: the camera or the phone's photos. Two large rows, easy to hit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 20.dp)) {
            SourceRow(stringResource(R.string.composer_take_photo), onClick = { closeThen(onCamera) }) { CameraIcon(it) }
            SourceRow(stringResource(R.string.composer_choose_photo), onClick = { closeThen(onGallery) }) { ImageIcon(it) }
        }
    }
}

@Composable
private fun SourceRow(label: String, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { icon(colors.onBackground) }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
    }
}
