package org.wizjer.android.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wizjer.android.R
import org.wizjer.android.data.ai.Assistant
import org.wizjer.android.data.ai.ImagePrep
import org.wizjer.android.data.ai.PreparedImage
import org.wizjer.android.ui.components.CloseIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.RetryIcon

/** A photo of a chat, loaded on demand; a soft placeholder while it comes, tap to retry if it failed. */
@Composable
fun ChatPhoto(
    id: String,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val images = remember { Assistant.get(context).images }
    var attempt by remember { mutableIntStateOf(0) }
    val bitmap by produceState<Result<Bitmap>?>(images.cached(id, maxSide)?.let { Result.success(it) }, id, maxSide, attempt) {
        if (value == null) value = images.load(id, maxSide)?.let { Result.success(it) } ?: Result.failure(NoSuchElementException(id))
    }
    PhotoBox(bitmap, contentScale, modifier, onClick = onClick, onRetry = { attempt++ })
}

/** A photo prepared in the composer, decoded small for its thumbnail. */
@Composable
fun PreparedPhoto(image: PreparedImage, maxSide: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<Result<Bitmap>?>(null, image) {
        value = withContext(Dispatchers.Default) { decodeSmall(image.bytes, maxSide) }?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException())
    }
    PhotoBox(bitmap, ContentScale.Crop, modifier, onClick = null, onRetry = {})
}

@Composable
private fun PhotoBox(
    bitmap: Result<Bitmap>?,
    contentScale: ContentScale,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.ai_photo)
    val loaded = bitmap?.getOrNull()
    Box(
        modifier
            .background(colors.surfaceContainer)
            .then(
                when {
                    loaded != null && onClick != null -> Modifier.clickable(role = Role.Image, onClick = onClick)
                    bitmap?.isFailure == true -> Modifier.clickable(role = Role.Button, onClick = onRetry)
                    else -> Modifier
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loaded != null -> Image(loaded.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            bitmap?.isFailure == true -> RetryIcon(colors.onSurfaceVariant)
        }
    }
}

/** The photos of a user's message, above its bubble: one large, or a tidy grid of squares. */
@Composable
fun MessagePhotos(ids: List<String>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    if (ids.size == 1) {
        ChatPhoto(
            ids.first(),
            maxSide = THUMB_LARGE,
            modifier = modifier
                .width(220.dp)
                .aspectRatio(0.8f)
                .clip(shape),
            onClick = { onOpen(ids.first()) },
        )
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ids.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { id ->
                    ChatPhoto(id, maxSide = THUMB_SMALL, modifier = Modifier.size(GRID_CELL).clip(shape), onClick = { onOpen(id) })
                }
            }
        }
    }
}

private val GRID_CELL: Dp = 112.dp
private const val THUMB_SMALL = 384
private const val THUMB_LARGE = 640

/** A photo on a black background: pinch or double-tap to zoom, the cross or Back to close. */
@Composable
fun PhotoViewer(id: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            ChatPhoto(
                id,
                maxSide = ImagePrep.MAX_SIDE,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            CloseButton(onDismiss)
        }
    }
}

@Composable
private fun BoxScope.CloseButton(onDismiss: () -> Unit) {
    IconCircleButton(
        onDismiss,
        stringResource(R.string.action_close),
        Modifier
            .align(Alignment.TopEnd)
            .systemBarsPadding()
            .padding(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.4f)),
    ) { CloseIcon(Color.White) }
}

/** Uploading: a dimmed photo with a spinner. Failed: a retry arrow. */
@Composable
fun BoxScope.UploadOverlay(uploading: Boolean, failed: Boolean) {
    if (!uploading && !failed) return
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            RetryIcon(Color.White, size = 22.dp)
        } else {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, trackColor = Color.Transparent, strokeWidth = 2.dp)
        }
    }
}

private fun decodeSmall(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
