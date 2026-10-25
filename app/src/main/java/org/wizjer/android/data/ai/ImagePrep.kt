package org.wizjer.android.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** A photo ready to upload: a JPEG of [width]×[height] pixels. */
class PreparedImage(val bytes: ByteArray, val mediaType: String, val width: Int, val height: Int)

/**
 * Makes a picked photo ready for the assistant: upright, at most [MAX_SIDE] px on its long edge
 * (the model sees no more detail than that) and a JPEG under the server's 2 MB limit.
 */
internal object ImagePrep {
    const val MAX_SIDE = 1568
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** Null when the file isn't an image the phone can read. */
    fun prepare(context: Context, uri: Uri): PreparedImage? = try {
        decode(context, uri)?.let { compress(opaque(it)) }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun decode(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Applies the photo's rotation itself; GIFs give their first frame.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeLegacy(context, uri)
        }

    private fun decodeLegacy(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val sampled = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(sampled.width, sampled.height))
        if (rotation == 0f && scale == 1f) return sampled
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        return Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
    }

    /** JPEG has no transparency: see-through parts (stickers, logos) go white, not black. */
    private fun opaque(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return flat
    }

    private fun compress(bitmap: Bitmap): PreparedImage? {
        var image = bitmap
        repeat(4) {
            for (quality in intArrayOf(88, 78, 68)) {
                val out = ByteArrayOutputStream()
                image.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= MAX_BYTES) return PreparedImage(out.toByteArray(), "image/jpeg", image.width, image.height)
            }
            image = Bitmap.createScaledBitmap(image, (image.width * 0.75f).roundToInt(), (image.height * 0.75f).roundToInt(), true)
        }
        return null
    }
}

/**
 * Files the camera app saves chat photos into, in the app's cache: each photo is read once
 * (by [ImagePrep]), so older ones are cleared on the next capture and at sign-out.
 */
object CameraPhotos {
    private const val KEEP_MILLIS = 10 * 60_000L

    /** A new file for the camera app to write to, as a `content://` address it is allowed to use. */
    fun newPhoto(context: Context): Uri {
        val dir = dir(context).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > KEEP_MILLIS }?.forEach { it.delete() }
        val file = File(dir, "photo-$now.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
    }

    fun clear(context: Context) {
        dir(context).deleteRecursively()
    }

    private fun dir(context: Context) = File(context.cacheDir, "camera")
}
